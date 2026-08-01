package com.community.ai.service;

import com.community.common.constant.RoleConstants;
import com.community.common.exception.BizException;
import com.community.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * 失物招领：分类、认领申请与核实
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LostBizService {

    private final JdbcTemplate jdbcTemplate;

    private void requireTenant() {
        if (TenantContext.getTenantId() == null) {
            throw new BizException(400, "缺少社区租户");
        }
    }

    private void assertStaff() {
        String role = TenantContext.getRole();
        if (!RoleConstants.isTenantStaff(role) && !RoleConstants.isPlatformAdmin(role)) {
            throw new BizException(403, "仅物业/网格员可操作");
        }
    }

    private void assertAdmin() {
        String role = TenantContext.getRole();
        if (!RoleConstants.isPropertyAdmin(role) && !RoleConstants.isPlatformAdmin(role)) {
            throw new BizException(403, "仅物业管理员可操作");
        }
    }

    /* ========== 分类 ========== */

    public List<Map<String, Object>> listCategories(Boolean enabledOnly) {
        requireTenant();
        String sql = "select id,tenant_id as tenantId,name,sort_no as sortNo,status,remark,create_time as createTime "
                + "from lost_category where tenant_id=? ";
        List<Object> args = new ArrayList<>();
        args.add(TenantContext.getTenantId());
        if (Boolean.TRUE.equals(enabledOnly)) {
            sql += "and status=1 ";
        }
        sql += "order by sort_no asc, id asc";
        return jdbcTemplate.queryForList(sql, args.toArray());
    }

    public Map<String, Object> saveCategory(Map<String, Object> body) {
        assertAdmin();
        requireTenant();
        Long tid = TenantContext.getTenantId();
        Long id = toLong(body.get("id"));
        String name = str(body.get("name"), null);
        if (!StringUtils.hasText(name)) {
            throw new BizException(400, "分类名称不能为空");
        }
        int sortNo = toInt(body.get("sortNo"), 0);
        int status = toInt(body.get("status"), 1);
        String remark = str(body.get("remark"), null);
        if (id != null && id > 0) {
            jdbcTemplate.update(
                    "update lost_category set name=?,sort_no=?,status=?,remark=? where id=? and tenant_id=?",
                    name, sortNo, status, remark, id, tid);
        } else {
            jdbcTemplate.update(
                    "insert into lost_category(tenant_id,name,sort_no,status,remark,create_time) values(?,?,?,?,?,now())",
                    tid, name, sortNo, status, remark);
            id = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        }
        return jdbcTemplate.queryForMap(
                "select id,tenant_id as tenantId,name,sort_no as sortNo,status,remark,create_time as createTime from lost_category where id=?",
                id);
    }

    public void deleteCategory(Long id) {
        assertAdmin();
        requireTenant();
        jdbcTemplate.update("delete from lost_category where id=? and tenant_id=?", id, TenantContext.getTenantId());
    }

    /* ========== 认领 ========== */

    public Map<String, Object> submitClaim(Long lostId, Map<String, Object> body) {
        requireTenant();
        Long tid = TenantContext.getTenantId();
        Long uid = TenantContext.getUserId();
        if (uid == null) {
            throw new BizException(401, "请先登录");
        }
        Map<String, Object> lost;
        try {
            lost = jdbcTemplate.queryForMap(
                    "select * from lost_found where id=? and tenant_id=?", lostId, tid);
        } catch (Exception e) {
            throw new BizException(404, "招领信息不存在");
        }
        if (!"open".equals(String.valueOf(lost.get("status")))) {
            throw new BizException(400, "该信息已结束，无法认领");
        }
        Long publisher = toLong(lost.get("user_id"));
        if (Objects.equals(publisher, uid)) {
            throw new BizException(400, "不能认领自己发布的信息");
        }
        Integer dup = jdbcTemplate.queryForObject(
                "select count(1) from lost_claim where tenant_id=? and lost_id=? and user_id=? and status='pending'",
                Integer.class, tid, lostId, uid);
        if (dup != null && dup > 0) {
            throw new BizException(400, "您已提交认领申请，请等待核实");
        }
        String userName = str(body == null ? null : body.get("userName"), null);
        String phone = str(body == null ? null : body.get("phone"), null);
        String remark = str(body == null ? null : body.get("remark"), null);
        if (!StringUtils.hasText(userName) || !StringUtils.hasText(phone)) {
            try {
                Map<String, Object> u = jdbcTemplate.queryForMap(
                        "select nickname, real_name as realName, phone from sys_user where id=?", uid);
                if (!StringUtils.hasText(userName)) {
                    userName = str(u.get("realName"), str(u.get("nickname"), null));
                }
                if (!StringUtils.hasText(phone)) {
                    phone = str(u.get("phone"), null);
                }
            } catch (Exception ignored) {
            }
        }
        if (!StringUtils.hasText(phone)) {
            throw new BizException(400, "请填写联系电话");
        }
        jdbcTemplate.update(
                "insert into lost_claim(tenant_id,lost_id,user_id,user_name,phone,remark,status,create_time) "
                        + "values(?,?,?,?,?,?,'pending',now())",
                tid, lostId, uid, userName, phone, remark);
        Long claimId = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        try {
            jdbcTemplate.update(
                    "update lost_found set claim_count=ifnull(claim_count,0)+1 where id=? and tenant_id=?",
                    lostId, tid);
        } catch (Exception ignored) {
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", claimId);
        resp.put("status", "pending");
        return resp;
    }

    public List<Map<String, Object>> listClaims(Long lostId, String status, String keyword) {
        assertStaff();
        requireTenant();
        String sql = "select c.id,c.tenant_id as tenantId,c.lost_id as lostId,c.user_id as userId,"
                + "c.user_name as userName,c.phone,c.remark,c.status,c.audit_remark as auditRemark,"
                + "c.auditor_id as auditorId,c.audit_time as auditTime,c.create_time as createTime,"
                + "l.title as lostTitle,l.item_type as itemType,l.category,l.status as lostStatus,"
                + "l.image_url as imageUrl,l.contact as publisherContact,l.contact_name as publisherName "
                + "from lost_claim c left join lost_found l on l.id=c.lost_id "
                + "where c.tenant_id=? ";
        List<Object> args = new ArrayList<>();
        args.add(TenantContext.getTenantId());
        if (lostId != null) {
            sql += "and c.lost_id=? ";
            args.add(lostId);
        }
        if (StringUtils.hasText(status)) {
            sql += "and c.status=? ";
            args.add(status.trim());
        }
        if (StringUtils.hasText(keyword)) {
            sql += "and (c.user_name like ? or c.phone like ? or l.title like ?) ";
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        sql += "order by case when c.status='pending' then 0 else 1 end, c.id desc limit 500";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args.toArray());
        for (Map<String, Object> m : rows) {
            normalizeTime(m, "createTime");
            normalizeTime(m, "auditTime");
            String it = String.valueOf(m.getOrDefault("itemType", "found"));
            m.put("itemTypeLabel", "lost".equalsIgnoreCase(it) ? "失物" : "招领");
            m.put("statusLabel", claimStatusLabel(String.valueOf(m.get("status"))));
        }
        return rows;
    }

    public Map<String, Object> verifyClaim(Long claimId, Map<String, Object> body) {
        assertAdmin();
        requireTenant();
        Long tid = TenantContext.getTenantId();
        Map<String, Object> claim;
        try {
            claim = jdbcTemplate.queryForMap(
                    "select * from lost_claim where id=? and tenant_id=?", claimId, tid);
        } catch (Exception e) {
            throw new BizException(404, "认领申请不存在");
        }
        if (!"pending".equals(String.valueOf(claim.get("status")))) {
            throw new BizException(400, "该申请已处理");
        }
        String action = str(body == null ? null : body.get("status"), null);
        if (!"approved".equals(action) && !"rejected".equals(action)) {
            throw new BizException(400, "status 需为 approved 或 rejected");
        }
        String auditRemark = str(body == null ? null : body.get("auditRemark"), null);
        Long auditorId = TenantContext.getUserId();
        jdbcTemplate.update(
                "update lost_claim set status=?,audit_remark=?,auditor_id=?,audit_time=now() where id=? and tenant_id=?",
                action, auditRemark, auditorId, claimId, tid);
        Long lostId = toLong(claim.get("lost_id"));
        if ("approved".equals(action) && lostId != null) {
            jdbcTemplate.update(
                    "update lost_found set status='found' where id=? and tenant_id=?",
                    lostId, tid);
            // 同信息其他待审认领自动驳回
            jdbcTemplate.update(
                    "update lost_claim set status='rejected',audit_remark=?,auditor_id=?,audit_time=now() "
                            + "where tenant_id=? and lost_id=? and status='pending' and id<>?",
                    "已有认领通过核实", auditorId, tid, lostId, claimId);
            try {
                jdbcTemplate.update(
                        "insert into sys_message(tenant_id,user_id,title,content,msg_type,read_flag,create_time) values(?,?,?,?,?,?,now())",
                        tid, claim.get("user_id"), "认领核实通过",
                        "您对「" + str(loadLostTitle(lostId), "物品") + "」的认领申请已通过核实",
                        "lost_claim", 0);
            } catch (Exception ignored) {
            }
        } else if ("rejected".equals(action)) {
            try {
                jdbcTemplate.update(
                        "insert into sys_message(tenant_id,user_id,title,content,msg_type,read_flag,create_time) values(?,?,?,?,?,?,now())",
                        tid, claim.get("user_id"), "认领核实未通过",
                        StringUtils.hasText(auditRemark) ? auditRemark : "您的认领申请未通过，请联系物业",
                        "lost_claim", 0);
            } catch (Exception ignored) {
            }
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", claimId);
        resp.put("status", action);
        return resp;
    }

    private String loadLostTitle(Long lostId) {
        try {
            return jdbcTemplate.queryForObject("select title from lost_found where id=?", String.class, lostId);
        } catch (Exception e) {
            return null;
        }
    }

    private static String claimStatusLabel(String s) {
        if ("pending".equals(s)) return "待核实";
        if ("approved".equals(s)) return "已通过";
        if ("rejected".equals(s)) return "已驳回";
        return s;
    }

    private static void normalizeTime(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v instanceof java.sql.Timestamp ts) {
            m.put(key, ts.toLocalDateTime().toString().replace('T', ' '));
        }
    }

    private static String str(Object v, String def) {
        if (v == null) return def;
        String s = String.valueOf(v).trim();
        return s.isEmpty() || "null".equalsIgnoreCase(s) ? def : s;
    }

    private static int toInt(Object v, int def) {
        if (v == null) return def;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            return def;
        }
    }

    private static Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }
}
