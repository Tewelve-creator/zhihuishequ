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
 * 意见反馈：业主提交 / 物业处理回复，通过后转入报事工单；按租户隔离
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackService {

    public static final String ST_PENDING = "PENDING";
    public static final String ST_PROCESSING = "PROCESSING";
    public static final String ST_APPROVED = "APPROVED";
    public static final String ST_DONE = "DONE";

    private final JdbcTemplate jdbc;
    private final RepairWorkOrderService repairWorkOrderService;

    @PostConstruct
    public void ensure() {
        try {
            jdbc.execute("""
                    create table if not exists community_feedback (
                      id bigint primary key auto_increment,
                      tenant_id bigint not null,
                      feedback_no varchar(32) not null,
                      user_id bigint null,
                      owner_name varchar(64) null,
                      house_id bigint null,
                      house_label varchar(128) null,
                      phone varchar(32) null,
                      feedback_type varchar(32) not null default '其他',
                      title varchar(128) not null,
                      content varchar(2000) null,
                      images varchar(2000) null comment 'JSON 数组或逗号分隔 URL',
                      status varchar(16) not null default 'PENDING',
                      reply_content varchar(2000) null,
                      reply_by bigint null,
                      reply_by_name varchar(64) null,
                      reply_time datetime null,
                      create_time datetime null,
                      update_time datetime null,
                      unique key uk_fb_no (tenant_id, feedback_no),
                      key idx_fb_tenant_status (tenant_id, status),
                      key idx_fb_user (user_id)
                    ) engine=InnoDB default charset=utf8mb4
                    """);
            jdbc.execute("""
                    create table if not exists community_feedback_log (
                      id bigint primary key auto_increment,
                      tenant_id bigint not null,
                      feedback_id bigint not null,
                      action varchar(32) not null,
                      from_status varchar(16) null,
                      to_status varchar(16) null,
                      content varchar(2000) null,
                      operator_id bigint null,
                      operator_name varchar(64) null,
                      create_time datetime null,
                      key idx_fbl_fb (feedback_id)
                    ) engine=InnoDB default charset=utf8mb4
                    """);
            addCol("community_feedback", "work_order_id", "bigint null");
        } catch (Exception e) {
            log.warn("community_feedback schema skip: {}", e.getMessage());
        }
    }

    private void addCol(String table, String col, String def) {
        try {
            Integer n = jdbc.queryForObject(
                    "select count(1) from information_schema.columns where table_schema=database() and table_name=? and column_name=?",
                    Integer.class, table, col);
            if (n == null || n == 0) {
                jdbc.execute("alter table " + table + " add column " + col + " " + def);
            }
        } catch (Exception e) {
            log.debug("add {}.{} skip: {}", table, col, e.getMessage());
        }
    }

    public List<Map<String, Object>> list(String status, String keyword) {
        assertStaff();
        Long tid = requireTenant();
        StringBuilder sql = new StringBuilder(
                "select id, feedback_no as feedbackNo, user_id as userId, owner_name as ownerName, "
                        + "house_id as houseId, house_label as houseLabel, phone, "
                        + "feedback_type as feedbackType, title, content, images, status, "
                        + "reply_content as replyContent, reply_by as replyBy, reply_by_name as replyByName, "
                        + "reply_time as replyTime, work_order_id as workOrderId, "
                        + "create_time as createTime, update_time as updateTime "
                        + "from community_feedback where tenant_id=? ");
        List<Object> args = new ArrayList<>();
        args.add(tid);
        if (StringUtils.hasText(status)) {
            sql.append("and status=? ");
            args.add(status.trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(keyword)) {
            sql.append("and (feedback_no like ? or ifnull(owner_name,'') like ? or ifnull(house_label,'') like ? "
                    + "or ifnull(phone,'') like ? or ifnull(title,'') like ? or ifnull(content,'') like ?) ");
            String like = "%" + keyword.trim() + "%";
            for (int i = 0; i < 6; i++) args.add(like);
        }
        sql.append("order by id desc limit 1000");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        for (Map<String, Object> r : rows) {
            enrich(r);
        }
        return rows;
    }

    public Map<String, Object> detail(Long id) {
        assertStaff();
        Map<String, Object> row = requireFeedback(id);
        enrich(row);
        row.put("logs", listLogs(id));
        return row;
    }

    /** 居民：我的反馈列表 */
    public List<Map<String, Object>> listMine() {
        Long tid = requireTenant();
        Long uid = TenantContext.getUserId();
        if (uid == null) throw new BizException(401, "未登录");
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select id, feedback_no as feedbackNo, user_id as userId, owner_name as ownerName, "
                        + "house_id as houseId, house_label as houseLabel, phone, "
                        + "feedback_type as feedbackType, title, content, images, status, "
                        + "reply_content as replyContent, reply_by as replyBy, reply_by_name as replyByName, "
                        + "reply_time as replyTime, work_order_id as workOrderId, "
                        + "create_time as createTime, update_time as updateTime "
                        + "from community_feedback where tenant_id=? and user_id=? order by id desc limit 100",
                tid, uid);
        for (Map<String, Object> r : rows) enrich(r);
        return rows;
    }

    public List<Map<String, Object>> listLogs(Long feedbackId) {
        assertStaff();
        requireFeedback(feedbackId);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select id, action, from_status as fromStatus, to_status as toStatus, content, "
                        + "operator_id as operatorId, operator_name as operatorName, create_time as createTime "
                        + "from community_feedback_log where feedback_id=? and tenant_id=? order by id desc",
                feedbackId, requireTenant());
        for (Map<String, Object> r : rows) {
            normalizeTime(r, "createTime");
            r.put("actionLabel", actionLabel(str(r.get("action"), "")));
            String from = str(r.get("fromStatus"), null);
            String to = str(r.get("toStatus"), null);
            if (StringUtils.hasText(from)) r.put("fromStatusLabel", statusLabel(from));
            if (StringUtils.hasText(to)) r.put("toStatusLabel", statusLabel(to));
        }
        return rows;
    }

    @Transactional
    public Map<String, Object> handle(Long id, Map<String, Object> body) {
        assertStaff();
        Map<String, Object> old = requireFeedback(id);
        String oldStatus = str(old.get("status"), ST_PENDING);
        String reply = str(body.get("replyContent"), null);
        String newStatus = str(body.get("status"), null);
        if (StringUtils.hasText(newStatus)) {
            newStatus = normalizeStatus(newStatus);
        } else {
            newStatus = oldStatus;
        }
        if (!StringUtils.hasText(reply) && newStatus.equals(oldStatus)) {
            throw new BizException(400, "请填写回复内容或变更状态");
        }
        if (ST_APPROVED.equals(newStatus) && !ST_APPROVED.equals(oldStatus)) {
            // 状态改为「已通过」时同步转工单
            Map<String, Object> approved = approveInternal(id, old, reply, false);
            return approved;
        }
        if (!StringUtils.hasText(reply)) {
            reply = str(old.get("replyContent"), null);
        }

        Long uid = TenantContext.getUserId();
        String uname = operatorName();
        LocalDateTime now = LocalDateTime.now();

        jdbc.update(
                "update community_feedback set status=?, reply_content=?, reply_by=?, reply_by_name=?, "
                        + "reply_time=?, update_time=? where id=? and tenant_id=?",
                newStatus, reply, uid, uname, Timestamp.valueOf(now), Timestamp.valueOf(now),
                id, requireTenant());

        String action;
        if (!Objects.equals(oldStatus, newStatus) && StringUtils.hasText(str(body.get("replyContent"), null))) {
            action = "REPLY_STATUS";
        } else if (!Objects.equals(oldStatus, newStatus)) {
            action = "STATUS";
        } else {
            action = "REPLY";
        }
        String logContent = str(body.get("replyContent"), null);
        if (!StringUtils.hasText(logContent)) {
            logContent = "状态变更为：" + statusLabel(newStatus);
        }
        addLog(id, action, oldStatus, newStatus, logContent, uid, uname);
        return detail(id);
    }

    /** 物业「通过」：标记已通过，并生成报事工单进入工单列表 */
    @Transactional
    public Map<String, Object> approve(Long id, Map<String, Object> body) {
        assertStaff();
        Map<String, Object> old = requireFeedback(id);
        String reply = body == null ? null : str(body.get("replyContent"), null);
        return approveInternal(id, old, reply, true);
    }

    private Map<String, Object> approveInternal(Long id, Map<String, Object> old, String reply, boolean requireChange) {
        String oldStatus = str(old.get("status"), ST_PENDING);
        Long existingWo = toLong(old.get("workOrderId"));
        if (ST_APPROVED.equals(oldStatus) && existingWo != null) {
            if (requireChange && !StringUtils.hasText(reply)) {
                return detail(id);
            }
        }
        if (ST_DONE.equals(oldStatus)) {
            throw new BizException(400, "已办结的反馈不可再通过转工单");
        }

        List<String> images = parseImages(old.get("images"));
        String imageUrl = images.isEmpty() ? null : images.get(0);
        Long orderId = existingWo;
        if (orderId == null) {
            orderId = repairWorkOrderService.createReportFromFeedback(
                    id,
                    toLong(old.get("userId")),
                    str(old.get("title"), null),
                    str(old.get("content"), null),
                    str(old.get("feedbackType"), "其他"),
                    imageUrl,
                    str(old.get("houseLabel"), null));
        }

        Long uid = TenantContext.getUserId();
        String uname = operatorName();
        LocalDateTime now = LocalDateTime.now();
        if (!StringUtils.hasText(reply)) {
            reply = str(old.get("replyContent"), null);
        }
        if (!StringUtils.hasText(reply)) {
            reply = "已通过，已转入报事工单 #" + orderId + " 处理";
        }

        jdbc.update(
                "update community_feedback set status=?, reply_content=?, reply_by=?, reply_by_name=?, "
                        + "reply_time=?, work_order_id=?, update_time=? where id=? and tenant_id=?",
                ST_APPROVED, reply, uid, uname, Timestamp.valueOf(now), orderId, Timestamp.valueOf(now),
                id, requireTenant());

        String logContent = "通过并转工单 #" + orderId;
        if (StringUtils.hasText(reply) && !reply.startsWith("已通过，已转入报事工单")) {
            logContent = logContent + "；" + reply;
        }
        if (existingWo != null && ST_APPROVED.equals(oldStatus)) {
            if (StringUtils.hasText(reply)) {
                addLog(id, "REPLY", oldStatus, ST_APPROVED, reply, uid, uname);
            }
        } else {
            addLog(id, "APPROVE", oldStatus, ST_APPROVED, logContent, uid, uname);
        }
        return detail(id);
    }

    /** 居民端提交（预留） */
    @Transactional
    public Map<String, Object> create(Map<String, Object> body) {
        Long tid = requireTenant();
        String title = str(body.get("title"), null);
        if (!StringUtils.hasText(title)) throw new BizException(400, "请填写标题");
        String type = str(body.get("feedbackType"), "其他");
        String content = str(body.get("content"), null);
        String images = normalizeImages(body.get("images"));
        String ownerName = str(body.get("ownerName"), null);
        String phone = str(body.get("phone"), null);
        String houseLabel = str(body.get("houseLabel"), null);
        Long houseId = toLong(body.get("houseId"));
        Long userId = TenantContext.getUserId();
        if (!StringUtils.hasText(ownerName) && userId != null) {
            try {
                ownerName = jdbc.queryForObject("select nickname from sys_user where id=?", String.class, userId);
            } catch (Exception ignored) {
            }
        }
        if (!StringUtils.hasText(phone) && userId != null) {
            try {
                phone = jdbc.queryForObject("select phone from sys_user where id=?", String.class, userId);
            } catch (Exception ignored) {
            }
        }
        String no = genNo(tid);
        jdbc.update(
                "insert into community_feedback(tenant_id,feedback_no,user_id,owner_name,house_id,house_label,phone,"
                        + "feedback_type,title,content,images,status,create_time,update_time) "
                        + "values(?,?,?,?,?,?,?,?,?,?,?,?,now(),now())",
                tid, no, userId, ownerName, houseId, houseLabel, phone, type, title, content, images, ST_PENDING);
        Long id = jdbc.queryForObject("select last_insert_id()", Long.class);
        addLog(id, "CREATE", null, ST_PENDING, "提交意见反馈", userId, ownerName);
        return detailForAny(id);
    }

    private Map<String, Object> detailForAny(Long id) {
        Map<String, Object> row = requireFeedback(id);
        enrich(row);
        return row;
    }

    private void enrich(Map<String, Object> r) {
        normalizeTime(r, "createTime");
        normalizeTime(r, "updateTime");
        normalizeTime(r, "replyTime");
        String st = str(r.get("status"), ST_PENDING);
        r.put("status", st);
        r.put("statusLabel", statusLabel(st));
        r.put("statusTagType", statusTag(st));
        r.put("imageList", parseImages(r.get("images")));
    }

    private void addLog(Long feedbackId, String action, String from, String to,
                        String content, Long operatorId, String operatorName) {
        jdbc.update(
                "insert into community_feedback_log(tenant_id,feedback_id,action,from_status,to_status,content,"
                        + "operator_id,operator_name,create_time) values(?,?,?,?,?,?,?,?,now())",
                requireTenant(), feedbackId, action, from, to, content, operatorId, operatorName);
    }

    private Map<String, Object> requireFeedback(Long id) {
        try {
            Map<String, Object> row = jdbc.queryForMap(
                    "select id, feedback_no as feedbackNo, user_id as userId, owner_name as ownerName, "
                            + "house_id as houseId, house_label as houseLabel, phone, "
                            + "feedback_type as feedbackType, title, content, images, status, "
                            + "reply_content as replyContent, reply_by as replyBy, reply_by_name as replyByName, "
                            + "reply_time as replyTime, work_order_id as workOrderId, "
                            + "create_time as createTime, update_time as updateTime "
                            + "from community_feedback where id=? and tenant_id=?",
                    id, requireTenant());
            return row;
        } catch (Exception e) {
            throw new BizException(404, "反馈不存在");
        }
    }

    private String genNo(Long tid) {
        String day = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        Integer n = jdbc.queryForObject(
                "select count(1) from community_feedback where tenant_id=? and feedback_no like ?",
                Integer.class, tid, "FB" + day + "%");
        int seq = (n == null ? 0 : n) + 1;
        return String.format("FB%s%04d", day, seq);
    }

    private static String normalizeStatus(String s) {
        String u = s.trim().toUpperCase(Locale.ROOT);
        return switch (u) {
            case "PENDING", "待处理" -> ST_PENDING;
            case "PROCESSING", "处理中" -> ST_PROCESSING;
            case "APPROVED", "PASSED", "已通过", "通过" -> ST_APPROVED;
            case "DONE", "CLOSED", "RESOLVED", "已办结", "已完结" -> ST_DONE;
            default -> throw new BizException(400, "不支持的状态: " + s);
        };
    }

    private static String statusLabel(String s) {
        return switch (s) {
            case ST_PENDING -> "待处理";
            case ST_PROCESSING -> "处理中";
            case ST_APPROVED -> "已通过";
            case ST_DONE -> "已办结";
            default -> s;
        };
    }

    private static String statusTag(String s) {
        return switch (s) {
            case ST_PENDING -> "warning";
            case ST_PROCESSING -> "primary";
            case ST_APPROVED -> "success";
            case ST_DONE -> "info";
            default -> "info";
        };
    }

    private static String actionLabel(String a) {
        return switch (a) {
            case "CREATE" -> "提交反馈";
            case "REPLY" -> "回复";
            case "STATUS" -> "变更状态";
            case "REPLY_STATUS" -> "回复并变更状态";
            case "APPROVE" -> "通过并转工单";
            default -> a;
        };
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
