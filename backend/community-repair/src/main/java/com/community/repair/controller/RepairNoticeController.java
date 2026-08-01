package com.community.repair.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.community.common.constant.RoleConstants;
import com.community.common.exception.BizException;
import com.community.common.feign.dto.AiImageRepairResponse;
import com.community.common.result.R;
import com.community.common.tenant.TenantContext;
import com.community.repair.entity.*;
import com.community.repair.mapper.*;
import com.community.repair.service.ActivityBizService;
import com.community.repair.service.ContentAuditService;
import com.community.repair.service.RepairNotifyService;
import com.community.repair.service.RepairWorkOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class RepairNoticeController {

    private final RepairOrderMapper repairOrderMapper;
    private final NoticeInfoMapper noticeInfoMapper;
    private final CommunityActivityMapper activityMapper;
    private final ActivitySignupMapper signupMapper;
    private final EmotionAppealMapper appealMapper;
    private final ContentAuditService contentAuditService;
    private final RepairNotifyService repairNotifyService;
    private final RepairWorkOrderService repairWorkOrderService;
    private final ActivityBizService activityBizService;
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
        if (!StringUtils.hasText(order.getOrderType())) {
            order.setOrderType("repair");
        }
        if ("report".equalsIgnoreCase(order.getOrderType())) {
            order.setOrderType("report");
        } else {
            order.setOrderType("repair");
        }
        if (order.getStatus() == null) {
            order.setStatus("pending");
        }
        if (StringUtils.hasText(order.getImageUrl())
                && (!StringUtils.hasText(order.getFacilityType()) || !StringUtils.hasText(order.getDamageLevel()))) {
            try {
                AiImageRepairResponse ai = contentAuditService.recognizeRepairImage(order.getImageUrl());
                if (!StringUtils.hasText(order.getFacilityType())) {
                    order.setFacilityType(ai.getFacilityType());
                }
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
        if (!StringUtils.hasText(order.getUrgency()) && StringUtils.hasText(order.getDamageLevel())) {
            order.setUrgency(mapUrgency(order.getDamageLevel()));
        }
        if (!StringUtils.hasText(order.getUrgency())) {
            order.setUrgency("中");
        }
        if (!StringUtils.hasText(order.getCategoryName()) && StringUtils.hasText(order.getFacilityType())) {
            order.setCategoryName(order.getFacilityType());
        }
        if (!StringUtils.hasText(order.getAiTag())
                && (StringUtils.hasText(order.getFacilityType()) || StringUtils.hasText(order.getDamageLevel()))) {
            order.setAiTag(joinTag(order.getFacilityType(), order.getDamageLevel()));
        }
        // 结构化位置：房屋档案 + locationHint（楼道/电梯厅等）
        repairWorkOrderService.fillOrderLocation(order, order.getHouseId(), order.getLocationHint());
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        repairOrderMapper.insert(order);
        // 回写识图日志关联工单，便于物业端追溯 AI 识别结果
        if (order.getId() != null && StringUtils.hasText(order.getImageUrl())) {
            try {
                jdbcTemplate.update(
                        "update ai_image_log set order_id=? where image_url=? and (order_id is null or order_id=0) "
                                + "order by id desc limit 5",
                        order.getId(), order.getImageUrl());
            } catch (Exception ignored) {
            }
        }
        // 已取消自动分派：新工单保持未指派，由物业在「工单分派」手动指派
        repairNotifyService.notifyStaffNewOrder(order);
        return R.ok(order);
    }

    private static String mapUrgency(String damageLevel) {
        if (damageLevel == null) return "中";
        if (damageLevel.contains("严重") || damageLevel.contains("重度") || damageLevel.contains("高")) return "高";
        if (damageLevel.contains("轻微") || damageLevel.contains("轻度") || damageLevel.contains("低")) return "低";
        return "中";
    }

    @GetMapping("/repair/orders")
    public R<List<RepairOrder>> listRepair(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean mine,
            @RequestParam(required = false) String orderType,
            @RequestParam(required = false) String urgency,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Boolean unassigned,
            @RequestParam(required = false) Boolean emotionAlert) {
        Long tenantId = requireTenant();
        LambdaQueryWrapper<RepairOrder> q = new LambdaQueryWrapper<RepairOrder>()
                .eq(RepairOrder::getTenantId, tenantId)
                .eq(status != null, RepairOrder::getStatus, status)
                .orderByDesc(RepairOrder::getId);
        if (StringUtils.hasText(orderType)) {
            if ("repair".equalsIgnoreCase(orderType)) {
                q.and(w -> w.eq(RepairOrder::getOrderType, "repair").or().isNull(RepairOrder::getOrderType));
            } else {
                q.eq(RepairOrder::getOrderType, orderType);
            }
        }
        if (StringUtils.hasText(urgency)) {
            q.and(w -> w.eq(RepairOrder::getUrgency, urgency).or().eq(RepairOrder::getDamageLevel, urgency));
        }
        if (categoryId != null) {
            q.eq(RepairOrder::getCategoryId, categoryId);
        }
        if (Boolean.TRUE.equals(unassigned)) {
            q.isNull(RepairOrder::getHandlerId);
        }
        if (Boolean.TRUE.equals(emotionAlert)) {
            q.isNotNull(RepairOrder::getEmotionCaseId);
        }
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
        enrichOrders(list);
        return R.ok(list);
    }

    private void enrichOrders(List<RepairOrder> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        Long tid = TenantContext.getTenantId();
        for (RepairOrder o : list) {
            String ot = o.getOrderType() == null ? "repair" : o.getOrderType();
            o.setOrderType(ot);
            o.setOrderTypeLabel("report".equals(ot) ? "报事" : "报修");
            o.setEmotionAlert(o.getEmotionCaseId() != null);
            o.setFromFeedback(o.getFeedbackId() != null);
            if (!StringUtils.hasText(o.getUrgency()) && StringUtils.hasText(o.getDamageLevel())) {
                o.setUrgency(mapUrgency(o.getDamageLevel()));
            }
            try {
                if (StringUtils.hasText(o.getImageUrl()) && tid != null) {
                    Integer n = jdbcTemplate.queryForObject(
                            "select count(1) from ai_image_log where tenant_id=? and image_url=?",
                            Integer.class, tid, o.getImageUrl());
                    o.setAiImageLogCount(n == null ? 0 : n);
                } else {
                    o.setAiImageLogCount(0);
                }
            } catch (Exception e) {
                o.setAiImageLogCount(0);
            }
            repairWorkOrderService.enrichLocationDisplay(o);
        }
    }

    @GetMapping("/repair/orders/{id}")
    public R<RepairOrder> detail(@PathVariable Long id) {
        RepairOrder o = requireOrderInTenant(id);
        assertCanViewOrder(o);
        fillHandlerNames(Collections.singletonList(o));
        enrichOrders(Collections.singletonList(o));
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

    /** 物业删除报修工单 */
    @DeleteMapping("/repair/orders/{id}")
    public R<Void> deleteRepair(@PathVariable Long id) {
        assertStaff();
        RepairOrder o = requireOrderInTenant(id);
        repairOrderMapper.deleteById(o.getId());
        return R.ok();
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

    // ---------- 报修分类 / 分派规则 / 统计 ----------
    @GetMapping("/repair/categories")
    public R<List<Map<String, Object>>> listCategories(
            @RequestParam(required = false) String orderType,
            @RequestParam(required = false) Boolean enabledOnly) {
        return R.ok(repairWorkOrderService.listCategories(orderType, enabledOnly));
    }

    @PostMapping("/repair/categories")
    public R<Map<String, Object>> saveCategory(@RequestBody Map<String, Object> body) {
        return R.ok(repairWorkOrderService.saveCategory(body));
    }

    @DeleteMapping("/repair/categories/{id}")
    public R<Void> deleteCategory(@PathVariable Long id) {
        repairWorkOrderService.deleteCategory(id);
        return R.ok();
    }

    @GetMapping("/repair/stats")
    public R<Map<String, Object>> repairStats(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return R.ok(repairWorkOrderService.stats(startDate, endDate));
    }

    /** 情绪调解触发报事工单（AI 服务或内部调用） */
    @PostMapping("/repair/orders/from-emotion")
    public R<Map<String, Object>> fromEmotion(@RequestBody Map<String, Object> body) {
        requireTenant();
        Long caseId = body.get("emotionCaseId") == null ? null : Long.valueOf(String.valueOf(body.get("emotionCaseId")));
        Long userId = body.get("userId") == null ? TenantContext.getUserId()
                : Long.valueOf(String.valueOf(body.get("userId")));
        Long orderId = repairWorkOrderService.createReportFromEmotion(
                caseId,
                userId,
                body.get("title") == null ? null : String.valueOf(body.get("title")),
                body.get("content") == null ? null : String.valueOf(body.get("content")),
                body.get("sentiment") == null ? null : String.valueOf(body.get("sentiment")),
                body.get("emotionTags") == null ? null : String.valueOf(body.get("emotionTags")),
                body.get("urgency") == null ? null : String.valueOf(body.get("urgency")));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orderId", orderId);
        return R.ok(m);
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
        notice.setAiAuditPass(1);
        if (notice.getStatus() == null) {
            notice.setStatus(1);
        }
        Object summaryObj = audit.get("summary");
        if (!StringUtils.hasText(notice.getSummary()) && summaryObj != null) {
            String summary = String.valueOf(summaryObj);
            if (summary.length() > 100) {
                summary = summary.substring(0, 97) + "...";
            }
            notice.setSummary(summary);
        }
        if (StringUtils.hasText(notice.getSummary()) && notice.getSummary().length() > 100) {
            notice.setSummary(notice.getSummary().substring(0, 97) + "...");
        }
        LocalDateTime now = LocalDateTime.now();
        notice.setCreateTime(now);
        if (notice.getPublishTime() == null) {
            notice.setPublishTime(now);
        }
        if (notice.getPushCount() == null) {
            notice.setPushCount(0);
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

    // ---------- 活动 + 报名 ----------
    @PostMapping("/activity")
    public R<CommunityActivity> createActivity(@RequestBody CommunityActivity a) {
        assertPropertyAdmin("网格员不可发布活动");
        Map<String, Object> audit = contentAuditService.audit((a.getTitle() == null ? "" : a.getTitle()) + " " +
                (a.getContent() == null ? "" : a.getContent()));
        a.setId(null);
        a.setTenantId(TenantContext.getTenantId());
        a.setPublisherId(TenantContext.getUserId());
        a.setSignedCount(0);
        a.setAiAuditPass(1);
        if (a.getPushCount() == null) {
            a.setPushCount(0);
        }
        if (!StringUtils.hasText(a.getSummary()) && audit.get("summary") != null) {
            String summary = String.valueOf(audit.get("summary"));
            if (summary.length() > 100) {
                summary = summary.substring(0, 97) + "...";
            }
            a.setSummary(summary);
        }
        if (StringUtils.hasText(a.getSummary()) && a.getSummary().length() > 100) {
            a.setSummary(a.getSummary().substring(0, 97) + "...");
        }
        if (a.getStatus() == null) {
            a.setStatus(1);
        }
        a.setCreateTime(LocalDateTime.now());
        if (a.getAllowCancel() == null) {
            a.setAllowCancel(1);
        }
        if (a.getStartTime() != null && a.getEndTime() != null && a.getEndTime().isBefore(a.getStartTime())) {
            throw new BizException(400, "结束时间不能早于开始时间");
        }
        activityMapper.insert(a);
        return R.ok(a);
    }

    @GetMapping("/activity")
    public R<List<Map<String, Object>>> listActivity(
            @RequestParam(defaultValue = "false") boolean all,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword) {
        boolean staff = RoleConstants.isTenantStaff(TenantContext.getRole())
                || RoleConstants.isPlatformAdmin(TenantContext.getRole());
        LambdaQueryWrapper<CommunityActivity> q = new LambdaQueryWrapper<CommunityActivity>()
                .orderByDesc(CommunityActivity::getId);
        if (!(all && staff)) {
            q.eq(CommunityActivity::getStatus, 1);
        }
        if (StringUtils.hasText(category)) {
            q.eq(CommunityActivity::getCategory, category);
        }
        if (StringUtils.hasText(keyword)) {
            q.and(w -> w.like(CommunityActivity::getTitle, keyword)
                    .or().like(CommunityActivity::getContent, keyword)
                    .or().like(CommunityActivity::getTags, keyword));
        }
        List<CommunityActivity> list = activityMapper.selectList(q);
        List<Map<String, Object>> out = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (CommunityActivity a : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("title", a.getTitle());
            m.put("category", a.getCategory());
            m.put("coverUrl", a.getCoverUrl());
            m.put("content", a.getContent());
            m.put("summary", a.getSummary());
            m.put("tags", a.getTags());
            m.put("targetRule", a.getTargetRule());
            m.put("location", a.getLocation());
            m.put("startTime", a.getStartTime() == null ? null : a.getStartTime().toString().replace('T', ' '));
            m.put("endTime", a.getEndTime() == null ? null : a.getEndTime().toString().replace('T', ' '));
            m.put("signupEnd", a.getSignupEnd() == null ? null : a.getSignupEnd().toString().replace('T', ' '));
            m.put("maxPeople", a.getMaxPeople());
            m.put("allowCancel", a.getAllowCancel() == null ? 1 : a.getAllowCancel());
            m.put("signedCount", a.getSignedCount());
            m.put("status", a.getStatus());
            m.put("statusLabel", a.getStatus() != null && a.getStatus() == 1 ? "上架" : "下架");
            m.put("aiAuditPass", a.getAiAuditPass());
            m.put("pushCount", a.getPushCount());
            String phase = "未开始";
            if (a.getEndTime() != null && !a.getEndTime().isAfter(now)) phase = "已结束";
            else if (a.getStartTime() != null && !a.getStartTime().isAfter(now)) phase = "进行中";
            m.put("phase", phase);
            m.put("createTime", a.getCreateTime() == null ? null : a.getCreateTime().toString().replace('T', ' '));
            out.add(m);
        }
        return R.ok(out);
    }

    @PutMapping("/activity/{id}")
    public R<CommunityActivity> updateActivity(@PathVariable Long id, @RequestBody CommunityActivity body) {
        assertPropertyAdmin("网格员不可编辑活动");
        CommunityActivity a = activityMapper.selectById(id);
        if (a == null || !Objects.equals(a.getTenantId(), TenantContext.getTenantId())) {
            throw new BizException("活动不存在");
        }
        if (StringUtils.hasText(body.getTitle()) || body.getContent() != null) {
            String title = StringUtils.hasText(body.getTitle()) ? body.getTitle() : a.getTitle();
            String content = body.getContent() != null ? body.getContent() : a.getContent();
            Map<String, Object> audit = contentAuditService.audit(
                    (title == null ? "" : title) + " " + (content == null ? "" : content));
            a.setAiAuditPass(1);
            if (!StringUtils.hasText(body.getSummary()) && audit.get("summary") != null) {
                String summary = String.valueOf(audit.get("summary"));
                if (summary.length() > 100) summary = summary.substring(0, 97) + "...";
                a.setSummary(summary);
            }
        }
        if (StringUtils.hasText(body.getTitle())) a.setTitle(body.getTitle());
        if (body.getCategory() != null) a.setCategory(body.getCategory());
        if (body.getCoverUrl() != null) a.setCoverUrl(body.getCoverUrl());
        if (body.getContent() != null) a.setContent(body.getContent());
        if (body.getSummary() != null) {
            String summary = body.getSummary();
            if (summary.length() > 100) summary = summary.substring(0, 97) + "...";
            a.setSummary(summary);
        }
        if (body.getTags() != null) a.setTags(body.getTags());
        if (body.getTargetRule() != null) a.setTargetRule(body.getTargetRule());
        if (body.getLocation() != null) a.setLocation(body.getLocation());
        if (body.getStartTime() != null) a.setStartTime(body.getStartTime());
        if (body.getEndTime() != null) a.setEndTime(body.getEndTime());
        if (body.getSignupEnd() != null) a.setSignupEnd(body.getSignupEnd());
        if (body.getMaxPeople() != null) a.setMaxPeople(body.getMaxPeople());
        if (body.getAllowCancel() != null) a.setAllowCancel(body.getAllowCancel());
        if (body.getStatus() != null) a.setStatus(body.getStatus());
        if (a.getStartTime() != null && a.getEndTime() != null && a.getEndTime().isBefore(a.getStartTime())) {
            throw new BizException(400, "结束时间不能早于开始时间");
        }
        activityMapper.updateById(a);
        return R.ok(a);
    }

    @PutMapping("/activity/{id}/status")
    public R<CommunityActivity> activityStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        assertPropertyAdmin("网格员不可上下架活动");
        CommunityActivity a = activityMapper.selectById(id);
        if (a == null || !Objects.equals(a.getTenantId(), TenantContext.getTenantId())) {
            throw new BizException("活动不存在");
        }
        a.setStatus(body.get("status") == null ? 0 : Integer.parseInt(String.valueOf(body.get("status"))));
        activityMapper.updateById(a);
        return R.ok(a);
    }

    @DeleteMapping("/activity/{id}")
    public R<Void> deleteActivity(@PathVariable Long id) {
        assertPropertyAdmin("网格员不可删除活动");
        activityBizService.deleteActivity(id);
        return R.ok();
    }

    @PostMapping("/activity/{id}/signup")
    @Transactional
    public R<ActivitySignup> signup(@PathVariable Long id, @RequestBody(required = false) ActivitySignup body) {
        CommunityActivity act = activityMapper.selectById(id);
        if (act == null || act.getStatus() == null || act.getStatus() != 1) {
            throw new BizException("活动不存在或已下架");
        }
        if (act.getSignupEnd() != null && act.getSignupEnd().isBefore(LocalDateTime.now())) {
            throw new BizException("报名已截止");
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
        if (!StringUtils.hasText(s.getUserName()) || !StringUtils.hasText(s.getPhone())) {
            Long uid = TenantContext.getUserId();
            if (uid != null) {
                try {
                    // sys_user 无 real_name，仅用 nickname + phone
                    Map<String, Object> u = jdbcTemplate.queryForMap(
                            "select nickname, phone from sys_user where id=?", uid);
                    if (!StringUtils.hasText(s.getUserName()) && u.get("nickname") != null) {
                        s.setUserName(String.valueOf(u.get("nickname")).trim());
                    }
                    if (!StringUtils.hasText(s.getPhone()) && u.get("phone") != null) {
                        s.setPhone(String.valueOf(u.get("phone")).trim());
                    }
                } catch (Exception e) {
                    // ignore lookup failure; still allow signup with body fields
                }
            }
        }
        s.setStatus("signed");
        s.setCreateTime(LocalDateTime.now());
        signupMapper.insert(s);
        act.setSignedCount((act.getSignedCount() == null ? 0 : act.getSignedCount()) + 1);
        activityMapper.updateById(act);
        activityBizService.syncSignupToSchedule(id, TenantContext.getUserId());
        return R.ok(s);
    }

    @GetMapping("/activity/{id}/signups")
    public R<List<ActivitySignup>> signups(@PathVariable Long id) {
        return R.ok(signupMapper.selectList(new LambdaQueryWrapper<ActivitySignup>()
                .eq(ActivitySignup::getActivityId, id)
                .in(ActivitySignup::getStatus, "signed", "checked")
                .orderByDesc(ActivitySignup::getId)));
    }

    @GetMapping("/activity/signups")
    public R<List<Map<String, Object>>> allSignups(
            @RequestParam(required = false) Long activityId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return R.ok(activityBizService.listAllSignups(activityId, status, keyword));
    }

    @PutMapping("/activity/signup/{signupId}/cancel")
    public R<Void> cancelSignupAdmin(
            @PathVariable Long signupId,
            @RequestBody(required = false) Map<String, String> body) {
        activityBizService.cancelSignup(signupId, body == null ? null : body.get("remark"));
        return R.ok();
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

    @GetMapping("/activity/categories")
    public R<List<Map<String, Object>>> activityCategories(
            @RequestParam(required = false) Boolean enabledOnly) {
        return R.ok(activityBizService.listCategories(enabledOnly));
    }

    @PostMapping("/activity/categories")
    public R<Map<String, Object>> saveActivityCategory(@RequestBody Map<String, Object> body) {
        return R.ok(activityBizService.saveCategory(body));
    }

    @DeleteMapping("/activity/categories/{id}")
    public R<Void> deleteActivityCategory(@PathVariable Long id) {
        activityBizService.deleteCategory(id);
        return R.ok();
    }

    @GetMapping("/activity/stats")
    public R<Map<String, Object>> activityStats() {
        return R.ok(activityBizService.stats());
    }

    @GetMapping("/activity/platform/list")
    public R<List<Map<String, Object>>> platformActivityList(
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category) {
        return R.ok(activityBizService.listPlatformActivities(tenantId, keyword, category));
    }

    @PutMapping("/activity/platform/{id}/status")
    public R<Map<String, Object>> platformActivityStatus(
            @PathVariable Long id, @RequestBody Map<String, Object> body) {
        int status = body.get("status") == null ? 1 : Integer.parseInt(String.valueOf(body.get("status")));
        return R.ok(activityBizService.platformSetActivityStatus(id, status));
    }

    @PostMapping("/activity/{id}/target-push")
    public R<Map<String, Object>> activityTargetPush(
            @PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        String rule = body == null || body.get("targetRule") == null ? null : String.valueOf(body.get("targetRule"));
        return R.ok(activityBizService.targetPush(id, rule));
    }

    // ---------- 历史 AI 倾诉工单（与朋友圈动态分离；动态见 AppealController） ----------
    @GetMapping("/appeal/tickets")
    public R<List<Map<String, Object>>> listAppealTickets(@RequestParam(required = false) String status) {
        boolean staff = RoleConstants.isTenantStaff(TenantContext.getRole())
                || RoleConstants.isPlatformAdmin(TenantContext.getRole());
        LambdaQueryWrapper<EmotionAppeal> q = new LambdaQueryWrapper<EmotionAppeal>()
                .in(EmotionAppeal::getStatus, List.of("pending", "processing", "done"))
                .eq(status != null && !status.isBlank(), EmotionAppeal::getStatus, status)
                .orderByDesc(EmotionAppeal::getId);
        if (!staff) {
            q.eq(EmotionAppeal::getUserId, TenantContext.getUserId());
        }
        List<EmotionAppeal> list = appealMapper.selectList(q);
        List<Map<String, Object>> out = new ArrayList<>();
        for (EmotionAppeal a : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("userId", a.getUserId());
            m.put("content", a.getContent());
            m.put("sentiment", a.getSentiment());
            m.put("score", a.getScore());
            m.put("status", a.getStatus());
            m.put("statusLabel", appealStatusLabel(a.getStatus()));
            m.put("handleRemark", a.getHandleRemark());
            m.put("handlerId", a.getHandlerId());
            m.put("createTime", a.getCreateTime() == null ? null : a.getCreateTime().toString().replace('T', ' '));
            String name = null;
            try {
                name = jdbcTemplate.queryForObject("select nickname from sys_user where id=?", String.class, a.getUserId());
            } catch (Exception ignored) {
            }
            m.put("userName", name == null ? ("住户#" + a.getUserId()) : name);
            out.add(m);
        }
        return R.ok(out);
    }

    @PutMapping("/appeal/{id}/handle")
    public R<EmotionAppeal> handleAppeal(@PathVariable Long id, @RequestBody EmotionAppeal body) {
        if (!RoleConstants.isTenantStaff(TenantContext.getRole())
                && !RoleConstants.isPlatformAdmin(TenantContext.getRole())) {
            throw new BizException(403, "仅物业人员可处理");
        }
        EmotionAppeal a = appealMapper.selectById(id);
        if (a == null) {
            throw new BizException("诉求不存在");
        }
        String st = body.getStatus() == null ? "processing" : body.getStatus();
        if ("done".equals(st) || "closed".equals(st) || "resolved".equals(st)) st = "done";
        if ("pending".equals(st)) st = "pending";
        if (!"pending".equals(st) && !"processing".equals(st) && !"done".equals(st)) {
            st = "processing";
        }
        a.setStatus(st);
        a.setHandleRemark(body.getHandleRemark());
        a.setHandlerId(TenantContext.getUserId());
        appealMapper.updateById(a);
        return R.ok(a);
    }

    private String appealStatusLabel(String s) {
        if ("pending".equals(s)) return "待处理";
        if ("processing".equals(s)) return "处理中";
        if ("done".equals(s) || "closed".equals(s) || "resolved".equals(s)) return "已完结";
        return s == null ? "待处理" : s;
    }
}
