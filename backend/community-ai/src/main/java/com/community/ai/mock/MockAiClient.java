package com.community.ai.mock;

import org.springframework.stereotype.Component;

import java.util.*;

/**
 * AI Mock 模拟工具类（默认启用）
 * <p>统一封装演示期规则引擎结果；切换 provider=http 时走 {@link ThirdPartyAiClient}。
 */
@Component
public class MockAiClient {

    private static final List<String[]> FACILITY_RULES = List.of(
            new String[]{"elev", "电梯", "中度"},
            new String[]{"电梯", "电梯", "中度"},
            new String[]{"water", "水管", "轻度"},
            new String[]{"漏水", "水管", "重度"},
            new String[]{"灯", "楼道照明", "轻度"},
            new String[]{"light", "楼道照明", "轻度"},
            new String[]{"门", "门禁/单元门", "中度"},
            new String[]{"door", "门禁/单元门", "中度"},
            new String[]{"park", "停车设施", "轻度"},
            new String[]{"消防", "消防设施", "重度"}
    );

    private static final Set<String> NEGATIVE = Set.of(
            "吵", "怒", "气", "烦", "差", "投诉", "矛盾", "骚扰", "威胁", "抑郁", "绝望", "崩溃", "骂"
    );

    public Map<String, Object> chat(String question, Optional<String> kbAnswer) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("provider", "mock");
        if (kbAnswer.isPresent()) {
            resp.put("answer", kbAnswer.get());
            resp.put("source", "tenant_kb");
            resp.put("transferHuman", false);
            return resp;
        }
        boolean human = question != null && (question.contains("人工") || question.contains("投诉"));
        if (human) {
            resp.put("answer", "【Mock AI】已为您登记转人工，社区工作人员将尽快联系您。");
            resp.put("transferHuman", true);
        } else {
            resp.put("answer", "【Mock AI社区助手】关于「" + nullToEmpty(question)
                    + "」：建议先查看本社区知识库或联系物业前台。"
                    + "（配置 ai.provider=http 与 ai.api-key 可切换真实大模型）");
            resp.put("transferHuman", false);
        }
        resp.put("source", "mock_llm");
        return resp;
    }

    public Map<String, Object> imageRepair(String imageUrl) {
        String lower = nullToEmpty(imageUrl).toLowerCase(Locale.ROOT);
        String facility = "公共设施";
        String level = "中度";
        double confidence = 0.62;
        for (String[] rule : FACILITY_RULES) {
            if (lower.contains(rule[0])) {
                facility = rule[1];
                level = rule[2];
                confidence = 0.86;
                break;
            }
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("facilityType", facility);
        resp.put("damageLevel", level);
        resp.put("suggestTitle", facility + "报修");
        resp.put("suggestDesc", "【Mock识图】疑似" + facility + "存在" + level + "损坏，建议尽快安排维修。图片：" + imageUrl);
        resp.put("confidence", confidence);
        resp.put("provider", "mock");
        return resp;
    }

    public Map<String, Object> emotion(String text) {
        String t = nullToEmpty(text);
        int hit = 0;
        for (String w : NEGATIVE) {
            if (t.contains(w)) {
                hit++;
            }
        }
        boolean negative = hit > 0;
        double score = negative ? Math.max(-0.95, -0.45 - hit * 0.1) : 0.62;
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("sentiment", negative ? "negative" : (t.contains("开心") || t.contains("感谢") ? "positive" : "neutral"));
        resp.put("score", score);
        resp.put("needGrid", negative);
        resp.put("advice", negative ? "【Mock】检测到负面情绪，已建议推送网格员轻调解" : "【Mock】情绪平稳，可继续AI陪伴疏导");
        resp.put("provider", "mock");
        return resp;
    }

    public Map<String, Object> summaryAndAudit(String content, List<String> sensitiveWords) {
        String c = nullToEmpty(content);
        List<String> hit = new ArrayList<>();
        for (String w : sensitiveWords) {
            if (w != null && !w.isBlank() && c.contains(w)) {
                hit.add(w);
            }
        }
        String summary;
        if (c.isBlank()) {
            summary = "";
        } else if (c.length() <= 40) {
            summary = c;
        } else {
            summary = "【Mock摘要】" + c.substring(0, 36) + "...";
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("pass", hit.isEmpty());
        resp.put("summary", summary);
        resp.put("hitWords", hit);
        resp.put("source", "mock");
        resp.put("provider", "mock");
        return resp;
    }

    public double lostSimilarity(String query, String title, String desc, String imageUrl, String candidateImage) {
        String q = nullToEmpty(query);
        String t = nullToEmpty(title);
        String d = nullToEmpty(desc);
        double score = 0;
        if (!q.isBlank()) {
            if (t.contains(q) || q.contains(t)) {
                score += 0.55;
            }
            if (d.contains(q) || q.contains(d)) {
                score += 0.35;
            }
            for (String token : q.split("[\\s,，、]+")) {
                if (token.length() >= 2 && (t.contains(token) || d.contains(token))) {
                    score += 0.08;
                }
            }
        }
        if (imageUrl != null && imageUrl.equals(candidateImage)) {
            score += 0.5;
        }
        // Mock：图片后缀相同给一点分
        if (imageUrl != null && candidateImage != null) {
            String a = imageUrl.toLowerCase(Locale.ROOT);
            String b = candidateImage.toLowerCase(Locale.ROOT);
            if (a.endsWith(".jpg") && b.endsWith(".jpg")) {
                score += 0.05;
            }
        }
        return Math.min(0.99, score);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s.trim();
    }
}
