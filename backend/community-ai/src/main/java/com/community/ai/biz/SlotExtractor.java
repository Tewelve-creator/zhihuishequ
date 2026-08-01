package com.community.ai.biz;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.community.ai.config.AiProviderGateway;
import com.community.ai.mock.ThirdPartyAiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从自然语言抽取业务实体字段
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SlotExtractor {

    private final ThirdPartyAiClient thirdPartyAiClient;
    private final AiProviderGateway providerGateway;

    private static final Pattern PHONE = Pattern.compile("1[3-9]\\d{9}");
    private static final Pattern PLATE = Pattern.compile(
            "[京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤青藏川宁琼使领][A-HJ-NP-Z][A-HJ-NP-Z0-9]{4,6}[A-HJ-NP-Z0-9挂学警港澳]?");
    private static final Pattern NAME1 = Pattern.compile("(?:姓名|叫|访客)[是为:：]?\\s*([\\u4e00-\\u9fa5]{2,4})");
    private static final Pattern NAME2 = Pattern.compile("([\\u4e00-\\u9fa5]{2,4})(?:来访|过来|到访)");
    private static final Pattern DT = Pattern.compile(
            "(20\\d{2})[-/年](\\d{1,2})[-/月](\\d{1,2})[日号]?\\s*(\\d{1,2})[:：点](\\d{1,2})?");

    public Map<String, Object> extract(BizIntent intent, String text, Map<String, Object> existing) {
        Map<String, Object> slots = new LinkedHashMap<>();
        if (existing != null) {
            slots.putAll(existing);
        }
        if (!StringUtils.hasText(text)) {
            return slots;
        }
        String q = text.trim();
        if ("跳过".equals(q) || "无".equals(q) || "没有".equals(q)) {
            if (intent == BizIntent.VISIT_CREATE && !slots.containsKey("visitorPhone")) {
                slots.put("visitorPhone", "");
            }
            if (intent == BizIntent.VISIT_CREATE && !slots.containsKey("expireTime")
                    && slots.containsKey("visitTime")) {
                slots.put("expireTime", "");
            }
            // 仅在已填写故障描述后，才允许跳过现场照片
            if (intent == BizIntent.REPAIR_CREATE
                    && (hasText(slots.get("description")) || hasText(slots.get("title")))
                    && !slots.containsKey("imageUrl")) {
                slots.put("imageUrl", "");
            }
        }

        mergeRules(intent, q, slots);
        if (useHttp() && intent == BizIntent.VISIT_CREATE) {
            try {
                mergeLlmVisit(q, slots);
            } catch (Exception e) {
                log.debug("slot llm skip: {}", e.getMessage());
            }
        }
        if (useHttp() && intent == BizIntent.REPAIR_CREATE) {
            try {
                mergeLlmRepair(q, slots);
            } catch (Exception e) {
                log.debug("slot llm skip: {}", e.getMessage());
            }
            // 防止 LLM 把「我要报修」口令当成故障描述
            Object desc = slots.get("description");
            if (hasText(desc) && isRepairTriggerOnly(String.valueOf(desc))) {
                slots.remove("description");
                slots.remove("title");
            }
        }
        return slots;
    }

    private void mergeRules(BizIntent intent, String q, Map<String, Object> slots) {
        Matcher phone = PHONE.matcher(q);
        if (phone.find() && !hasText(slots.get("visitorPhone"))) {
            slots.put("visitorPhone", phone.group());
        }
        Matcher plate = PLATE.matcher(q.replace(" ", "").toUpperCase());
        if (plate.find()) {
            slots.put("plateNo", plate.group());
            slots.put("driveFlag", true);
        }
        if (containsAny(q, "不开车", "不驾车", "步行", "骑车", "地铁", "打车来")) {
            slots.put("driveFlag", false);
            slots.putIfAbsent("plateNo", "");
        } else if (containsAny(q, "开车", "驾车", "开车来", "有车")) {
            slots.put("driveFlag", true);
        }

        Matcher n1 = NAME1.matcher(q);
        if (n1.find() && !hasText(slots.get("visitorName"))) {
            slots.put("visitorName", n1.group(1));
        } else {
            Matcher n2 = NAME2.matcher(q);
            if (n2.find() && !hasText(slots.get("visitorName"))) {
                String name = n2.group(1);
                if (!List.of("明天", "今天", "后天", "客人", "朋友", "亲戚").contains(name)) {
                    slots.put("visitorName", name);
                }
            }
        }

        parseRelativeTime(q, slots);
        Matcher dt = DT.matcher(q);
        if (dt.find()) {
            String start = fmt(dt.group(1), dt.group(2), dt.group(3), dt.group(4),
                    dt.group(5) == null ? "0" : dt.group(5));
            if (!hasText(slots.get("visitTime"))) {
                slots.put("visitTime", start);
            } else if (!hasText(slots.get("expireTime"))) {
                slots.put("expireTime", start);
            }
        }

        if (containsAny(q, "探亲", "访友", "装修", "维修", "送货", "快递", "看望")) {
            for (String r : List.of("探亲", "访友", "装修", "维修", "送货", "快递", "看望")) {
                if (q.contains(r)) {
                    slots.put("visitReason", r.equals("看望") ? "探亲" : r);
                    break;
                }
            }
        }

        if (intent == BizIntent.REPAIR_CREATE) {
            if (containsAny(q, "水管", "漏水", "水龙头")) {
                slots.putIfAbsent("facilityType", "水管");
            } else if (containsAny(q, "电梯")) {
                slots.putIfAbsent("facilityType", "电梯");
            } else if (containsAny(q, "门禁", "门锁")) {
                slots.putIfAbsent("facilityType", "门禁");
            } else if (containsAny(q, "电路", "灯", "停电")) {
                slots.putIfAbsent("facilityType", "电路");
            }
            // 上传后的图片路径/URL（前端上传后作为消息发送）
            if (looksLikeImageRef(q) && !slots.containsKey("imageUrl")) {
                slots.put("imageUrl", q);
            }
            // 纯触发语（我要报修/保修）不写入描述，继续追问位置与现象
            if (!isRepairTriggerOnly(q) && !isSkipPhrase(q)) {
                if (!hasText(slots.get("title")) && q.length() <= 40 && !looksLikeImageRef(q)) {
                    slots.put("title", q.length() > 20 ? q.substring(0, 20) : q);
                }
                if (!hasText(slots.get("description")) && !looksLikeImageRef(q)) {
                    slots.put("description", q);
                }
            }
        }
    }

    private boolean isSkipPhrase(String q) {
        return "跳过".equals(q) || "无".equals(q) || "没有".equals(q);
    }

    /** 仅发起报修的口令，不含具体故障描述 */
    private boolean isRepairTriggerOnly(String q) {
        if (!StringUtils.hasText(q)) return true;
        String s = q.trim()
                .replaceAll("[，。！？、\\s]+", "")
                .replace("一下", "")
                .replace("帮忙", "")
                .replace("帮我", "")
                .replace("请", "");
        return Set.of(
                "报修", "保修", "报事", "我要报修", "我要保修", "我要报事",
                "发起报修", "发起保修", "帮我报修", "帮我保修",
                "想报修", "想保修", "报个修", "保个修"
        ).contains(s);
    }

    private boolean looksLikeImageRef(String q) {
        if (!StringUtils.hasText(q)) return false;
        String s = q.trim();
        return s.startsWith("/uploads/")
                || s.startsWith("http://") || s.startsWith("https://")
                || s.matches("(?i).+\\.(jpg|jpeg|png|gif|webp|bmp)(\\?.*)?$");
    }

    private void parseRelativeTime(String q, Map<String, Object> slots) {
        LocalDate base = LocalDate.now();
        if (q.contains("后天")) {
            base = base.plusDays(2);
        } else if (q.contains("明天")) {
            base = base.plusDays(1);
        } else if (q.contains("今天")) {
            base = LocalDate.now();
        } else {
            return;
        }
        int hour = 10;
        int minute = 0;
        Matcher hm = Pattern.compile("(\\d{1,2})[:：点](\\d{1,2})?").matcher(q);
        if (hm.find()) {
            hour = Integer.parseInt(hm.group(1));
            minute = hm.group(2) == null ? 0 : Integer.parseInt(hm.group(2));
        } else if (q.contains("上午")) {
            hour = 10;
        } else if (q.contains("下午")) {
            hour = 15;
        } else if (q.contains("晚上")) {
            hour = 19;
        }
        LocalDateTime ldt = LocalDateTime.of(base, LocalTime.of(Math.min(hour, 23), Math.min(minute, 59)));
        if (!hasText(slots.get("visitTime"))) {
            slots.put("visitTime", ldt.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
    }

    private void mergeLlmVisit(String q, Map<String, Object> slots) {
        String sys = """
                从居民话语抽取访客预约字段，只返回 JSON：
                visitorName,visitorPhone,driveFlag(true/false/null),plateNo,visitTime(yyyy-MM-dd HH:mm:ss),
                expireTime(yyyy-MM-dd HH:mm:ss),visitReason
                未知字段用 null。不要解释。
                """;
        String raw = thirdPartyAiClient.chatCompletion(sys, q);
        JSONObject obj = parseJson(raw);
        if (obj == null) return;
        putIfAbsentText(slots, "visitorName", obj.getString("visitorName"));
        putIfAbsentText(slots, "visitorPhone", obj.getString("visitorPhone"));
        putIfAbsentText(slots, "plateNo", obj.getString("plateNo"));
        putIfAbsentText(slots, "visitTime", obj.getString("visitTime"));
        putIfAbsentText(slots, "expireTime", obj.getString("expireTime"));
        putIfAbsentText(slots, "visitReason", obj.getString("visitReason"));
        if (obj.get("driveFlag") != null && slots.get("driveFlag") == null) {
            slots.put("driveFlag", obj.getBoolean("driveFlag"));
        }
    }

    private void mergeLlmRepair(String q, Map<String, Object> slots) {
        String sys = """
                抽取报修字段，只返回 JSON：title,description,facilityType,damageLevel
                未知用 null。
                """;
        JSONObject obj = parseJson(thirdPartyAiClient.chatCompletion(sys, q));
        if (obj == null) return;
        putIfAbsentText(slots, "title", obj.getString("title"));
        putIfAbsentText(slots, "description", obj.getString("description"));
        putIfAbsentText(slots, "facilityType", obj.getString("facilityType"));
        putIfAbsentText(slots, "damageLevel", obj.getString("damageLevel"));
    }

    public List<String> missingRequired(BizIntent intent, Map<String, Object> slots) {
        List<String> miss = new ArrayList<>();
        if (intent == BizIntent.VISIT_CREATE) {
            if (!hasText(slots.get("visitorName"))) miss.add("visitorName");
            if (slots.get("driveFlag") == null) miss.add("driveFlag");
            if (Boolean.TRUE.equals(asBool(slots.get("driveFlag"))) && !hasText(slots.get("plateNo"))) {
                miss.add("plateNo");
            }
            if (!hasText(slots.get("visitTime"))) miss.add("visitTime");
        } else if (intent == BizIntent.REPAIR_CREATE) {
            if (!hasText(slots.get("title")) && !hasText(slots.get("description"))) {
                miss.add("description");
            } else if (!slots.containsKey("imageUrl")) {
                // 描述齐全后追问现场照片（可跳过）
                miss.add("imageUrl");
            }
        }
        // 查询类无必填（房屋由上下文注入）
        return miss;
    }

    public String askPrompt(String field) {
        return switch (field) {
            case "visitorName" -> "请告诉我访客姓名。";
            case "visitorPhone" -> "访客手机号是多少？（没有可回复「跳过」）";
            case "driveFlag" -> "访客是否驾车来访？请回复「开车」或「不开车」。";
            case "plateNo" -> "请提供访客车牌号，例如：京A12345。";
            case "visitTime" -> "请来访开始时间？可以说「明天上午10点」或「2026-07-27 14:00」。";
            case "expireTime" -> "来访结束时间？没有可回复「跳过」（默认到访后12小时有效）。";
            case "visitReason" -> "来访事由是什么？（如探亲、维修）";
            case "description" -> "请告诉我问题在哪、什么故障现象（例如：3栋2单元水管漏水）。";
            case "imageUrl" -> "请上传一张现场照片（点下方「拍照上传」）；没有照片可回复「跳过」。";
            case "title" -> "请给报修起个简短标题。";
            case "facilityType" -> "是什么设施出问题了？（如水管、电梯、门禁）";
            default -> "请补充：" + field;
        };
    }

    public String fieldLabel(String field) {
        return switch (field) {
            case "visitorName" -> "访客姓名";
            case "visitorPhone" -> "访客电话";
            case "driveFlag" -> "是否驾车";
            case "plateNo" -> "车牌号";
            case "visitTime" -> "来访开始时间";
            case "expireTime" -> "来访结束时间";
            case "visitReason" -> "来访事由";
            case "title" -> "报修标题";
            case "description" -> "报修描述";
            case "facilityType" -> "设施类型";
            case "damageLevel" -> "损坏程度";
            case "imageUrl" -> "现场照片";
            case "houseId" -> "房屋";
            default -> field;
        };
    }

    public List<Map<String, String>> buildPreview(BizIntent intent, Map<String, Object> slots) {
        List<Map<String, String>> list = new ArrayList<>();
        List<String> keys = switch (intent) {
            case VISIT_CREATE -> List.of("visitorName", "visitorPhone", "driveFlag", "plateNo",
                    "visitTime", "expireTime", "visitReason");
            case REPAIR_CREATE -> List.of("title", "description", "facilityType", "damageLevel", "imageUrl");
            case FEE_UNPAID -> List.of("houseId");
            case CAR_LIST -> List.of("houseId");
            case VISIT_LIST -> List.of();
        };
        for (String k : keys) {
            Object v = slots.get(k);
            if (v == null || "".equals(String.valueOf(v))) {
                if ("visitorPhone".equals(k) || "expireTime".equals(k) || "plateNo".equals(k)
                        || "visitReason".equals(k) || "damageLevel".equals(k) || "imageUrl".equals(k)) {
                    continue;
                }
                if ("driveFlag".equals(k)) continue;
            }
            String display;
            if ("driveFlag".equals(k)) {
                display = Boolean.TRUE.equals(asBool(v)) ? "是" : "否";
            } else if ("houseId".equals(k)) {
                display = v == null ? "当前房屋" : String.valueOf(v);
            } else if ("imageUrl".equals(k)) {
                display = "已上传";
            } else {
                display = String.valueOf(v);
            }
            if ("driveFlag".equals(k) && v == null) continue;
            Map<String, String> row = new LinkedHashMap<>();
            row.put("key", k);
            row.put("label", fieldLabel(k));
            row.put("value", display);
            list.add(row);
        }
        if (intent == BizIntent.FEE_UNPAID) {
            list.add(Map.of("key", "tab", "label", "查询范围", "value", "未缴账单"));
        }
        if (intent == BizIntent.VISIT_LIST) {
            list.add(Map.of("key", "status", "label", "状态", "value", "已通过（有效）"));
        }
        if (intent == BizIntent.CAR_LIST && list.isEmpty()) {
            list.add(Map.of("key", "scope", "label", "范围", "value", "本人名下常驻车辆"));
        }
        return list;
    }

    private void putIfAbsentText(Map<String, Object> slots, String key, String val) {
        if (hasText(val) && !hasText(slots.get(key))) {
            slots.put(key, val.trim());
        }
    }

    private JSONObject parseJson(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        String json = raw.trim();
        int i = json.indexOf('{');
        int j = json.lastIndexOf('}');
        if (i >= 0 && j > i) json = json.substring(i, j + 1);
        try {
            return JSON.parseObject(json);
        } catch (Exception e) {
            return null;
        }
    }

    private String fmt(String y, String m, String d, String h, String mi) {
        return String.format("%s-%02d-%02d %02d:%02d:00",
                y, Integer.parseInt(m), Integer.parseInt(d),
                Integer.parseInt(h), Integer.parseInt(mi));
    }

    private boolean hasText(Object o) {
        return o != null && StringUtils.hasText(String.valueOf(o));
    }

    private Boolean asBool(Object o) {
        if (o instanceof Boolean b) return b;
        if (o == null) return null;
        String s = String.valueOf(o);
        if ("true".equalsIgnoreCase(s) || "1".equals(s) || "是".equals(s)) return true;
        if ("false".equalsIgnoreCase(s) || "0".equals(s) || "否".equals(s)) return false;
        return null;
    }

    private boolean containsAny(String q, String... keys) {
        for (String k : keys) {
            if (q.contains(k)) return true;
        }
        return false;
    }

    private boolean useHttp() {
        return providerGateway.useLlm();
    }
}
