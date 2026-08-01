package com.community.ai.biz;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.community.ai.config.AiProviderGateway;
import com.community.ai.mock.ThirdPartyAiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 意图识别：规则优先 + LLM 分类（有密钥时）；无法识别则降级 RAG/闲聊
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntentRecognizer {

    private final ThirdPartyAiClient thirdPartyAiClient;
    private final AiProviderGateway providerGateway;

    private static final Pattern CANCEL = Pattern.compile("^(取消|算了|不要了|停止|退出)(本次|当前|操作)?$|取消(预约|报修|操作)");
    private static final Pattern CONFIRM = Pattern.compile("^(确认|确定|好的|可以|是的|没问题|同意)(提交|办理)?$");

    public boolean isCancel(String text) {
        return text != null && CANCEL.matcher(text.trim()).find();
    }

    public boolean isConfirm(String text) {
        return text != null && CONFIRM.matcher(text.trim()).find();
    }

    public Optional<BizIntent> recognize(String text) {
        if (!StringUtils.hasText(text)) {
            return Optional.empty();
        }
        String q = text.trim();

        // 纯制度咨询留给 RAG（怎么交/流程/规定），业务动词除外
        if (looksLikePolicyQa(q)) {
            return Optional.empty();
        }

        Optional<BizIntent> byRule = byRules(q);
        if (byRule.isPresent()) {
            return byRule;
        }

        if (providerGateway.useLlm()) {
            try {
                return byLlm(q);
            } catch (Exception e) {
                log.warn("intent llm fail: {}", e.getMessage());
            }
        }
        return Optional.empty();
    }

    private boolean looksLikePolicyQa(String q) {
        boolean policy = q.contains("怎么") || q.contains("如何") || q.contains("流程")
                || q.contains("规定") || q.contains("制度") || q.contains("政策") || q.contains("须知");
        boolean action = containsAny(q, "帮我", "登记", "查一下", "查询", "我的", "预约",
                "报修", "报事", "保修");
        return policy && !action;
    }

    private Optional<BizIntent> byRules(String q) {
        if (containsAny(q, "有效访客", "我的访客", "查访客", "访客预约列表", "还有哪些访客")) {
            return Optional.of(BizIntent.VISIT_LIST);
        }
        // 自然语言：帮我登记访客 / 登记一下访客 等
        if (containsAny(q, "预约访客", "访客预约", "登记访客", "来访预约", "访客登记",
                "帮我预约访客", "帮我登记访客", "登记一下访客", "帮登记访客", "临时访客", "访客帮我")) {
            return Optional.of(BizIntent.VISIT_CREATE);
        }
        if ((q.contains("访客") || q.contains("来访")) && containsAny(q, "帮我", "登记", "预约", "办理")) {
            return Optional.of(BizIntent.VISIT_CREATE);
        }
        // 「保修」为常见口误/别称，与「报修」同等识别
        if (containsAny(q, "报修", "保修", "报事", "漏水", "要修", "坏了", "电梯故障", "水管",
                "发起报修", "帮我报修", "我要报修", "我要保修", "帮我保修")) {
            return Optional.of(BizIntent.REPAIR_CREATE);
        }
        if (containsAny(q, "欠费", "未缴", "我的账单", "查账单", "有没有欠", "欠多少", "查欠费",
                "未交物业费", "该交多少", "要交多少", "交多少钱", "交多少物业费", "我交多少",
                "还有没有要交", "账单到期", "缴费截止日期", "什么时候交费", "何时交物业费")) {
            return Optional.of(BizIntent.FEE_UNPAID);
        }
        if (containsAny(q, "我的车", "常驻车辆", "名下车辆", "查车牌", "我的车牌", "车辆列表")) {
            return Optional.of(BizIntent.CAR_LIST);
        }
        return Optional.empty();
    }

    private Optional<BizIntent> byLlm(String q) {
        String sys = """
                你是智慧社区意图分类器。只返回 JSON：{"intent":"XXX"}
                intent 只能是以下之一：
                VISIT_CREATE=登记/预约访客（如「帮我登记访客」）
                REPAIR_CREATE=发起报事报修（含「报修」「保修」口误）
                FEE_UNPAID=查询本人欠费账单
                CAR_LIST=查询我的常驻车辆
                VISIT_LIST=查询有效访客预约
                NONE=制度咨询、闲聊或其他
                """;
        String raw = thirdPartyAiClient.chatCompletion(sys, q);
        if (!StringUtils.hasText(raw)) {
            return Optional.empty();
        }
        String json = raw.trim();
        int i = json.indexOf('{');
        int j = json.lastIndexOf('}');
        if (i >= 0 && j > i) {
            json = json.substring(i, j + 1);
        }
        JSONObject obj = JSON.parseObject(json);
        String intent = obj.getString("intent");
        if (!StringUtils.hasText(intent) || "NONE".equalsIgnoreCase(intent)) {
            return Optional.empty();
        }
        try {
            return Optional.of(BizIntent.valueOf(intent.trim().toUpperCase()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private boolean containsAny(String q, String... keys) {
        for (String k : keys) {
            if (q.contains(k)) return true;
        }
        return false;
    }
}
