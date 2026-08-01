package com.community.auth.service;

import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.community.auth.dto.HouseBindRequest;
import com.community.auth.dto.HouseholdSaveRequest;
import com.community.auth.dto.MoveAuditRequest;
import com.community.auth.entity.CommunityHouse;
import com.community.auth.entity.HouseMoveApply;
import com.community.auth.entity.HouseholdResident;
import com.community.auth.entity.SysUser;
import com.community.auth.entity.UserHouseBind;
import com.community.auth.mapper.CommunityHouseMapper;
import com.community.auth.mapper.HouseMoveApplyMapper;
import com.community.auth.mapper.HouseholdResidentMapper;
import com.community.auth.mapper.SysUserMapper;
import com.community.auth.mapper.UserHouseBindMapper;
import com.community.common.constant.RoleConstants;
import com.community.common.exception.BizException;
import com.community.common.result.PageResult;
import com.community.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 迁入迁出审批 + 住户档案同步。
 * 居民绑定/解绑改为提交审批；物业审核通过后写 user_house_bind 与 household_resident。
 */
@Service
@RequiredArgsConstructor
public class HouseMoveService {

    public static final String TYPE_IN = "MOVE_IN";
    public static final String TYPE_OUT = "MOVE_OUT";
    public static final String ST_PENDING = "PENDING";
    public static final String ST_APPROVED = "APPROVED";
    public static final String ST_REJECTED = "REJECTED";
    public static final String ID_OWNER = "OWNER";
    public static final String ID_TENANT = "TENANT";
    public static final String HS_ACTIVE = "ACTIVE";
    public static final String HS_PENDING = "PENDING";
    public static final String HS_MOVING_OUT = "MOVING_OUT";
    public static final String HS_MOVED_OUT = "MOVED_OUT";

    private final HouseMoveApplyMapper applyMapper;
    private final HouseholdResidentMapper householdMapper;
    private final CommunityHouseMapper houseMapper;
    private final UserHouseBindMapper bindMapper;
    private final SysUserMapper userMapper;
    private final HouseService houseService;
    private final JdbcTemplate jdbcTemplate;

    /** 居民：提交迁入（绑定）申请 */
    @Transactional
    public Map<String, Object> applyMoveIn(HouseBindRequest req) {
        Long userId = requireUserId();
        Long tenantId = requireTenantId();
        CommunityHouse house = houseService.resolveHousePublic(req, tenantId);
        assertSameTenant(house, tenantId);

        Long bound = bindMapper.selectCount(new LambdaQueryWrapper<UserHouseBind>()
                .eq(UserHouseBind::getUserId, userId)
                .eq(UserHouseBind::getHouseId, house.getId()));
        if (bound != null && bound > 0) {
            throw new BizException("该房屋已绑定，无需重复申请");
        }
        assertNoPending(userId, house.getId(), TYPE_IN);

        SysUser user = userMapper.selectById(userId);
        HouseMoveApply apply = baseApply(user, house, TYPE_IN, req);
        applyMapper.insert(apply);
        // 提交绑定即写入住户管理（待审核），物业通过后改为正常并生效绑定
        upsertHouseholdPending(apply, user, house);
        return toApplyMap(apply);
    }

    /** 居民：提交迁出（解绑）申请 */
    @Transactional
    public Map<String, Object> applyMoveOut(Long houseId, String remark) {
        Long userId = requireUserId();
        Long tenantId = requireTenantId();
        UserHouseBind bind = bindMapper.selectOne(new LambdaQueryWrapper<UserHouseBind>()
                .eq(UserHouseBind::getUserId, userId)
                .eq(UserHouseBind::getTenantId, tenantId)
                .eq(UserHouseBind::getHouseId, houseId));
        if (bind == null) {
            throw new BizException(404, "未绑定该房屋");
        }
        long count = bindMapper.selectCount(new LambdaQueryWrapper<UserHouseBind>()
                .eq(UserHouseBind::getUserId, userId)
                .eq(UserHouseBind::getTenantId, tenantId));
        if (count <= 1) {
            throw new BizException("至少保留一套绑定房屋，不能申请解绑最后一套");
        }
        assertNoPending(userId, houseId, TYPE_OUT);

        CommunityHouse house = houseMapper.selectById(houseId);
        if (house == null) {
            throw new BizException(404, "房屋不存在");
        }
        SysUser user = userMapper.selectById(userId);
        HouseBindRequest fake = new HouseBindRequest();
        fake.setRemark(remark);
        HouseMoveApply apply = baseApply(user, house, TYPE_OUT, fake);
        // 身份沿用住户档案
        HouseholdResident hr = findActiveHousehold(tenantId, houseId, user.getPhone());
        if (hr != null) {
            apply.setIdentityType(hr.getIdentityType());
            hr.setStatus(HS_MOVING_OUT);
            hr.setUpdateTime(LocalDateTime.now());
            householdMapper.updateById(hr);
        }
        applyMapper.insert(apply);
        return toApplyMap(apply);
    }

    public List<Map<String, Object>> myApplies() {
        Long userId = requireUserId();
        Long tenantId = requireTenantId();
        return applyMapper.selectList(new LambdaQueryWrapper<HouseMoveApply>()
                        .eq(HouseMoveApply::getUserId, userId)
                        .eq(HouseMoveApply::getTenantId, tenantId)
                        .orderByDesc(HouseMoveApply::getId)
                        .last("limit 50"))
                .stream().map(this::toApplyMap).collect(Collectors.toList());
    }

    public PageResult<Map<String, Object>> pageApplies(String status, String applyType, int page, int size) {
        return pageApplies(status, applyType, null, page, size);
    }

    public PageResult<Map<String, Object>> pageApplies(
            String status, String applyType, String keyword, int page, int size) {
        requireStaff();
        Long tenantId = requireTenantId();
        LambdaQueryWrapper<HouseMoveApply> q = new LambdaQueryWrapper<HouseMoveApply>()
                .eq(HouseMoveApply::getTenantId, tenantId)
                .eq(StringUtils.hasText(applyType), HouseMoveApply::getApplyType, applyType)
                .orderByDesc(HouseMoveApply::getId);
        if (StringUtils.hasText(status)) {
            String st = status.trim();
            if ("HISTORY".equalsIgnoreCase(st) || "DONE".equalsIgnoreCase(st) || "FINISHED".equalsIgnoreCase(st)) {
                q.in(HouseMoveApply::getStatus, ST_APPROVED, ST_REJECTED);
            } else if (st.contains(",")) {
                q.in(HouseMoveApply::getStatus, java.util.Arrays.asList(st.split(",")));
            } else {
                q.eq(HouseMoveApply::getStatus, st);
            }
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            q.and(w -> w.like(HouseMoveApply::getApplyNo, kw)
                    .or().like(HouseMoveApply::getApplicantName, kw)
                    .or().like(HouseMoveApply::getApplicantPhone, kw)
                    .or().like(HouseMoveApply::getHouseLabel, kw));
        }
        Page<HouseMoveApply> p = applyMapper.selectPage(new Page<>(page, size), q);
        List<Map<String, Object>> records = p.getRecords().stream().map(this::toApplyMap).collect(Collectors.toList());
        return PageResult.of(page, size, p.getTotal(), records);
    }

    /** 小区迁移流动统计（当前租户） */
    public Map<String, Object> moveStats() {
        requireStaff();
        Long tenantId = requireTenantId();
        Map<String, Object> m = new HashMap<>();
        m.put("pendingTotal", cnt("select count(1) from house_move_apply where tenant_id=? and status=?", tenantId, ST_PENDING));
        m.put("approvedTotal", cnt("select count(1) from house_move_apply where tenant_id=? and status=?", tenantId, ST_APPROVED));
        m.put("rejectedTotal", cnt("select count(1) from house_move_apply where tenant_id=? and status=?", tenantId, ST_REJECTED));
        m.put("moveInPending", cnt("select count(1) from house_move_apply where tenant_id=? and apply_type=? and status=?",
                tenantId, TYPE_IN, ST_PENDING));
        m.put("moveOutPending", cnt("select count(1) from house_move_apply where tenant_id=? and apply_type=? and status=?",
                tenantId, TYPE_OUT, ST_PENDING));
        m.put("moveInApproved", cnt("select count(1) from house_move_apply where tenant_id=? and apply_type=? and status=?",
                tenantId, TYPE_IN, ST_APPROVED));
        m.put("moveOutApproved", cnt("select count(1) from house_move_apply where tenant_id=? and apply_type=? and status=?",
                tenantId, TYPE_OUT, ST_APPROVED));
        m.put("activeHousehold", cnt("select count(1) from household_resident where tenant_id=? and status=?",
                tenantId, HS_ACTIVE));
        m.put("pendingHousehold", cnt("select count(1) from household_resident where tenant_id=? and status=?",
                tenantId, HS_PENDING));
        m.put("movingOutHousehold", cnt("select count(1) from household_resident where tenant_id=? and status=?",
                tenantId, HS_MOVING_OUT));
        m.put("movedOutHousehold", cnt("select count(1) from household_resident where tenant_id=? and status=?",
                tenantId, HS_MOVED_OUT));

        // 近 30 日补齐无数据日期，便于前端画连续趋势
        Map<String, Map<String, Object>> dayMap = new HashMap<>();
        try {
            List<Map<String, Object>> raw = jdbcTemplate.queryForList(
                    "select date(create_time) as day, "
                            + "sum(case when apply_type='MOVE_IN' then 1 else 0 end) as moveIn, "
                            + "sum(case when apply_type='MOVE_OUT' then 1 else 0 end) as moveOut, "
                            + "count(1) as total "
                            + "from house_move_apply where tenant_id=? and create_time>=date_sub(curdate(), interval 29 day) "
                            + "group by date(create_time) order by day",
                    tenantId);
            for (Map<String, Object> row : raw) {
                String day = formatDay(row.get("day"));
                Map<String, Object> item = new HashMap<>();
                item.put("day", day);
                item.put("moveIn", toInt(row.get("moveIn")));
                item.put("moveOut", toInt(row.get("moveOut")));
                item.put("total", toInt(row.get("total")));
                dayMap.put(day, item);
            }
        } catch (Exception ignored) {
        }
        List<Map<String, Object>> byDay = new java.util.ArrayList<>();
        java.time.LocalDate start = java.time.LocalDate.now().minusDays(29);
        for (int i = 0; i < 30; i++) {
            String day = start.plusDays(i).toString();
            Map<String, Object> item = dayMap.get(day);
            if (item == null) {
                item = new HashMap<>();
                item.put("day", day);
                item.put("moveIn", 0);
                item.put("moveOut", 0);
                item.put("total", 0);
            }
            byDay.add(item);
        }
        m.put("recentTrend", byDay);

        int ownerCnt = 0;
        int tenantCnt = 0;
        try {
            List<Map<String, Object>> rawId = jdbcTemplate.queryForList(
                    "select ifnull(nullif(identity_type,''),'OWNER') as name, count(1) as value "
                            + "from house_move_apply where tenant_id=? and status=? group by name",
                    tenantId, ST_APPROVED);
            for (Map<String, Object> row : rawId) {
                String name = String.valueOf(row.get("name"));
                int v = toInt(row.get("value"));
                if (ID_TENANT.equalsIgnoreCase(name)) {
                    tenantCnt += v;
                } else {
                    ownerCnt += v;
                }
            }
        } catch (Exception ignored) {
        }
        List<Map<String, Object>> byIdentity = new java.util.ArrayList<>();
        Map<String, Object> ownerRow = new HashMap<>();
        ownerRow.put("name", ID_OWNER);
        ownerRow.put("label", "业主");
        ownerRow.put("value", ownerCnt);
        byIdentity.add(ownerRow);
        Map<String, Object> tenantRow = new HashMap<>();
        tenantRow.put("name", ID_TENANT);
        tenantRow.put("label", "租客");
        tenantRow.put("value", tenantCnt);
        byIdentity.add(tenantRow);
        m.put("byIdentity", byIdentity);
        return m;
    }

    private static String formatDay(Object day) {
        if (day == null) {
            return "";
        }
        if (day instanceof java.sql.Date) {
            return ((java.sql.Date) day).toLocalDate().toString();
        }
        if (day instanceof java.time.LocalDate) {
            return day.toString();
        }
        String s = String.valueOf(day);
        return s.length() >= 10 ? s.substring(0, 10) : s;
    }

    private static int toInt(Object v) {
        if (v == null) {
            return 0;
        }
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            return 0;
        }
    }

    private int cnt(String sql, Object... args) {
        try {
            Integer n = jdbcTemplate.queryForObject(sql, Integer.class, args);
            return n == null ? 0 : n;
        } catch (Exception e) {
            return 0;
        }
    }

    @Transactional
    public Map<String, Object> approve(Long applyId, MoveAuditRequest req) {
        requireStaff();
        HouseMoveApply apply = requireApply(applyId);
        if (!ST_PENDING.equals(apply.getStatus())) {
            throw new BizException("该申请已处理");
        }
        if (TYPE_IN.equals(apply.getApplyType())) {
            syncMoveIn(apply);
        } else if (TYPE_OUT.equals(apply.getApplyType())) {
            syncMoveOut(apply);
        } else {
            throw new BizException("未知申请类型");
        }
        fillAudit(apply, ST_APPROVED, req == null ? null : req.getRemark());
        applyMapper.updateById(apply);
        notifyApplicant(apply, true);
        return toApplyMap(apply);
    }

    @Transactional
    public Map<String, Object> reject(Long applyId, MoveAuditRequest req) {
        requireStaff();
        HouseMoveApply apply = requireApply(applyId);
        if (!ST_PENDING.equals(apply.getStatus())) {
            throw new BizException("该申请已处理");
        }
        String remark = req == null ? null : req.getRemark();
        if (!StringUtils.hasText(remark)) {
            throw new BizException(400, "驳回请填写原因");
        }
        // 迁入驳回：待审核住户移除展示（标已迁出避免脏数据）
        if (TYPE_IN.equals(apply.getApplyType())) {
            HouseholdResident hr = findHouseholdAny(apply.getTenantId(), apply.getHouseId(), apply.getApplicantPhone());
            if (hr != null && HS_PENDING.equals(hr.getStatus())) {
                hr.setStatus(HS_MOVED_OUT);
                hr.setUpdateTime(LocalDateTime.now());
                householdMapper.updateById(hr);
            }
        }
        // 迁出驳回：住户状态从迁出中恢复正常
        if (TYPE_OUT.equals(apply.getApplyType())) {
            HouseholdResident hr = findActiveOrMoving(apply.getTenantId(), apply.getHouseId(), apply.getApplicantPhone());
            if (hr != null && HS_MOVING_OUT.equals(hr.getStatus())) {
                hr.setStatus(HS_ACTIVE);
                hr.setUpdateTime(LocalDateTime.now());
                householdMapper.updateById(hr);
            }
        }
        fillAudit(apply, ST_REJECTED, remark);
        applyMapper.updateById(apply);
        notifyApplicant(apply, false);
        return toApplyMap(apply);
    }

    private void notifyApplicant(HouseMoveApply apply, boolean passed) {
        try {
            String title = passed ? "迁入迁出审批通过" : "迁入迁出审批驳回";
            String content = (TYPE_IN.equals(apply.getApplyType()) ? "迁入" : "迁出")
                    + "申请「" + apply.getApplyNo() + "」"
                    + (passed ? "已通过，住户信息已同步" : "已驳回：" + (apply.getAuditRemark() == null ? "" : apply.getAuditRemark()));
            jdbcTemplate.update(
                    "insert into sys_message(tenant_id,user_id,title,content,msg_type,read_flag,create_time) values(?,?,?,?,?,?,now())",
                    apply.getTenantId(), apply.getUserId(), title, content, "house_move", 0);
        } catch (Exception ignored) {
        }
    }

    public PageResult<Map<String, Object>> pageHousehold(
            String status, String identityType, String keyword, int page, int size) {
        requireStaff();
        ensureHouseholdLeaseColumns();
        Long tenantId = requireTenantId();
        LambdaQueryWrapper<HouseholdResident> q = new LambdaQueryWrapper<HouseholdResident>()
                .eq(HouseholdResident::getTenantId, tenantId)
                .eq(StringUtils.hasText(status), HouseholdResident::getStatus, status)
                .orderByDesc(HouseholdResident::getId);
        if (StringUtils.hasText(identityType)) {
            String idt = normalizeIdentity(identityType);
            q.eq(HouseholdResident::getIdentityType, idt);
        }
        if (StringUtils.hasText(keyword)) {
            q.and(w -> w.like(HouseholdResident::getName, keyword)
                    .or().like(HouseholdResident::getPhone, keyword)
                    .or().like(HouseholdResident::getHouseLabel, keyword));
        }
        Page<HouseholdResident> p = householdMapper.selectPage(new Page<>(page, size), q);
        List<Map<String, Object>> records = p.getRecords().stream().map(this::toHouseholdMap).collect(Collectors.toList());
        return PageResult.of(page, size, p.getTotal(), records);
    }

    /** 兼容旧调用 */
    public PageResult<Map<String, Object>> pageHousehold(String status, String keyword, int page, int size) {
        return pageHousehold(status, null, keyword, page, size);
    }

    @Transactional
    public Map<String, Object> saveHousehold(HouseholdSaveRequest req) {
        requireStaff();
        Long tenantId = requireTenantId();
        if (!StringUtils.hasText(req.getName()) || !StringUtils.hasText(req.getPhone())) {
            throw new BizException("姓名和手机号必填");
        }
        if (req.getHouseId() == null) {
            throw new BizException("请选择房屋");
        }
        CommunityHouse house = houseMapper.selectById(req.getHouseId());
        if (house == null || !Objects.equals(house.getTenantId(), tenantId)) {
            throw new BizException(403, "房屋不属于当前社区");
        }
        String identity = normalizeIdentity(req.getIdentityType());
        String status = StringUtils.hasText(req.getStatus()) ? req.getStatus() : HS_ACTIVE;
        HouseholdResident row;
        if (req.getId() != null) {
            row = householdMapper.selectById(req.getId());
            if (row == null || !Objects.equals(row.getTenantId(), tenantId)) {
                throw new BizException(404, "住户不存在");
            }
            row.setName(req.getName().trim());
            row.setPhone(req.getPhone().trim());
            row.setHouseId(req.getHouseId());
            row.setIdentityType(identity);
            row.setStatus(status);
            row.setHouseLabel(HouseService.formatLabel(house));
            if (req.getUserId() != null) {
                row.setUserId(req.getUserId());
            }
            applyLease(row, identity, req.getLeaseStart(), req.getLeaseEnd());
            row.setUpdateTime(LocalDateTime.now());
            householdMapper.updateById(row);
            if (HS_MOVED_OUT.equals(status)) {
                disableResidentCars(tenantId, row.getHouseId(), row.getUserId());
            } else if (HS_ACTIVE.equals(status)) {
                SysUser u = null;
                if (row.getUserId() != null) {
                    u = userMapper.selectById(row.getUserId());
                }
                if (u == null) {
                    u = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                            .eq(SysUser::getPhone, row.getPhone())
                            .eq(SysUser::getTenantId, tenantId)
                            .last("limit 1"));
                }
                if (u == null && ID_TENANT.equals(identity)) {
                    u = createTenantAppUser(tenantId, row.getPhone(), row.getName());
                }
                if (u != null) {
                    row.setUserId(u.getId());
                    householdMapper.updateById(row);
                    ensureBind(tenantId, u.getId(), house.getId());
                }
            }
        } else {
            Long exists = householdMapper.selectCount(new LambdaQueryWrapper<HouseholdResident>()
                    .eq(HouseholdResident::getTenantId, tenantId)
                    .eq(HouseholdResident::getHouseId, req.getHouseId())
                    .eq(HouseholdResident::getPhone, req.getPhone().trim())
                    .ne(HouseholdResident::getStatus, HS_MOVED_OUT));
            if (exists != null && exists > 0) {
                throw new BizException("该房屋下已存在相同手机号住户");
            }
            row = new HouseholdResident();
            row.setTenantId(tenantId);
            row.setUserId(req.getUserId());
            row.setHouseId(req.getHouseId());
            row.setName(req.getName().trim());
            row.setPhone(req.getPhone().trim());
            row.setIdentityType(identity);
            row.setStatus(status);
            row.setSource("MANUAL");
            row.setHouseLabel(HouseService.formatLabel(house));
            applyLease(row, identity, req.getLeaseStart(), req.getLeaseEnd());
            row.setCreateTime(LocalDateTime.now());
            row.setUpdateTime(LocalDateTime.now());
            householdMapper.insert(row);
            // 若手机号对应本租户居民账号，同步绑定关系；租客无账号时自动开通 App 登录
            if (HS_ACTIVE.equals(status)) {
                SysUser u = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getPhone, row.getPhone())
                        .eq(SysUser::getTenantId, tenantId)
                        .last("limit 1"));
                if (u == null && ID_TENANT.equals(identity)) {
                    u = createTenantAppUser(tenantId, row.getPhone(), row.getName());
                }
                if (u != null) {
                    row.setUserId(u.getId());
                    householdMapper.updateById(row);
                    ensureBind(tenantId, u.getId(), house.getId());
                }
            }
        }
        return toHouseholdMap(row);
    }

    /** 物业录入租客时开通 App 账号（默认密码 123456） */
    private SysUser createTenantAppUser(Long tenantId, String phone, String name) {
        SysUser user = new SysUser();
        user.setTenantId(tenantId);
        user.setPhone(phone);
        user.setPassword(DigestUtil.md5Hex("123456"));
        user.setNickname(StringUtils.hasText(name) ? name.trim() : ("租客" + phone.substring(Math.max(0, phone.length() - 4))));
        user.setRole(RoleConstants.RESIDENT);
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        userMapper.insert(user);
        try {
            Long roleId = jdbcTemplate.queryForObject(
                    "select id from sys_role where role_code=? limit 1", Long.class, RoleConstants.RESIDENT);
            if (roleId != null) {
                Integer n = jdbcTemplate.queryForObject(
                        "select count(1) from sys_user_role where user_id=? and role_id=?",
                        Integer.class, user.getId(), roleId);
                if (n == null || n == 0) {
                    jdbcTemplate.update("insert into sys_user_role(user_id,role_id) values(?,?)", user.getId(), roleId);
                }
            }
        } catch (Exception ignored) {
        }
        return user;
    }

    @Transactional
    public void removeHousehold(Long id) {
        requireStaff();
        Long tenantId = requireTenantId();
        HouseholdResident row = householdMapper.selectById(id);
        if (row == null || !Objects.equals(row.getTenantId(), tenantId)) {
            throw new BizException(404, "住户不存在");
        }
        row.setStatus(HS_MOVED_OUT);
        row.setUpdateTime(LocalDateTime.now());
        householdMapper.updateById(row);
        if (row.getUserId() != null) {
            bindMapper.delete(new LambdaQueryWrapper<UserHouseBind>()
                    .eq(UserHouseBind::getUserId, row.getUserId())
                    .eq(UserHouseBind::getHouseId, row.getHouseId()));
        }
        disableResidentCars(tenantId, row.getHouseId(), row.getUserId());
    }

    /** 审批通过：迁入 → 绑定 + 住户档案 */
    private void syncMoveIn(HouseMoveApply apply) {
        ensureBind(apply.getTenantId(), apply.getUserId(), apply.getHouseId());
        CommunityHouse house = houseMapper.selectById(apply.getHouseId());
        HouseholdResident hr = findHouseholdAny(apply.getTenantId(), apply.getHouseId(), apply.getApplicantPhone());
        if (hr == null) {
            hr = new HouseholdResident();
            hr.setTenantId(apply.getTenantId());
            hr.setUserId(apply.getUserId());
            hr.setHouseId(apply.getHouseId());
            hr.setName(apply.getApplicantName());
            hr.setPhone(apply.getApplicantPhone());
            hr.setIdentityType(normalizeIdentity(apply.getIdentityType()));
            hr.setStatus(HS_ACTIVE);
            hr.setSource("APPLY");
            hr.setHouseLabel(apply.getHouseLabel() != null ? apply.getHouseLabel()
                    : (house == null ? "" : HouseService.formatLabel(house)));
            hr.setCreateTime(LocalDateTime.now());
            hr.setUpdateTime(LocalDateTime.now());
            householdMapper.insert(hr);
        } else {
            hr.setUserId(apply.getUserId());
            hr.setName(apply.getApplicantName());
            hr.setIdentityType(normalizeIdentity(apply.getIdentityType()));
            hr.setStatus(HS_ACTIVE);
            hr.setHouseLabel(apply.getHouseLabel() != null ? apply.getHouseLabel() : hr.getHouseLabel());
            hr.setUpdateTime(LocalDateTime.now());
            householdMapper.updateById(hr);
        }
    }

    /** 居民一提交迁入申请就出现在住户管理（待审核） */
    private void upsertHouseholdPending(HouseMoveApply apply, SysUser user, CommunityHouse house) {
        HouseholdResident hr = findHouseholdAny(apply.getTenantId(), apply.getHouseId(), apply.getApplicantPhone());
        if (hr == null) {
            hr = new HouseholdResident();
            hr.setTenantId(apply.getTenantId());
            hr.setUserId(apply.getUserId());
            hr.setHouseId(apply.getHouseId());
            hr.setName(apply.getApplicantName() != null ? apply.getApplicantName() : user.getNickname());
            hr.setPhone(apply.getApplicantPhone() != null ? apply.getApplicantPhone() : user.getPhone());
            hr.setIdentityType(normalizeIdentity(apply.getIdentityType()));
            hr.setStatus(HS_PENDING);
            hr.setSource("APPLY");
            hr.setHouseLabel(HouseService.formatLabel(house));
            hr.setCreateTime(LocalDateTime.now());
            hr.setUpdateTime(LocalDateTime.now());
            householdMapper.insert(hr);
        } else if (!HS_ACTIVE.equals(hr.getStatus())) {
            hr.setUserId(apply.getUserId());
            hr.setName(apply.getApplicantName());
            hr.setIdentityType(normalizeIdentity(apply.getIdentityType()));
            hr.setStatus(HS_PENDING);
            hr.setHouseLabel(HouseService.formatLabel(house));
            hr.setUpdateTime(LocalDateTime.now());
            householdMapper.updateById(hr);
        }
    }

    /** 审批通过：迁出 → 解绑 + 住户已迁出 */
    private void syncMoveOut(HouseMoveApply apply) {
        UserHouseBind bind = bindMapper.selectOne(new LambdaQueryWrapper<UserHouseBind>()
                .eq(UserHouseBind::getUserId, apply.getUserId())
                .eq(UserHouseBind::getHouseId, apply.getHouseId()));
        if (bind != null) {
            boolean wasDefault = bind.getIsDefault() != null && bind.getIsDefault() == 1;
            bindMapper.deleteById(bind.getId());
            if (wasDefault) {
                UserHouseBind next = bindMapper.selectOne(new LambdaQueryWrapper<UserHouseBind>()
                        .eq(UserHouseBind::getUserId, apply.getUserId())
                        .eq(UserHouseBind::getTenantId, apply.getTenantId())
                        .orderByAsc(UserHouseBind::getId)
                        .last("limit 1"));
                if (next != null) {
                    next.setIsDefault(1);
                    bindMapper.updateById(next);
                }
            }
        }
        HouseholdResident hr = findActiveOrMoving(apply.getTenantId(), apply.getHouseId(), apply.getApplicantPhone());
        if (hr != null) {
            hr.setStatus(HS_MOVED_OUT);
            hr.setUpdateTime(LocalDateTime.now());
            householdMapper.updateById(hr);
        }
        disableResidentCars(apply.getTenantId(), apply.getHouseId(), apply.getUserId());
    }

    /** 住户迁出后停用该房屋下本人常驻车辆（resident_car） */
    private void disableResidentCars(Long tenantId, Long houseId, Long userId) {
        try {
            if (userId != null) {
                jdbcTemplate.update(
                        "update resident_car set status='DISABLED', update_time=now() "
                                + "where tenant_id=? and house_id=? and user_id=? and status='ACTIVE'",
                        tenantId, houseId, userId);
            } else {
                jdbcTemplate.update(
                        "update resident_car set status='DISABLED', update_time=now() "
                                + "where tenant_id=? and house_id=? and status='ACTIVE'",
                        tenantId, houseId);
            }
        } catch (Exception ignored) {
            // 表未初始化时不影响迁出主流程
        }
    }

    private void ensureBind(Long tenantId, Long userId, Long houseId) {
        Long cnt = bindMapper.selectCount(new LambdaQueryWrapper<UserHouseBind>()
                .eq(UserHouseBind::getUserId, userId)
                .eq(UserHouseBind::getHouseId, houseId));
        if (cnt != null && cnt > 0) {
            return;
        }
        long total = bindMapper.selectCount(new LambdaQueryWrapper<UserHouseBind>()
                .eq(UserHouseBind::getUserId, userId)
                .eq(UserHouseBind::getTenantId, tenantId));
        UserHouseBind bind = new UserHouseBind();
        bind.setTenantId(tenantId);
        bind.setUserId(userId);
        bind.setHouseId(houseId);
        bind.setIsDefault(total == 0 ? 1 : 0);
        bind.setCreateTime(LocalDateTime.now());
        bindMapper.insert(bind);
    }

    private HouseMoveApply baseApply(SysUser user, CommunityHouse house, String type, HouseBindRequest req) {
        HouseMoveApply apply = new HouseMoveApply();
        apply.setApplyNo(genApplyNo(type));
        apply.setTenantId(house.getTenantId());
        apply.setUserId(user.getId());
        apply.setHouseId(house.getId());
        apply.setApplyType(type);
        apply.setIdentityType(normalizeIdentity(req == null ? null : req.getIdentityType()));
        apply.setApplicantName(user.getNickname());
        apply.setApplicantPhone(user.getPhone());
        apply.setHouseLabel(HouseService.formatLabel(house));
        apply.setStatus(ST_PENDING);
        apply.setRemark(req == null ? null : req.getRemark());
        apply.setCreateTime(LocalDateTime.now());
        return apply;
    }

    private void fillAudit(HouseMoveApply apply, String status, String remark) {
        apply.setStatus(status);
        apply.setAuditUserId(TenantContext.getUserId());
        SysUser auditor = userMapper.selectById(TenantContext.getUserId());
        apply.setAuditName(auditor == null ? null : auditor.getNickname());
        apply.setAuditRemark(remark);
        apply.setAuditTime(LocalDateTime.now());
    }

    private void assertNoPending(Long userId, Long houseId, String type) {
        Long cnt = applyMapper.selectCount(new LambdaQueryWrapper<HouseMoveApply>()
                .eq(HouseMoveApply::getUserId, userId)
                .eq(HouseMoveApply::getHouseId, houseId)
                .eq(HouseMoveApply::getApplyType, type)
                .eq(HouseMoveApply::getStatus, ST_PENDING));
        if (cnt != null && cnt > 0) {
            throw new BizException("已有待审批的" + (TYPE_IN.equals(type) ? "迁入" : "迁出") + "申请");
        }
    }

    private HouseMoveApply requireApply(Long id) {
        Long tenantId = requireTenantId();
        HouseMoveApply apply = applyMapper.selectById(id);
        if (apply == null || !Objects.equals(apply.getTenantId(), tenantId)) {
            throw new BizException(404, "申请不存在");
        }
        return apply;
    }

    private HouseholdResident findActiveHousehold(Long tenantId, Long houseId, String phone) {
        return householdMapper.selectOne(new LambdaQueryWrapper<HouseholdResident>()
                .eq(HouseholdResident::getTenantId, tenantId)
                .eq(HouseholdResident::getHouseId, houseId)
                .eq(HouseholdResident::getPhone, phone)
                .eq(HouseholdResident::getStatus, HS_ACTIVE)
                .last("limit 1"));
    }

    private HouseholdResident findHouseholdAny(Long tenantId, Long houseId, String phone) {
        return householdMapper.selectOne(new LambdaQueryWrapper<HouseholdResident>()
                .eq(HouseholdResident::getTenantId, tenantId)
                .eq(HouseholdResident::getHouseId, houseId)
                .eq(HouseholdResident::getPhone, phone)
                .orderByDesc(HouseholdResident::getId)
                .last("limit 1"));
    }

    private HouseholdResident findActiveOrMoving(Long tenantId, Long houseId, String phone) {
        return householdMapper.selectOne(new LambdaQueryWrapper<HouseholdResident>()
                .eq(HouseholdResident::getTenantId, tenantId)
                .eq(HouseholdResident::getHouseId, houseId)
                .eq(HouseholdResident::getPhone, phone)
                .in(HouseholdResident::getStatus, HS_ACTIVE, HS_MOVING_OUT, HS_PENDING)
                .orderByDesc(HouseholdResident::getId)
                .last("limit 1"));
    }

    private Map<String, Object> toApplyMap(HouseMoveApply a) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", a.getId());
        m.put("applyNo", a.getApplyNo());
        m.put("no", a.getApplyNo());
        m.put("tenantId", a.getTenantId());
        m.put("userId", a.getUserId());
        m.put("houseId", a.getHouseId());
        m.put("applyType", a.getApplyType());
        m.put("type", TYPE_IN.equals(a.getApplyType()) ? "迁入" : "迁出");
        m.put("identityType", a.getIdentityType());
        m.put("identityLabel", ID_TENANT.equals(a.getIdentityType()) ? "租客" : "业主");
        m.put("applicantName", a.getApplicantName());
        m.put("name", a.getApplicantName());
        m.put("applicantPhone", a.getApplicantPhone());
        m.put("phone", a.getApplicantPhone());
        m.put("houseLabel", a.getHouseLabel());
        m.put("room", a.getHouseLabel());
        m.put("status", a.getStatus());
        m.put("statusLabel", statusLabel(a.getStatus()));
        m.put("remark", a.getRemark());
        m.put("auditName", a.getAuditName());
        m.put("auditRemark", a.getAuditRemark());
        m.put("auditTime", a.getAuditTime());
        m.put("createTime", a.getCreateTime());
        return m;
    }

    private Map<String, Object> toHouseholdMap(HouseholdResident r) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", r.getId());
        m.put("tenantId", r.getTenantId());
        m.put("userId", r.getUserId());
        m.put("houseId", r.getHouseId());
        m.put("name", r.getName());
        m.put("phone", r.getPhone());
        m.put("identityType", r.getIdentityType());
        m.put("type", ID_TENANT.equals(r.getIdentityType()) ? "租客" : "业主");
        m.put("status", r.getStatus());
        m.put("statusLabel", householdStatusLabel(r.getStatus()));
        m.put("source", r.getSource());
        m.put("houseLabel", r.getHouseLabel());
        m.put("room", r.getHouseLabel());
        m.put("leaseStart", r.getLeaseStart());
        m.put("leaseEnd", r.getLeaseEnd());
        m.put("createTime", r.getCreateTime());
        m.put("updateTime", r.getUpdateTime());
        return m;
    }

    private void applyLease(HouseholdResident row, String identity, String start, String end) {
        ensureHouseholdLeaseColumns();
        if (ID_TENANT.equals(identity)) {
            row.setLeaseStart(parseLocalDate(start));
            row.setLeaseEnd(parseLocalDate(end));
        } else {
            row.setLeaseStart(null);
            row.setLeaseEnd(null);
        }
    }

    private void ensureHouseholdLeaseColumns() {
        try {
            jdbcTemplate.update("alter table household_resident add column lease_start date null");
        } catch (Exception ignored) {
        }
        try {
            jdbcTemplate.update("alter table household_resident add column lease_end date null");
        } catch (Exception ignored) {
        }
    }

    private static java.time.LocalDate parseLocalDate(String s) {
        if (!StringUtils.hasText(s)) return null;
        try {
            return java.time.LocalDate.parse(s.trim().substring(0, Math.min(10, s.trim().length())));
        } catch (Exception e) {
            throw new BizException("租赁日期格式应为 yyyy-MM-dd");
        }
    }

    private static String statusLabel(String s) {
        if (ST_APPROVED.equals(s)) return "已通过";
        if (ST_REJECTED.equals(s)) return "已驳回";
        return "待审批";
    }

    private static String householdStatusLabel(String s) {
        if (HS_MOVED_OUT.equals(s)) return "已迁出";
        if (HS_MOVING_OUT.equals(s)) return "迁出中";
        if (HS_PENDING.equals(s)) return "待审核";
        return "正常";
    }

    private static String normalizeIdentity(String v) {
        if (ID_TENANT.equalsIgnoreCase(v) || "租客".equals(v)) return ID_TENANT;
        return ID_OWNER;
    }

    private String genApplyNo(String type) {
        String prefix = TYPE_OUT.equals(type) ? "MO" : "MI";
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int r = ThreadLocalRandom.current().nextInt(100, 999);
        return prefix + ts + r;
    }

    private void assertSameTenant(CommunityHouse house, Long tenantId) {
        if (!Objects.equals(house.getTenantId(), tenantId)) {
            throw new BizException(403, "禁止跨社区申请房屋");
        }
    }

    private void requireStaff() {
        String role = TenantContext.getRole();
        if (!RoleConstants.PROPERTY_ADMIN.equals(role) && !RoleConstants.GRID_WORKER.equals(role)
                && !RoleConstants.isPlatformAdmin(role)) {
            throw new BizException(403, "仅物业人员可操作");
        }
    }

    private Long requireUserId() {
        Long userId = TenantContext.getUserId();
        if (userId == null) throw new BizException(401, "未登录");
        return userId;
    }

    private Long requireTenantId() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) throw new BizException(400, "缺少租户信息");
        return tenantId;
    }
}
