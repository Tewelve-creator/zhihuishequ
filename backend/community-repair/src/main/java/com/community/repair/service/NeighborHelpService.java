package com.community.repair.service;

import com.community.common.constant.RoleConstants;
import com.community.common.exception.BizException;
import com.community.common.tenant.TenantContext;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 邻里互助帖：与邻里倾诉（appeal 朋友圈）相互独立
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NeighborHelpService {

    public static final String ST_NORMAL = "NORMAL";
    public static final String ST_OFF = "OFF";

    private final JdbcTemplate jdbc;

    @PostConstruct
    public void ensure() {
        try {
            jdbc.execute("""
                    create table if not exists neighbor_help_post (
                      id bigint primary key auto_increment,
                      tenant_id bigint not null,
                      post_no varchar(32) not null,
                      user_id bigint null,
                      owner_name varchar(64) null,
                      house_id bigint null,
                      house_label varchar(128) null,
                      help_type varchar(32) not null default '其他',
                      title varchar(128) not null,
                      content varchar(2000) null,
                      images varchar(2000) null,
                      status varchar(16) not null default 'NORMAL' comment 'NORMAL正常 OFF已下架',
                      off_reason varchar(500) null,
                      off_by bigint null,
                      off_by_name varchar(64) null,
                      off_time datetime null,
                      create_time datetime null,
                      update_time datetime null,
                      unique key uk_nh_no (tenant_id, post_no),
                      key idx_nh_tenant_status (tenant_id, status)
                    ) engine=InnoDB default charset=utf8mb4
                    """);
        } catch (Exception e) {
            log.warn("neighbor_help_post schema skip: {}", e.getMessage());
        }
        ensureColumn("neighbor_help_post", "contact_phone",
                "alter table neighbor_help_post add column contact_phone varchar(32) null");
        ensureColumn("neighbor_help_post", "helper_count",
                "alter table neighbor_help_post add column helper_count int not null default 0");
        try {
            jdbc.execute("""
                    create table if not exists neighbor_help_offer (
                      id bigint primary key auto_increment,
                      tenant_id bigint not null,
                      post_id bigint not null,
                      user_id bigint not null,
                      user_name varchar(64) null,
                      phone varchar(32) not null,
                      remark varchar(500) null,
                      status varchar(16) not null default 'pending' comment 'pending/accepted/rejected',
                      create_time datetime null,
                      key idx_nho_post (tenant_id, post_id, status),
                      key idx_nho_user (tenant_id, user_id)
                    ) engine=InnoDB default charset=utf8mb4
                    """);
        } catch (Exception e) {
            log.warn("neighbor_help_offer schema skip: {}", e.getMessage());
        }
    }

    private void ensureColumn(String table, String column, String alterSql) {
        try {
            Integer cnt = jdbc.queryForObject(
                    "select count(1) from information_schema.columns "
                            + "where table_schema = database() and table_name = ? and column_name = ?",
                    Integer.class, table, column);
            if (cnt != null && cnt > 0) return;
            jdbc.execute(alterSql);
        } catch (Exception ignored) {
        }
    }

    public List<Map<String, Object>> list(String helpType, String status, String keyword) {
        assertStaff();
        Long tid = requireTenant();
        StringBuilder sql = new StringBuilder(
                "select id, post_no as postNo, user_id as userId, owner_name as ownerName, "
                        + "house_id as houseId, house_label as houseLabel, help_type as helpType, "
                        + "title, content, images, status, off_reason as offReason, "
                        + "off_by_name as offByName, off_time as offTime, "
                        + "create_time as createTime, update_time as updateTime "
                        + "from neighbor_help_post where tenant_id=? ");
        List<Object> args = new ArrayList<>();
        args.add(tid);
        if (StringUtils.hasText(helpType)) {
            sql.append("and help_type=? ");
            args.add(helpType.trim());
        }
        if (StringUtils.hasText(status)) {
            sql.append("and status=? ");
            args.add(status.trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(keyword)) {
            sql.append("and (post_no like ? or ifnull(owner_name,'') like ? or ifnull(house_label,'') like ? "
                    + "or ifnull(title,'') like ? or ifnull(content,'') like ?) ");
            String like = "%" + keyword.trim() + "%";
            for (int i = 0; i < 5; i++) args.add(like);
        }
        sql.append("order by id desc limit 1000");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        for (Map<String, Object> r : rows) enrich(r);
        return rows;
    }

    /** 居民端：仅展示未下架互助帖 */
    public List<Map<String, Object>> listPublic(String helpType) {
        Long tid = requireTenant();
        ensure();
        StringBuilder sql = new StringBuilder(
                "select p.id, p.post_no as postNo, p.user_id as userId, p.owner_name as ownerName, "
                        + "p.house_id as houseId, p.house_label as houseLabel, p.help_type as helpType, "
                        + "p.title, p.content, p.images, p.status, p.contact_phone as contactPhone, "
                        + "ifnull(p.helper_count,0) as helperCount, "
                        + "p.create_time as createTime, p.update_time as updateTime, "
                        + "u.phone as publisherPhone "
                        + "from neighbor_help_post p "
                        + "left join sys_user u on u.id=p.user_id "
                        + "where p.tenant_id=? and p.status=? ");
        List<Object> args = new ArrayList<>();
        args.add(tid);
        args.add(ST_NORMAL);
        if (StringUtils.hasText(helpType)) {
            sql.append("and p.help_type=? ");
            args.add(helpType.trim());
        }
        sql.append("order by p.id desc limit 200");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        Long uid = TenantContext.getUserId();
        for (Map<String, Object> r : rows) {
            enrich(r);
            if (!StringUtils.hasText(str(r.get("contactPhone"), null))) {
                r.put("contactPhone", r.get("publisherPhone"));
            }
            r.remove("publisherPhone");
            Long ownerId = toLong(r.get("userId"));
            r.put("mine", uid != null && Objects.equals(uid, ownerId));
            r.put("offered", uid != null && hasOffered(tid, toLong(r.get("id")), uid));
        }
        return rows;
    }

    /** 居民：我来帮忙 */
    @Transactional
    public Map<String, Object> offerHelp(Long postId, Map<String, Object> body) {
        Long tid = requireTenant();
        Long uid = TenantContext.getUserId();
        if (uid == null) throw new BizException(401, "请先登录");
        ensure();
        Map<String, Object> post = requirePost(postId);
        if (!ST_NORMAL.equals(str(post.get("status"), ST_NORMAL))) {
            throw new BizException(400, "该互助已下架，无法接单");
        }
        Long ownerId = toLong(post.get("userId"));
        if (Objects.equals(ownerId, uid)) {
            throw new BizException(400, "不能帮自己发布的求助");
        }
        if (hasOffered(tid, postId, uid)) {
            throw new BizException(400, "您已提交过帮忙申请，请等待对方联系");
        }
        String phone = str(body == null ? null : body.get("phone"), null);
        String remark = str(body == null ? null : body.get("remark"), null);
        String userName = str(body == null ? null : body.get("userName"), null);
        if (!StringUtils.hasText(phone)) {
            try {
                phone = jdbc.queryForObject("select phone from sys_user where id=?", String.class, uid);
            } catch (Exception ignored) {
            }
        }
        if (!StringUtils.hasText(phone)) {
            throw new BizException(400, "请填写联系电话");
        }
        if (!StringUtils.hasText(userName)) {
            try {
                userName = jdbc.queryForObject("select nickname from sys_user where id=?", String.class, uid);
            } catch (Exception ignored) {
            }
        }
        jdbc.update(
                "insert into neighbor_help_offer(tenant_id,post_id,user_id,user_name,phone,remark,status,create_time) "
                        + "values(?,?,?,?,?,?,'pending',now())",
                tid, postId, uid, userName, phone, remark);
        try {
            jdbc.update(
                    "update neighbor_help_post set helper_count=ifnull(helper_count,0)+1, update_time=now() "
                            + "where id=? and tenant_id=?",
                    postId, tid);
        } catch (Exception ignored) {
        }
        // 通知发帖人
        if (ownerId != null) {
            try {
                jdbc.update(
                        "insert into sys_message(tenant_id,user_id,title,content,msg_type,read_flag,create_time) "
                                + "values(?,?,?,?,?,?,now())",
                        tid, ownerId, "邻里互助：有人愿意帮忙",
                        (StringUtils.hasText(userName) ? userName : "邻居") + " 愿意帮忙「"
                                + str(post.get("title"), "您的求助") + "」，电话：" + phone
                                + (StringUtils.hasText(remark) ? ("。留言：" + remark) : ""),
                        "neighbor_help", 0);
            } catch (Exception ignored) {
            }
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("postId", postId);
        resp.put("phone", phone);
        resp.put("status", "pending");
        resp.put("message", "已提交，对方可通过电话或站内消息联系您");
        return resp;
    }

    private boolean hasOffered(Long tid, Long postId, Long uid) {
        if (postId == null || uid == null) return false;
        try {
            Integer n = jdbc.queryForObject(
                    "select count(1) from neighbor_help_offer where tenant_id=? and post_id=? and user_id=?",
                    Integer.class, tid, postId, uid);
            return n != null && n > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public Map<String, Object> detail(Long id) {
        assertStaff();
        Map<String, Object> row = requirePost(id);
        enrich(row);
        return row;
    }

    /** 下架违规帖子 */
    @Transactional
    public Map<String, Object> takeDown(Long id, String reason) {
        assertStaff();
        Map<String, Object> old = requirePost(id);
        if (ST_OFF.equals(str(old.get("status"), ST_NORMAL))) {
            throw new BizException(400, "该帖子已下架");
        }
        if (!StringUtils.hasText(reason)) {
            throw new BizException(400, "请填写违规备注");
        }
        Long uid = TenantContext.getUserId();
        String uname = operatorName();
        jdbc.update(
                "update neighbor_help_post set status=?, off_reason=?, off_by=?, off_by_name=?, "
                        + "off_time=now(), update_time=now() where id=? and tenant_id=?",
                ST_OFF, reason.trim(), uid, uname, id, requireTenant());
        return detail(id);
    }

    /** 恢复上架（可选） */
    @Transactional
    public Map<String, Object> restore(Long id) {
        assertStaff();
        requirePost(id);
        jdbc.update(
                "update neighbor_help_post set status=?, off_reason=null, off_by=null, off_by_name=null, "
                        + "off_time=null, update_time=now() where id=? and tenant_id=?",
                ST_NORMAL, id, requireTenant());
        return detail(id);
    }

    /** 住户发布 */
    @Transactional
    public Map<String, Object> create(Map<String, Object> body) {
        Long tid = requireTenant();
        ensure();
        String title = str(body.get("title"), null);
        if (!StringUtils.hasText(title)) throw new BizException(400, "请填写标题");
        String helpType = str(body.get("helpType"), "其他");
        String content = str(body.get("content"), null);
        String images = normalizeImages(body.get("images"));
        String ownerName = str(body.get("ownerName"), null);
        String houseLabel = str(body.get("houseLabel"), null);
        String contactPhone = str(body.get("contactPhone"), str(body.get("phone"), null));
        Long houseId = toLong(body.get("houseId"));
        Long userId = TenantContext.getUserId();
        if (!StringUtils.hasText(ownerName) && userId != null) {
            try {
                ownerName = jdbc.queryForObject("select nickname from sys_user where id=?", String.class, userId);
            } catch (Exception ignored) {
            }
        }
        if (!StringUtils.hasText(contactPhone) && userId != null) {
            try {
                contactPhone = jdbc.queryForObject("select phone from sys_user where id=?", String.class, userId);
            } catch (Exception ignored) {
            }
        }
        String no = genNo(tid);
        jdbc.update(
                "insert into neighbor_help_post(tenant_id,post_no,user_id,owner_name,house_id,house_label,"
                        + "help_type,title,content,images,contact_phone,status,create_time,update_time) "
                        + "values(?,?,?,?,?,?,?,?,?,?,?,?,now(),now())",
                tid, no, userId, ownerName, houseId, houseLabel, helpType, title, content, images,
                contactPhone, ST_NORMAL);
        Long id = jdbc.queryForObject("select last_insert_id()", Long.class);
        return detailStaffOrOwner(id);
    }

    private Map<String, Object> detailStaffOrOwner(Long id) {
        Map<String, Object> row = requirePost(id);
        enrich(row);
        return row;
    }

    private void enrich(Map<String, Object> r) {
        normalizeTime(r, "createTime");
        normalizeTime(r, "updateTime");
        normalizeTime(r, "offTime");
        String st = str(r.get("status"), ST_NORMAL);
        r.put("status", st);
        r.put("statusLabel", ST_OFF.equals(st) ? "已下架" : "正常展示");
        r.put("statusTagType", ST_OFF.equals(st) ? "info" : "success");
        r.put("imageList", parseImages(r.get("images")));
    }

    private Map<String, Object> requirePost(Long id) {
        try {
            return jdbc.queryForMap(
                    "select id, post_no as postNo, user_id as userId, owner_name as ownerName, "
                            + "house_id as houseId, house_label as houseLabel, help_type as helpType, "
                            + "title, content, images, status, off_reason as offReason, "
                            + "off_by as offBy, off_by_name as offByName, off_time as offTime, "
                            + "create_time as createTime, update_time as updateTime "
                            + "from neighbor_help_post where id=? and tenant_id=?",
                    id, requireTenant());
        } catch (Exception e) {
            throw new BizException(404, "帖子不存在");
        }
    }

    private String genNo(Long tid) {
        String day = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        Integer n = jdbc.queryForObject(
                "select count(1) from neighbor_help_post where tenant_id=? and post_no like ?",
                Integer.class, tid, "NH" + day + "%");
        int seq = (n == null ? 0 : n) + 1;
        return String.format("NH%s%04d", day, seq);
    }

    private void assertStaff() {
        String role = TenantContext.getRole();
        if (!RoleConstants.isTenantStaff(role) && !RoleConstants.isPlatformAdmin(role)) {
            throw new BizException(403, "仅物业人员可操作");
        }
    }

    private Long requireTenant() {
        Long tid = TenantContext.getTenantId();
        if (tid == null) throw new BizException(401, "未登录");
        return tid;
    }

    private String operatorName() {
        Long uid = TenantContext.getUserId();
        if (uid == null) return "物业";
        try {
            String n = jdbc.queryForObject("select nickname from sys_user where id=?", String.class, uid);
            return StringUtils.hasText(n) ? n : ("用户#" + uid);
        } catch (Exception e) {
            return "物业";
        }
    }

    private static List<String> parseImages(Object images) {
        if (images == null) return List.of();
        String s = String.valueOf(images).trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) return List.of();
        if (s.startsWith("[")) {
            s = s.replace("[", "").replace("]", "").replace("\"", "");
        }
        List<String> out = new ArrayList<>();
        for (String p : s.split("[,;\\s]+")) {
            if (StringUtils.hasText(p)) out.add(p.trim());
        }
        return out;
    }

    private static String normalizeImages(Object images) {
        if (images == null) return null;
        if (images instanceof Collection<?> col) {
            List<String> list = new ArrayList<>();
            for (Object o : col) {
                if (o != null && StringUtils.hasText(String.valueOf(o))) list.add(String.valueOf(o).trim());
            }
            return list.isEmpty() ? null : String.join(",", list);
        }
        String s = String.valueOf(images).trim();
        return s.isEmpty() ? null : s;
    }

    private static void normalizeTime(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v instanceof Timestamp ts) {
            m.put(key, ts.toLocalDateTime().toString().replace('T', ' '));
        } else if (v instanceof LocalDateTime ldt) {
            m.put(key, ldt.toString().replace('T', ' '));
        }
    }

    private static String str(Object v, String def) {
        if (v == null) return def;
        String s = String.valueOf(v).trim();
        return s.isEmpty() || "null".equalsIgnoreCase(s) ? def : s;
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
