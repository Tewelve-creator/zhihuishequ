package com.community.repair.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.community.common.constant.RoleConstants;
import com.community.common.exception.BizException;
import com.community.common.feign.dto.AiImageRepairResponse;
import com.community.common.result.R;
import com.community.common.tenant.TenantContext;
import com.community.repair.entity.*;
import com.community.repair.mapper.*;
import com.community.repair.service.ContentAuditService;
import com.community.repair.service.RepairNotifyService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class RepairNoticeController {

    private final RepairOrderMapper repairOrderMapper;
    private final NoticeInfoMapper noticeInfoMapper;
    private final VisitorAppointmentMapper visitorMapper;
    private final CommunityActivityMapper activityMapper;
    private final ActivitySignupMapper signupMapper;
    private final EmotionAppealMapper appealMapper;
    private final ContentAuditService contentAuditService;
    private final RepairNotifyService repairNotifyService;
    private final JdbcTemplate jdbcTemplate;

    // ---------- 报修（角色隔离：居民仅本人；物业本社区全部） ----------
    @PostMapping("/repair/ai/recognize")
    public R<AiImageRepairResponse> recognizeImage(@RequestBody Map<String, String> body) {
        requireTenant();
        return R.ok(contentAuditService.recognizeRepairImage(body.get("imageUrl")));
    }

    /** 仅居民可提交报修 */
    @PostMapping("/repair/orders")
    public R<RepairOrder> createRepair(@RequestBody RepairOrder order) {
        requireTenant();
        if (RoleConstants.isTenantStaff(TenantContext.getRole())
                || RoleConstants.isPlatformAdmin(TenantContext.getRole())) {
            throw new BizException(403, "物业端不可提交报修，请在工单列表处理");
        }
        Long tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        order.setId(null);
        order.setTenantId(tenantId);
        order.setUserId(userId);
        if (order.getStatus() == null) {
            order.setStatus("pending");
        }
        if (StringUtils.hasText(order.getImageUrl()) && !StringUtils.hasText(order.getFacilityType())) {
            try {
                AiImageRepairResponse ai = contentAuditService.recognizeRepairImage(order.getImageUrl());
                order.setFacilityType(ai.getFacilityType());
                if (!StringUtils.hasText(order.getDamageLevel())) {
                    order.setDamageLevel(ai.getDamageLevel());
                }
                if (!StringUtils.hasText(order.getTitle())) {
                    order.setTitle(ai.getSuggestTitle());
                }
                if (!StringUtils.hasText(order.getDescription()) && StringUtils.hasText(ai.getSuggestDesc())) {
                    order.setDescription(ai.getSuggestDesc());
                }
                if (!StringUtils.hasText(order.getAiTag())) {
                    order.setAiTag(joinTag(ai.getFacilityType(), ai.getDamageLevel()));
                }
            } catch (Exception ignored) {
            }
        }
        if (!StringUtils.hasText(order.getAiTag())
                && (StringUtils.hasText(order.getFacilityType()) || StringUtils.hasText(order.getDamageLevel()))) {
            order.setAiTag(joinTag(order.getFacilityType(), order.getDamageLevel()));
        }
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        repairOrderMapper.insert(order);
        repairNotifyService.notifyStaffNewOrder(order);
        return R.ok(order);
    }

    @GetMapping("/repair/orders")
    public R<List<RepairOrder>> listRepair(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean mine) {
        Long tenantId = requireTenant();
        LambdaQueryWrapper<RepairOrder> q = new LambdaQueryWrapper<RepairOrder>()
                .eq(RepairOrder::getTenantId, tenantId)
                .eq(status != null, RepairOrder::getStatus, status)
                .orderByDesc(RepairOrder::getId);
        // 居民：仅本人；物业/网格：本社区全部（mine=true 时仅看指派给我的）
        if (!RoleConstants.isTenantStaff(TenantContext.getRole())
                && !RoleConstants.isPlatformAdmin(TenantContext.getRole())) {
            Long uid = TenantContext.getUserId();
            if (uid == null) {
                return R.ok(Collections.emptyList());
            }
            q.eq(RepairOrder::getUserId, uid);
        } else if (Boolean.TRUE.equals(mine)) {
            Long uid = TenantContext.getUserId();
            if (uid == null) {
                return R.ok(Collections.emptyList());
            }
            q.eq(RepairOrder::getHandlerId, uid);
        }
        List<RepairOrder> list = repairOrderMapper.selectList(q);
        fillHandlerNames(list);
        return R.ok(list);
    }

    @GetMapping("/repair/orders/{id}")
    public R<RepairOrder> detail(@PathVariable Long id) {
        RepairOrder o = requireOrderInTenant(id);
        assertCanViewOrder(o);
        fillHandlerNames(Collections.singletonList(o));
        return R.ok(o);
    }

    @PutMapping("/repair/orders/{id}/handle")
    public R<RepairOrder> handle(@PathVariable Long id, @RequestBody RepairOrder body) {
        assertStaff();
        RepairOrder o = requireOrderInTenant(id);
        o.setStatus(body.getStatus() == null ? "processing" : body.getStatus());
        o.setHandleRemark(body.getHandleRemark());
        if (StringUtils.hasText(body.getFinishImageUrl())) {
            o.setFinishImageUrl(body.getFinishImageUrl());
        }
        o.setUpdateTime(LocalDateTime.now());
        repairOrderMapper.updateById(o);
        return R.ok(o);
    }

    /** 物业接单 */
    @PutMapping("/repair/orders/{id}/accept")
    public R<RepairOrder> accept(@PathVariable Long id) {
        assertStaff();
        RepairOrder o = requireOrderInTenant(id);
        o.setStatus("processing");
        o.setHandlerId(TenantContext.getUserId());
        o.setAcceptTime(LocalDateTime.now());
        o.setUpdateTime(LocalDateTime.now());
        repairOrderMapper.updateById(o);
        fillHandlerNames(Collections.singletonList(o));
        return R.ok(o);
    }

    /**
     * 指派给物业工作人员（物业管理员）
     * body: { "handlerId": 123 }
     */
    @PutMapping("/repair/orders/{id}/assign")
    public R<RepairOrder> assign(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        assertPropertyAdmin("仅物业管理员可指派工单");
        RepairOrder o = requireOrderInTenant(id);
        if ("done".equals(o.getStatus())) {
            throw new BizException(400, "已完工工单不可再指派");
        }
        Long handlerId = null;
        if (body != null && body.get("handlerId") != null) {
            handlerId = Long.valueOf(String.valueOf(body.get("handlerId")));
        }
        if (handlerId == null) {
            throw new BizException(400, "请选择处理人");
        }
        Map<String, Object> staff = requireStaffInTenant(handlerId, o.getTenantId());
        o.setHandlerId(handlerId);
        if ("pending".equals(o.getStatus()) || o.getStatus() == null) {
            o.setStatus("processing");
            o.setAcceptTime(LocalDateTime.now());
        }
        o.setUpdateTime(LocalDateTime.now());
        repairOrderMapper.updateById(o);
        o.setHandlerName(str(staff.get("nickname"), str(staff.get("phone"), "工作人员")));
        repairNotifyService.notifyAssigned(o, handlerId);
        return R.ok(o);
    }

    /** 物业完工 + 上传维修照片 */
    @PutMapping("/repair/orders/{id}/finish")
    public R<RepairOrder> finish(@PathVariable Long id, @RequestBody(required = false) RepairOrder body) {
        assertStaff();
        RepairOrder o = requireOrderInTenant(id);
        if (body != null) {
            if (StringUtils.hasText(body.getHandleRemark())) {
                o.setHandleRemark(body.getHandleRemark());
            }
            if (StringUtils.hasText(body.getFinishImageUrl())) {
                o.setFinishImageUrl(body.getFinishImageUrl());
            }
        }
        o.setStatus("done");
        o.setFinishTime(LocalDateTime.now());
        o.setUpdateTime(LocalDateTime.now());
        if (o.getHandlerId() == null) {
            o.setHandlerId(TenantContext.getUserId());
        }
        repairOrderMapper.updateById(o);
        return R.ok(o);
    }

    private Long requireTenant() {
        Long tid = TenantContext.getTenantId();
        if (tid == null) {
            throw new BizException(40001, "缺少租户上下文，禁止跨社区访问");
        }
        return tid;
    }

    private void assertStaff() {
        if (!RoleConstants.isTenantStaff(TenantContext.getRole())
                && !RoleConstants.isPlatformAdmin(TenantContext.getRole())) {
            throw new BizException(403, "仅物业工作人员可操作");
        }
    }

    private void assertPropertyAdmin(String tip) {
        String role = TenantContext.getRole();
        if (RoleConstants.isPlatformAdmin(role) || RoleConstants.isPropertyAdmin(role)) {
            return;
        }
        throw new BizException(403, tip == null ? "仅物业管理员可操作" : tip);
    }

    private RepairOrder requireOrderInTenant(Long id) {
        Long tid = requireTenant();
        RepairOrder o = repairOrderMapper.selectById(id);
        if (o == null || !Objects.equals(tid, o.getTenantId())) {
            throw new BizException(404, "工单不存在");
        }
        return o;
    }

    private void assertCanViewOrder(RepairOrder o) {
        if (RoleConstants.isTenantStaff(TenantContext.getRole())
                || RoleConstants.isPlatformAdmin(TenantContext.getRole())) {
            return;
        }
        Long uid = TenantContext.getUserId();
        if (uid == null || !Objects.equals(uid, o.getUserId())) {
            throw new BizException(403, "无权查看他人工单");
        }
    }

    private static String joinTag(String facility, String level) {
        String a = facility == null ? "" : facility.trim();
        String b = level == null ? "" : level.trim();
        if (a.isEmpty() && b.isEmpty()) {
            return null;
        }
        if (a.isEmpty()) {
            return b;
        }
        if (b.isEmpty()) {
            return a;
        }
        return a + " · " + b;
    }

    private Map<String, Object> requireStaffInTenant(Long userId, Long tenantId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select id, nickname, phone, role from sys_user where id=? and tenant_id=? and status=1 "
                        + "and role in ('property_admin','grid_worker') limit 1",
                userId, tenantId);
        if (rows.isEmpty()) {
            throw new BizException(400, "处理人不是本社区在职物业工作人员");
        }
        return rows.get(0);
    }

    private void fillHandlerNames(List<RepairOrder> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        List<Long> ids = list.stream()
                .map(RepairOrder::getHandlerId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return;
        }
        String in = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select id, nickname, phone from sys_user where id in (" + in + ")");
        Map<Long, String> nameMap = new HashMap<>();
        for (Map<String, Object> r : rows) {
            Long id = ((Number) r.get("id")).longValue();
            nameMap.put(id, str(r.get("nickname"), str(r.get("phone"), "")));
        }
        for (RepairOrder o : list) {
            if (o.getHandlerId() != null) {
                o.setHandlerName(nameMap.get(o.getHandlerId()));
            }
        }
    }

    private static String str(Object v, String def) {
        if (v == null) {
            return def;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? def : s;
    }

    // ---------- 公告（强制敏感词审核；仅物业管理员可发布/删除；支持定时发布） ----------
    @PostMapping("/notice")
    public R<NoticeInfo> createNotice(@RequestBody NoticeInfo notice) {
        assertPropertyAdmin("网格员不可发布公告");
        if (!StringUtils.hasText(notice.getTitle()) || !StringUtils.hasText(notice.getContent())) {
            throw new BizException(400, "标题和正文不能为空");
        }
        Map<String, Object> audit = contentAuditService.audit(
                (notice.getTitle() == null ? "" : notice.getTitle()) + " " +
                        (notice.getContent() == null ? "" : notice.getContent()));
        notice.setId(null);
        notice.setTenantId(TenantContext.getTenantId());
        notice.setPublisherId(TenantContext.getUserId());
        if (notice.getStatus() == null) {
            notice.setStatus(1);
        }
        if (notice.getSummary() == null) {
            notice.setSummary(String.valueOf(audit.get("summary")));
        }
        LocalDateTime now = LocalDateTime.now();
        notice.setCreateTime(now);
        if (notice.getPublishTime() == null) {
            notice.setPublishTime(now);
        }
        noticeInfoMapper.insert(notice);
        fillPublishState(Collections.singletonList(notice));
        return R.ok(notice);
    }

    /**
     * 公告列表
     * - 默认：居民/物业可见「已到点且启用」的公告（App 首页/列表）
     * - manage=true：物业管理员查看全部（含定时待发）
     */
    @GetMapping("/notice")
    public R<List<NoticeInfo>> listNotice(@RequestParam(required = false) Boolean manage) {
        boolean isManage = Boolean.TRUE.equals(manage);
        if (isManage) {
            assertPropertyAdmin("仅物业管理员可管理公告");
            List<NoticeInfo> all = noticeInfoMapper.selectList(new LambdaQueryWrapper<NoticeInfo>()
                    .orderByDesc(NoticeInfo::getPublishTime)
                    .orderByDesc(NoticeInfo::getId));
            fillPublishState(all);
            return R.ok(all);
        }
        // 网格员也可阅读已发布公告；不可管理
        LocalDateTime now = LocalDateTime.now();
        List<NoticeInfo> list = noticeInfoMapper.selectList(new LambdaQueryWrapper<NoticeInfo>()
                .eq(NoticeInfo::getStatus, 1)
                .and(w -> w.isNull(NoticeInfo::getPublishTime).or().le(NoticeInfo::getPublishTime, now))
                .orderByDesc(NoticeInfo::getPublishTime)
                .orderByDesc(NoticeInfo::getId));
        fillPublishState(list);
        return R.ok(list);
    }

    @GetMapping("/notice/{id}")
    public R<NoticeInfo> noticeDetail(@PathVariable Long id) {
        NoticeInfo n = noticeInfoMapper.selectById(id);
        if (n == null) {
            throw new BizException(404, "公告不存在");
        }
        boolean staff = RoleConstants.isTenantStaff(TenantContext.getRole())
                || RoleConstants.isPlatformAdmin(TenantContext.getRole());
        if (!staff) {
            if (n.getStatus() == null || n.getStatus() != 1) {
                throw new BizException(404, "公告不存在");
            }
            LocalDateTime pt = n.getPublishTime();
            if (pt != null && pt.isAfter(LocalDateTime.now())) {
                throw new BizException(404, "公告尚未到发布时间");
            }
        }
        fillPublishState(Collections.singletonList(n));
        return R.ok(n);
    }

    @PutMapping("/notice/{id}")
    public R<NoticeInfo> updateNotice(@PathVariable Long id, @RequestBody NoticeInfo body) {
        assertPropertyAdmin("网格员不可修改公告");
        NoticeInfo n = noticeInfoMapper.selectById(id);
        if (n == null) {
            throw new BizException(404, "公告不存在");
        }
        if (StringUtils.hasText(body.getTitle())) {
            n.setTitle(body.getTitle());
        }
        if (StringUtils.hasText(body.getContent())) {
            n.setContent(body.getContent());
        }
        if (body.getSummary() != null) {
            n.setSummary(body.getSummary());
        }
        if (body.getStatus() != null) {
            n.setStatus(body.getStatus());
        }
        if (body.getPublishTime() != null) {
            n.setPublishTime(body.getPublishTime());
        }
        noticeInfoMapper.updateById(n);
        fillPublishState(Collections.singletonList(n));
        return R.ok(n);
    }

    @DeleteMapping("/notice/{id}")
    public R<Void> deleteNotice(@PathVariable Long id) {
        assertPropertyAdmin("网格员不可删除公告");
        noticeInfoMapper.deleteById(id);
        return R.ok();
    }

    private void fillPublishState(List<NoticeInfo> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (NoticeInfo n : list) {
            if (n.getStatus() != null && n.getStatus() == 0) {
                n.setPublishState("已下架");
            } else if (n.getPublishTime() != null && n.getPublishTime().isAfter(now)) {
                n.setPublishState("定时待发");
            } else {
                n.setPublishState("已发布");
            }
        }
    }

    // ---------- 访客预约 ----------
    @PostMapping("/visit")
    public R<VisitorAppointment> createVisit(@RequestBody VisitorAppointment v) {
        v.setId(null);
        v.setTenantId(TenantContext.getTenantId());
        v.setUserId(TenantContext.getUserId());
        if (v.getStatus() == null) {
            v.setStatus("pending");
        }
        if (v.getVisitorCount() == null) {
            v.setVisitorCount(1);
        }
        v.setCreateTime(LocalDateTime.now());
        visitorMapper.insert(v);
        return R.ok(v);
    }

    @GetMapping("/visit")
    public R<List<VisitorAppointment>> listVisit(@RequestParam(required = false) String status) {
        String role = TenantContext.getRole();
        LambdaQueryWrapper<VisitorAppointment> q = new LambdaQueryWrapper<VisitorAppointment>()
                .eq(status != null, VisitorAppointment::getStatus, status)
                .orderByDesc(VisitorAppointment::getId);
        if ("resident".equals(role)) {
            q.eq(VisitorAppointment::getUserId, TenantContext.getUserId());
        }
        return R.ok(visitorMapper.selectList(q));
    }

    @PutMapping("/visit/{id}/audit")
    public R<VisitorAppointment> auditVisit(@PathVariable Long id, @RequestBody Map<String, String> body) {
        assertStaff();
        VisitorAppointment v = visitorMapper.selectById(id);
        if (v == null) {
            throw new BizException("预约不存在");
        }
        String st = body.getOrDefault("status", "approved");
        v.setStatus(st);
        v.setRemark(body.get("remark"));
        visitorMapper.updateById(v);
        return R.ok(v);
    }

    @PutMapping("/visit/{id}/checkin")
    public R<VisitorAppointment> checkin(@PathVariable Long id) {
        assertStaff();
        VisitorAppointment v = visitorMapper.selectById(id);
        if (v == null) {
            throw new BizException("预约不存在");
        }
        v.setStatus("checked_in");
        visitorMapper.updateById(v);
        return R.ok(v);
    }

    // ---------- 活动 + 报名 ----------
    @PostMapping("/activity")
    public R<CommunityActivity> createActivity(@RequestBody CommunityActivity a) {
        assertPropertyAdmin("网格员不可发布活动");
        contentAuditService.audit((a.getTitle() == null ? "" : a.getTitle()) + " " +
                (a.getContent() == null ? "" : a.getContent()));
        a.setId(null);
        a.setTenantId(TenantContext.getTenantId());
        a.setPublisherId(TenantContext.getUserId());
        a.setSignedCount(0);
        if (a.getStatus() == null) {
            a.setStatus(1);
        }
        a.setCreateTime(LocalDateTime.now());
        activityMapper.insert(a);
        return R.ok(a);
    }

    @GetMapping("/activity")
    public R<List<CommunityActivity>> listActivity() {
        return R.ok(activityMapper.selectList(new LambdaQueryWrapper<CommunityActivity>()
                .eq(CommunityActivity::getStatus, 1)
                .orderByDesc(CommunityActivity::getId)));
    }

    @PostMapping("/activity/{id}/signup")
    @Transactional
    public R<ActivitySignup> signup(@PathVariable Long id, @RequestBody(required = false) ActivitySignup body) {
        CommunityActivity act = activityMapper.selectById(id);
        if (act == null || act.getStatus() == null || act.getStatus() != 1) {
            throw new BizException("活动不存在或已下架");
        }
        if (act.getMaxPeople() != null && act.getSignedCount() != null
                && act.getSignedCount() >= act.getMaxPeople()) {
            throw new BizException("名额已满");
        }
        Long cnt = signupMapper.selectCount(new LambdaQueryWrapper<ActivitySignup>()
                .eq(ActivitySignup::getActivityId, id)
                .eq(ActivitySignup::getUserId, TenantContext.getUserId())
                .eq(ActivitySignup::getStatus, "signed"));
        if (cnt != null && cnt > 0) {
            throw new BizException("已报名，请勿重复");
        }
        ActivitySignup s = new ActivitySignup();
        s.setTenantId(TenantContext.getTenantId());
        s.setActivityId(id);
        s.setUserId(TenantContext.getUserId());
        s.setUserName(body == null ? null : body.getUserName());
        s.setPhone(body == null ? null : body.getPhone());
        s.setStatus("signed");
        s.setCreateTime(LocalDateTime.now());
        signupMapper.insert(s);
        act.setSignedCount((act.getSignedCount() == null ? 0 : act.getSignedCount()) + 1);
        activityMapper.updateById(act);
        return R.ok(s);
    }

    @GetMapping("/activity/{id}/signups")
    public R<List<ActivitySignup>> signups(@PathVariable Long id) {
        return R.ok(signupMapper.selectList(new LambdaQueryWrapper<ActivitySignup>()
                .eq(ActivitySignup::getActivityId, id)
                .eq(ActivitySignup::getStatus, "signed")
                .orderByDesc(ActivitySignup::getId)));
    }

    @PutMapping("/activity/signup/{signupId}/check")
    public R<ActivitySignup> checkSignup(@PathVariable Long signupId) {
        ActivitySignup s = signupMapper.selectById(signupId);
        if (s == null) {
            throw new BizException("报名记录不存在");
        }
        s.setStatus("checked");
        signupMapper.updateById(s);
        return R.ok(s);
    }

    // ---------- 情绪诉求（网格员处理） ----------
    @PostMapping("/appeal")
    public R<EmotionAppeal> createAppeal(@RequestBody EmotionAppeal a) {
        a.setId(null);
        a.setTenantId(TenantContext.getTenantId());
        a.setUserId(TenantContext.getUserId());
        if (a.getStatus() == null) {
            a.setStatus("pending");
        }
        a.setCreateTime(LocalDateTime.now());
        appealMapper.insert(a);
        return R.ok(a);
    }

    @GetMapping("/appeal")
    public R<List<EmotionAppeal>> listAppeal(@RequestParam(required = false) String status) {
        return R.ok(appealMapper.selectList(new LambdaQueryWrapper<EmotionAppeal>()
                .eq(status != null, EmotionAppeal::getStatus, status)
                .orderByDesc(EmotionAppeal::getId)));
    }

    @PutMapping("/appeal/{id}/handle")
    public R<EmotionAppeal> handleAppeal(@PathVariable Long id, @RequestBody EmotionAppeal body) {
        EmotionAppeal a = appealMapper.selectById(id);
        if (a == null) {
            throw new BizException("诉求不存在");
        }
        a.setStatus(body.getStatus() == null ? "processing" : body.getStatus());
        a.setHandleRemark(body.getHandleRemark());
        a.setHandlerId(TenantContext.getUserId());
        appealMapper.updateById(a);
        return R.ok(a);
    }
}
