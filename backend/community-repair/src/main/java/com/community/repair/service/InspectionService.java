package com.community.repair.service;

import com.community.common.constant.RoleConstants;
import com.community.common.exception.BizException;
import com.community.common.tenant.TenantContext;
import com.community.repair.dto.InspCheckinRequest;
import com.community.repair.dto.InspPointSaveRequest;
import com.community.repair.dto.InspTaskSaveRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class InspectionService {

    public static final String ST_PENDING = "PENDING";
    public static final String ST_DOING = "DOING";
    public static final String ST_DONE = "DONE";
    public static final String ST_OVERDUE = "OVERDUE";
    /** GPS 允许偏差（米） */
    public static final double GPS_RADIUS_M = 200;

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbc;

    /* ===================== 点位 ===================== */

    public List<Map<String, Object>> listPoints(String keyword, Integer status) {
        assertStaffView();
        ensurePointExt();
        Long tid = requireTenant();
        StringBuilder sql = new StringBuilder(
                "select id, tenant_id as tenantId, name, point_type as pointType, location_desc as locationDesc, address, "
                        + "latitude, longitude, status, qr_code as qrCode, qr_enabled as qrEnabled, remark, create_time as createTime "
                        + "from insp_point where tenant_id=? ");
        List<Object> args = new ArrayList<>();
        args.add(tid);
        if (status != null) {
            sql.append("and status=? ");
            args.add(status);
        }
        if (StringUtils.hasText(keyword)) {
            sql.append("and (name like ? or location_desc like ? or address like ? or point_type like ?) ");
            String k = "%" + keyword.trim() + "%";
            args.add(k);
            args.add(k);
            args.add(k);
            args.add(k);
        }
        sql.append("order by id desc limit 500");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        rows.forEach(this::decoratePoint);
        return rows;
    }

    @Transactional
    public Map<String, Object> savePoint(InspPointSaveRequest req) {
        assertCanManage();
        ensurePointExt();
        Long tid = requireTenant();
        if (!StringUtils.hasText(req.getName())) {
            throw new BizException("请填写点位名称");
        }
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        int st = req.getStatus() == null ? 1 : req.getStatus();
        int qrEn = req.getQrEnabled() == null ? 1 : req.getQrEnabled();
        String pointType = normalizePointType(req.getPointType());
        if (req.getId() != null) {
            jdbc.update(
                    "update insp_point set name=?, point_type=?, location_desc=?, address=?, latitude=?, longitude=?, "
                            + "status=?, qr_enabled=?, remark=?, update_time=? where id=? and tenant_id=?",
                    req.getName().trim(), pointType, req.getLocationDesc(), req.getAddress(),
                    req.getLatitude(), req.getLongitude(), st, qrEn, req.getRemark(), now, req.getId(), tid);
            return getPoint(tid, req.getId());
        }
        String qr = java.util.UUID.randomUUID().toString().replace("-", "");
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "insert into insp_point(tenant_id,name,point_type,location_desc,address,latitude,longitude,status,qr_code,qr_enabled,remark,create_time) "
                            + "values(?,?,?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            int i = 1;
            ps.setLong(i++, tid);
            ps.setString(i++, req.getName().trim());
            ps.setString(i++, pointType);
            ps.setString(i++, req.getLocationDesc());
            ps.setString(i++, req.getAddress());
            setDouble(ps, i++, req.getLatitude());
            setDouble(ps, i++, req.getLongitude());
            ps.setInt(i++, st);
            ps.setString(i++, qr);
            ps.setInt(i++, qrEn);
            ps.setString(i++, req.getRemark());
            ps.setTimestamp(i, now);
            return ps;
        }, kh);
        return getPoint(tid, kh.getKey().longValue());
    }

    @Transactional
    public Map<String, Object> regenQr(Long id) {
        assertCanManage();
        ensurePointExt();
        Long tid = requireTenant();
        String qr = java.util.UUID.randomUUID().toString().replace("-", "");
        int n = jdbc.update(
                "update insp_point set qr_code=?, update_time=? where id=? and tenant_id=?",
                qr, Timestamp.valueOf(LocalDateTime.now()), id, tid);
        if (n == 0) throw new BizException("点位不存在");
        return getPoint(tid, id);
    }

    @Transactional
    public Map<String, Object> bindDemoLocation(Double latitude, Double longitude) {
        assertCanManage();
        Long tid = requireTenant();
        if (latitude == null || longitude == null) {
            throw new BizException("缺少定位坐标");
        }
        // 四个演示点围绕当前位置做微小偏移（约 10~30 米内），便于同点位反复打卡演示
        double[][] offsets = {
                {0, 0},
                {0.00008, 0.00005},
                {-0.00006, 0.00009},
                {0.00005, -0.00007}
        };
        List<Map<String, Object>> points = jdbc.queryForList(
                "select id from insp_point where tenant_id=? and status=1 order by id limit 20", tid);
        int i = 0;
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        for (Map<String, Object> p : points) {
            double[] off = offsets[Math.min(i, offsets.length - 1)];
            double lat = latitude + off[0];
            double lng = longitude + off[1];
            Long pid = toLong(p.get("id"));
            jdbc.update(
                    "update insp_point set latitude=?, longitude=?, update_time=? where id=? and tenant_id=?",
                    lat, lng, now, pid, tid);
            // 未完成任务中的点位快照一并更新，否则打卡仍按旧坐标校验
            jdbc.update(
                    "update insp_task_point tp join insp_task t on t.id=tp.task_id "
                            + "set tp.latitude=?, tp.longitude=? "
                            + "where tp.tenant_id=? and tp.point_id=? and t.status in ('PENDING','DOING')",
                    lat, lng, tid, pid);
            i++;
        }
        Map<String, Object> m = new HashMap<>();
        m.put("updated", points.size());
        m.put("latitude", latitude);
        m.put("longitude", longitude);
        return m;
    }

    @Transactional
    public void deletePoint(Long id) {
        assertCanManage();
        Long tid = requireTenant();
        Long used = jdbc.queryForObject(
                "select count(1) from insp_task_point tp join insp_task t on t.id=tp.task_id "
                        + "where tp.point_id=? and tp.tenant_id=? and t.status in ('PENDING','DOING')",
                Long.class, id, tid);
        if (used != null && used > 0) {
            throw new BizException("点位已被进行中/未开始任务引用，无法删除");
        }
        jdbc.update("update insp_point set status=0, update_time=? where id=? and tenant_id=?",
                Timestamp.valueOf(LocalDateTime.now()), id, tid);
    }

    /* ===================== 任务（PC） ===================== */

    public List<Map<String, Object>> listTasks(String status, String keyword) {
        assertStaffView();
        Long tid = requireTenant();
        maybeRefreshStatus(tid);
        StringBuilder sql = new StringBuilder(
                "select id, tenant_id as tenantId, title as task, title, assignee_id as assigneeId, "
                        + "assignee_name as assigneeName, assignee_name as owner, cycle_type as cycleType, "
                        + "start_time as startTime, end_time as endTime, requirement, remark, "
                        + "point_total as points, point_total as pointTotal, point_done as done, "
                        + "point_done as pointDone, hazard_count as hazardCount, status, "
                        + "parent_task_id as parentTaskId, create_time as createTime "
                        + "from insp_task where tenant_id=? ");
        List<Object> args = new ArrayList<>();
        args.add(tid);
        if (StringUtils.hasText(status)) {
            sql.append("and status=? ");
            args.add(status);
        }
        if (StringUtils.hasText(keyword)) {
            sql.append("and (title like ? or assignee_name like ?) ");
            String k = "%" + keyword.trim() + "%";
            args.add(k);
            args.add(k);
        }
        sql.append("order by id desc limit 500");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        rows.forEach(this::decorateTask);
        return rows;
    }

    @Transactional
    public Map<String, Object> saveTask(InspTaskSaveRequest req) {
        assertCanManage();
        Long tid = requireTenant();
        validateTask(req);
        List<Map<String, Object>> points = loadActivePoints(tid, req.getPointIds());
        if (points.isEmpty()) {
            throw new BizException("请至少选择一个有效巡检点位");
        }
        String cycle = normalizeCycle(req.getCycleType());
        String assigneeName = resolveAssigneeName(tid, req.getAssigneeId(), req.getAssigneeName());
        Timestamp start = parseDateTime(req.getStartTime());
        Timestamp end = parseDateTime(req.getEndTime());
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());

        if (req.getId() != null) {
            Map<String, Object> old = getTaskRaw(tid, req.getId());
            if (ST_DONE.equals(str(old.get("status")))) {
                throw new BizException("已完成任务不可编辑");
            }
            if (toInt(old.get("point_done")) > 0) {
                throw new BizException("已有打卡进度的任务不可修改点位，请新建任务");
            }
            jdbc.update(
                    "update insp_task set title=?, assignee_id=?, assignee_name=?, cycle_type=?, "
                            + "start_time=?, end_time=?, requirement=?, remark=?, point_total=?, update_time=? "
                            + "where id=? and tenant_id=?",
                    req.getTitle().trim(), req.getAssigneeId(), assigneeName, cycle,
                    start, end, req.getRequirement(), req.getRemark(), points.size(), now,
                    req.getId(), tid);
            jdbc.update("delete from insp_task_point where task_id=? and tenant_id=?", req.getId(), tid);
            insertTaskPoints(tid, req.getId(), points);
            notifyAssignee(tid, req.getAssigneeId(), req.getTitle().trim(), "巡检任务已更新，请及时执行");
            return getTaskDetail(req.getId());
        }

        KeyHolder kh = new GeneratedKeyHolder();
        Long createBy = TenantContext.getUserId();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "insert into insp_task(tenant_id,title,assignee_id,assignee_name,cycle_type,start_time,end_time,"
                            + "requirement,remark,point_total,point_done,hazard_count,status,create_by,create_time) "
                            + "values(?,?,?,?,?,?,?,?,?,?,0,0,'PENDING',?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            int i = 1;
            ps.setLong(i++, tid);
            ps.setString(i++, req.getTitle().trim());
            ps.setLong(i++, req.getAssigneeId());
            ps.setString(i++, assigneeName);
            ps.setString(i++, cycle);
            ps.setTimestamp(i++, start);
            ps.setTimestamp(i++, end);
            ps.setString(i++, req.getRequirement());
            ps.setString(i++, req.getRemark());
            ps.setInt(i++, points.size());
            if (createBy == null) ps.setObject(i++, null);
            else ps.setLong(i++, createBy);
            ps.setTimestamp(i, now);
            return ps;
        }, kh);
        long taskId = kh.getKey().longValue();
        insertTaskPoints(tid, taskId, points);
        notifyAssignee(tid, req.getAssigneeId(), req.getTitle().trim(), "您有新的巡检任务，请打开App执行打卡");
        return getTaskDetail(taskId);
    }

    @Transactional
    public void deleteTask(Long id) {
        assertCanManage();
        Long tid = requireTenant();
        Map<String, Object> task = getTaskRaw(tid, id);
        if (ST_DONE.equals(str(task.get("status")))) {
            throw new BizException("已完成的巡检任务不允许删除");
        }
        jdbc.update("delete from insp_task_point where task_id=? and tenant_id=?", id, tid);
        jdbc.update("delete from insp_task where id=? and tenant_id=?", id, tid);
        // 打卡记录永久保留，不删
    }

    public Map<String, Object> getTaskDetail(Long id) {
        assertStaffView();
        Long tid = requireTenant();
        Map<String, Object> task = decorateTask(mapTask(getTaskRaw(tid, id)));
        List<Map<String, Object>> points = jdbc.queryForList(
                "select tp.id, tp.task_id as taskId, tp.point_id as pointId, tp.point_name as pointName, "
                        + "tp.location_desc as locationDesc, tp.latitude, tp.longitude, tp.sort_no as sortNo, "
                        + "tp.checked, tp.checkin_id as checkinId, "
                        + "c.check_time as checkTime, c.photo_urls as photoUrls, c.remark as checkinRemark, "
                        + "c.has_hazard as hasHazard, c.hazard_desc as hazardDesc, c.user_name as checkerName, "
                        + "c.distance_m as distanceM "
                        + "from insp_task_point tp "
                        + "left join insp_checkin c on c.id = tp.checkin_id "
                        + "where tp.tenant_id=? and tp.task_id=? order by tp.sort_no, tp.id",
                tid, id);
        for (Map<String, Object> p : points) {
            p.put("photos", splitUrls(str(p.get("photoUrls"))));
        }
        task.put("taskPoints", points);
        task.put("pointIds", points.stream().map(p -> toLong(p.get("pointId"))).toList());
        return task;
    }

    private static List<String> splitUrls(String s) {
        if (!StringUtils.hasText(s)) return List.of();
        List<String> out = new ArrayList<>();
        for (String part : s.split("[,;]")) {
            String u = part.trim();
            // 过滤无效的本地 blob，无法在 PC 明细展示
            if (!u.isEmpty() && !u.startsWith("blob:")) {
                out.add(u);
            }
        }
        return out;
    }

    public List<Map<String, Object>> listHazards(String status) {
        assertStaffView();
        Long tid = requireTenant();
        StringBuilder sql = new StringBuilder(
                "select c.id, c.task_id as taskId, t.title as taskTitle, c.point_name as pointName, "
                        + "c.user_name as userName, c.check_time as checkTime, c.hazard_desc as hazardDesc, "
                        + "c.hazard_status as hazardStatus, c.photo_urls as photoUrls "
                        + "from insp_checkin c left join insp_task t on t.id=c.task_id "
                        + "where c.tenant_id=? and c.has_hazard=1 ");
        List<Object> args = new ArrayList<>();
        args.add(tid);
        if (StringUtils.hasText(status)) {
            sql.append("and c.hazard_status=? ");
            args.add(status);
        }
        sql.append("order by c.id desc limit 500");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    @Transactional
    public void fixHazard(Long checkinId) {
        assertCanManage();
        Long tid = requireTenant();
        jdbc.update(
                "update insp_checkin set hazard_status='FIXED' where id=? and tenant_id=? and has_hazard=1",
                checkinId, tid);
    }

    /* ===================== App 执行 ===================== */

    public List<Map<String, Object>> myTasks(String status, String scope) {
        assertStaffView();
        Long tid = requireTenant();
        Long uid = requireUser();
        maybeRefreshStatus(tid);
        String role = TenantContext.getRole();
        boolean adminView = RoleConstants.isPropertyAdmin(role) || RoleConstants.isPlatformAdmin(role);
        // assigned：强制只看指派给自己的（首页待办/汇总页）；all：管理员可看本社区全部
        boolean onlyAssigned = "assigned".equalsIgnoreCase(scope) || !adminView;

        StringBuilder sql = new StringBuilder(
                "select id, title, assignee_id as assigneeId, assignee_name as assigneeName, "
                        + "cycle_type as cycleType, start_time as startTime, end_time as endTime, "
                        + "requirement, remark, point_total as pointTotal, point_done as pointDone, "
                        + "hazard_count as hazardCount, status, parent_task_id as parentTaskId "
                        + "from insp_task where tenant_id=? ");
        List<Object> args = new ArrayList<>();
        args.add(tid);
        if (onlyAssigned) {
            sql.append("and assignee_id=? ");
            args.add(uid);
        }
        if (StringUtils.hasText(status) && !"all".equalsIgnoreCase(status)) {
            if ("PENDING".equalsIgnoreCase(status)) {
                sql.append("and status in ('PENDING','OVERDUE') ");
            } else {
                sql.append("and status=? ");
                args.add(status);
            }
        }
        // 待执行列表：未到开始时间的循环新任务不展示，避免「打完又出现未打卡」误解
        if (!"DONE".equalsIgnoreCase(status) && !"all".equalsIgnoreCase(status)) {
            sql.append("and (start_time is null or start_time <= now()) ");
        }
        sql.append("order by field(status,'OVERDUE','DOING','PENDING','DONE'), id desc limit 200");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        rows.forEach(this::decorateTask);
        return rows;
    }

    public List<Map<String, Object>> myHazards(String status) {
        assertStaffView();
        Long tid = requireTenant();
        Long uid = requireUser();
        String role = TenantContext.getRole();
        boolean adminView = RoleConstants.isPropertyAdmin(role) || RoleConstants.isPlatformAdmin(role);

        StringBuilder sql = new StringBuilder(
                "select c.id, c.task_id as taskId, t.title as taskTitle, c.point_name as pointName, "
                        + "c.user_id as userId, c.user_name as userName, c.check_time as checkTime, "
                        + "c.hazard_desc as hazardDesc, c.hazard_status as hazardStatus, "
                        + "c.photo_urls as photoUrls, c.latitude, c.longitude "
                        + "from insp_checkin c left join insp_task t on t.id=c.task_id "
                        + "where c.tenant_id=? and c.has_hazard=1 ");
        List<Object> args = new ArrayList<>();
        args.add(tid);
        if (!adminView) {
            sql.append("and c.user_id=? ");
            args.add(uid);
        }
        if (StringUtils.hasText(status)) {
            sql.append("and c.hazard_status=? ");
            args.add(status);
        }
        sql.append("order by c.id desc limit 200");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    /**
     * 首页待办：当前登录人（执行人）名下未开始/进行中的巡检任务数量
     */
    public Map<String, Object> mineTodoSummary() {
        assertStaffView();
        Long tid = requireTenant();
        Long uid = requireUser();
        Long pending = jdbc.queryForObject(
                "select count(1) from insp_task where tenant_id=? and assignee_id=? "
                        + "and status in ('PENDING','DOING','OVERDUE') and (start_time is null or start_time <= now())",
                Long.class, tid, uid);
        Long doing = jdbc.queryForObject(
                "select count(1) from insp_task where tenant_id=? and assignee_id=? and status='DOING' "
                        + "and (start_time is null or start_time <= now())",
                Long.class, tid, uid);
        Long notStart = jdbc.queryForObject(
                "select count(1) from insp_task where tenant_id=? and assignee_id=? and status='PENDING' "
                        + "and (start_time is null or start_time <= now())",
                Long.class, tid, uid);
        Map<String, Object> m = new HashMap<>();
        m.put("todoCount", pending == null ? 0 : pending.intValue());
        m.put("doingCount", doing == null ? 0 : doing.intValue());
        m.put("pendingCount", notStart == null ? 0 : notStart.intValue());
        return m;
    }

    public Map<String, Object> myTaskDetail(Long id) {
        assertStaffView();
        Long tid = requireTenant();
        Long uid = requireUser();
        Map<String, Object> task = getTaskDetail(id);
        if (!RoleConstants.isPropertyAdmin(TenantContext.getRole())
                && !Objects.equals(toLong(task.get("assigneeId")), uid)) {
            throw new BizException(403, "无权查看该巡检任务");
        }
        return task;
    }

    @Transactional
    public Map<String, Object> checkin(Long taskId, InspCheckinRequest req) {
        assertStaffView();
        Long tid = requireTenant();
        Long uid = requireUser();
        if (req.getTaskPointId() == null) {
            throw new BizException("缺少任务点位");
        }
        Map<String, Object> task = getTaskRaw(tid, taskId);
        if (!Objects.equals(toLong(task.get("assignee_id")), uid)
                && !RoleConstants.isPropertyAdmin(TenantContext.getRole())) {
            throw new BizException(403, "仅任务执行人可打卡");
        }
        String st = str(task.get("status"));
        if (ST_DONE.equals(st)) {
            throw new BizException("任务已完成");
        }
        List<Map<String, Object>> tps = jdbc.queryForList(
                "select * from insp_task_point where tenant_id=? and task_id=? and id=? limit 1",
                tid, taskId, req.getTaskPointId());
        if (tps.isEmpty()) {
            throw new BizException("点位不存在");
        }
        Map<String, Object> tp = tps.get(0);
        if (toInt(tp.get("checked")) == 1) {
            throw new BizException("该点位已打卡");
        }

        Double lat = req.getLatitude();
        Double lng = req.getLongitude();
        Double pLat = toDouble(tp.get("latitude"));
        Double pLng = toDouble(tp.get("longitude"));
        boolean gpsOk = true;
        Double distance = null;
        if (pLat != null && pLng != null) {
            if (lat == null || lng == null) {
                throw new BizException("该点位需开启GPS定位后打卡");
            }
            distance = haversineMeters(pLat, pLng, lat, lng);
            if (distance > GPS_RADIUS_M) {
                gpsOk = false;
                throw new BizException(String.format(
                        "定位偏差过大（%.0f米），请到达点位 %.0f 米范围内再打卡", distance, GPS_RADIUS_M));
            }
        }

        boolean hazard = Boolean.TRUE.equals(req.getHasHazard());
        if (hazard && !StringUtils.hasText(req.getHazardDesc())) {
            throw new BizException("发现隐患时请填写隐患描述");
        }

        String userName = resolveUserName(uid);
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        KeyHolder kh = new GeneratedKeyHolder();
        Double finalDistance = distance;
        boolean finalGpsOk = gpsOk;
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "insert into insp_checkin(tenant_id,task_id,task_point_id,point_id,point_name,user_id,user_name,"
                            + "check_time,latitude,longitude,distance_m,gps_ok,photo_urls,has_hazard,hazard_desc,"
                            + "hazard_status,remark,create_time) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            int i = 1;
            ps.setLong(i++, tid);
            ps.setLong(i++, taskId);
            ps.setLong(i++, req.getTaskPointId());
            ps.setLong(i++, toLong(tp.get("point_id")));
            ps.setString(i++, str(tp.get("point_name")));
            ps.setLong(i++, uid);
            ps.setString(i++, userName);
            ps.setTimestamp(i++, now);
            setDouble(ps, i++, lat);
            setDouble(ps, i++, lng);
            setDouble(ps, i++, finalDistance);
            ps.setInt(i++, finalGpsOk ? 1 : 0);
            ps.setString(i++, req.getPhotoUrls());
            ps.setInt(i++, hazard ? 1 : 0);
            ps.setString(i++, req.getHazardDesc());
            ps.setString(i++, hazard ? "OPEN" : null);
            ps.setString(i++, req.getRemark());
            ps.setTimestamp(i, now);
            return ps;
        }, kh);
        long checkinId = kh.getKey().longValue();
        jdbc.update(
                "update insp_task_point set checked=1, checkin_id=? where id=? and tenant_id=?",
                checkinId, req.getTaskPointId(), tid);

        int done = toInt(task.get("point_done")) + 1;
        int total = toInt(task.get("point_total"));
        int hazards = toInt(task.get("hazard_count")) + (hazard ? 1 : 0);
        String newStatus = done >= total ? ST_DONE : ST_DOING;
        jdbc.update(
                "update insp_task set point_done=?, hazard_count=?, status=?, update_time=? where id=? and tenant_id=?",
                done, hazards, newStatus, now, taskId, tid);

        if (ST_DONE.equals(newStatus)) {
            try {
                spawnNextCycleIfNeeded(tid, task);
            } catch (Exception e) {
                // 循环任务生成失败不影响本次打卡成功
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("checkinId", checkinId);
        result.put("pointDone", done);
        result.put("pointTotal", total);
        result.put("status", newStatus);
        result.put("statusLabel", statusLabel(newStatus));
        result.put("gpsOk", gpsOk);
        result.put("distanceM", distance);
        return result;
    }

    /* ===================== helpers ===================== */

    private void spawnNextCycleIfNeeded(Long tid, Map<String, Object> finished) {
        String cycle = str(finished.get("cycle_type"));
        if (!"DAILY".equals(cycle) && !"WEEKLY".equals(cycle)) {
            return;
        }
        long days = "WEEKLY".equals(cycle) ? 7 : 1;
        LocalDateTime oldStart = toLocalDateTime(finished.get("start_time"));
        LocalDateTime oldEnd = toLocalDateTime(finished.get("end_time"));
        LocalDateTime ns = (oldStart != null ? oldStart : LocalDateTime.now()).plusDays(days);
        LocalDateTime ne = (oldEnd != null ? oldEnd : LocalDateTime.now().plusDays(1)).plusDays(days);

        // 避免重复生成：同标题+同执行人+同周期+未完成已存在则跳过
        Long exist = jdbc.queryForObject(
                "select count(1) from insp_task where tenant_id=? and assignee_id=? and title=? and cycle_type=? "
                        + "and status in ('PENDING','DOING') and parent_task_id=?",
                Long.class, tid, finished.get("assignee_id"), finished.get("title"), cycle, finished.get("id"));
        if (exist != null && exist > 0) {
            return;
        }

        List<Map<String, Object>> pts = jdbc.queryForList(
                "select point_id from insp_task_point where tenant_id=? and task_id=? order by sort_no,id",
                tid, finished.get("id"));
        List<Long> pointIds = pts.stream().map(p -> toLong(p.get("point_id"))).toList();
        if (pointIds.isEmpty()) {
            return;
        }
        List<Map<String, Object>> points = loadActivePoints(tid, pointIds);
        KeyHolder kh = new GeneratedKeyHolder();
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "insert into insp_task(tenant_id,title,assignee_id,assignee_name,cycle_type,start_time,end_time,"
                            + "requirement,remark,point_total,point_done,hazard_count,status,parent_task_id,create_by,create_time) "
                            + "values(?,?,?,?,?,?,?,?,?,?,0,0,'PENDING',?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            int i = 1;
            ps.setLong(i++, tid);
            ps.setString(i++, str(finished.get("title")));
            ps.setLong(i++, toLong(finished.get("assignee_id")));
            ps.setString(i++, str(finished.get("assignee_name")));
            ps.setString(i++, cycle);
            ps.setTimestamp(i++, Timestamp.valueOf(ns));
            ps.setTimestamp(i++, Timestamp.valueOf(ne));
            ps.setString(i++, str(finished.get("requirement")));
            ps.setString(i++, str(finished.get("remark")));
            ps.setInt(i++, points.size());
            ps.setLong(i++, toLong(finished.get("id")));
            Long createBy = TenantContext.getUserId();
            if (createBy == null) ps.setObject(i++, null);
            else ps.setLong(i++, createBy);
            ps.setTimestamp(i, now);
            return ps;
        }, kh);
        long newId = kh.getKey().longValue();
        insertTaskPoints(tid, newId, points);
        notifyAssignee(tid, toLong(finished.get("assignee_id")), str(finished.get("title")),
                "循环巡检任务已自动生成新一轮，请继续执行");
    }

    private void maybeRefreshStatus(Long tid) {
        try {
            jdbc.update(
                    "update insp_task set status=?, update_time=now() "
                            + "where tenant_id=? and status in ('PENDING','DOING') "
                            + "and end_time is not null and end_time < now()",
                    ST_OVERDUE, tid);
        } catch (Exception ignored) {
        }
    }

    /** 巡检打卡台账 */
    public List<Map<String, Object>> listCheckins(String keyword, String result, Long taskId, int limit) {
        assertStaffView();
        Long tid = requireTenant();
        int lim = Math.min(Math.max(limit, 1), 500);
        StringBuilder sql = new StringBuilder(
                "select c.id, c.task_id as taskId, t.title as taskTitle, c.point_id as pointId, c.point_name as pointName, "
                        + "c.user_id as userId, c.user_name as userName, c.check_time as checkTime, "
                        + "c.latitude, c.longitude, c.distance_m as distanceM, c.gps_ok as gpsOk, "
                        + "c.photo_urls as photoUrls, c.has_hazard as hasHazard, c.hazard_desc as hazardDesc, "
                        + "c.hazard_status as hazardStatus, c.remark, "
                        + "case when c.has_hazard=1 then 'ABNORMAL' else 'NORMAL' end as result "
                        + "from insp_checkin c left join insp_task t on t.id=c.task_id "
                        + "where c.tenant_id=? ");
        List<Object> args = new ArrayList<>();
        args.add(tid);
        if (taskId != null) {
            sql.append("and c.task_id=? ");
            args.add(taskId);
        }
        if ("ABNORMAL".equalsIgnoreCase(result) || "异常".equals(result)) {
            sql.append("and c.has_hazard=1 ");
        } else if ("NORMAL".equalsIgnoreCase(result) || "正常".equals(result)) {
            sql.append("and (c.has_hazard=0 or c.has_hazard is null) ");
        }
        if (StringUtils.hasText(keyword)) {
            sql.append("and (c.point_name like ? or c.user_name like ? or t.title like ? or c.hazard_desc like ?) ");
            String k = "%" + keyword.trim() + "%";
            args.add(k); args.add(k); args.add(k); args.add(k);
        }
        sql.append("order by c.id desc limit ").append(lim);
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        for (Map<String, Object> r : rows) {
            Object ct = r.get("checkTime");
            if (ct != null) {
                String s = String.valueOf(ct).replace('T', ' ');
                r.put("checkTime", s.length() > 19 ? s.substring(0, 19) : s);
            }
            boolean abnormal = r.get("hasHazard") != null && ((Number) r.get("hasHazard")).intValue() == 1;
            r.put("result", abnormal ? "ABNORMAL" : "NORMAL");
            r.put("resultLabel", abnormal ? "异常" : "正常");
            r.put("photos", splitUrls(str(r.get("photoUrls"))));
        }
        return rows;
    }

    private void insertTaskPoints(Long tid, long taskId, List<Map<String, Object>> points) {
        int sort = 1;
        for (Map<String, Object> p : points) {
            jdbc.update(
                    "insert into insp_task_point(tenant_id,task_id,point_id,point_name,location_desc,latitude,longitude,sort_no,checked) "
                            + "values(?,?,?,?,?,?,?,?,0)",
                    tid, taskId, p.get("id"), p.get("name"), p.get("locationDesc"),
                    p.get("latitude"), p.get("longitude"), sort++);
        }
    }

    private List<Map<String, Object>> loadActivePoints(Long tid, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        String in = ids.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("0");
        return jdbc.queryForList(
                "select id, name, location_desc as locationDesc, latitude, longitude "
                        + "from insp_point where tenant_id=? and status=1 and id in (" + in + ")",
                tid);
    }

    private void notifyAssignee(Long tid, Long assigneeId, String title, String content) {
        if (assigneeId == null) return;
        try {
            jdbc.update(
                    "insert into sys_message(tenant_id,user_id,title,content,msg_type,read_flag,create_time) values(?,?,?,?,?,?,now())",
                    tid, assigneeId, "巡检任务：" + title, content, "inspection", 0);
        } catch (Exception ignored) {
            // 消息表缺失时不影响主流程
        }
    }

    private Map<String, Object> getPoint(Long tid, Long id) {
        ensurePointExt();
        List<Map<String, Object>> list = jdbc.queryForList(
                "select id, tenant_id as tenantId, name, point_type as pointType, location_desc as locationDesc, address, "
                        + "latitude, longitude, status, qr_code as qrCode, qr_enabled as qrEnabled, remark, create_time as createTime "
                        + "from insp_point where tenant_id=? and id=? limit 1",
                tid, id);
        if (list.isEmpty()) throw new BizException(404, "点位不存在");
        return decoratePoint(list.get(0));
    }

    private Map<String, Object> decoratePoint(Map<String, Object> m) {
        String type = str(m.get("pointType"));
        if (!StringUtils.hasText(type)) type = "OTHER";
        m.put("pointType", type);
        m.put("pointTypeLabel", pointTypeLabel(type));
        Object qe = m.get("qrEnabled");
        int en = qe == null ? 1 : ((Number) qe).intValue();
        m.put("qrEnabled", en);
        m.put("qrEnabledLabel", en == 1 ? "已启用" : "未启用");
        return m;
    }

    private String normalizePointType(String t) {
        if (!StringUtils.hasText(t)) return "OTHER";
        String u = t.trim().toUpperCase();
        return switch (u) {
            case "FIRE", "消防" -> "FIRE";
            case "ELEVATOR", "电梯" -> "ELEVATOR";
            case "PARKING", "车库", "停车" -> "PARKING";
            case "GARDEN", "绿化", "花园" -> "GARDEN";
            case "GATE", "门禁", "出入口" -> "GATE";
            case "PUMP", "水泵" -> "PUMP";
            default -> "OTHER";
        };
    }

    private String pointTypeLabel(String t) {
        return switch (t == null ? "" : t) {
            case "FIRE" -> "消防";
            case "ELEVATOR" -> "电梯";
            case "PARKING" -> "车库";
            case "GARDEN" -> "绿化";
            case "GATE" -> "门禁";
            case "PUMP" -> "水泵";
            default -> "其他";
        };
    }

    private void ensurePointExt() {
        try {
            Integer c = jdbc.queryForObject(
                    "select count(1) from information_schema.columns where table_schema=database() "
                            + "and table_name='insp_point' and column_name='point_type'", Integer.class);
            if (c == null || c == 0) {
                jdbc.execute("alter table insp_point add column point_type varchar(32) null");
            }
        } catch (Exception ignored) {
        }
        try {
            Integer c = jdbc.queryForObject(
                    "select count(1) from information_schema.columns where table_schema=database() "
                            + "and table_name='insp_point' and column_name='qr_code'", Integer.class);
            if (c == null || c == 0) {
                jdbc.execute("alter table insp_point add column qr_code varchar(64) null");
            }
        } catch (Exception ignored) {
        }
        try {
            Integer c = jdbc.queryForObject(
                    "select count(1) from information_schema.columns where table_schema=database() "
                            + "and table_name='insp_point' and column_name='qr_enabled'", Integer.class);
            if (c == null || c == 0) {
                jdbc.execute("alter table insp_point add column qr_enabled tinyint not null default 1");
            }
        } catch (Exception ignored) {
        }
        // 为无二维码的旧点位补码
        try {
            List<Map<String, Object>> miss = jdbc.queryForList(
                    "select id from insp_point where qr_code is null or qr_code='' limit 200");
            for (Map<String, Object> r : miss) {
                jdbc.update("update insp_point set qr_code=? where id=?",
                        java.util.UUID.randomUUID().toString().replace("-", ""), r.get("id"));
            }
        } catch (Exception ignored) {
        }
    }

    private Map<String, Object> getTaskRaw(Long tid, Long id) {
        List<Map<String, Object>> list = jdbc.queryForList(
                "select * from insp_task where tenant_id=? and id=? limit 1", tid, id);
        if (list.isEmpty()) throw new BizException(404, "任务不存在");
        return list.get(0);
    }

    private Map<String, Object> mapTask(Map<String, Object> raw) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", raw.get("id"));
        m.put("tenantId", raw.get("tenant_id") != null ? raw.get("tenant_id") : raw.get("tenantId"));
        m.put("title", raw.get("title"));
        m.put("task", raw.get("title"));
        m.put("assigneeId", raw.get("assignee_id") != null ? raw.get("assignee_id") : raw.get("assigneeId"));
        m.put("assigneeName", raw.get("assignee_name") != null ? raw.get("assignee_name") : raw.get("assigneeName"));
        m.put("owner", m.get("assigneeName"));
        m.put("cycleType", raw.get("cycle_type") != null ? raw.get("cycle_type") : raw.get("cycleType"));
        m.put("startTime", raw.get("start_time") != null ? raw.get("start_time") : raw.get("startTime"));
        m.put("endTime", raw.get("end_time") != null ? raw.get("end_time") : raw.get("endTime"));
        m.put("requirement", raw.get("requirement"));
        m.put("remark", raw.get("remark"));
        m.put("pointTotal", raw.get("point_total") != null ? raw.get("point_total") : raw.get("pointTotal"));
        m.put("points", m.get("pointTotal"));
        m.put("pointDone", raw.get("point_done") != null ? raw.get("point_done") : raw.get("pointDone"));
        m.put("done", m.get("pointDone"));
        m.put("hazardCount", raw.get("hazard_count") != null ? raw.get("hazard_count") : raw.get("hazardCount"));
        m.put("status", raw.get("status"));
        m.put("parentTaskId", raw.get("parent_task_id") != null ? raw.get("parent_task_id") : raw.get("parentTaskId"));
        m.put("createTime", raw.get("create_time") != null ? raw.get("create_time") : raw.get("createTime"));
        return m;
    }

    private Map<String, Object> decorateTask(Map<String, Object> m) {
        Object id = m.get("id") != null ? m.get("id") : m.get("ID");
        if (id instanceof Number) {
            m.put("id", ((Number) id).longValue());
        } else if (id != null) {
            m.put("id", id);
        }
        String st = str(m.get("status"));
        m.put("statusLabel", statusLabel(st));
        String cycle = str(m.get("cycleType") != null ? m.get("cycleType") : m.get("cycle_type"));
        m.put("cycleType", cycle);
        m.put("cycleLabel", switch (cycle) {
            case "DAILY" -> "每日";
            case "WEEKLY" -> "每周";
            default -> "单次";
        });
        return m;
    }

    private String statusLabel(String st) {
        return switch (st) {
            case ST_DOING -> "进行中";
            case ST_DONE -> "已完成";
            case ST_OVERDUE -> "逾期";
            default -> "待执行";
        };
    }

    private void validateTask(InspTaskSaveRequest req) {
        if (!StringUtils.hasText(req.getTitle())) throw new BizException("请填写任务名称");
        if (req.getAssigneeId() == null) throw new BizException("请选择执行人");
        if (req.getPointIds() == null || req.getPointIds().isEmpty()) {
            throw new BizException("请选择巡检点位");
        }
    }

    private String normalizeCycle(String c) {
        if (c == null) return "ONCE";
        String u = c.trim().toUpperCase();
        if (u.contains("日") || "DAILY".equals(u)) return "DAILY";
        if (u.contains("周") || "WEEKLY".equals(u)) return "WEEKLY";
        return "ONCE";
    }

    private String resolveAssigneeName(Long tid, Long assigneeId, String fallback) {
        if (StringUtils.hasText(fallback)) return fallback.trim();
        try {
            List<Map<String, Object>> u = jdbc.queryForList(
                    "select nickname, phone from sys_user where id=? and (tenant_id=? or tenant_id is null) limit 1",
                    assigneeId, tid);
            if (!u.isEmpty()) {
                String n = str(u.get(0).get("nickname"));
                return StringUtils.hasText(n) ? n : str(u.get(0).get("phone"));
            }
        } catch (Exception ignored) {
        }
        return "执行人#" + assigneeId;
    }

    private String resolveUserName(Long uid) {
        try {
            List<Map<String, Object>> u = jdbc.queryForList(
                    "select nickname, phone from sys_user where id=? limit 1", uid);
            if (!u.isEmpty()) {
                String n = str(u.get(0).get("nickname"));
                return StringUtils.hasText(n) ? n : str(u.get(0).get("phone"));
            }
        } catch (Exception ignored) {
        }
        return "用户#" + uid;
    }

    private void assertStaffView() {
        String role = TenantContext.getRole();
        if (RoleConstants.isPropertyAdmin(role) || RoleConstants.isGridWorker(role)
                || RoleConstants.isPlatformAdmin(role) || RoleConstants.isTenantStaff(role)) {
            return;
        }
        throw new BizException(403, "无权访问巡检功能");
    }

    private void assertCanManage() {
        String role = TenantContext.getRole();
        if (RoleConstants.isPropertyAdmin(role) || RoleConstants.isPlatformAdmin(role)) {
            return;
        }
        throw new BizException(403, "仅物业管理员可管理巡检任务/点位");
    }

    private Long requireTenant() {
        Long tid = TenantContext.getTenantId();
        if (tid == null) throw new BizException(40001, "缺少租户上下文");
        return tid;
    }

    private Long requireUser() {
        Long uid = TenantContext.getUserId();
        if (uid == null) throw new BizException(401, "未登录");
        return uid;
    }

    private static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static void setDouble(PreparedStatement ps, int idx, Double v) throws java.sql.SQLException {
        if (v == null) ps.setObject(idx, null);
        else ps.setBigDecimal(idx, BigDecimal.valueOf(v).setScale(7, RoundingMode.HALF_UP));
    }

    private static Timestamp parseDateTime(String s) {
        if (!StringUtils.hasText(s)) return null;
        String t = s.trim().replace('T', ' ');
        if (t.length() == 16) t = t + ":00";
        try {
            return Timestamp.valueOf(LocalDateTime.parse(t.substring(0, Math.min(19, t.length())), DT));
        } catch (Exception e) {
            throw new BizException("时间格式错误，应为 yyyy-MM-dd HH:mm:ss");
        }
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).longValue();
        try {
            return Long.valueOf(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
    }

    private static int toInt(Object o) {
        if (o == null) return 0;
        if (o instanceof Number) return ((Number) o).intValue();
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception e) {
            return 0;
        }
    }

    private static LocalDateTime toLocalDateTime(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof LocalDateTime) {
            return (LocalDateTime) o;
        }
        if (o instanceof Timestamp) {
            return ((Timestamp) o).toLocalDateTime();
        }
        if (o instanceof java.util.Date) {
            return new Timestamp(((java.util.Date) o).getTime()).toLocalDateTime();
        }
        String s = String.valueOf(o).trim().replace('T', ' ');
        if (s.length() >= 19) {
            try {
                return LocalDateTime.parse(s.substring(0, 19), DT);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static Double toDouble(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).doubleValue();
        try {
            return Double.valueOf(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
    }
}
