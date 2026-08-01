package com.community.repair.service;

import com.community.common.constant.RoleConstants;
import com.community.common.exception.BizException;
import com.community.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 设备维保工单：待派单 → 维修中 → 已完工；可从巡检异常一键生成。
 */
@Service
@RequiredArgsConstructor
public class EquipWorkOrderService {

    public static final String ST_PENDING = "PENDING";
    public static final String ST_DOING = "DOING";
    public static final String ST_DONE = "DONE";

    private final JdbcTemplate jdbc;

    public List<Map<String, Object>> list(String status, String keyword, String source) {
        assertStaff();
        ensureTable();
        Long tid = requireTenant();
        StringBuilder sql = new StringBuilder(
                "select id, tenant_id as tenantId, title, device_id as deviceId, device_name as deviceName, "
                        + "location, source, source_checkin_id as sourceCheckinId, status, "
                        + "assignee_id as assigneeId, assignee_name as assigneeName, fault_desc as faultDesc, "
                        + "repair_content as repairContent, parts_replaced as partsReplaced, "
                        + "repair_photos as repairPhotos, accept_photos as acceptPhotos, "
                        + "create_by_name as createByName, assign_time as assignTime, finish_time as finishTime, "
                        + "remark, create_time as createTime "
                        + "from equip_work_order where tenant_id=? ");
        List<Object> args = new ArrayList<>();
        args.add(tid);
        if (StringUtils.hasText(status)) {
            sql.append("and status=? ");
            args.add(status.trim().toUpperCase());
        }
        if (StringUtils.hasText(source)) {
            sql.append("and source=? ");
            args.add(source.trim().toUpperCase());
        }
        if (StringUtils.hasText(keyword)) {
            sql.append("and (title like ? or device_name like ? or location like ? or fault_desc like ? or assignee_name like ?) ");
            String k = "%" + keyword.trim() + "%";
            args.add(k); args.add(k); args.add(k); args.add(k); args.add(k);
        }
        sql.append("order by field(status,'PENDING','DOING','DONE'), id desc limit 500");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        rows.forEach(this::decorate);
        return rows;
    }

    public Map<String, Object> get(Long id) {
        assertStaff();
        ensureTable();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select * from equip_work_order where id=? and tenant_id=?", id, requireTenant());
        if (rows.isEmpty()) throw new BizException("工单不存在");
        return decorate(camel(rows.get(0)));
    }

    @Transactional
    public Map<String, Object> save(Map<String, Object> body) {
        assertManage();
        ensureTable();
        Long tid = requireTenant();
        Long id = parseLong(body.get("id"));
        String title = str(body.get("title"));
        if (!StringUtils.hasText(title)) throw new BizException("请填写工单标题");
        String deviceName = str(body.get("deviceName"));
        String location = str(body.get("location"));
        Long deviceId = parseLong(body.get("deviceId"));
        String fault = str(body.get("faultDesc"));
        String remark = str(body.get("remark"));
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());

        if (id == null) {
            KeyHolder kh = new GeneratedKeyHolder();
            jdbc.update(con -> {
                PreparedStatement ps = con.prepareStatement(
                        "insert into equip_work_order(tenant_id,title,device_id,device_name,location,source,status,"
                                + "fault_desc,create_by,create_by_name,remark,create_time) "
                                + "values(?,?,?,?,?,'MANUAL','PENDING',?,?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS);
                int i = 1;
                ps.setLong(i++, tid);
                ps.setString(i++, title.trim());
                if (deviceId == null) ps.setObject(i++, null); else ps.setLong(i++, deviceId);
                ps.setString(i++, deviceName);
                ps.setString(i++, location);
                ps.setString(i++, fault);
                Long uid = TenantContext.getUserId();
                if (uid == null) ps.setObject(i++, null); else ps.setLong(i++, uid);
                ps.setString(i++, resolveName(uid));
                ps.setString(i++, remark);
                ps.setTimestamp(i, now);
                return ps;
            }, kh);
            return get(kh.getKey().longValue());
        }
        jdbc.update(
                "update equip_work_order set title=?, device_id=?, device_name=?, location=?, fault_desc=?, remark=?, update_time=? "
                        + "where id=? and tenant_id=? and status<>'DONE'",
                title.trim(), deviceId, deviceName, location, fault, remark, now, id, tid);
        return get(id);
    }

    /** 从巡检异常打卡一键生成维保工单 */
    @Transactional
    public Map<String, Object> createFromCheckin(Long checkinId, Map<String, Object> body) {
        assertManage();
        ensureTable();
        Long tid = requireTenant();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select c.*, t.title as task_title from insp_checkin c "
                        + "left join insp_task t on t.id=c.task_id "
                        + "where c.id=? and c.tenant_id=? and c.has_hazard=1",
                checkinId, tid);
        if (rows.isEmpty()) throw new BizException("巡检异常记录不存在");
        Map<String, Object> c = rows.get(0);

        Integer exist = jdbc.queryForObject(
                "select count(1) from equip_work_order where tenant_id=? and source_checkin_id=?",
                Integer.class, tid, checkinId);
        if (exist != null && exist > 0) {
            throw new BizException("该异常已生成过维保工单");
        }

        String pointName = str(c.get("point_name"));
        String hazard = str(c.get("hazard_desc"));
        String taskTitle = str(c.get("task_title"));
        String title = body != null && StringUtils.hasText(str(body.get("title")))
                ? str(body.get("title"))
                : ("巡检异常维修：" + (StringUtils.hasText(pointName) ? pointName : "点位#" + checkinId));
        String deviceName = body != null ? str(body.get("deviceName")) : null;
        if (!StringUtils.hasText(deviceName)) deviceName = pointName;
        String location = body != null && StringUtils.hasText(str(body.get("location")))
                ? str(body.get("location")) : pointName;
        String fault = StringUtils.hasText(hazard) ? hazard
                : ("巡检任务「" + taskTitle + "」上报异常");

        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        KeyHolder kh = new GeneratedKeyHolder();
        String finalDeviceName = deviceName;
        String finalLocation = location;
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "insert into equip_work_order(tenant_id,title,device_name,location,source,source_checkin_id,status,"
                            + "fault_desc,create_by,create_by_name,remark,create_time) "
                            + "values(?,?,?,?,'INSPECTION',?,'PENDING',?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            int i = 1;
            ps.setLong(i++, tid);
            ps.setString(i++, title);
            ps.setString(i++, finalDeviceName);
            ps.setString(i++, finalLocation);
            ps.setLong(i++, checkinId);
            ps.setString(i++, fault);
            Long uid = TenantContext.getUserId();
            if (uid == null) ps.setObject(i++, null); else ps.setLong(i++, uid);
            ps.setString(i++, resolveName(uid));
            ps.setString(i++, "来源巡检打卡#" + checkinId);
            ps.setTimestamp(i, now);
            return ps;
        }, kh);
        return get(kh.getKey().longValue());
    }

    /** 派单 → 维修中 */
    @Transactional
    public Map<String, Object> assign(Long id, Map<String, Object> body) {
        assertManage();
        Long tid = requireTenant();
        Map<String, Object> wo = get(id);
        if (!ST_PENDING.equals(str(wo.get("status")))) {
            throw new BizException("仅待派单工单可派单");
        }
        Long assigneeId = parseLong(body.get("assigneeId"));
        String assigneeName = str(body.get("assigneeName"));
        if (assigneeId == null && !StringUtils.hasText(assigneeName)) {
            throw new BizException("请指定维修人员");
        }
        if (!StringUtils.hasText(assigneeName)) {
            assigneeName = resolveName(assigneeId);
        }
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        jdbc.update(
                "update equip_work_order set status=?, assignee_id=?, assignee_name=?, assign_time=?, update_time=? where id=? and tenant_id=?",
                ST_DOING, assigneeId, assigneeName, now, now, id, tid);
        try {
            if (assigneeId != null) {
                jdbc.update(
                        "insert into sys_message(tenant_id,user_id,title,content,msg_type,read_flag,create_time) values(?,?,?,?,?,?,now())",
                        tid, assigneeId, "维保工单待处理", "您有新的设备维保工单：" + wo.get("title"), "equip", 0);
            }
        } catch (Exception ignored) {
        }
        return get(id);
    }

    /** 完工验收 */
    @Transactional
    public Map<String, Object> complete(Long id, Map<String, Object> body) {
        assertStaff();
        Long tid = requireTenant();
        Map<String, Object> wo = get(id);
        String st = str(wo.get("status"));
        if (!ST_DOING.equals(st) && !ST_PENDING.equals(st)) {
            throw new BizException("工单已完工");
        }
        String repair = str(body.get("repairContent"));
        if (!StringUtils.hasText(repair)) throw new BizException("请填写维修内容");
        String parts = str(body.get("partsReplaced"));
        String repairPhotos = str(body.get("repairPhotos"));
        String acceptPhotos = str(body.get("acceptPhotos"));
        String remark = str(body.get("remark"));
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        jdbc.update(
                "update equip_work_order set status=?, repair_content=?, parts_replaced=?, repair_photos=?, accept_photos=?, "
                        + "finish_time=?, remark=ifnull(?, remark), update_time=? where id=? and tenant_id=?",
                ST_DONE, repair, parts, repairPhotos, acceptPhotos, now, remark, now, id, tid);

        // 若来自巡检异常，同步标记隐患已整改
        Long checkinId = parseLong(wo.get("sourceCheckinId"));
        if (checkinId != null) {
            try {
                jdbc.update(
                        "update insp_checkin set hazard_status='FIXED' where id=? and tenant_id=? and has_hazard=1",
                        checkinId, tid);
            } catch (Exception ignored) {
            }
        }
        return get(id);
    }

    @Transactional
    public void delete(Long id) {
        assertManage();
        Long tid = requireTenant();
        Map<String, Object> wo = get(id);
        if (ST_DONE.equals(str(wo.get("status")))) {
            throw new BizException("已完工工单不可删除");
        }
        jdbc.update("delete from equip_work_order where id=? and tenant_id=?", id, tid);
    }

    public Map<String, Object> todoSummary() {
        assertStaff();
        ensureTable();
        Long tid = requireTenant();
        Map<String, Object> m = new LinkedHashMap<>();
        Integer pending = jdbc.queryForObject(
                "select count(1) from equip_work_order where tenant_id=? and status='PENDING'", Integer.class, tid);
        Integer doing = jdbc.queryForObject(
                "select count(1) from equip_work_order where tenant_id=? and status='DOING'", Integer.class, tid);
        m.put("pendingCount", pending == null ? 0 : pending);
        m.put("doingCount", doing == null ? 0 : doing);
        m.put("todoCount", (pending == null ? 0 : pending) + (doing == null ? 0 : doing));
        return m;
    }

    public void ensureTable() {
        try {
            jdbc.execute(
                    "create table if not exists equip_work_order ("
                            + "id bigint primary key auto_increment,"
                            + "tenant_id bigint not null,"
                            + "title varchar(160) not null,"
                            + "device_id bigint null,"
                            + "device_name varchar(120) null,"
                            + "location varchar(255) null,"
                            + "source varchar(16) not null default 'MANUAL',"
                            + "source_checkin_id bigint null,"
                            + "status varchar(16) not null default 'PENDING',"
                            + "assignee_id bigint null,"
                            + "assignee_name varchar(64) null,"
                            + "fault_desc varchar(1000) null,"
                            + "repair_content varchar(2000) null,"
                            + "parts_replaced varchar(1000) null,"
                            + "repair_photos varchar(2000) null,"
                            + "accept_photos varchar(2000) null,"
                            + "create_by bigint null,"
                            + "create_by_name varchar(64) null,"
                            + "assign_time datetime null,"
                            + "finish_time datetime null,"
                            + "remark varchar(500) null,"
                            + "create_time datetime default current_timestamp,"
                            + "update_time datetime null,"
                            + "key idx_tenant_status (tenant_id, status)"
                            + ") engine=InnoDB default charset=utf8mb4");
        } catch (Exception ignored) {
        }
    }

    private Map<String, Object> decorate(Map<String, Object> m) {
        String st = str(m.get("status"));
        m.put("statusLabel", switch (st) {
            case ST_DOING -> "维修中";
            case ST_DONE -> "已完工";
            default -> "待派单";
        });
        String src = str(m.get("source"));
        m.put("sourceLabel", "INSPECTION".equals(src) ? "巡检上报" : "手动新建");
        fmtTime(m, "createTime");
        fmtTime(m, "assignTime");
        fmtTime(m, "finishTime");
        return m;
    }

    private void fmtTime(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v != null) {
            String s = String.valueOf(v).replace('T', ' ');
            m.put(key, s.length() > 19 ? s.substring(0, 19) : s);
        }
    }

    private String resolveName(Long uid) {
        if (uid == null) return "管理员";
        try {
            List<Map<String, Object>> u = jdbc.queryForList(
                    "select nickname, username from sys_user where id=? limit 1", uid);
            if (!u.isEmpty()) {
                Object n = u.get(0).get("nickname");
                if (n != null && StringUtils.hasText(String.valueOf(n))) return String.valueOf(n);
                Object un = u.get(0).get("username");
                if (un != null) return String.valueOf(un);
            }
        } catch (Exception ignored) {
        }
        return "用户#" + uid;
    }

    private void assertStaff() {
        String role = TenantContext.getRole();
        if (!RoleConstants.isPropertyAdmin(role) && !RoleConstants.isGridWorker(role)
                && !RoleConstants.isPlatformAdmin(role)) {
            throw new BizException(403, "仅物业人员可查看");
        }
    }

    private void assertManage() {
        String role = TenantContext.getRole();
        if (!RoleConstants.isPropertyAdmin(role) && !RoleConstants.isPlatformAdmin(role)) {
            throw new BizException(403, "仅物业管理员可操作");
        }
    }

    private Long requireTenant() {
        Long tid = TenantContext.getTenantId();
        if (tid == null) throw new BizException(40001, "缺少租户");
        return tid;
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o).trim();
    }

    private Long parseLong(Object o) {
        if (o == null || !StringUtils.hasText(String.valueOf(o))) return null;
        try {
            return Long.valueOf(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> camel(Map<String, Object> row) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : row.entrySet()) {
            String k = e.getKey();
            if (k == null) continue;
            StringBuilder sb = new StringBuilder();
            boolean up = false;
            for (char c : k.toCharArray()) {
                if (c == '_') { up = true; continue; }
                sb.append(up ? Character.toUpperCase(c) : c);
                up = false;
            }
            m.put(sb.toString(), e.getValue());
        }
        return m;
    }
}
