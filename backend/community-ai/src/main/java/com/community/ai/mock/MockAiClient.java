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
            resp.put("answer", "好的，已帮您记下转人工需求，社区工作人员会尽快联系您～");
            resp.put("transferHuman", true);
        } else {
            // 友好兜底：不再输出生硬的「找不到资料 / Mock AI」话术
            resp.put("answer", "这个问题我还不太确定具体制度条文呢～您可以换个说法再问，"
                    + "或者说「帮我登记访客」「我要报修」「查一下欠费」，我直接帮您办。"
                    + "（配置 AI_API_KEY 后可自动切换真实大模型）");
            resp.put("transferHuman", false);
        }
        resp.put("source", "mock_llm");
        return resp;
    }

    public Map<String, Object> imageRepair(String imageUrl) {
        String lower = nullToEmpty(imageUrl).toLowerCase(Locale.ROOT);
        String facility = "公共设施";
        String level = "中度";
        String location = "小区公共区域";
        double confidence = 0.62;
        for (String[] rule : FACILITY_RULES) {
            if (lower.contains(rule[0])) {
                facility = rule[1];
                level = rule[2];
                confidence = 0.86;
                break;
            }
        }
        if (lower.contains("灯") || lower.contains("light")) location = "楼道/路灯附近";
        else if (lower.contains("elev") || lower.contains("电梯")) location = "电梯轿厢/厅门";
        else if (lower.contains("门") || lower.contains("door")) location = "单元门/楼道门窗";
        else if (lower.contains("健身") || lower.contains("器材")) {
            facility = "健身器材";
            location = "社区健身区";
            confidence = Math.max(confidence, 0.84);
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("facilityType", facility);
        resp.put("damageLevel", level);
        resp.put("locationHint", location);
        resp.put("suggestTitle", facility + "报修");
        resp.put("suggestDesc", "【Mock识图】疑似" + facility + "（" + location + "）存在" + level + "损坏，建议尽快安排维修。");
        resp.put("confidence", confidence);
        resp.put("needManual", confidence < 0.85);
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
        boolean anger = t.contains("怒") || t.contains("骂") || t.contains("威胁") || t.contains("吵");
        boolean anxiety = t.contains("烦") || t.contains("焦虑") || t.contains("抑郁") || t.contains("崩溃") || t.contains("绝望");
        boolean negative = hit > 0;
        boolean positive = t.contains("开心") || t.contains("感谢") || t.contains("满意");
        String sentiment = negative ? "negative" : (positive ? "positive" : "neutral");
        double score = negative ? Math.max(-0.95, -0.45 - hit * 0.1) : (positive ? 0.78 : 0.2);
        List<String> tags = new ArrayList<>();
        if (anger) tags.add("愤怒");
        if (anxiety) tags.add("焦虑");
        if (positive) tags.add("正面");
        if (!negative && !positive) tags.add("中性");
        if (negative && tags.isEmpty()) tags.add("负面");
        boolean sharp = anger || hit >= 2 || score <= -0.7;
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("sentiment", sentiment);
        resp.put("score", score);
        resp.put("emotionTags", String.join(",", tags));
        resp.put("needGrid", sharp);
        resp.put("advice", sharp
                ? "检测到较尖锐负面情绪。建议：先深呼吸、避免当面争执；可先用文字冷静表达诉求。系统已可通知物业工作人员协助轻调解。"
                : (negative
                ? "情绪有些低落。建议：把具体事实和期望分开写清楚，先尝试友好沟通；需要时再请物业工作人员介入。"
                : "情绪整体平稳。如需倾诉，可继续描述具体情况，或咨询智能管家获取制度说明。"));
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
        } else if (c.length() <= 100) {
            summary = c;
        } else {
            summary = c.substring(0, 97) + "...";
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
