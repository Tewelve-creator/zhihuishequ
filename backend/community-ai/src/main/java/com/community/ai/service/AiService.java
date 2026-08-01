package com.community.ai.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.community.ai.biz.AiBizButlerService;
import com.community.ai.config.AiProviderGateway;
import com.community.ai.entity.AiChatLog;
import com.community.ai.entity.AiKnowledge;
import com.community.ai.entity.AiScheduleRemind;
import com.community.ai.mapper.AiChatLogMapper;
import com.community.ai.mapper.AiKnowledgeMapper;
import com.community.ai.mapper.AiScheduleRemindMapper;
import com.community.ai.mock.MockAiClient;
import com.community.ai.mock.ThirdPartyAiClient;
import com.community.ai.rag.RagHit;
import com.community.ai.rag.RagKnowledgeService;
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
 * 智能管家：业务指令 + RAG 制度问答 + 闲聊；按环境自动 Mock/真实 LLM
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
    private final AiBizButlerService aiBizButlerService;
    private final AiProviderGateway providerGateway;
    private final RagKnowledgeService ragKnowledgeService;
    private final FriendlyChatHelper friendlyChatHelper;
    private final KbSettingsService kbSettingsService;
    private final AiFeatureService aiFeatureService;

    @Value("${ai.daily-quota:200}")
    private int dailyQuota;

    /* ===================== 1. 智能管家对话（业务指令 / RAG / 闲聊） ===================== */

    @SentinelResource(value = "ai_chat", blockHandler = "chatBlock")
    public AiChatResponse chat(AiChatRequest req) {
        fillContext(req.getTenantId(), req.getUserId());
        requireTenant();
        long start = System.currentTimeMillis();
        checkQuota("chat");
        String question = req.getQuestion() == null ? "" : req.getQuestion().trim();
        if (!StringUtils.hasText(question)) {
            throw new BizException(400, "问题不能为空");
        }

        // 1) 业务办理（含会话追问/确认）——禁止未确认写库
        Optional<AiChatResponse> biz = aiBizButlerService.tryHandle(question);
        if (biz.isPresent()) {
            AiChatResponse resp = biz.get();
            saveChatLog(question, resp);
            logCall("chat_biz", true, start, resp.getIntent());
            return resp;
        }

        // 2) 闲聊打招呼 —— 友好回复，不提示找不到资料
        if (friendlyChatHelper.isChitchat(question)) {
            AiChatResponse resp = friendlyChatHelper.reply(question);
            saveChatLog(question, resp);
            logCall("chat_chitchat", true, start, null);
            return resp;
        }

        // 3) 按策略检索知识库或走大模型
        String strategy = "rag_first";
        try {
            strategy = kbSettingsService.replyStrategy(TenantContext.getTenantId());
        } catch (Exception ignored) {
        }

        if (!"llm_first".equals(strategy)) {
            Optional<RagHit> ragOpt = ragKnowledgeService.retrieve(question);
            if (ragOpt.isPresent() && ragOpt.get().isAccepted()) {
                AiChatResponse resp = answerWithRag(question, ragOpt.get());
                saveChatLog(question, resp);
                logCall("chat_rag", true, start, String.format(Locale.ROOT, "score=%.2f", ragOpt.get().getScore()));
                return resp;
            }
            if ("kb_only".equals(strategy)) {
                AiChatResponse resp = AiChatResponse.ofRag(
                        "抱歉，知识库中暂未找到相关内容。建议您联系物业客服核实，或换个问法再试。",
                        "kb_miss", providerGateway.activeMode(), false);
                resp.setMode("KB_ONLY");
                saveChatLog(question, resp);
                logCall("chat_kb_miss", true, start, null);
                return resp;
            }
        }

        // 4) 知识库未命中（或 llm_first）：大模型作答
        AiChatResponse resp = answerWithoutKb(question);
        saveChatLog(question, resp);
        logCall("chat", true, start, resp.getSource());
        return resp;
    }

    private String butlerSystem() {
        try {
            return kbSettingsService.systemPrompt(TenantContext.getTenantId());
        } catch (Exception e) {
            return KbSettingsService.DEFAULT_SYSTEM_PROMPT;
        }
    }

    private static final String UNOFFICIAL_TIP =
            "⚠温馨提示：该内容不属于本小区官方规定，仅供参考，小区相关事务建议联系物业核实。";

    /** 知识库未命中：DeepSeek/LLM 自由作答；Mock 模式本地兜底。均追加温馨提示。 */
    private AiChatResponse answerWithoutKb(String question) {
        String answer;
        String source;
        if (providerGateway.useLlm()) {
            try {
                answer = thirdPartyAiClient.chatCompletion(butlerSystem(), question);
                source = "llm_general";
            } catch (Exception e) {
                log.warn("llm general fallback: {}", e.getMessage());
                answer = "这个问题我可以结合常识帮您说明一下，具体以本小区物业规定为准。";
                source = "llm_general_fallback";
            }
        } else {
            answer = friendlyChatHelper.generalAnswer(question);
            source = "mock_general";
        }
        answer = ensureUnofficialTip(answer);
        AiChatResponse resp = AiChatResponse.ofRag(answer, source, providerGateway.activeMode(),
                question.contains("人工") || question.contains("投诉"));
        resp.setMode("LLM");
        return resp;
    }

    private String ensureUnofficialTip(String answer) {
        boolean enable = true;
        try {
            enable = kbSettingsService.enableUnofficialTip(TenantContext.getTenantId());
        } catch (Exception ignored) {
        }
        if (!enable) return answer == null ? "" : answer.trim();
        String a = answer == null ? "" : answer.trim();
        if (a.contains("不属于本小区官方规定")) {
            return a;
        }
        return a + "\n\n" + UNOFFICIAL_TIP;
    }

    private AiChatResponse answerWithRag(String question, RagHit hit) {
        if ("document".equals(hit.getHitType())) {
            return answerWithDocRag(question, hit);
        }
        AiKnowledge kb = hit.getKnowledge();
        ragKnowledgeService.bumpHit(kb);
        String answer = kb.getAnswer();
        String source = "tenant_kb";
        if (providerGateway.useLlm()) {
            try {
                String polished = thirdPartyAiClient.chatCompletion(
                        butlerSystem() + "\n以下为小区知识库资料（请优先依据作答，无需再加非官方温馨提示）：\n"
                                + kb.getAnswer(),
                        question);
                if (StringUtils.hasText(polished)) {
                    answer = polished.trim();
                    source = "tenant_kb_llm";
                }
            } catch (Exception e) {
                log.debug("rag polish skip: {}", e.getMessage());
            }
        }
        AiChatResponse resp = AiChatResponse.ofRag(answer, source, providerGateway.activeMode(), false);
        resp.setMode("RAG");
        // 供问答日志详情排查：落库召回片段与得分
        resp.setRagScore(hit.getScore());
        resp.setRagHitType("qa");
        resp.setRagDocTitle(kb.getQuestion());
        resp.setRagChunk(StringUtils.hasText(kb.getAnswer()) ? kb.getAnswer() : kb.getQuestion());
        return resp;
    }

    private AiChatResponse answerWithDocRag(String question, RagHit hit) {
        String excerpt = hit.getChunkContent() == null ? "" : hit.getChunkContent().trim();
        String answer = excerpt;
        String source = "tenant_doc";
        if (providerGateway.useLlm()) {
            try {
                String polished = thirdPartyAiClient.chatCompletion(
                        butlerSystem() + "\n以下为小区知识库文档摘录（请优先依据作答，无需再加非官方温馨提示）：\n"
                                + excerpt,
                        question);
                if (StringUtils.hasText(polished)) {
                    answer = polished.trim();
                    source = "tenant_doc_llm";
                }
            } catch (Exception e) {
                log.debug("doc rag polish skip: {}", e.getMessage());
            }
        }
        String fileName = StringUtils.hasText(hit.getDocFileName()) ? hit.getDocFileName() : hit.getDocTitle();
        if ("community".equalsIgnoreCase(hit.getDocCategory()) && StringUtils.hasText(fileName)) {
            if (!answer.contains("来源：") && !answer.contains("来源:")) {
                answer = answer + "\n\n—— 来源：" + fileName;
            }
        }
        AiChatResponse resp = AiChatResponse.ofRag(answer, source, providerGateway.activeMode(), false);
        resp.setMode("RAG");
        if (StringUtils.hasText(fileName)) {
            resp.setSourceDocs(List.of(fileName));
        }
        resp.setRagScore(hit.getScore());
        resp.setRagHitType("document");
        resp.setRagDocTitle(StringUtils.hasText(hit.getDocTitle()) ? hit.getDocTitle() : fileName);
        resp.setRagChunk(excerpt);
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
        ensureChatLogExt();
        return chatLogMapper.selectList(new LambdaQueryWrapper<AiChatLog>()
                .eq(AiChatLog::getTenantId, TenantContext.getTenantId())
                .eq(AiChatLog::getUserId, uid)
                .orderByDesc(AiChatLog::getId)
                .last("limit 50"));
    }

    /**
     * 物业：问答日志（提问/回答/评价）
     * @param sourceGroup 来源分组：kb|llm|biz|chitchat
     */
    public List<Map<String, Object>> listAdminChatLogs(String keyword, Integer rating, String sourceGroup, int limit) {
        requireTenant();
        assertStaffForKb();
        ensureChatLogExt();
        int lim = Math.min(Math.max(limit, 1), 300);
        Long tid = TenantContext.getTenantId();
        StringBuilder sql = new StringBuilder(
                "select id, tenant_id as tenantId, user_id as userId, user_nickname as userNickname, "
                        + "question, answer, source, transfer_human as transferHuman, "
                        + "rating, rating_remark as ratingRemark, "
                        + "rag_score as ragScore, rag_chunk as ragChunk, rag_doc_title as ragDocTitle, "
                        + "create_time as createTime "
                        + "from ai_chat_log where tenant_id=? ");
        List<Object> args = new ArrayList<>();
        args.add(tid);
        if (rating != null && (rating == 1 || rating == 2)) {
            sql.append("and rating=? ");
            args.add(rating);
        }
        appendSourceGroupFilter(sql, args, sourceGroup);
        if (StringUtils.hasText(keyword)) {
            sql.append("and (question like ? or answer like ? or user_nickname like ?) ");
            String k = "%" + keyword.trim() + "%";
            args.add(k);
            args.add(k);
            args.add(k);
        }
        sql.append("order by id desc limit ").append(lim);
        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList(sql.toString(), args.toArray());
        } catch (Exception e) {
            // 兼容尚未加 RAG 列的旧库
            StringBuilder sql2 = new StringBuilder(
                    "select id, tenant_id as tenantId, user_id as userId, user_nickname as userNickname, "
                            + "question, answer, source, transfer_human as transferHuman, "
                            + "rating, rating_remark as ratingRemark, create_time as createTime "
                            + "from ai_chat_log where tenant_id=? ");
            List<Object> args2 = new ArrayList<>();
            args2.add(tid);
            if (rating != null && (rating == 1 || rating == 2)) {
                sql2.append("and rating=? ");
                args2.add(rating);
            }
            appendSourceGroupFilter(sql2, args2, sourceGroup);
            if (StringUtils.hasText(keyword)) {
                sql2.append("and (question like ? or answer like ? or user_nickname like ?) ");
                String k = "%" + keyword.trim() + "%";
                args2.add(k);
                args2.add(k);
                args2.add(k);
            }
            sql2.append("order by id desc limit ").append(lim);
            rows = jdbcTemplate.queryForList(sql2.toString(), args2.toArray());
        }
        for (Map<String, Object> r : rows) {
            Object ct = r.get("createTime");
            if (ct != null) {
                r.put("createTime", String.valueOf(ct).replace('T', ' ').substring(0, Math.min(19, String.valueOf(ct).length())));
            }
            Integer rt = r.get("rating") == null ? null : ((Number) r.get("rating")).intValue();
            r.put("ratingLabel", rt == null ? "未评价" : (rt == 1 ? "好评" : "差评"));
            Object th = r.get("transferHuman");
            r.put("transferHuman", th != null && ((Number) th).intValue() == 1);
            String src = r.get("source") == null ? "" : String.valueOf(r.get("source"));
            String group = sourceGroupOf(src);
            r.put("sourceGroup", group);
            r.put("sourceLabel", sourceLabelOf(src));
            // 是否展示知识库召回区
            boolean hasRag = r.get("ragChunk") != null && StringUtils.hasText(String.valueOf(r.get("ragChunk")));
            r.put("hasRagHit", hasRag || "kb".equals(group));
            if (r.get("ragScore") instanceof Number n) {
                r.put("ragScoreText", String.format(Locale.ROOT, "%.2f", n.doubleValue()));
            }
        }
        return rows;
    }

    /**
     * 问答日志统计：
     * - 卡片：当日对话总数 / 知识库命中 / 好评 / 差评
     * - 图表：业主高频咨询 TOP10（按 topRange：today|7d|30d）
     */
    public Map<String, Object> chatLogTodayStats(String topRange) {
        requireTenant();
        assertStaffForKb();
        ensureChatLogExt();
        Long tid = TenantContext.getTenantId();
        // 按租户当日（数据库本地日期）聚合卡片
        Integer total = jdbcTemplate.queryForObject(
                "select count(1) from ai_chat_log where tenant_id=? and date(create_time)=curdate()",
                Integer.class, tid);
        Integer kbHit = jdbcTemplate.queryForObject(
                "select count(1) from ai_chat_log where tenant_id=? and date(create_time)=curdate() "
                        + "and source in ('tenant_kb','tenant_kb_llm','tenant_doc','tenant_doc_llm')",
                Integer.class, tid);
        Integer good = jdbcTemplate.queryForObject(
                "select count(1) from ai_chat_log where tenant_id=? and date(create_time)=curdate() and rating=1",
                Integer.class, tid);
        Integer bad = jdbcTemplate.queryForObject(
                "select count(1) from ai_chat_log where tenant_id=? and date(create_time)=curdate() and rating=2",
                Integer.class, tid);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("todayTotal", total == null ? 0 : total);
        m.put("kbHitCount", kbHit == null ? 0 : kbHit);
        m.put("goodCount", good == null ? 0 : good);
        m.put("badCount", bad == null ? 0 : bad);

        String range = normalizeTopRange(topRange);
        m.put("topRange", range);
        m.put("topQuestions", listTopQuestions(tid, range));
        return m;
    }

    /** 兼容旧调用 */
    public Map<String, Object> chatLogTodayStats() {
        return chatLogTodayStats("today");
    }

    /**
     * 高频咨询聚合：按完整提问文本分组。
     * 排除智能管家多轮业务中的槽位短答（如「明天上午十点」「不开车」「确认」），
     * 避免把对话过程中的回答误统计为咨询问题。
     */
    private List<Map<String, Object>> listTopQuestions(Long tid, String range) {
        String timeSql = switch (range) {
            case "7d" -> "and create_time >= date_sub(curdate(), interval 6 day)";
            case "30d" -> "and create_time >= date_sub(curdate(), interval 29 day)";
            default -> "and date(create_time)=curdate()";
        };
        // 多取一些候选，再在内存中过滤短答/槽位回复后取 TOP10
        String sql = "select left(trim(question), 80) as question, count(1) as cnt "
                + "from ai_chat_log where tenant_id=? " + timeSql + " "
                + "and question is not null and trim(question)<>'' "
                + "and char_length(trim(question)) >= 2 "
                + "group by left(trim(question), 80) "
                + "order by cnt desc, question asc limit 80";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, tid);
        List<Map<String, Object>> out = new ArrayList<>();
        int rank = 1;
        for (Map<String, Object> r : rows) {
            String q = r.get("question") == null ? "" : String.valueOf(r.get("question")).trim();
            if (!isConsultQuestionForTop(q)) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("rank", rank++);
            item.put("question", q);
            item.put("count", r.get("cnt") == null ? 0 : ((Number) r.get("cnt")).intValue());
            out.add(item);
            if (out.size() >= 10) {
                break;
            }
        }
        return out;
    }

    /**
     * 是否计为「业主咨询问题」。
     * 过滤：确认/取消、寒暄、驾车槽位、纯时间点、短姓名、无意图短句等业务过程回答。
     */
    private static boolean isConsultQuestionForTop(String question) {
        if (!StringUtils.hasText(question)) {
            return false;
        }
        String t = question.trim();
        // 上传占位、链接
        if (t.startsWith("[已上传") || t.startsWith("http://") || t.startsWith("https://")) {
            return false;
        }
        // 确认 / 取消 / 跳过
        if (t.matches("^(确认|取消|跳过|是|否|好的|好|嗯|行|可以|不用了|没了|ok|OK)[.!！。]?$")) {
            return false;
        }
        // 纯寒暄
        if (t.matches("^(你好|您好|在吗|嗨|哈喽|hello|hi|Hello|Hi)[.!！？]?$")) {
            return false;
        }
        // 驾车槽位短答
        if (t.matches("^(开|不开)?车$") || "不开车".equals(t) || "不驾车".equals(t)
                || "驾车".equals(t) || "开车".equals(t) || "不开".equals(t)) {
            return false;
        }
        // 纯时间/日期回答（业务追问来访时间）
        if (t.matches("^\\d{4}[-/年].*") || t.matches("^\\d{1,2}[:：点].*")) {
            return false;
        }
        if (t.matches(".*(明天|后天|大后天|今天|上午|下午|晚上|点钟|[0-9一二三四五六七八九十两]点).*")
                && t.length() <= 24
                && !hasConsultIntentKeyword(t)) {
            return false;
        }
        // 短姓名/短词且无咨询意图（如「马兴桦」）
        if (t.length() <= 4 && t.matches("^[\\u4e00-\\u9fa5A-Za-z]{1,4}$") && !hasConsultIntentKeyword(t)) {
            return false;
        }
        // 过短且无意图
        if (t.length() < 6 && !hasConsultIntentKeyword(t)) {
            return false;
        }
        return true;
    }

    private static boolean hasConsultIntentKeyword(String t) {
        String[] keys = {
                "怎么", "如何", "什么", "吗", "么", "呢", "帮我", "帮忙", "请问",
                "查询", "查一下", "查下", "报修", "报事", "物业", "访客", "预约", "登记",
                "欠费", "缴费", "交费", "车辆", "车牌", "制度", "规定", "活动", "快递",
                "投诉", "开票", "发票", "停车", "噪音", "水电", "暖气", "门禁", "通行"
        };
        for (String k : keys) {
            if (t.contains(k)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeTopRange(String topRange) {
        if (!StringUtils.hasText(topRange)) return "today";
        String r = topRange.trim().toLowerCase(Locale.ROOT);
        return switch (r) {
            case "7d", "7", "week" -> "7d";
            case "30d", "30", "month" -> "30d";
            default -> "today";
        };
    }

    private static void appendSourceGroupFilter(StringBuilder sql, List<Object> args, String sourceGroup) {
        if (!StringUtils.hasText(sourceGroup)) return;
        String g = sourceGroup.trim().toLowerCase(Locale.ROOT);
        switch (g) {
            case "kb" -> sql.append("and source in ('tenant_kb','tenant_kb_llm','tenant_doc','tenant_doc_llm') ");
            case "llm" -> sql.append("and source in ('llm_general','llm_general_fallback','mock_general','llm_http','mock_llm','mock') ");
            case "biz" -> sql.append("and source like 'biz_%' ");
            case "chitchat" -> sql.append("and source in ('mock_chitchat','llm_chitchat') ");
            default -> {
            }
        }
    }

    private static String sourceGroupOf(String src) {
        if (!StringUtils.hasText(src)) return "other";
        if (src.startsWith("biz_")) return "biz";
        return switch (src) {
            case "tenant_kb", "tenant_kb_llm", "tenant_doc", "tenant_doc_llm" -> "kb";
            case "mock_chitchat", "llm_chitchat" -> "chitchat";
            case "llm_general", "llm_general_fallback", "mock_general", "llm_http", "mock_llm", "mock" -> "llm";
            default -> "other";
        };
    }

    private static String sourceLabelOf(String src) {
        if (!StringUtils.hasText(src)) return "—";
        if (src.startsWith("biz_")) return "业务流程";
        return switch (src) {
            case "tenant_kb", "tenant_kb_llm", "tenant_doc", "tenant_doc_llm" -> "问答知识库";
            case "llm_general", "llm_general_fallback", "mock_general", "llm_http", "mock_llm", "mock" -> "大模型";
            case "mock_chitchat", "llm_chitchat" -> "闲聊";
            case "kb_miss" -> "知识库未命中";
            default -> src;
        };
    }

    /** 业主评价对话 1=好评 2=差评 */
    public Map<String, Object> rateChatLog(Long id, Integer rating, String remark) {
        requireTenant();
        ensureChatLogExt();
        if (rating == null || (rating != 1 && rating != 2)) {
            throw new BizException("评价无效");
        }
        Long uid = TenantContext.getUserId();
        AiChatLog row = chatLogMapper.selectById(id);
        if (row == null || !Objects.equals(row.getTenantId(), TenantContext.getTenantId())) {
            throw new BizException("记录不存在");
        }
        String role = TenantContext.getRole();
        boolean staff = RoleConstants.isPropertyAdmin(role) || RoleConstants.isGridWorker(role)
                || RoleConstants.isPlatformAdmin(role);
        if (!staff && !Objects.equals(row.getUserId(), uid)) {
            throw new BizException(403, "只能评价本人的对话");
        }
        row.setRating(rating);
        row.setRatingRemark(StringUtils.hasText(remark) ? remark.trim() : null);
        chatLogMapper.updateById(row);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("rating", rating);
        m.put("ratingLabel", rating == 1 ? "好评" : "差评");
        return m;
    }

    private void ensureChatLogExt() {
        ensureChatLogCol("rating", "alter table ai_chat_log add column rating tinyint null");
        ensureChatLogCol("rating_remark", "alter table ai_chat_log add column rating_remark varchar(255) null");
        ensureChatLogCol("user_nickname", "alter table ai_chat_log add column user_nickname varchar(64) null");
        // RAG 排查字段：得分 / 召回片段 / 文档标题
        ensureChatLogCol("rag_score", "alter table ai_chat_log add column rag_score double null comment 'RAG相似度'");
        ensureChatLogCol("rag_chunk", "alter table ai_chat_log add column rag_chunk text null comment 'RAG召回片段'");
        ensureChatLogCol("rag_doc_title", "alter table ai_chat_log add column rag_doc_title varchar(255) null");
    }

    private void ensureChatLogCol(String column, String alterSql) {
        try {
            Integer c = jdbcTemplate.queryForObject(
                    "select count(1) from information_schema.columns where table_schema=database() "
                            + "and table_name='ai_chat_log' and column_name=?",
                    Integer.class, column);
            if (c == null || c == 0) {
                jdbcTemplate.execute(alterSql);
            }
        } catch (Exception ignored) {
        }
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
        if (providerGateway.useLlm() && Boolean.TRUE.equals(raw.get("pass"))) {
            try {
                String summary = thirdPartyAiClient.chatCompletion(
                        "请用不超过100字总结社区公告，不要换行", req.getContent());
                if (StringUtils.hasText(summary) && summary.length() > 100) {
                    summary = summary.substring(0, 97) + "...";
                }
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
        if (providerGateway.useLlm()) {
            try {
                String prompt = """
                        请识别社区报修图片，只返回 JSON（不要 markdown），字段：
                        facilityType（设施类型，如路灯/电梯/健身器材/楼道门窗/水管等）,
                        locationHint（位置建议，如几号楼楼道/单元门口/健身区）,
                        damageLevel（轻微/中度/严重）,
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
        resp.setLocationHint(str(raw.get("locationHint")));
        double conf = raw.get("confidence") instanceof Number n ? n.doubleValue() : 0.6;
        resp.setConfidence(conf);
        resp.setNeedManual(raw.get("needManual") instanceof Boolean b ? b : conf < 0.85);
        if (!StringUtils.hasText(resp.getLocationHint())) {
            resp.setLocationHint("请补充具体位置（楼栋/单元/公共区域）");
            if (Boolean.FALSE.equals(resp.getNeedManual()) && conf < 0.9) {
                resp.setNeedManual(false);
            }
        }
        resp.setProvider(str(raw.getOrDefault("provider", providerGateway.activeMode())));
        aiFeatureService.saveImageLog(
                req.getImageUrl(),
                resp.getFacilityType(),
                resp.getLocationHint(),
                resp.getDamageLevel(),
                resp.getConfidence(),
                resp.getProvider(),
                Boolean.TRUE.equals(resp.getNeedManual()),
                null);
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
        resp.setEmotionTags(str(raw.get("emotionTags")));
        resp.setAppealCreated(false);

        Long caseId = aiFeatureService.createEmotionCase(
                req.getText(),
                resp.getSentiment(),
                resp.getEmotionTags(),
                resp.getScore(),
                resp.getAdvice(),
                Boolean.TRUE.equals(resp.getNeedGrid()));
        resp.setCaseId(caseId);
        if (Boolean.TRUE.equals(resp.getNeedGrid())) {
            resp.setAppealCreated(true);
            resp.setAppealId(caseId);
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
        resp.setProvider(providerGateway.activeMode());
        logCall("lost", true, start, "matched=" + resp.getMatched().size());
        return resp;
    }

    public AiLostMatchResponse lostBlock(AiLostMatchRequest req, BlockException ex) {
        throw new BizException(429, "失物匹配触发限流");
    }

    public Long publishLost(Map<String, String> body) {
        requireTenant();
        Long tid = TenantContext.getTenantId();
        Long uid = TenantContext.getUserId();
        String title = body.get("title");
        String description = body.get("description");
        if (!StringUtils.hasText(title)) {
            throw new BizException(400, "请填写标题");
        }
        // 发布前 AI 内容审核
        AiAuditRequest auditReq = new AiAuditRequest();
        auditReq.setBizType("lost");
        auditReq.setContent((title == null ? "" : title) + "\n" + (description == null ? "" : description));
        AiAuditResponse audit = audit(auditReq);
        if (!Boolean.TRUE.equals(audit.getPass())) {
            String hits = audit.getHitWords() == null || audit.getHitWords().isEmpty()
                    ? "含不当内容"
                    : String.join(",", audit.getHitWords());
            throw new BizException(400, "AI内容审核未通过：" + hits);
        }

        String itemType = body.get("itemType");
        if (itemType == null || itemType.isBlank()) itemType = body.getOrDefault("type", "found");
        if (!"lost".equalsIgnoreCase(itemType)) itemType = "found";

        Long houseId = parseLong(body.get("houseId"));
        if (houseId == null) {
            houseId = TenantContext.getHouseId();
        }
        String houseLabel = blankToNull(body.get("houseLabel"));
        if (houseId == null) {
            houseId = findDefaultHouseId(tid, uid);
        }
        if (!StringUtils.hasText(houseLabel) && houseId != null) {
            houseLabel = resolveHouseLabel(tid, houseId);
        }

        String imageUrl = normalizeStoredImageUrl(blankToNull(body.get("imageUrl")));
        String contactName = blankToNull(body.get("contactName"));
        if (!StringUtils.hasText(contactName) && uid != null) {
            contactName = findUserNickname(uid);
        }
        String category = blankToNull(body.get("category"));

        try {
            jdbcTemplate.update(
                    "insert into lost_found(tenant_id,user_id,house_id,house_label,title,item_type,category,description,contact,contact_name,image_url,status,ai_audit_pass,claim_count,create_time) "
                            + "values(?,?,?,?,?,?,?,?,?,?,?,'open',1,0,now())",
                    tid,
                    uid,
                    houseId,
                    houseLabel,
                    title,
                    itemType.toLowerCase(),
                    category,
                    description,
                    body.get("contact"),
                    contactName,
                    imageUrl);
        } catch (Exception e) {
            // 兼容尚未加列的库
            jdbcTemplate.update(
                    "insert into lost_found(tenant_id,user_id,house_id,house_label,title,item_type,description,contact,contact_name,image_url,status,create_time) "
                            + "values(?,?,?,?,?,?,?,?,?,?,'open',now())",
                    tid,
                    uid,
                    houseId,
                    houseLabel,
                    title,
                    itemType.toLowerCase(),
                    description,
                    body.get("contact"),
                    contactName,
                    imageUrl);
        }
        return jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
    }

    public List<Map<String, Object>> listLost(String status, String itemType) {
        return listLost(status, itemType, null, null);
    }

    public List<Map<String, Object>> listLost(String status, String itemType, String category, String keyword) {
        requireTenant();
        // 居民可浏览本社区进行中信息；物业可按状态筛选
        boolean staff = RoleConstants.isTenantStaff(TenantContext.getRole())
                || RoleConstants.isPlatformAdmin(TenantContext.getRole());
        StringBuilder sql = new StringBuilder(
                "select l.*, u.nickname as publisher_name from lost_found l "
                        + "left join sys_user u on u.id=l.user_id where l.tenant_id=? ");
        List<Object> args = new ArrayList<>();
        args.add(TenantContext.getTenantId());
        if (!staff) {
            // 居民默认只看进行中
            sql.append("and l.status='open' ");
        } else if (status != null && !status.isBlank()) {
            sql.append("and l.status=? ");
            args.add(status.trim());
        }
        if (itemType != null && !itemType.isBlank()) {
            sql.append("and l.item_type=? ");
            args.add(itemType.trim());
        }
        if (category != null && !category.isBlank()) {
            sql.append("and l.category=? ");
            args.add(category.trim());
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append("and (l.title like ? or l.description like ? or l.contact like ? or l.contact_name like ?) ");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
        sql.append("order by l.id desc limit 200");
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), args.toArray());
        List<Map<String, Object>> out = new ArrayList<>();
        Long tid = TenantContext.getTenantId();
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            r.forEach((k, v) -> {
                if (k == null) return;
                StringBuilder sb = new StringBuilder();
                boolean up = false;
                for (char c : k.toCharArray()) {
                    if (c == '_') { up = true; continue; }
                    sb.append(up ? Character.toUpperCase(c) : c);
                    up = false;
                }
                if (v instanceof java.sql.Timestamp ts) v = ts.toLocalDateTime().toString().replace('T', ' ');
                m.put(sb.toString(), v);
            });
            String it = String.valueOf(m.getOrDefault("itemType", "found"));
            m.put("itemTypeLabel", "lost".equalsIgnoreCase(it) ? "失物" : "招领");
            String st = String.valueOf(m.getOrDefault("status", ""));
            m.put("statusLabel", "found".equals(st) || "closed".equals(st) ? "已找到/已认领" : "open".equals(st) ? "进行中" : st);

            // 补全历史数据缺失的房屋展示名
            Object houseLabel = m.get("houseLabel");
            Object houseIdObj = m.get("houseId");
            if (!hasTextObj(houseLabel) && houseIdObj != null) {
                String label = resolveHouseLabel(tid, Long.valueOf(String.valueOf(houseIdObj)));
                if (StringUtils.hasText(label)) {
                    m.put("houseLabel", label);
                }
            }
            if (!hasTextObj(m.get("houseLabel")) && m.get("userId") != null) {
                Long hid = findDefaultHouseId(tid, Long.valueOf(String.valueOf(m.get("userId"))));
                if (hid != null) {
                    m.put("houseId", hid);
                    String label = resolveHouseLabel(tid, hid);
                    if (StringUtils.hasText(label)) {
                        m.put("houseLabel", label);
                    }
                }
            }

            Object img = m.get("imageUrl");
            if (img != null) {
                m.put("imageUrl", normalizeStoredImageUrl(String.valueOf(img)));
            }
            // 统一主键，避免驱动返回 ID / id 不一致导致前端删除失败
            Object rid = m.get("id");
            if (rid == null) {
                rid = m.get("ID");
            }
            if (rid != null) {
                m.put("id", Long.valueOf(String.valueOf(rid)));
            }
            out.add(m);
        }
        return out;
    }

    private Long parseLong(String s) {
        if (!StringUtils.hasText(s)) return null;
        try {
            return Long.valueOf(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private String blankToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    private boolean hasTextObj(Object o) {
        return o != null && StringUtils.hasText(String.valueOf(o)) && !"null".equalsIgnoreCase(String.valueOf(o));
    }

    /** App 可能把 /api 或完整网关地址写入库，统一成相对路径便于管理端预览 */
    private String normalizeStoredImageUrl(String url) {
        if (!StringUtils.hasText(url)) return null;
        String u = url.trim();
        if (u.startsWith("blob:") || u.startsWith("data:")) return u;
        if (u.startsWith("http://") || u.startsWith("https://")) {
            try {
                java.net.URI uri = java.net.URI.create(u);
                u = uri.getPath() == null ? u : uri.getPath();
            } catch (Exception ignored) {
            }
        }
        while (u.startsWith("/api/")) {
            u = u.substring(4);
        }
        if (!u.startsWith("/")) u = "/" + u;
        return u;
    }

    private Long findDefaultHouseId(Long tid, Long uid) {
        if (tid == null || uid == null) return null;
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "select house_id as houseId from user_house_bind where tenant_id=? and user_id=? "
                            + "order by is_default desc, id asc limit 1",
                    tid, uid);
            if (rows.isEmpty() || rows.get(0).get("houseId") == null) return null;
            return Long.valueOf(String.valueOf(rows.get(0).get("houseId")));
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveHouseLabel(Long tid, Long houseId) {
        if (tid == null || houseId == null) return null;
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "select community_name as communityName, building, unit, room_no as roomNo "
                            + "from community_house where tenant_id=? and id=? limit 1",
                    tid, houseId);
            if (rows.isEmpty()) return null;
            Map<String, Object> h = rows.get(0);
            String building = strObj(h.get("building"));
            String unit = strObj(h.get("unit"));
            String room = strObj(h.get("roomNo"));
            String shortLabel = building + unit + room;
            String community = strObj(h.get("communityName"));
            if (StringUtils.hasText(community) && StringUtils.hasText(shortLabel)) {
                return community + " · " + shortLabel;
            }
            return StringUtils.hasText(shortLabel) ? shortLabel : community;
        } catch (Exception e) {
            return null;
        }
    }

    private String findUserNickname(Long uid) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "select nickname from sys_user where id=? limit 1", uid);
            if (rows.isEmpty()) return null;
            Object n = rows.get(0).get("nickname");
            return n == null ? null : String.valueOf(n);
        } catch (Exception e) {
            return null;
        }
    }

    private String strObj(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    public Map<String, Object> markLostFound(Long id) {
        assertPropertyAdminOnly();
        int n = jdbcTemplate.update(
                "update lost_found set status='found' where id=? and tenant_id=?",
                id, TenantContext.getTenantId());
        if (n == 0) throw new BizException("记录不存在");
        return listLost(null, null).stream().filter(m -> id.equals(Long.valueOf(String.valueOf(m.get("id")))))
                .findFirst().orElse(Map.of("id", id, "status", "found"));
    }

    public void deleteLost(Long id) {
        assertPropertyAdminOnly();
        requireTenant();
        if (id == null || id <= 0) {
            throw new BizException(400, "无效的记录ID");
        }
        Long tid = TenantContext.getTenantId();
        Integer exists = jdbcTemplate.queryForObject(
                "select count(1) from lost_found where id=? and tenant_id=?",
                Integer.class, id, tid);
        if (exists == null || exists == 0) {
            throw new BizException(404, "记录不存在或无权删除");
        }
        jdbcTemplate.update("delete from lost_found where id=? and tenant_id=?", id, tid);
    }

    private void assertPropertyAdminOnly() {
        String role = TenantContext.getRole();
        if (!RoleConstants.isPropertyAdmin(role) && !RoleConstants.isPlatformAdmin(role)) {
            throw new BizException(403, "仅物业管理员可操作");
        }
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

    private void saveChatLog(String question, AiChatResponse resp) {
        try {
            ensureChatLogExt();
            AiChatLog logRow = new AiChatLog();
            logRow.setTenantId(TenantContext.getTenantId());
            logRow.setUserId(TenantContext.getUserId());
            logRow.setQuestion(question);
            logRow.setAnswer(resp.getAnswer());
            logRow.setSource(resp.getSource());
            logRow.setTransferHuman(Boolean.TRUE.equals(resp.getTransferHuman()) ? 1 : 0);
            logRow.setCreateTime(LocalDateTime.now());
            if (resp.getRagScore() != null) {
                logRow.setRagScore(resp.getRagScore());
            }
            if (StringUtils.hasText(resp.getRagChunk())) {
                String chunk = resp.getRagChunk();
                logRow.setRagChunk(chunk.length() > 4000 ? chunk.substring(0, 4000) : chunk);
            }
            if (StringUtils.hasText(resp.getRagDocTitle())) {
                logRow.setRagDocTitle(resp.getRagDocTitle());
            }
            try {
                Long uid = TenantContext.getUserId();
                if (uid != null) {
                    List<Map<String, Object>> u = jdbcTemplate.queryForList(
                            "select nickname from sys_user where id=? limit 1", uid);
                    if (!u.isEmpty() && u.get(0).get("nickname") != null) {
                        logRow.setUserNickname(String.valueOf(u.get(0).get("nickname")));
                    }
                }
            } catch (Exception ignored) {
            }
            chatLogMapper.insert(logRow);
            resp.setLogId(logRow.getId());
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
            return aiFeatureService.loadMergedSensitiveWords();
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
