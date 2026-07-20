package com.community.ai.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.community.ai.entity.AiChatLog;
import com.community.ai.entity.AiKnowledge;
import com.community.ai.entity.AiScheduleRemind;
import com.community.ai.mapper.AiChatLogMapper;
import com.community.ai.mapper.AiKnowledgeMapper;
import com.community.ai.mapper.AiScheduleRemindMapper;
import com.community.ai.mock.MockAiClient;
import com.community.ai.mock.ThirdPartyAiClient;
import com.community.common.constant.RoleConstants;
import com.community.common.exception.BizException;
import com.community.common.feign.dto.*;
import com.community.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 第三阶段 AI 能力：Mock 优先，预留 http 真模型；租户知识库隔离 + Redis/Sentinel 限流
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    private final AiKnowledgeMapper knowledgeMapper;
    private final AiChatLogMapper chatLogMapper;
    private final AiScheduleRemindMapper scheduleRemindMapper;
    private final StringRedisTemplate redisTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final MockAiClient mockAiClient;
    private final ThirdPartyAiClient thirdPartyAiClient;

    @Value("${ai.provider:mock}")
    private String provider;

    @Value("${ai.daily-quota:200}")
    private int dailyQuota;

    /* ===================== 1. 知识库 + 问答 ===================== */

    @SentinelResource(value = "ai_chat", blockHandler = "chatBlock")
    public AiChatResponse chat(AiChatRequest req) {
        fillContext(req.getTenantId(), req.getUserId());
        requireTenant();
        // 物业端不做业主咨询对话，仅居民可调用
        if (RoleConstants.isTenantStaff(TenantContext.getRole())) {
            throw new BizException(403, "物业端请使用知识库/审核工具，勿使用业主问答");
        }
        long start = System.currentTimeMillis();
        checkQuota("chat");
        String question = req.getQuestion() == null ? "" : req.getQuestion().trim();
        if (!StringUtils.hasText(question)) {
            throw new BizException(400, "问题不能为空");
        }

        Optional<String> kb = findKbAnswer(question);
        Map<String, Object> raw;
        if (kb.isPresent()) {
            raw = mockAiClient.chat(question, kb);
        } else if (useHttp()) {
            try {
                String answer = thirdPartyAiClient.chatCompletion(
                        "你是智慧社区助手，请简洁回答本社区居民问题。", question);
                raw = new LinkedHashMap<>();
                raw.put("answer", answer);
                raw.put("source", "llm_http");
                raw.put("provider", "http");
                raw.put("transferHuman", question.contains("人工") || question.contains("投诉"));
            } catch (Exception e) {
                log.warn("http llm fallback mock: {}", e.getMessage());
                raw = mockAiClient.chat(question, Optional.empty());
            }
        } else {
            raw = mockAiClient.chat(question, Optional.empty());
        }

        AiChatResponse resp = new AiChatResponse();
        resp.setAnswer(String.valueOf(raw.get("answer")));
        resp.setSource(String.valueOf(raw.get("source")));
        resp.setProvider(String.valueOf(raw.getOrDefault("provider", provider)));
        resp.setTransferHuman(Boolean.TRUE.equals(raw.get("transferHuman")));
        saveChatLog(question, resp);
        logCall("chat", true, start, null);
        return resp;
    }

    public AiChatResponse chatBlock(AiChatRequest req, BlockException ex) {
        throw new BizException(429, "AI问答触发限流，请稍后再试");
    }

    /** 居民本人对话记录（租户隔离） */
    public List<AiChatLog> listMyChatLogs() {
        requireTenant();
        Long uid = TenantContext.getUserId();
        if (uid == null) {
            return List.of();
        }
        return chatLogMapper.selectList(new LambdaQueryWrapper<AiChatLog>()
                .eq(AiChatLog::getTenantId, TenantContext.getTenantId())
                .eq(AiChatLog::getUserId, uid)
                .orderByDesc(AiChatLog::getId)
                .last("limit 50"));
    }

    public List<AiKnowledge> listKb() {
        requireTenant();
        return knowledgeMapper.selectList(new LambdaQueryWrapper<AiKnowledge>()
                .eq(AiKnowledge::getTenantId, TenantContext.getTenantId())
                .eq(AiKnowledge::getStatus, 1)
                .orderByDesc(AiKnowledge::getId));
    }

    public List<AiKnowledge> listKbAll() {
        requireTenant();
        assertStaffForKb();
        return knowledgeMapper.selectList(new LambdaQueryWrapper<AiKnowledge>()
                .eq(AiKnowledge::getTenantId, TenantContext.getTenantId())
                .orderByDesc(AiKnowledge::getId));
    }

    public AiKnowledge saveKb(AiKnowledge kb) {
        assertStaffForKb();
        Long tid = TenantContext.getTenantId();
        if (tid == null) {
            throw new BizException(40001, "缺少租户上下文");
        }
        kb.setTenantId(tid);
        if (kb.getHitCount() == null) {
            kb.setHitCount(0);
        }
        if (kb.getStatus() == null) {
            kb.setStatus(1);
        }
        if (!StringUtils.hasText(kb.getCategory())) {
            kb.setCategory("general");
        }
        if (kb.getId() == null) {
            kb.setCreateTime(LocalDateTime.now());
            knowledgeMapper.insert(kb);
        } else {
            AiKnowledge old = knowledgeMapper.selectById(kb.getId());
            if (old == null || !tid.equals(old.getTenantId())) {
                throw new BizException(403, "禁止跨租户修改知识库");
            }
            kb.setUpdateTime(LocalDateTime.now());
            knowledgeMapper.updateById(kb);
        }
        return kb;
    }

    public void deleteKb(Long id) {
        assertStaffForKb();
        Long tid = TenantContext.getTenantId();
        AiKnowledge old = knowledgeMapper.selectById(id);
        if (old == null) {
            return;
        }
        if (tid != null && !tid.equals(old.getTenantId())) {
            throw new BizException(403, "禁止跨租户删除知识库");
        }
        knowledgeMapper.deleteById(id);
    }

    private void assertStaffForKb() {
        if (!RoleConstants.isTenantStaff(TenantContext.getRole())
                && !RoleConstants.isPlatformAdmin(TenantContext.getRole())) {
            throw new BizException(403, "仅物业工作人员可维护知识库");
        }
    }

    private void requireTenant() {
        if (TenantContext.getTenantId() == null) {
            throw new BizException(40001, "缺少租户上下文，禁止跨社区访问");
        }
    }

    /** 物业端：报修识图等调用记录（本社区） */
    public List<Map<String, Object>> listCallLogs(String scene, int limit) {
        requireTenant();
        assertStaffForKb();
        Long tid = TenantContext.getTenantId();
        int lim = Math.min(Math.max(limit, 1), 100);
        String sql = "select id, tenant_id as tenantId, user_id as userId, scene, success, cost_ms as costMs, remark, create_time as createTime "
                + "from ai_call_log where tenant_id=? "
                + (StringUtils.hasText(scene) ? "and scene=? " : "")
                + "order by id desc limit " + lim;
        try {
            if (StringUtils.hasText(scene)) {
                return jdbcTemplate.queryForList(sql, tid, scene);
            }
            return jdbcTemplate.queryForList(sql, tid);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /** 物业端：业主咨询统计（本社区，不含隐私正文） */
    public Map<String, Object> chatStats() {
        requireTenant();
        assertStaffForKb();
        Long tid = TenantContext.getTenantId();
        Map<String, Object> m = new HashMap<>();
        try {
            Integer total = jdbcTemplate.queryForObject(
                    "select count(1) from ai_chat_log where tenant_id=?", Integer.class, tid);
            Integer today = jdbcTemplate.queryForObject(
                    "select count(1) from ai_chat_log where tenant_id=? and date(create_time)=curdate()",
                    Integer.class, tid);
            Integer transfer = jdbcTemplate.queryForObject(
                    "select count(1) from ai_chat_log where tenant_id=? and transfer_human=1",
                    Integer.class, tid);
            m.put("total", total == null ? 0 : total);
            m.put("today", today == null ? 0 : today);
            m.put("transferHuman", transfer == null ? 0 : transfer);
        } catch (Exception e) {
            m.put("total", 0);
            m.put("today", 0);
            m.put("transferHuman", 0);
        }
        return m;
    }

    /* ===================== 2. 公告摘要 + 敏感词 ===================== */

    @SentinelResource(value = "ai_audit", blockHandler = "auditBlock")
    public AiAuditResponse audit(AiAuditRequest req) {
        fillContext(req.getTenantId(), null);
        requireTenant();
        long start = System.currentTimeMillis();
        checkQuota("audit");
        List<String> words = loadSensitiveWords();
        Map<String, Object> raw = mockAiClient.summaryAndAudit(req.getContent(), words);
        if (useHttp() && Boolean.TRUE.equals(raw.get("pass"))) {
            try {
                String summary = thirdPartyAiClient.chatCompletion(
                        "请用不超过40字总结社区公告", req.getContent());
                raw.put("summary", summary);
                raw.put("source", "llm_http");
            } catch (Exception ignored) {
            }
        }
        AiAuditResponse resp = toAudit(raw);
        saveAuditLog(req, resp);
        logCall("audit", true, start, null);
        return resp;
    }

    public AiAuditResponse auditBlock(AiAuditRequest req, BlockException ex) {
        throw new BizException(429, "内容审核触发限流");
    }

    @SentinelResource(value = "ai_summary", blockHandler = "summaryBlock")
    public AiAuditResponse noticeSummary(AiAuditRequest req) {
        if (req.getBizType() == null) {
            req.setBizType("notice");
        }
        return audit(req);
    }

    public AiAuditResponse summaryBlock(AiAuditRequest req, BlockException ex) {
        throw new BizException(429, "公告摘要触发限流");
    }

    /* ===================== 3. 报修识图 ===================== */

    @SentinelResource(value = "ai_image", blockHandler = "imageBlock")
    public AiImageRepairResponse imageRepair(AiImageRepairRequest req) {
        fillContext(req.getTenantId(), req.getUserId());
        requireTenant();
        long start = System.currentTimeMillis();
        checkQuota("image");
        Map<String, Object> raw = mockAiClient.imageRepair(req.getImageUrl());
        if (useHttp()) {
            try {
                String prompt = """
                        请识别社区报修图片，只返回 JSON（不要 markdown），字段：
                        facilityType（设施类型，如水管/电梯/路灯/门禁/墙面/绿化等）,
                        damageLevel（轻度/中度/重度）,
                        suggestTitle（短标题）,
                        suggestDesc（一两句描述）,
                        confidence（0到1的小数）
                        """;
                String tip = thirdPartyAiClient.visionDescribe(req.getImageUrl(), prompt);
                Map<String, Object> parsed = parseVisionJson(tip);
                if (parsed != null && !parsed.isEmpty()) {
                    raw.putAll(parsed);
                    raw.put("provider", "http");
                } else if (StringUtils.hasText(tip)) {
                    String facility = tip.replaceAll("[\\s\\n\\r]+", " ").trim();
                    if (facility.length() > 40) {
                        facility = facility.substring(0, 40);
                    }
                    raw.put("facilityType", facility);
                    raw.put("suggestTitle", facility + "报修");
                    raw.put("suggestDesc", tip);
                    raw.put("provider", "http");
                    raw.put("confidence", 0.75);
                }
            } catch (Exception e) {
                log.warn("vision http fallback to mock: {}", e.getMessage());
            }
        }
        AiImageRepairResponse resp = new AiImageRepairResponse();
        resp.setFacilityType(str(raw.get("facilityType")));
        resp.setDamageLevel(str(raw.get("damageLevel")));
        resp.setSuggestTitle(str(raw.get("suggestTitle")));
        resp.setSuggestDesc(str(raw.get("suggestDesc")));
        resp.setConfidence(raw.get("confidence") instanceof Number n ? n.doubleValue() : 0.6);
        resp.setProvider(str(raw.getOrDefault("provider", provider)));
        logCall("image", true, start, resp.getFacilityType());
        return resp;
    }

    public AiImageRepairResponse imageBlock(AiImageRepairRequest req, BlockException ex) {
        throw new BizException(429, "识图报修触发限流");
    }

    /* ===================== 4. 情绪倾诉 ===================== */

    @SentinelResource(value = "ai_emotion", blockHandler = "emotionBlock")
    public AiEmotionResponse emotion(AiEmotionRequest req) {
        fillContext(req.getTenantId(), req.getUserId());
        long start = System.currentTimeMillis();
        checkQuota("emotion");
        Map<String, Object> raw = mockAiClient.emotion(req.getText());
        AiEmotionResponse resp = new AiEmotionResponse();
        resp.setSentiment(str(raw.get("sentiment")));
        resp.setScore(raw.get("score") instanceof Number n ? n.doubleValue() : 0.0);
        resp.setNeedGrid(Boolean.TRUE.equals(raw.get("needGrid")));
        resp.setAdvice(str(raw.get("advice")));
        resp.setAppealCreated(false);

        if (Boolean.TRUE.equals(resp.getNeedGrid())
                && TenantContext.getTenantId() != null
                && TenantContext.getUserId() != null) {
            Long appealId = createAppealAndNotifyGrid(req.getText(), resp.getSentiment(), resp.getScore());
            resp.setAppealCreated(true);
            resp.setAppealId(appealId);
        }
        logCall("emotion", true, start, resp.getSentiment());
        return resp;
    }

    public AiEmotionResponse emotionBlock(AiEmotionRequest req, BlockException ex) {
        throw new BizException(429, "情绪识别触发限流");
    }

    /* ===================== 5. 失物匹配 ===================== */

    @SentinelResource(value = "ai_lost", blockHandler = "lostBlock")
    public AiLostMatchResponse lostMatch(AiLostMatchRequest req) {
        fillContext(req.getTenantId(), null);
        long start = System.currentTimeMillis();
        checkQuota("lost");
        Long tid = TenantContext.getTenantId();
        List<Map<String, Object>> candidates = new ArrayList<>();
        try {
            if (tid != null) {
                candidates = jdbcTemplate.queryForList(
                        "select id,title,description,contact,image_url as imageUrl,create_time as createTime "
                                + "from lost_found where tenant_id=? and status='open' order by id desc limit 50",
                        tid);
            }
        } catch (Exception e) {
            log.warn("lost_found query fail: {}", e.getMessage());
        }

        List<Map<String, Object>> scored = new ArrayList<>();
        for (Map<String, Object> row : candidates) {
            double sim = mockAiClient.lostSimilarity(
                    req.getDescription(),
                    str(row.get("title")),
                    str(row.get("description")),
                    req.getImageUrl(),
                    str(row.get("imageUrl")));
            if (sim >= 0.35) {
                Map<String, Object> m = new LinkedHashMap<>(row);
                m.put("similarity", Math.round(sim * 1000) / 1000.0);
                scored.add(m);
            }
        }
        scored.sort((a, b) -> Double.compare(
                ((Number) b.getOrDefault("similarity", 0)).doubleValue(),
                ((Number) a.getOrDefault("similarity", 0)).doubleValue()));

        AiLostMatchResponse resp = new AiLostMatchResponse();
        if (scored.isEmpty()) {
            resp.setMatched(candidates.stream().limit(3).peek(r -> r.put("similarity", 0.2)).toList());
            resp.setTip("未精确命中，返回近期失物供人工确认");
        } else {
            resp.setMatched(scored.stream().limit(10).toList());
            resp.setTip("已按相似度匹配失物记录");
        }
        resp.setProvider(provider);
        logCall("lost", true, start, "matched=" + resp.getMatched().size());
        return resp;
    }

    public AiLostMatchResponse lostBlock(AiLostMatchRequest req, BlockException ex) {
        throw new BizException(429, "失物匹配触发限流");
    }

    public Long publishLost(Map<String, String> body) {
        jdbcTemplate.update(
                "insert into lost_found(tenant_id,user_id,title,description,contact,image_url,status,create_time) values(?,?,?,?,?,?,'open',now())",
                TenantContext.getTenantId(),
                TenantContext.getUserId(),
                body.get("title"),
                body.get("description"),
                body.get("contact"),
                body.get("imageUrl"));
        return jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
    }

    /* ===================== 6. 日程提醒 ===================== */

    public AiScheduleRemind createRemind(AiScheduleRemind body) {
        Long tid = TenantContext.getTenantId();
        Long uid = TenantContext.getUserId();
        if (tid == null || uid == null) {
            throw new BizException(400, "缺少租户或用户");
        }
        body.setId(null);
        body.setTenantId(tid);
        body.setUserId(uid);
        body.setStatus("pending");
        body.setCreateTime(LocalDateTime.now());
        if (body.getRemindTime() == null) {
            body.setRemindTime(LocalDateTime.now().plusHours(1));
        }
        scheduleRemindMapper.insert(body);
        return body;
    }

    public List<AiScheduleRemind> myReminds() {
        return scheduleRemindMapper.selectList(new LambdaQueryWrapper<AiScheduleRemind>()
                .eq(AiScheduleRemind::getUserId, TenantContext.getUserId())
                .orderByAsc(AiScheduleRemind::getRemindTime)
                .last("limit 50"));
    }

    public int dispatchDueReminds() {
        TenantContext.setSkipTenant(true);
        try {
            List<AiScheduleRemind> due = scheduleRemindMapper.selectList(new LambdaQueryWrapper<AiScheduleRemind>()
                    .eq(AiScheduleRemind::getStatus, "pending")
                    .le(AiScheduleRemind::getRemindTime, LocalDateTime.now())
                    .last("limit 200"));
            int n = 0;
            for (AiScheduleRemind r : due) {
                try {
                    jdbcTemplate.update(
                            "insert into sys_message(tenant_id,user_id,title,content,msg_type,read_flag,create_time) values(?,?,?,?,?,?,now())",
                            r.getTenantId(), r.getUserId(),
                            "日程提醒：" + r.getTitle(),
                            r.getContent() == null ? r.getTitle() : r.getContent(),
                            "schedule", 0);
                    r.setStatus("sent");
                    r.setSentTime(LocalDateTime.now());
                    scheduleRemindMapper.updateById(r);
                    n++;
                } catch (Exception e) {
                    log.warn("remind send fail id={}: {}", r.getId(), e.getMessage());
                }
            }
            return n;
        } finally {
            TenantContext.clear();
        }
    }

    /* ===================== 内部 ===================== */

    private Optional<String> findKbAnswer(String question) {
        Long tid = TenantContext.getTenantId();
        List<AiKnowledge> list = knowledgeMapper.selectList(new LambdaQueryWrapper<AiKnowledge>()
                .eq(tid != null, AiKnowledge::getTenantId, tid)
                .eq(AiKnowledge::getStatus, 1)
                .like(AiKnowledge::getQuestion, question)
                .orderByDesc(AiKnowledge::getHitCount)
                .last("limit 5"));
        if (!list.isEmpty()) {
            AiKnowledge best = list.get(0);
            bumpHit(best);
            return Optional.ofNullable(best.getAnswer());
        }
        // 问题包含知识库关键词（仅本租户）
        List<AiKnowledge> all = knowledgeMapper.selectList(new LambdaQueryWrapper<AiKnowledge>()
                .eq(tid != null, AiKnowledge::getTenantId, tid)
                .eq(AiKnowledge::getStatus, 1)
                .last("limit 80"));
        for (AiKnowledge kb : all) {
            if (kb.getQuestion() != null && question.contains(kb.getQuestion())) {
                bumpHit(kb);
                return Optional.ofNullable(kb.getAnswer());
            }
        }
        return Optional.empty();
    }

    private void bumpHit(AiKnowledge kb) {
        kb.setHitCount((kb.getHitCount() == null ? 0 : kb.getHitCount()) + 1);
        knowledgeMapper.updateById(kb);
    }

    private void saveChatLog(String question, AiChatResponse resp) {
        try {
            AiChatLog logRow = new AiChatLog();
            logRow.setTenantId(TenantContext.getTenantId());
            logRow.setUserId(TenantContext.getUserId());
            logRow.setQuestion(question);
            logRow.setAnswer(resp.getAnswer());
            logRow.setSource(resp.getSource());
            logRow.setTransferHuman(Boolean.TRUE.equals(resp.getTransferHuman()) ? 1 : 0);
            logRow.setCreateTime(LocalDateTime.now());
            chatLogMapper.insert(logRow);
        } catch (Exception e) {
            log.debug("chat log skip: {}", e.getMessage());
        }
    }

    private void saveAuditLog(AiAuditRequest req, AiAuditResponse resp) {
        try {
            jdbcTemplate.update(
                    "insert into ai_audit_log(tenant_id,biz_type,content,summary,pass_flag,hit_words,create_time) values(?,?,?,?,?,?,now())",
                    TenantContext.getTenantId(),
                    req.getBizType() == null ? "notice" : req.getBizType(),
                    req.getContent(),
                    resp.getSummary(),
                    Boolean.TRUE.equals(resp.getPass()) ? 1 : 0,
                    resp.getHitWords() == null ? "" : String.join(",", resp.getHitWords()));
        } catch (Exception e) {
            log.debug("audit log skip: {}", e.getMessage());
        }
    }

    private Long createAppealAndNotifyGrid(String text, String sentiment, Double score) {
        jdbcTemplate.update(
                "insert into emotion_appeal(tenant_id,user_id,content,sentiment,score,status,create_time) values(?,?,?,?,?,'pending',now())",
                TenantContext.getTenantId(), TenantContext.getUserId(), text, sentiment, score);
        Long appealId = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        try {
            List<Map<String, Object>> grids = jdbcTemplate.queryForList(
                    "select id from sys_user where tenant_id=? and role in ('grid_worker','property_admin') and status=1 limit 20",
                    TenantContext.getTenantId());
            for (Map<String, Object> g : grids) {
                jdbcTemplate.update(
                        "insert into sys_message(tenant_id,user_id,title,content,msg_type,read_flag,create_time) values(?,?,?,?,?,?,now())",
                        TenantContext.getTenantId(),
                        g.get("id"),
                        "负面情绪调解工单",
                        "居民情绪倾诉触发自动工单#" + appealId + "：" + (text.length() > 80 ? text.substring(0, 80) + "..." : text),
                        "emotion", 0);
            }
        } catch (Exception e) {
            log.warn("notify grid fail: {}", e.getMessage());
        }
        return appealId;
    }

    private List<String> loadSensitiveWords() {
        try {
            return jdbcTemplate.queryForList("select word from sys_sensitive_word", String.class);
        } catch (Exception e) {
            return List.of("赌博", "色情", "诈骗", "传销");
        }
    }

    private void checkQuota(String scene) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return;
        }
        String key = "ai:quota:" + tenantId + ":" + LocalDate.now();
        Long used = redisTemplate.opsForValue().increment(key);
        if (used != null && used == 1L) {
            redisTemplate.expire(key, 2, TimeUnit.DAYS);
        }
        if (used != null && used > dailyQuota) {
            throw new BizException(429, "本社区今日AI调用额度已用尽（" + dailyQuota + "），请明日再试或升级套餐");
        }
    }

    private void logCall(String scene, boolean success, long startMs, String remark) {
        try {
            jdbcTemplate.update(
                    "insert into ai_call_log(tenant_id,user_id,scene,success,cost_ms,remark,create_time) values(?,?,?,?,?,?,now())",
                    TenantContext.getTenantId(),
                    TenantContext.getUserId(),
                    scene,
                    success ? 1 : 0,
                    (int) (System.currentTimeMillis() - startMs),
                    remark);
        } catch (Exception ignored) {
        }
    }

    private void fillContext(Long tenantId, Long userId) {
        Long ctxTid = TenantContext.getTenantId();
        // 网关已透传租户时，以上下文为准，忽略 body 中错误的 tenantId
        if (tenantId != null && ctxTid == null) {
            TenantContext.setTenantId(tenantId);
        } else if (tenantId != null && ctxTid != null && !tenantId.equals(ctxTid)) {
            log.warn("ignore body tenantId={} , keep context tenantId={}", tenantId, ctxTid);
        }
        if (userId != null && TenantContext.getUserId() == null) {
            TenantContext.setUserId(userId);
        }
    }

    private boolean useHttp() {
        return "http".equalsIgnoreCase(provider) && thirdPartyAiClient.available();
    }

    /** 从模型回复中提取 JSON（兼容 ```json 包裹） */
    private Map<String, Object> parseVisionJson(String tip) {
        if (!StringUtils.hasText(tip)) {
            return null;
        }
        String text = tip.trim();
        int fence = text.indexOf("```");
        if (fence >= 0) {
            int start = text.indexOf('\n', fence);
            int end = text.indexOf("```", fence + 3);
            if (start > 0 && end > start) {
                text = text.substring(start + 1, end).trim();
                if (text.regionMatches(true, 0, "json", 0, 4)) {
                    text = text.substring(4).trim();
                }
            }
        }
        int l = text.indexOf('{');
        int r = text.lastIndexOf('}');
        if (l < 0 || r <= l) {
            return null;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = cn.hutool.json.JSONUtil.toBean(text.substring(l, r + 1), Map.class);
            Map<String, Object> out = new LinkedHashMap<>();
            if (map.get("facilityType") != null) {
                out.put("facilityType", str(map.get("facilityType")));
            }
            if (map.get("damageLevel") != null) {
                out.put("damageLevel", str(map.get("damageLevel")));
            }
            if (map.get("suggestTitle") != null) {
                out.put("suggestTitle", str(map.get("suggestTitle")));
            }
            if (map.get("suggestDesc") != null) {
                out.put("suggestDesc", str(map.get("suggestDesc")));
            }
            Object conf = map.get("confidence");
            if (conf instanceof Number n) {
                out.put("confidence", n.doubleValue());
            } else if (conf != null) {
                try {
                    out.put("confidence", Double.parseDouble(str(conf)));
                } catch (Exception ignored) {
                }
            }
            return out.isEmpty() ? null : out;
        } catch (Exception e) {
            return null;
        }
    }

    private AiAuditResponse toAudit(Map<String, Object> raw) {
        AiAuditResponse resp = new AiAuditResponse();
        resp.setPass(Boolean.TRUE.equals(raw.get("pass")));
        resp.setSummary(str(raw.get("summary")));
        Object hw = raw.get("hitWords");
        if (hw instanceof List<?> list) {
            resp.setHitWords(list.stream().map(String::valueOf).toList());
        }
        resp.setSource(str(raw.getOrDefault("source", "mock")));
        return resp;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    /** 兼容旧 Map 入参 */
    public Map<String, Object> chatMap(String question) {
        AiChatRequest req = new AiChatRequest();
        req.setQuestion(question);
        AiChatResponse r = chat(req);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("answer", r.getAnswer());
        m.put("source", r.getSource());
        m.put("provider", r.getProvider());
        m.put("transferHuman", r.getTransferHuman());
        return m;
    }
}
