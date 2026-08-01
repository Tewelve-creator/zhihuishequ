package com.community.ai.biz;

import com.alibaba.fastjson2.JSON;
import com.community.common.constant.RoleConstants;
import com.community.common.exception.BizException;
import com.community.common.feign.dto.AiBizConfirmRequest;
import com.community.common.feign.dto.AiChatResponse;
import com.community.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * 智能管家业务指令分支：意图识别 → 槽位补齐 → 确认 → 执行（禁止未确认写库）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiBizButlerService {

    private final IntentRecognizer intentRecognizer;
    private final SlotExtractor slotExtractor;
    private final BizSessionStore sessionStore;
    private final RepairApiClient repairApiClient;
    private final JdbcTemplate jdbcTemplate;

    /**
     * @return empty = 走 RAG；有值 = 业务分支已处理
     */
    public Optional<AiChatResponse> tryHandle(String question) {
        Long tid = TenantContext.getTenantId();
        Long uid = TenantContext.getUserId();
        if (tid == null || uid == null) {
            return Optional.empty();
        }

        BizSession session = sessionStore.get(tid, uid);

        // 取消
        if (intentRecognizer.isCancel(question)) {
            sessionStore.clear(tid, uid);
            AiChatResponse r = new AiChatResponse();
            r.setMode("BIZ_CANCEL");
            r.setNeedConfirm(false);
            r.setAnswer("好的，已取消本次业务办理。您可以继续咨询制度，或重新发起办理指令。");
            r.setSource("biz_cancel");
            r.setProvider("butler");
            r.setTransferHuman(false);
            if (session != null) {
                writeOpLog(session, "CANCELLED", null, "用户取消");
            }
            return Optional.of(r);
        }

        // 补槽/待确认时：用户明确换了业务意图（如访客登记中途说「查一下欠费」）→ 切换
        if (session != null && ("collecting".equals(session.getStatus())
                || "awaiting_confirm".equals(session.getStatus()))) {
            Optional<BizIntent> switched = intentRecognizer.recognize(question);
            if (switched.isPresent() && !switched.get().name().equals(session.getIntent())) {
                writeOpLog(session, "CANCELLED", null, "用户切换意图:" + switched.get().name());
                sessionStore.clear(tid, uid);
                return Optional.of(startIntent(switched.get(), question, tid, uid));
            }
        }

        // 会话中：等待确认
        if (session != null && "awaiting_confirm".equals(session.getStatus())) {
            if (intentRecognizer.isConfirm(question)) {
                return Optional.of(executeConfirm(session));
            }
            AiChatResponse r = buildConfirmResponse(session);
            r.setAnswer("请确认下方信息是否正确。点「确认办理」或回复「确认」继续；回复「取消」放弃。\n\n"
                    + r.getAnswer());
            return Optional.of(r);
        }

        // 会话中：继续补槽
        if (session != null && "collecting".equals(session.getStatus())) {
            BizIntent intent = BizIntent.valueOf(session.getIntent());
            Map<String, Object> slots = slotExtractor.extract(intent, question, session.getSlots());
            injectHouse(slots);
            session.setSlots(slots);
            return Optional.of(advance(session, intent));
        }

        // 新意图
        Optional<BizIntent> intentOpt = intentRecognizer.recognize(question);
        if (intentOpt.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(startIntent(intentOpt.get(), question, tid, uid));
    }

    private AiChatResponse startIntent(BizIntent intent, String question, Long tid, Long uid) {
        BizSession neu = new BizSession();
        neu.setIntent(intent.name());
        neu.setStatus("collecting");
        neu.setTenantId(tid);
        neu.setUserId(uid);
        neu.setHouseId(TenantContext.getHouseId());
        Map<String, Object> slots = slotExtractor.extract(intent, question, new LinkedHashMap<>());
        injectHouse(slots);
        neu.setSlots(slots);
        return advance(neu, intent);
    }

    public AiChatResponse confirm(AiBizConfirmRequest req) {
        if (req == null || !StringUtils.hasText(req.getActionToken())) {
            throw new BizException("缺少确认令牌");
        }
        BizSession session = sessionStore.getByToken(req.getActionToken());
        if (session == null) {
            throw new BizException("确认已失效，请重新发起办理");
        }
        if (!Objects.equals(session.getTenantId(), TenantContext.getTenantId())
                || !Objects.equals(session.getUserId(), TenantContext.getUserId())) {
            throw new BizException(403, "无权确认他人操作");
        }
        if (!"awaiting_confirm".equals(session.getStatus())) {
            throw new BizException("当前状态不可确认");
        }
        return executeConfirm(session);
    }

    public AiChatResponse cancel(AiBizConfirmRequest req) {
        Long tid = TenantContext.getTenantId();
        Long uid = TenantContext.getUserId();
        BizSession session = null;
        if (req != null && StringUtils.hasText(req.getActionToken())) {
            session = sessionStore.getByToken(req.getActionToken());
        }
        if (session == null) {
            session = sessionStore.get(tid, uid);
        }
        sessionStore.clear(tid, uid);
        if (session != null) {
            writeOpLog(session, "CANCELLED", null, "用户取消");
        }
        AiChatResponse r = new AiChatResponse();
        r.setMode("BIZ_CANCEL");
        r.setNeedConfirm(false);
        r.setAnswer("已取消本次操作。");
        r.setSource("biz_cancel");
        r.setProvider("butler");
        return r;
    }

    public List<Map<String, Object>> listOpLogs(int limit) {
        Long tid = TenantContext.getTenantId();
        if (tid == null) throw new BizException(40001, "缺少租户");
        if (!RoleConstants.isTenantStaff(TenantContext.getRole())
                && !RoleConstants.isPlatformAdmin(TenantContext.getRole())) {
            throw new BizException(403, "仅物业可查看业务操作日志");
        }
        int lim = Math.min(Math.max(limit, 1), 100);
        try {
            return jdbcTemplate.queryForList(
                    "select id, tenant_id as tenantId, user_id as userId, house_id as houseId, intent, "
                            + "status, source, slots_json as slotsJson, result_json as resultJson, "
                            + "create_time as createTime from ai_biz_op_log where tenant_id=? "
                            + "order by id desc limit " + lim,
                    tid);
        } catch (Exception e) {
            return List.of();
        }
    }

    private AiChatResponse advance(BizSession session, BizIntent intent) {
        List<String> missing = slotExtractor.missingRequired(intent, session.getSlots());
        if (!missing.isEmpty()) {
            session.setStatus("collecting");
            session.setActionToken(null);
            sessionStore.save(session);
            String ask = slotExtractor.askPrompt(missing.get(0));
            AiChatResponse r = new AiChatResponse();
            r.setMode("BIZ_ASK");
            r.setIntent(intent.name());
            r.setNeedConfirm(false);
            r.setMissingFields(missing);
            r.setSlots(session.getSlots());
            r.setAnswer("好的，我来帮您" + intent.getLabel() + "。\n" + ask);
            r.setSource("biz_ask");
            r.setProvider("butler");
            r.setTransferHuman(false);
            return r;
        }

        // 参数齐全：查询类直接执行；写操作必须确认后才能落库
        if (!intent.isWriteOp()) {
            return executeConfirm(session);
        }

        String token = sessionStore.newToken();
        session.setStatus("awaiting_confirm");
        session.setActionToken(token);
        sessionStore.save(session);
        return buildConfirmResponse(session);
    }

    private AiChatResponse buildConfirmResponse(BizSession session) {
        BizIntent intent = BizIntent.valueOf(session.getIntent());
        List<Map<String, String>> preview = slotExtractor.buildPreview(intent, session.getSlots());
        StringBuilder sb = new StringBuilder();
        sb.append("已整理您的「").append(intent.getLabel()).append("」信息，请确认：\n");
        for (Map<String, String> row : preview) {
            sb.append("· ").append(row.get("label")).append("：").append(row.get("value")).append("\n");
        }
        sb.append("\n确认后才会提交；可点下方按钮或回复「确认」/「取消」。");

        AiChatResponse r = new AiChatResponse();
        r.setMode("BIZ_CONFIRM");
        r.setIntent(intent.name());
        r.setNeedConfirm(true);
        r.setActionToken(session.getActionToken());
        r.setSlots(session.getSlots());
        r.setPreview(preview);
        r.setAnswer(sb.toString().trim());
        r.setSource("biz_confirm");
        r.setProvider("butler");
        r.setTransferHuman(false);
        return r;
    }

    private AiChatResponse executeConfirm(BizSession session) {
        BizIntent intent = BizIntent.valueOf(session.getIntent());
        Long tid = session.getTenantId();
        Long uid = session.getUserId();
        Object data;
        try {
            data = dispatch(intent, session.getSlots());
        } catch (BizException e) {
            writeOpLog(session, "FAILED", null, e.getMessage());
            sessionStore.clear(tid, uid);
            AiChatResponse r = new AiChatResponse();
            r.setMode("BIZ_RESULT");
            r.setIntent(intent.name());
            r.setNeedConfirm(false);
            r.setAnswer("办理失败：" + e.getMessage());
            r.setSource("biz_fail");
            r.setProvider("butler");
            return r;
        } catch (Exception e) {
            writeOpLog(session, "FAILED", null, e.getMessage());
            sessionStore.clear(tid, uid);
            throw e;
        }
        writeOpLog(session, "CONFIRMED", data, null);
        sessionStore.clear(tid, uid);

        AiChatResponse r = new AiChatResponse();
        r.setMode("BIZ_RESULT");
        r.setIntent(intent.name());
        r.setNeedConfirm(false);
        r.setData(data);
        r.setAnswer(RepairApiClient.summarizeResult(intent, data));
        r.setSource("biz_done");
        r.setProvider("butler");
        r.setTransferHuman(false);
        return r;
    }

    private Object dispatch(BizIntent intent, Map<String, Object> slots) {
        Long houseId = TenantContext.getHouseId();
        if (houseId == null && slots.get("houseId") != null) {
            houseId = Long.valueOf(String.valueOf(slots.get("houseId")));
            TenantContext.setHouseId(houseId);
        }
        return switch (intent) {
            case VISIT_CREATE -> {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("visitorName", slots.get("visitorName"));
                body.put("visitorPhone", emptyToNull(slots.get("visitorPhone")));
                body.put("driveFlag", Boolean.TRUE.equals(asBool(slots.get("driveFlag"))));
                body.put("plateNo", emptyToNull(slots.get("plateNo")));
                body.put("visitTime", slots.get("visitTime"));
                body.put("expireTime", emptyToNull(slots.get("expireTime")));
                body.put("visitReason", slots.get("visitReason") != null ? slots.get("visitReason") : "探亲");
                body.put("houseId", houseId);
                body.put("remark", "[AI管家]");
                yield repairApiClient.createVisit(body);
            }
            case REPAIR_CREATE -> {
                Map<String, Object> body = new LinkedHashMap<>();
                String title = str(slots.get("title"));
                String desc = str(slots.get("description"));
                if (!StringUtils.hasText(title)) {
                    title = StringUtils.hasText(desc) && desc.length() > 20 ? desc.substring(0, 20) : desc;
                }
                body.put("title", title);
                body.put("description", StringUtils.hasText(desc) ? desc : title);
                body.put("facilityType", slots.get("facilityType"));
                body.put("damageLevel", slots.get("damageLevel"));
                body.put("imageUrl", emptyToNull(slots.get("imageUrl")));
                body.put("aiTag", "AI管家");
                yield repairApiClient.createRepair(body);
            }
            case FEE_UNPAID -> repairApiClient.listUnpaidBills(houseId);
            case CAR_LIST -> repairApiClient.listCars(houseId);
            case VISIT_LIST -> repairApiClient.listApprovedVisits();
        };
    }

    private void injectHouse(Map<String, Object> slots) {
        Long hid = TenantContext.getHouseId();
        if (hid != null) {
            slots.put("houseId", hid);
        }
    }

    private void writeOpLog(BizSession session, String status, Object result, String err) {
        try {
            jdbcTemplate.update(
                    "insert into ai_biz_op_log(tenant_id,user_id,house_id,intent,action_token,slots_json,result_json,status,source,create_time) "
                            + "values(?,?,?,?,?,?,?,?,?,now())",
                    session.getTenantId(),
                    session.getUserId(),
                    session.getHouseId(),
                    session.getIntent(),
                    session.getActionToken(),
                    JSON.toJSONString(session.getSlots()),
                    err != null ? err : JSON.toJSONString(result),
                    status,
                    "ai_butler");
        } catch (Exception e) {
            log.warn("ai_biz_op_log skip: {}", e.getMessage());
        }
    }

    private Object emptyToNull(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : s;
    }

    private Boolean asBool(Object o) {
        if (o instanceof Boolean b) return b;
        if (o == null) return false;
        return "true".equalsIgnoreCase(String.valueOf(o)) || "1".equals(String.valueOf(o));
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
