package com.community.repair.service;

import com.community.common.constant.RoleConstants;
import com.community.common.exception.BizException;
import com.community.common.tenant.TenantContext;
import com.community.repair.dto.EquipDeviceSaveRequest;
import com.community.repair.dto.EquipMaintRecordRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EquipmentService {

    public static final String ST_NORMAL = "NORMAL";
    public static final String ST_DUE = "DUE";
    public static final String ST_OVERDUE = "OVERDUE";
    /** 临近维保提醒天数 */
    public static final int DUE_DAYS = 7;

    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final JdbcTemplate jdbc;

    public List<Map<String, Object>> listDevices(String status, String keyword) {
        assertStaffView();
        Long tid = requireTenant();
        ensureQuantityColumn();
        refreshStatuses(tid);
        StringBuilder sql = new StringBuilder(
                "select id, tenant_id as tenantId, name, location, device_type as deviceType, "
                        + "ifnull(quantity,1) as quantity, "
                        + "cycle_type as cycleType, vendor_name as vendorName, contact_name as contactName, "
                        + "contact_phone as contactPhone, last_maint_date as lastMaintDate, "
                        + "next_maint_date as nextMaintDate, status, remark, create_time as createTime "
                        + "from equip_device where tenant_id=? ");
        List<Object> args = new ArrayList<>();
        args.add(tid);
        if (StringUtils.hasText(status)) {
            sql.append("and status=? ");
            args.add(status);
        }
        if (StringUtils.hasText(keyword)) {
            sql.append("and (name like ? or location like ? or device_type like ? or vendor_name like ?) ");
            String k = "%" + keyword.trim() + "%";
            args.add(k);
            args.add(k);
            args.add(k);
            args.add(k);
        }
        sql.append("order by field(status,'OVERDUE','DUE','NORMAL'), next_maint_date asc, id desc limit 500");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        rows.forEach(this::decorateDevice);
        return rows;
    }

    public Map<String, Object> getDevice(Long id) {
        assertStaffView();
        Long tid = requireTenant();
        ensureQuantityColumn();
        refreshStatuses(tid);
        return decorateDevice(mapDevice(getDeviceRaw(tid, id)));
    }

    @Transactional
    public Map<String, Object> saveDevice(EquipDeviceSaveRequest req) {
        assertCanManageDevice();
        Long tid = requireTenant();
        ensureQuantityColumn();
        validateDevice(req);
        String cycle = normalizeCycle(req.getCycleType());
        int quantity = req.getQuantity() == null || req.getQuantity() < 1 ? 1 : req.getQuantity();
        Date last = parseDate(req.getLastMaintDate());
        Date next = parseDate(req.getNextMaintDate());
        if (next == null && last != null) {
            next = Date.valueOf(calcNext(last.toLocalDate(), cycle));
        }
        String status = calcStatus(next == null ? null : next.toLocalDate());
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        final Date lastDate = last;
        final Date nextDate = next;
        final String statusFinal = status;
        final int qtyFinal = quantity;

        if (req.getId() != null) {
            getDeviceRaw(tid, req.getId());
            jdbc.update(
                    "update equip_device set name=?, location=?, device_type=?, quantity=?, cycle_type=?, vendor_name=?, "
                            + "contact_name=?, contact_phone=?, last_maint_date=?, next_maint_date=?, status=?, "
                            + "remark=?, update_time=? where id=? and tenant_id=?",
                    req.getName().trim(), req.getLocation(), req.getDeviceType().trim(), qtyFinal, cycle,
                    req.getVendorName(), req.getContactName(), req.getContactPhone(),
                    lastDate, nextDate, statusFinal, req.getRemark(), now, req.getId(), tid);
            return getDevice(req.getId());
        }

        KeyHolder kh = new GeneratedKeyHolder();
        Long createBy = TenantContext.getUserId();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "insert into equip_device(tenant_id,name,location,device_type,quantity,cycle_type,vendor_name,"
                            + "contact_name,contact_phone,last_maint_date,next_maint_date,status,remark,create_by,create_time) "
                            + "values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            int i = 1;
            ps.setLong(i++, tid);
            ps.setString(i++, req.getName().trim());
            ps.setString(i++, req.getLocation());
            ps.setString(i++, req.getDeviceType().trim());
            ps.setInt(i++, qtyFinal);
            ps.setString(i++, cycle);
            ps.setString(i++, req.getVendorName());
            ps.setString(i++, req.getContactName());
            ps.setString(i++, req.getContactPhone());
            ps.setDate(i++, lastDate);
            ps.setDate(i++, nextDate);
            ps.setString(i++, statusFinal);
            ps.setString(i++, req.getRemark());
            if (createBy == null) ps.setObject(i++, null);
            else ps.setLong(i++, createBy);
            ps.setTimestamp(i, now);
            return ps;
        }, kh);
        return getDevice(kh.getKey().longValue());
    }

    @Transactional
    public void deleteDevice(Long id) {
        assertCanManageDevice();
        Long tid = requireTenant();
        getDeviceRaw(tid, id);
        Long cnt = jdbc.queryForObject(
                "select count(1) from equip_maint_record where tenant_id=? and device_id=?",
                Long.class, tid, id);
        if (cnt != null && cnt > 0) {
            throw new BizException("该设备已有维保历史记录，禁止直接删除");
        }
        jdbc.update("delete from equip_device where id=? and tenant_id=?", id, tid);
    }

    public List<Map<String, Object>> listRecords(Long deviceId) {
        assertStaffView();
        Long tid = requireTenant();
        getDeviceRaw(tid, deviceId);
        return jdbc.queryForList(
                "select id, device_id as deviceId, device_name as deviceName, maint_date as maintDate, "
                        + "content, photo_urls as photoUrls, attachment_urls as attachmentUrls, "
                        + "worker_name as workerName, create_by_name as createByName, remark, "
                        + "advance_next as advanceNext, create_time as createTime "
                        + "from equip_maint_record where tenant_id=? and device_id=? order by maint_date desc, id desc",
                tid, deviceId);
    }

    @Transactional
    public Map<String, Object> addRecord(EquipMaintRecordRequest req) {
        assertCanAddRecord();
        Long tid = requireTenant();
        if (req.getDeviceId() == null) throw new BizException("缺少设备ID");
        if (!StringUtils.hasText(req.getContent())) throw new BizException("请填写维保内容");
        Map<String, Object> device = getDeviceRaw(tid, req.getDeviceId());
        Date maintDate = parseDate(req.getMaintDate());
        if (maintDate == null) {
            maintDate = Date.valueOf(LocalDate.now());
        }
        boolean advance = req.getAdvanceNext() == null || Boolean.TRUE.equals(req.getAdvanceNext());
        Long uid = TenantContext.getUserId();
        String userName = resolveUserName(uid);
        String worker = StringUtils.hasText(req.getWorkerName()) ? req.getWorkerName().trim() : userName;

        KeyHolder kh = new GeneratedKeyHolder();
        Date finalMaint = maintDate;
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "insert into equip_maint_record(tenant_id,device_id,device_name,maint_date,content,photo_urls,"
                            + "attachment_urls,worker_name,create_by,create_by_name,remark,advance_next,create_time) "
                            + "values(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            int i = 1;
            ps.setLong(i++, tid);
            ps.setLong(i++, req.getDeviceId());
            ps.setString(i++, str(device.get("name")));
            ps.setDate(i++, finalMaint);
            ps.setString(i++, req.getContent().trim());
            ps.setString(i++, req.getPhotoUrls());
            ps.setString(i++, req.getAttachmentUrls());
            ps.setString(i++, worker);
            if (uid == null) ps.setObject(i++, null);
            else ps.setLong(i++, uid);
            ps.setString(i++, userName);
            ps.setString(i++, req.getRemark());
            ps.setInt(i++, advance ? 1 : 0);
            ps.setTimestamp(i, Timestamp.valueOf(LocalDateTime.now()));
            return ps;
        }, kh);

        if (advance) {
            String cycle = str(device.get("cycle_type"));
            LocalDate next = calcNext(finalMaint.toLocalDate(), cycle);
            String status = calcStatus(next);
            jdbc.update(
                    "update equip_device set last_maint_date=?, next_maint_date=?, status=?, update_time=? "
                            + "where id=? and tenant_id=?",
                    finalMaint, Date.valueOf(next), status, Timestamp.valueOf(LocalDateTime.now()),
                    req.getDeviceId(), tid);
        } else {
            jdbc.update(
                    "update equip_device set last_maint_date=?, update_time=? where id=? and tenant_id=?",
                    finalMaint, Timestamp.valueOf(LocalDateTime.now()), req.getDeviceId(), tid);
            refreshStatuses(tid);
        }

        Map<String, Object> m = new HashMap<>();
        m.put("id", kh.getKey().longValue());
        m.put("device", getDevice(req.getDeviceId()));
        return m;
    }

    /** 首页待办：待维保+已逾期数量（本社区） */
    public Map<String, Object> todoSummary() {
        assertStaffView();
        Long tid = requireTenant();
        refreshStatuses(tid);
        maybeNotifyDue(tid);
        Long due = jdbc.queryForObject(
                "select count(1) from equip_device where tenant_id=? and status in ('DUE','OVERDUE')",
                Long.class, tid);
        Long overdue = jdbc.queryForObject(
                "select count(1) from equip_device where tenant_id=? and status='OVERDUE'",
                Long.class, tid);
        Map<String, Object> m = new HashMap<>();
        m.put("todoCount", due == null ? 0 : due.intValue());
        m.put("overdueCount", overdue == null ? 0 : overdue.intValue());
        return m;
    }

    public List<Map<String, Object>> dueList() {
        assertStaffView();
        Long tid = requireTenant();
        refreshStatuses(tid);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select id, name, location, device_type as deviceType, next_maint_date as nextMaintDate, "
                        + "status, vendor_name as vendorName "
                        + "from equip_device where tenant_id=? and status in ('DUE','OVERDUE') "
                        + "order by field(status,'OVERDUE','DUE'), next_maint_date asc limit 50",
                tid);
        rows.forEach(this::decorateDevice);
        return rows;
    }

    /* ---------- helpers ---------- */

    public void refreshStatuses(Long tid) {
        jdbc.update(
                "update equip_device set status=?, update_time=? where tenant_id=? "
                        + "and next_maint_date is not null and next_maint_date < curdate()",
                ST_OVERDUE, Timestamp.valueOf(LocalDateTime.now()), tid);
        jdbc.update(
                "update equip_device set status=?, update_time=? where tenant_id=? "
                        + "and next_maint_date is not null and next_maint_date >= curdate() "
                        + "and next_maint_date <= date_add(curdate(), interval ? day)",
                ST_DUE, Timestamp.valueOf(LocalDateTime.now()), tid, DUE_DAYS);
        jdbc.update(
                "update equip_device set status=?, update_time=? where tenant_id=? "
                        + "and (next_maint_date is null or next_maint_date > date_add(curdate(), interval ? day))",
                ST_NORMAL, Timestamp.valueOf(LocalDateTime.now()), tid, DUE_DAYS);
    }

    private void maybeNotifyDue(Long tid) {
        try {
            List<Map<String, Object>> due = jdbc.queryForList(
                    "select id, name, next_maint_date as nextMaintDate, status from equip_device "
                            + "where tenant_id=? and status in ('DUE','OVERDUE') limit 20",
                    tid);
            if (due.isEmpty()) return;
            List<Map<String, Object>> staff = jdbc.queryForList(
                    "select id from sys_user where tenant_id=? and role in ('property_admin','grid_worker') and status=1",
                    tid);
            String title = "设备维保提醒";
            String content = "有 " + due.size() + " 台设备待维保/已逾期，请及时登记维保记录。";
            for (Map<String, Object> u : staff) {
                Long uid = toLong(u.get("id"));
                Long exist = jdbc.queryForObject(
                        "select count(1) from sys_message where tenant_id=? and user_id=? and msg_type=? "
                                + "and title=? and create_time >= curdate()",
                        Long.class, tid, uid, "equip_maint", title);
                if (exist != null && exist > 0) continue;
                jdbc.update(
                        "insert into sys_message(tenant_id,user_id,title,content,msg_type,read_flag,create_time) "
                                + "values(?,?,?,?,?,?,now())",
                        tid, uid, title, content, "equip_maint", 0);
            }
        } catch (Exception ignored) {
        }
    }

    private Map<String, Object> getDeviceRaw(Long tid, Long id) {
        List<Map<String, Object>> list = jdbc.queryForList(
                "select * from equip_device where tenant_id=? and id=? limit 1", tid, id);
        if (list.isEmpty()) throw new BizException(404, "设备不存在");
        return list.get(0);
    }

    private Map<String, Object> mapDevice(Map<String, Object> raw) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", raw.get("id"));
        m.put("tenantId", raw.get("tenant_id") != null ? raw.get("tenant_id") : raw.get("tenantId"));
        m.put("name", raw.get("name"));
        m.put("location", raw.get("location"));
        m.put("deviceType", raw.get("device_type") != null ? raw.get("device_type") : raw.get("deviceType"));
        Object qty = raw.get("quantity");
        m.put("quantity", qty == null ? 1 : Integer.parseInt(String.valueOf(qty)));
        m.put("cycleType", raw.get("cycle_type") != null ? raw.get("cycle_type") : raw.get("cycleType"));
        m.put("vendorName", raw.get("vendor_name") != null ? raw.get("vendor_name") : raw.get("vendorName"));
        m.put("contactName", raw.get("contact_name") != null ? raw.get("contact_name") : raw.get("contactName"));
        m.put("contactPhone", raw.get("contact_phone") != null ? raw.get("contact_phone") : raw.get("contactPhone"));
        Object last = raw.get("last_maint_date") != null ? raw.get("last_maint_date") : raw.get("lastMaintDate");
        Object next = raw.get("next_maint_date") != null ? raw.get("next_maint_date") : raw.get("nextMaintDate");
        m.put("lastMaintDate", last == null ? null : String.valueOf(last).substring(0, Math.min(10, String.valueOf(last).length())));
        m.put("nextMaintDate", next == null ? null : String.valueOf(next).substring(0, Math.min(10, String.valueOf(next).length())));
        m.put("status", raw.get("status"));
        m.put("remark", raw.get("remark"));
        m.put("createTime", raw.get("create_time") != null ? raw.get("create_time") : raw.get("createTime"));
        return m;
    }

    private void ensureQuantityColumn() {
        try {
            Integer cnt = jdbc.queryForObject(
                    "select count(1) from information_schema.columns "
                            + "where table_schema = database() and table_name = 'equip_device' and column_name = 'quantity'",
                    Integer.class);
            if (cnt != null && cnt > 0) return;
            jdbc.execute("alter table equip_device add column quantity int not null default 1 comment '设备数量'");
        } catch (Exception ignored) {
        }
    }

    private Map<String, Object> decorateDevice(Map<String, Object> m) {
        String st = str(m.get("status"));
        m.put("statusLabel", switch (st) {
            case ST_DUE -> "待维保";
            case ST_OVERDUE -> "已逾期";
            default -> "正常";
        });
        String cycle = str(m.get("cycleType") != null ? m.get("cycleType") : m.get("cycle_type"));
        m.put("cycleType", cycle);
        m.put("cycleLabel", cycleLabel(cycle));
        return m;
    }

    private void validateDevice(EquipDeviceSaveRequest req) {
        if (!StringUtils.hasText(req.getName())) throw new BizException("请填写设备名称");
        if (!StringUtils.hasText(req.getLocation())) throw new BizException("请填写设备位置");
        if (!StringUtils.hasText(req.getDeviceType())) throw new BizException("请填写设备类型");
    }

    private String normalizeCycle(String c) {
        if (c == null) return "QUARTER";
        String u = c.trim().toUpperCase();
        if (u.contains("月") || "MONTH".equals(u)) return "MONTH";
        if (u.contains("半年") || "HALF_YEAR".equals(u) || "HALF".equals(u)) return "HALF_YEAR";
        if (u.contains("年") || "YEAR".equals(u) || "ANNUAL".equals(u)) return "YEAR";
        return "QUARTER";
    }

    private String cycleLabel(String cycle) {
        return switch (cycle) {
            case "MONTH" -> "月度";
            case "HALF_YEAR" -> "半年";
            case "YEAR" -> "年度";
            default -> "季度";
        };
    }

    private LocalDate calcNext(LocalDate last, String cycle) {
        return switch (normalizeCycle(cycle)) {
            case "MONTH" -> last.plusMonths(1);
            case "HALF_YEAR" -> last.plusMonths(6);
            case "YEAR" -> last.plusYears(1);
            default -> last.plusMonths(3);
        };
    }

    private String calcStatus(LocalDate next) {
        if (next == null) return ST_NORMAL;
        LocalDate today = LocalDate.now();
        if (next.isBefore(today)) return ST_OVERDUE;
        if (!next.isAfter(today.plusDays(DUE_DAYS))) return ST_DUE;
        return ST_NORMAL;
    }

    private void assertStaffView() {
        String role = TenantContext.getRole();
        if (RoleConstants.isPropertyAdmin(role) || RoleConstants.isGridWorker(role)
                || RoleConstants.isPlatformAdmin(role)) {
            return;
        }
        throw new BizException(403, "无权访问设备维保（居民不可操作）");
    }

    private void assertCanManageDevice() {
        String role = TenantContext.getRole();
        if (RoleConstants.isPropertyAdmin(role) || RoleConstants.isPlatformAdmin(role)) {
            return;
        }
        throw new BizException(403, "网格员不可新增/编辑/删除设备台账");
    }

    private void assertCanAddRecord() {
        assertStaffView();
    }

    private Long requireTenant() {
        Long tid = TenantContext.getTenantId();
        if (tid == null) throw new BizException(40001, "缺少租户上下文");
        return tid;
    }

    private String resolveUserName(Long uid) {
        if (uid == null) return "工作人员";
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

    private static Date parseDate(String s) {
        if (!StringUtils.hasText(s)) return null;
        try {
            return Date.valueOf(s.substring(0, 10));
        } catch (Exception e) {
            throw new BizException("日期格式错误，应为 yyyy-MM-dd");
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
}
