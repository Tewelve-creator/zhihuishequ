package com.community.repair.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.community.common.constant.RoleConstants;
import com.community.common.exception.BizException;
import com.community.common.tenant.TenantContext;
import com.community.repair.dto.VisitCreateRequest;
import com.community.repair.entity.VisitorAppointment;
import com.community.repair.mapper.VisitorAppointmentMapper;
import com.community.repair.util.PlateNoUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 临时访客：免审核、驾车车牌、超时过期、线下核销/扫码通行、居民撤销、通行日志
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VisitService {

    public static final String ST_APPROVED = "approved";
    public static final String ST_CHECKED = "checked_in";
    public static final String ST_EXPIRED = "expired";
    public static final String ST_CANCELLED = "cancelled";
    public static final String ACCESS_ONCE = "ONCE";

    public static final int VALID_HOURS = 12;

    private final VisitorAppointmentMapper visitorMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ParkingService parkingService;

    /** H5 通行页前缀，如 http://localhost:5180/#/pages/visitor/pass?token= */
    @Value("${visit.pass-page-base:}")
    private String passPageBase;

    /** 短时二维码有效秒数（定时刷新预留） */
    @Value("${visit.qr-refresh-seconds:60}")
    private int qrRefreshSeconds;

    /** 预留：打开凭证是否强制手机号校验 */
    @Value("${visit.phone-verify-enabled:false}")
    private boolean phoneVerifyEnabled;

    @Value("${visit.qr-sign-secret:visit-pass-demo-secret}")
    private String qrSignSecret;

    /** 短时 nonce 防重放（进程内；生产可换 Redis） */
    private final ConcurrentHashMap<String, Long> usedQrNonces = new ConcurrentHashMap<>();

    @PostConstruct
    public void ensureSchema() {
        try {
            addCol("visitor_appointment", "pass_token", "varchar(64) null");
            addCol("visitor_appointment", "access_mode", "varchar(16) null default 'ONCE'");
            addCol("visitor_appointment", "pass_used", "tinyint not null default 0");
            jdbcTemplate.execute("""
                    create table if not exists visitor_access_log (
                      id bigint primary key auto_increment,
                      tenant_id bigint not null,
                      visit_id bigint not null,
                      pass_token varchar(64) null,
                      result varchar(32) not null,
                      fail_reason varchar(255) null,
                      channel varchar(32) null,
                      operator_id bigint null,
                      operator_name varchar(64) null,
                      remark varchar(255) null,
                      create_time datetime null,
                      key idx_val_visit (tenant_id, visit_id),
                      key idx_val_token (pass_token)
                    )
                    """);
            addCol("visitor_access_log", "client_ip", "varchar(64) null");
            // 历史数据补 token
            jdbcTemplate.update(
                    "update visitor_appointment set pass_token=replace(uuid(),'-','') "
                            + "where pass_token is null or pass_token=''");
            jdbcTemplate.update(
                    "update visitor_appointment set access_mode='ONCE' where access_mode is null or access_mode=''");
        } catch (Exception e) {
            log.warn("visit schema ensure skip: {}", e.getMessage());
        }
    }

    private void addCol(String table, String col, String def) {
        try {
            Integer n = jdbcTemplate.queryForObject(
                    "select count(1) from information_schema.columns where table_schema=database() and table_name=? and column_name=?",
                    Integer.class, table, col);
            if (n == null || n == 0) {
                jdbcTemplate.execute("alter table " + table + " add column " + col + " " + def);
            }
        } catch (Exception ignored) {
        }
    }

    @Transactional
    public VisitorAppointment create(VisitCreateRequest req) {
        assertResident();
        if (!StringUtils.hasText(req.getVisitorName())) {
            throw new BizException("请填写访客姓名");
        }
        if (!StringUtils.hasText(req.getVisitReason())) {
            throw new BizException("请选择来访事由");
        }
        boolean drive = Boolean.TRUE.equals(req.getDriveFlag());
        String plate = null;
        if (drive) {
            PlateNoUtils.validate(req.getPlateNo());
            plate = PlateNoUtils.normalize(req.getPlateNo());
        }

        LocalDateTime visitTime = parseDateTime(req.getVisitTime());
        if (visitTime == null) {
            visitTime = LocalDateTime.now().plusHours(1);
        }
        LocalDateTime expireTime = parseDateTime(req.getExpireTime());
        if (expireTime == null) {
            expireTime = visitTime.plusHours(VALID_HOURS);
        }
        if (!expireTime.isAfter(visitTime)) {
            throw new BizException("来访结束时间需晚于开始时间");
        }
        if (expireTime.isBefore(LocalDateTime.now())) {
            throw new BizException("预约时间已过期，请重新选择到访时间");
        }

        Long tid = requireTenant();
        VisitorAppointment v = new VisitorAppointment();
        v.setTenantId(tid);
        v.setUserId(TenantContext.getUserId());
        v.setHouseId(req.getHouseId() != null ? req.getHouseId() : TenantContext.getHouseId());
        v.setVisitorName(req.getVisitorName().trim());
        if (StringUtils.hasText(req.getVisitorPhone())) {
            String phone = req.getVisitorPhone().trim().replaceAll("\\s+", "");
            if (!phone.matches("1[3-9]\\d{9}")) {
                throw new BizException("请输入正确的11位手机号");
            }
            v.setVisitorPhone(phone);
        } else {
            v.setVisitorPhone(null);
        }
        v.setDriveFlag(drive ? 1 : 0);
        v.setPlateNo(plate);
        v.setIdCard(null);
        v.setVisitReason(req.getVisitReason().trim());
        v.setVisitTime(visitTime);
        v.setExpireTime(expireTime);
        v.setVisitorCount(req.getVisitorCount() == null || req.getVisitorCount() < 1 ? 1 : req.getVisitorCount());
        v.setStatus(ST_APPROVED);
        v.setRemark(req.getRemark());
        v.setCreateTime(LocalDateTime.now());
        v.setPassToken(newToken());
        v.setAccessMode(ACCESS_ONCE);
        v.setPassUsed(0);
        visitorMapper.insert(v);
        // 驾车访客：车牌同步到临时通行授权 / 临时通行记录
        if (drive && StringUtils.hasText(plate)) {
            try {
                parkingService.syncFromVisitAppointment(v);
            } catch (Exception e) {
                log.warn("sync visit plate to parking skip: {}", e.getMessage());
            }
        }
        return decorate(v);
    }

    /** App / 员工列表 */
    public List<VisitorAppointment> list(String status) {
        assertCanView();
        Long tid = requireTenant();
        refreshExpired(tid);
        LambdaQueryWrapper<VisitorAppointment> q = new LambdaQueryWrapper<VisitorAppointment>()
                .eq(VisitorAppointment::getTenantId, tid)
                .orderByDesc(VisitorAppointment::getId)
                .last("limit 500");
        if (isResident()) {
            q.eq(VisitorAppointment::getUserId, TenantContext.getUserId());
        }
        if (StringUtils.hasText(status)) {
            q.eq(VisitorAppointment::getStatus, status.trim());
        }
        List<VisitorAppointment> list = visitorMapper.selectList(q);
        list.forEach(this::decorate);
        return list;
    }

    /**
     * 物业后台分页列表
     */
    public Map<String, Object> adminPage(String status, String phone, String keyword,
                                         String startTime, String endTime,
                                         int page, int size) {
        assertStaff();
        Long tid = requireTenant();
        refreshExpired(tid);
        int p = Math.max(page, 1);
        int s = Math.min(Math.max(size, 1), 200);
        StringBuilder where = new StringBuilder(" where tenant_id=? ");
        List<Object> args = new ArrayList<>();
        args.add(tid);
        if (StringUtils.hasText(status)) {
            String st = status.trim();
            // 展示态：生效中 / 待生效（底层均为 approved）
            if ("active".equalsIgnoreCase(st) || "生效中".equals(st)) {
                where.append("and status=? and visit_time is not null and visit_time <= now() ")
                        .append("and (expire_time is null or expire_time >= now()) ");
                args.add(ST_APPROVED);
            } else if ("pending_effect".equalsIgnoreCase(st) || "待生效".equals(st)) {
                where.append("and status=? and visit_time is not null and visit_time > now() ");
                args.add(ST_APPROVED);
            } else {
                where.append("and status=? ");
                args.add(st);
            }
        }
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : (StringUtils.hasText(phone) ? phone.trim() : null);
        if (StringUtils.hasText(kw)) {
            where.append("and (visitor_phone like ? or visitor_name like ?) ");
            args.add("%" + kw + "%");
            args.add("%" + kw + "%");
        }
        if (StringUtils.hasText(startTime)) {
            where.append("and ifnull(visit_time,create_time) >= ? ");
            args.add(startTime.trim().replace('T', ' '));
        }
        if (StringUtils.hasText(endTime)) {
            where.append("and ifnull(visit_time,create_time) <= ? ");
            args.add(endTime.trim().replace('T', ' '));
        }
        Integer total = jdbcTemplate.queryForObject(
                "select count(1) from visitor_appointment" + where, Integer.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add((p - 1) * s);
        pageArgs.add(s);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select * from visitor_appointment" + where + " order by id desc limit ?,?",
                pageArgs.toArray());
        List<Map<String, Object>> records = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            VisitorAppointment v = mapRow(r);
            decorate(v);
            records.add(toAdminMap(v));
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("records", records);
        m.put("total", total == null ? 0 : total);
        m.put("page", p);
        m.put("size", s);
        return m;
    }

    public Map<String, Object> adminDetail(Long id) {
        assertStaff();
        VisitorAppointment v = requireVisit(id);
        decorate(v);
        Map<String, Object> m = toAdminMap(v);
        m.put("accessLogs", listAccessLogs(v.getTenantId(), v.getId()));
        return m;
    }

    /** 物业删除预约台账（含通行日志） */
    @Transactional
    public void adminDelete(Long id) {
        assertStaff();
        VisitorAppointment v = requireVisit(id);
        try {
            parkingService.revokeFromVisitAppointment(v);
        } catch (Exception e) {
            log.warn("revoke visit parking on delete skip: {}", e.getMessage());
        }
        try {
            jdbcTemplate.update(
                    "delete from visitor_access_log where tenant_id=? and visit_id=?",
                    v.getTenantId(), v.getId());
        } catch (Exception ignored) {
        }
        visitorMapper.deleteById(v.getId());
    }

    /** 物业线下核销 */
    @Transactional
    public VisitorAppointment checkin(Long id) {
        assertStaff();
        VisitorAppointment v = requireVisit(id);
        doPass(v, "STAFF_CHECKIN", TenantContext.getUserId(), "物业线下核销");
        return decorate(v);
    }

    /**
     * 居民撤销：仅未生效（当前时间早于到访开始）且仍为 approved
     */
    @Transactional
    public VisitorAppointment cancel(Long id) {
        assertResident();
        VisitorAppointment v = requireVisit(id);
        if (!Objects.equals(v.getUserId(), TenantContext.getUserId())) {
            throw new BizException(403, "只能撤销本人预约");
        }
        refreshOne(v);
        if (!ST_APPROVED.equals(v.getStatus())) {
            throw new BizException("当前状态不可撤销");
        }
        if (v.getVisitTime() != null && !LocalDateTime.now().isBefore(v.getVisitTime())) {
            throw new BizException("预约已生效，无法撤销；请联系物业处理");
        }
        v.setStatus(ST_CANCELLED);
        v.setRemark((v.getRemark() == null ? "" : v.getRemark() + " ") + "[业主撤销]");
        visitorMapper.updateById(v);
        writeLog(v, "FAIL", "预约已撤销", "OWNER_CANCEL", TenantContext.getUserId(), null, "业主撤销预约");
        try {
            parkingService.revokeFromVisitAppointment(v);
        } catch (Exception e) {
            log.warn("revoke visit parking skip: {}", e.getMessage());
        }
        return decorate(v);
    }

    public Map<String, Object> todoSummary() {
        assertStaff();
        Long tid = requireTenant();
        refreshExpired(tid);
        Long pending = visitorMapper.selectCount(new LambdaQueryWrapper<VisitorAppointment>()
                .eq(VisitorAppointment::getTenantId, tid)
                .eq(VisitorAppointment::getStatus, ST_APPROVED));
        Map<String, Object> m = new HashMap<>();
        m.put("todoCount", pending == null ? 0 : pending.intValue());
        return m;
    }

    /**
     * 通行页信息（匿名）：实时校验后才签发短时二维码载荷；记录打开日志。
     * 业主侧不生成二维码图片，仅转发 passUrl。
     */
    public Map<String, Object> passInfo(String token, String visitorPhone, String clientIp) {
        return buildPassView(token, visitorPhone, clientIp, true);
    }

    /**
     * 刷新短时二维码载荷（定时刷新预留）；不再重复写 PAGE_OPEN。
     */
    public Map<String, Object> passQrRefresh(String token, String visitorPhone, String clientIp) {
        Map<String, Object> m = buildPassView(token, visitorPhone, clientIp, false);
        Map<String, Object> slim = new LinkedHashMap<>();
        slim.put("valid", m.get("valid"));
        slim.put("failReason", m.get("failReason"));
        slim.put("showQr", m.get("showQr"));
        slim.put("qrPayload", m.get("qrPayload"));
        slim.put("qrExpireAt", m.get("qrExpireAt"));
        slim.put("qrRefreshSeconds", m.get("qrRefreshSeconds"));
        slim.put("phoneVerifyRequired", m.get("phoneVerifyRequired"));
        return slim;
    }

    private Map<String, Object> buildPassView(String token, String visitorPhone, String clientIp, boolean logOpen) {
        VisitorAppointment v = findByToken(token);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("phoneVerifyEnabled", phoneVerifyEnabled);
        m.put("qrRefreshSeconds", Math.max(qrRefreshSeconds, 15));
        m.put("ownerHint", "业主仅可转发通行链接，二维码由访客打开本页后实时生成，请勿截图转发");
        if (v == null) {
            m.put("valid", false);
            m.put("showQr", false);
            m.put("failReason", "通行链接无效或预约不存在");
            m.put("status", "invalid");
            m.put("statusLabel", "无效");
            m.put("statusTone", "gray");
            return m;
        }
        decorate(v);
        if (logOpen) {
            writeLog(v, "OPEN", null, "PAGE_OPEN", null, clientIp, "访客打开通行凭证页");
        }
        String fail = passFailReason(v);
        boolean phoneOk = true;
        boolean phoneRequired = false;
        if (phoneVerifyEnabled && fail == null) {
            phoneRequired = true;
            phoneOk = phoneMatches(v, visitorPhone);
            if (!phoneOk) {
                fail = StringUtils.hasText(visitorPhone) ? "手机号校验未通过" : "请输入预约手机号后显示二维码";
            }
        }
        boolean appointmentOk = passFailReason(v) == null;
        boolean showQr = appointmentOk && phoneOk;
        // valid/showQr：仅实时校验通过才允许渲染二维码
        m.put("valid", showQr);
        m.put("showQr", showQr);
        m.put("appointmentOk", appointmentOk);
        m.put("failReason", fail);
        m.put("phoneVerifyRequired", phoneRequired && appointmentOk && !phoneOk);
        m.put("status", v.getStatus());
        m.put("statusLabel", v.getStatusLabel());
        m.put("statusTone", v.getStatusTone());
        m.put("communityName", v.getCommunityName());
        m.put("hostHouse", v.getHostHouse());
        m.put("hostNickname", v.getHostNickname());
        m.put("visitorName", v.getVisitorName());
        m.put("visitReason", v.getVisitReason());
        m.put("visitTime", v.getVisitTime());
        m.put("expireTime", v.getExpireTime());
        m.put("accessMode", v.getAccessMode());
        // 不向匿名页回传长期 passToken，避免被截图固化；扫码内容只用短时 qrPayload
        m.put("passUrl", v.getPassUrl());
        m.put("tip", "二维码由本页实时签发并定时刷新；撤销 / 过期 / 单次通行后立即失效且不再显示");
        if (showQr) {
            Map<String, String> qr = issueQrPayload(v.getPassToken());
            m.put("qrPayload", qr.get("payload"));
            m.put("qrExpireAt", qr.get("expireAt"));
        } else {
            m.put("qrPayload", null);
            m.put("qrExpireAt", null);
        }
        return m;
    }

    /**
     * 扫码通行校验：优先短时 qrPayload，兼容裸 token（演示）。
     */
    @Transactional
    public Map<String, Object> passVerify(String tokenOrNull, String qrPayload) {
        Map<String, Object> resp = new LinkedHashMap<>();
        String token = tokenOrNull;
        String nonce = null;
        Long nonceExp = null;
        if (StringUtils.hasText(qrPayload)) {
            try {
                String[] parsed = parseAndValidateQrPayload(qrPayload.trim());
                token = parsed[0];
                nonce = parsed[1];
                nonceExp = Long.parseLong(parsed[2]);
            } catch (BizException e) {
                resp.put("ok", false);
                resp.put("msg", e.getMessage());
                return resp;
            }
        }
        VisitorAppointment v = findByToken(token);
        if (v == null) {
            resp.put("ok", false);
            resp.put("msg", "二维码无效或预约不存在");
            return resp;
        }
        try {
            doPass(v, "QR_SCAN", TenantContext.getUserId(), "扫码通行");
            if (nonce != null) {
                usedQrNonces.put(nonce, nonceExp == null ? 0L : nonceExp);
            }
            decorate(v);
            resp.put("ok", true);
            resp.put("msg", "通行成功");
            resp.put("visitorName", v.getVisitorName());
            resp.put("hostHouse", v.getHostHouse());
            return resp;
        } catch (BizException e) {
            writeLog(v, "FAIL", e.getMessage(), "QR_SCAN", TenantContext.getUserId(), null, null);
            resp.put("ok", false);
            resp.put("msg", e.getMessage());
            return resp;
        }
    }

    /** 全库过期刷新（定时任务） */
    public int refreshAllExpired() {
        TenantContext.setSkipTenant(true);
        try {
            return jdbcTemplate.update(
                    "update visitor_appointment set status=? where status=? and expire_time is not null and expire_time < now()",
                    ST_EXPIRED, ST_APPROVED);
        } finally {
            TenantContext.clear();
        }
    }

    private void doPass(VisitorAppointment v, String channel, Long operatorId, String remark) {
        refreshOne(v);
        String fail = passFailReason(v);
        if (fail != null) {
            throw new BizException(fail);
        }
        v.setStatus(ST_CHECKED);
        v.setCheckinTime(LocalDateTime.now());
        v.setCheckinBy(operatorId);
        v.setPassUsed(1);
        visitorMapper.updateById(v);
        writeLog(v, "SUCCESS", null, channel, operatorId, null, remark);
        notifyOwnerPassed(v);
    }

    private String passFailReason(VisitorAppointment v) {
        if (v == null) return "二维码无效或预约不存在";
        if (ST_CANCELLED.equals(v.getStatus())) return "预约已撤销，二维码失效";
        if (ST_EXPIRED.equals(v.getStatus())) return "预约已过期，二维码失效";
        if (ST_CHECKED.equals(v.getStatus()) || Objects.equals(v.getPassUsed(), 1)) {
            return "已通行，单次二维码已失效";
        }
        if (!ST_APPROVED.equals(v.getStatus())) return "当前预约状态不可通行";
        LocalDateTime now = LocalDateTime.now();
        if (v.getVisitTime() != null && now.isBefore(v.getVisitTime())) {
            return "尚未到达通行开始时间";
        }
        if (v.getExpireTime() != null && now.isAfter(v.getExpireTime())) {
            return "已超出通行有效期";
        }
        return null;
    }

    private VisitorAppointment findByToken(String token) {
        if (!StringUtils.hasText(token)) return null;
        boolean prevSkip = TenantContext.isSkipTenant();
        TenantContext.setSkipTenant(true);
        try {
            VisitorAppointment v = visitorMapper.selectOne(new LambdaQueryWrapper<VisitorAppointment>()
                    .eq(VisitorAppointment::getPassToken, token.trim())
                    .last("limit 1"));
            if (v != null && v.getTenantId() != null) {
                TenantContext.setTenantId(v.getTenantId());
            }
            return v;
        } finally {
            TenantContext.setSkipTenant(prevSkip);
        }
    }

    private void writeLog(VisitorAppointment v, String result, String failReason,
                          String channel, Long operatorId, String clientIp, String remark) {
        try {
            jdbcTemplate.update(
                    "insert into visitor_access_log(tenant_id,visit_id,pass_token,result,fail_reason,channel,"
                            + "operator_id,operator_name,remark,client_ip,create_time) "
                            + "values(?,?,?,?,?,?,?,?,?,?,now())",
                    v.getTenantId(), v.getId(), v.getPassToken(), result, failReason, channel,
                    operatorId, null, remark, clientIp);
        } catch (Exception e) {
            // 兼容未加 client_ip 列的库
            try {
                jdbcTemplate.update(
                        "insert into visitor_access_log(tenant_id,visit_id,pass_token,result,fail_reason,channel,"
                                + "operator_id,operator_name,remark,create_time) values(?,?,?,?,?,?,?,?,?,now())",
                        v.getTenantId(), v.getId(), v.getPassToken(), result, failReason, channel,
                        operatorId, null, remark);
            } catch (Exception e2) {
                log.warn("access log skip: {}", e2.getMessage());
            }
        }
    }

    private List<Map<String, Object>> listAccessLogs(Long tid, Long visitId) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "select id, result, fail_reason as failReason, channel, operator_id as operatorId, "
                            + "remark, client_ip as clientIp, create_time as createTime from visitor_access_log "
                            + "where tenant_id=? and visit_id=? order by id desc limit 80",
                    tid, visitId);
            for (Map<String, Object> r : rows) {
                r.put("resultLabel", resultLabel(String.valueOf(r.get("result"))));
                r.put("channelLabel", channelLabel(String.valueOf(r.get("channel"))));
            }
            return rows;
        } catch (Exception e) {
            try {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                        "select id, result, fail_reason as failReason, channel, operator_id as operatorId, "
                                + "remark, create_time as createTime from visitor_access_log "
                                + "where tenant_id=? and visit_id=? order by id desc limit 80",
                        tid, visitId);
                for (Map<String, Object> r : rows) {
                    r.put("resultLabel", resultLabel(String.valueOf(r.get("result"))));
                    r.put("channelLabel", channelLabel(String.valueOf(r.get("channel"))));
                }
                return rows;
            } catch (Exception e2) {
                return List.of();
            }
        }
    }

    private static String resultLabel(String r) {
        if ("SUCCESS".equals(r)) return "通行成功";
        if ("OPEN".equals(r)) return "打开凭证";
        if ("FAIL".equals(r)) return "校验失败";
        return r == null ? "—" : r;
    }

    private static String channelLabel(String c) {
        if ("QR_SCAN".equals(c)) return "扫码通行";
        if ("STAFF_CHECKIN".equals(c)) return "物业核销";
        if ("OWNER_CANCEL".equals(c)) return "业主撤销";
        if ("PAGE_OPEN".equals(c)) return "打开凭证页";
        return c == null ? "—" : c;
    }

    private boolean phoneMatches(VisitorAppointment v, String input) {
        if (!StringUtils.hasText(v.getVisitorPhone())) {
            // 预约未填手机号时不强制
            return true;
        }
        if (!StringUtils.hasText(input)) return false;
        String a = v.getVisitorPhone().replaceAll("\\s+", "");
        String b = input.replaceAll("\\s+", "");
        return a.equals(b);
    }

    /** 签发短时二维码载荷：V1.token.ts.nonce.sign */
    private Map<String, String> issueQrPayload(String passToken) {
        long ttl = Math.max(qrRefreshSeconds, 15);
        long exp = System.currentTimeMillis() / 1000 + ttl;
        String nonce = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String sign = signQr(passToken, exp, nonce);
        String payload = "V1." + passToken + "." + exp + "." + nonce + "." + sign;
        Map<String, String> m = new LinkedHashMap<>();
        m.put("payload", payload);
        m.put("expireAt", String.valueOf(exp));
        return m;
    }

    /** @return [token, nonce, expEpoch] */
    private String[] parseAndValidateQrPayload(String payload) {
        String[] p = payload.split("\\.");
        if (p.length != 5 || !"V1".equals(p[0])) {
            throw new BizException("二维码已失效，请刷新通行页");
        }
        String token = p[1];
        long exp;
        try {
            exp = Long.parseLong(p[2]);
        } catch (Exception e) {
            throw new BizException("二维码已失效，请刷新通行页");
        }
        String nonce = p[3];
        String sign = p[4];
        long now = System.currentTimeMillis() / 1000;
        if (now > exp + 5) {
            throw new BizException("二维码已过期，请刷新通行页后重试");
        }
        if (!sign.equals(signQr(token, exp, nonce))) {
            throw new BizException("二维码校验失败");
        }
        if (usedQrNonces.containsKey(nonce)) {
            throw new BizException("二维码已使用，请刷新通行页");
        }
        long cutoff = now - 3600;
        usedQrNonces.entrySet().removeIf(e -> e.getValue() < cutoff);
        return new String[]{token, nonce, String.valueOf(exp)};
    }

    private String signQr(String token, long exp, String nonce) {
        String raw = token + "|" + exp + "|" + nonce + "|" + qrSignSecret;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 16; i++) {
                sb.append(String.format("%02x", dig[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(raw.hashCode());
        }
    }

    private void notifyOwnerPassed(VisitorAppointment v) {
        if (v.getUserId() == null) return;
        try {
            jdbcTemplate.update(
                    "insert into sys_message(tenant_id,user_id,title,content,msg_type,read_flag,create_time) values(?,?,?,?,?,?,now())",
                    v.getTenantId(), v.getUserId(),
                    "访客已通行",
                    "您的访客「" + v.getVisitorName() + "」已完成扫码/核销通行"
                            + (v.getVisitReason() != null ? "（" + v.getVisitReason() + "）" : "") + "。",
                    "visitor_pass", 0);
        } catch (Exception e) {
            log.debug("notify owner skip: {}", e.getMessage());
        }
    }

    public int refreshExpired(Long tid) {
        return visitorMapper.update(null, new LambdaUpdateWrapper<VisitorAppointment>()
                .eq(VisitorAppointment::getTenantId, tid)
                .eq(VisitorAppointment::getStatus, ST_APPROVED)
                .isNotNull(VisitorAppointment::getExpireTime)
                .lt(VisitorAppointment::getExpireTime, LocalDateTime.now())
                .set(VisitorAppointment::getStatus, ST_EXPIRED));
    }

    private void refreshOne(VisitorAppointment v) {
        if (v != null && ST_APPROVED.equals(v.getStatus())
                && v.getExpireTime() != null
                && v.getExpireTime().isBefore(LocalDateTime.now())) {
            v.setStatus(ST_EXPIRED);
            visitorMapper.updateById(v);
            try {
                parkingService.revokeFromVisitAppointment(v);
            } catch (Exception ignored) {
            }
        }
    }

    private VisitorAppointment decorate(VisitorAppointment v) {
        if (v == null) return null;
        refreshOne(v);
        if (!StringUtils.hasText(v.getPassToken())) {
            v.setPassToken(newToken());
            try {
                visitorMapper.updateById(v);
            } catch (Exception ignored) {
            }
        }
        if (!StringUtils.hasText(v.getAccessMode())) {
            v.setAccessMode(ACCESS_ONCE);
        }
        fillHostInfo(v);
        applyStatusDisplay(v);
        // 剩余有效秒数（相对 expireTime）
        if (ST_APPROVED.equals(v.getStatus()) && v.getExpireTime() != null) {
            long sec = Duration.between(LocalDateTime.now(), v.getExpireTime()).getSeconds();
            v.setRemainSeconds(Math.max(sec, 0));
        } else {
            v.setRemainSeconds(0L);
        }
        // 未生效前可撤销
        v.setCanCancel(ST_APPROVED.equals(v.getStatus())
                && v.getVisitTime() != null
                && LocalDateTime.now().isBefore(v.getVisitTime()));
        v.setPassUrl(buildPassUrl(v.getPassToken()));
        return v;
    }

    private void applyStatusDisplay(VisitorAppointment v) {
        LocalDateTime now = LocalDateTime.now();
        if (ST_CANCELLED.equals(v.getStatus())) {
            v.setStatusLabel("业主撤销");
            v.setStatusTone("orange");
        } else if (ST_EXPIRED.equals(v.getStatus())) {
            v.setStatusLabel("已过期");
            v.setStatusTone("gray");
        } else if (ST_CHECKED.equals(v.getStatus())) {
            v.setStatusLabel("已通行");
            v.setStatusTone("blue");
        } else if (ST_APPROVED.equals(v.getStatus())) {
            if (v.getVisitTime() != null && now.isBefore(v.getVisitTime())) {
                v.setStatusLabel("待生效");
                v.setStatusTone("teal");
            } else {
                v.setStatusLabel("生效中");
                v.setStatusTone("green");
            }
        } else {
            v.setStatusLabel(v.getStatus());
            v.setStatusTone("gray");
        }
    }

    private void fillHostInfo(VisitorAppointment v) {
        try {
            if (v.getHouseId() != null) {
                List<Map<String, Object>> hs = jdbcTemplate.queryForList(
                        "select community_name, building, unit, room_no from community_house where id=? limit 1",
                        v.getHouseId());
                if (!hs.isEmpty()) {
                    Map<String, Object> h = hs.get(0);
                    v.setCommunityName(str(h.get("community_name")));
                    String house = str(h.get("building")) + "栋" + str(h.get("unit")) + "单元" + str(h.get("room_no"));
                    v.setHostHouse(house.replace("null", "").replace("栋单元", "栋"));
                }
            }
            if (!StringUtils.hasText(v.getCommunityName()) && v.getTenantId() != null) {
                List<String> names = jdbcTemplate.query(
                        "select community_name from community_house where tenant_id=? and community_name is not null limit 1",
                        (rs, i) -> rs.getString(1), v.getTenantId());
                if (!names.isEmpty()) v.setCommunityName(names.get(0));
            }
            if (v.getUserId() != null) {
                List<String> ns = jdbcTemplate.query(
                        "select nickname from sys_user where id=? limit 1",
                        (rs, i) -> rs.getString(1), v.getUserId());
                if (!ns.isEmpty()) v.setHostNickname(ns.get(0));
            }
        } catch (Exception ignored) {
        }
        if (!StringUtils.hasText(v.getCommunityName())) {
            v.setCommunityName("智慧社区");
        }
        if (!StringUtils.hasText(v.getHostHouse())) {
            v.setHostHouse(v.getHouseId() == null ? "—" : ("房屋#" + v.getHouseId()));
        }
    }

    private Map<String, Object> toAdminMap(VisitorAppointment v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", v.getId());
        m.put("tenantId", v.getTenantId());
        m.put("userId", v.getUserId());
        m.put("houseId", v.getHouseId());
        m.put("visitorName", v.getVisitorName());
        m.put("visitorPhone", v.getVisitorPhone());
        m.put("plateNo", v.getPlateNo());
        m.put("driveFlag", v.getDriveFlag());
        m.put("visitReason", v.getVisitReason());
        m.put("visitTime", v.getVisitTime());
        m.put("expireTime", v.getExpireTime());
        m.put("visitorCount", v.getVisitorCount());
        m.put("status", v.getStatus());
        m.put("statusLabel", v.getStatusLabel());
        m.put("statusTone", v.getStatusTone());
        m.put("checkinTime", v.getCheckinTime());
        m.put("remark", v.getRemark());
        m.put("createTime", v.getCreateTime());
        m.put("passToken", v.getPassToken());
        m.put("accessMode", v.getAccessMode());
        m.put("passUsed", v.getPassUsed());
        m.put("hostHouse", v.getHostHouse());
        m.put("communityName", v.getCommunityName());
        m.put("hostNickname", v.getHostNickname());
        m.put("remainSeconds", v.getRemainSeconds());
        m.put("passUrl", v.getPassUrl());
        return m;
    }

    private VisitorAppointment mapRow(Map<String, Object> r) {
        VisitorAppointment v = new VisitorAppointment();
        v.setId(toLong(r.get("id")));
        v.setTenantId(toLong(r.get("tenant_id")));
        v.setUserId(toLong(r.get("user_id")));
        v.setHouseId(toLong(r.get("house_id")));
        v.setVisitorName(str(r.get("visitor_name")));
        v.setVisitorPhone(str(r.get("visitor_phone")));
        v.setPlateNo(str(r.get("plate_no")));
        v.setDriveFlag(r.get("drive_flag") == null ? 0 : ((Number) r.get("drive_flag")).intValue());
        v.setVisitReason(str(r.get("visit_reason")));
        v.setVisitTime(toLdt(r.get("visit_time")));
        v.setExpireTime(toLdt(r.get("expire_time")));
        v.setVisitorCount(r.get("visitor_count") == null ? 1 : ((Number) r.get("visitor_count")).intValue());
        v.setStatus(str(r.get("status")));
        v.setCheckinTime(toLdt(r.get("checkin_time")));
        v.setCheckinBy(toLong(r.get("checkin_by")));
        v.setRemark(str(r.get("remark")));
        v.setCreateTime(toLdt(r.get("create_time")));
        v.setPassToken(str(r.get("pass_token")));
        v.setAccessMode(str(r.get("access_mode")));
        v.setPassUsed(r.get("pass_used") == null ? 0 : ((Number) r.get("pass_used")).intValue());
        return v;
    }

    private String buildPassUrl(String token) {
        if (!StringUtils.hasText(token)) return null;
        if (StringUtils.hasText(passPageBase)) {
            String base = passPageBase.trim();
            return base.contains("?") ? base + token : base + token;
        }
        // 默认相对 H5 hash 路由，前端可拼完整域名
        return "/#/pages/visitor/pass?token=" + token;
    }

    private static String newToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private VisitorAppointment requireVisit(Long id) {
        Long tid = requireTenant();
        VisitorAppointment v = visitorMapper.selectById(id);
        if (v == null || !Objects.equals(v.getTenantId(), tid)) {
            throw new BizException(404, "预约不存在");
        }
        return v;
    }

    private LocalDateTime parseDateTime(String s) {
        if (!StringUtils.hasText(s)) return null;
        String t = s.trim().replace('T', ' ').replace('/', '-');
        String[] parts = t.split("\\s+", 2);
        String datePart = parts[0];
        String timePart = parts.length > 1 ? parts[1] : "00:00:00";
        String[] ymd = datePart.split("-");
        if (ymd.length != 3) {
            throw new BizException("到访时间格式错误");
        }
        try {
            int y = Integer.parseInt(ymd[0]);
            int m = Integer.parseInt(ymd[1]);
            int d = Integer.parseInt(ymd[2]);
            String[] hms = timePart.split(":");
            int hh = hms.length > 0 ? Integer.parseInt(hms[0]) : 0;
            int mm = hms.length > 1 ? Integer.parseInt(hms[1]) : 0;
            int ss = hms.length > 2 ? Integer.parseInt(hms[2].replaceAll("[^0-9].*$", "")) : 0;
            return LocalDateTime.of(y, m, d, hh, mm, ss);
        } catch (Exception e) {
            throw new BizException("到访时间格式错误，请重新选择");
        }
    }

    private Long requireTenant() {
        Long tid = TenantContext.getTenantId();
        if (tid == null) throw new BizException(40001, "缺少租户上下文");
        return tid;
    }

    private boolean isResident() {
        return RoleConstants.RESIDENT.equals(TenantContext.getRole());
    }

    private void assertResident() {
        if (!isResident()) {
            throw new BizException(403, "仅居民可提交访客预约");
        }
    }

    private void assertStaff() {
        String role = TenantContext.getRole();
        if (RoleConstants.isPropertyAdmin(role) || RoleConstants.isGridWorker(role)
                || RoleConstants.isPlatformAdmin(role)) {
            return;
        }
        throw new BizException(403, "仅物业工作人员可操作");
    }

    private void assertCanView() {
        String role = TenantContext.getRole();
        if (isResident() || RoleConstants.isPropertyAdmin(role) || RoleConstants.isGridWorker(role)
                || RoleConstants.isPlatformAdmin(role)) {
            return;
        }
        throw new BizException(403, "无权查看访客预约");
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
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

    private static LocalDateTime toLdt(Object v) {
        if (v == null) return null;
        if (v instanceof LocalDateTime ldt) return ldt;
        if (v instanceof java.sql.Timestamp ts) return ts.toLocalDateTime();
        try {
            String s = String.valueOf(v).replace(' ', 'T');
            if (s.length() >= 19) {
                return LocalDateTime.parse(s.substring(0, 19), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
