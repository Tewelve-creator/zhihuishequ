package com.community.ai.service;

import com.community.ai.config.AiProviderGateway;
import com.community.ai.mock.ThirdPartyAiClient;
import com.community.common.feign.dto.AiChatResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 闲聊 / 打招呼友好回复（不走「找不到资料」）
 */
@Component
@RequiredArgsConstructor
public class FriendlyChatHelper {

    private final AiProviderGateway providerGateway;
    private final ThirdPartyAiClient thirdPartyAiClient;

    private static final Pattern GREET = Pattern.compile(
            "^(你好|您好|hi|hello|哈喽|嗨|在吗|早上好|下午好|晚上好|小智|管家)[呀啊吗么～~!！。.\\s]*$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern THANKS = Pattern.compile(".*(谢谢|感谢|多谢).*");

    public boolean isChitchat(String text) {
        if (!StringUtils.hasText(text)) return false;
        String q = text.trim();
        if (q.length() <= 12 && GREET.matcher(q).matches()) return true;
        if (THANKS.matcher(q).matches() && q.length() <= 20) return true;
        if (q.equals("你是谁") || q.equals("你能做什么") || q.equals("你会什么")) return true;
        return false;
    }

    public AiChatResponse reply(String question) {
        String q = question == null ? "" : question.trim();
        String answer;
        String source;
        if (providerGateway.useLlm()) {
            try {
                answer = thirdPartyAiClient.chatCompletion(
                        """
                        你是智慧社区「智能管家小智」，语气亲切简洁。
                        用户在闲聊或打招呼：友好回应，并简短介绍你能：解答社区制度、帮办访客预约/报修、查询欠费与车辆。
                        不要说「找不到资料」。不要编造具体物业费金额或制度条文。
                        控制在80字内。
                        """,
                        q);
                source = "llm_chitchat";
            } catch (Exception e) {
                answer = localFriendly(q);
                source = "mock_chitchat";
            }
        } else {
            answer = localFriendly(q);
            source = "mock_chitchat";
        }
        AiChatResponse r = AiChatResponse.ofRag(answer, source, providerGateway.activeMode(), false);
        r.setMode("CHITCHAT");
        return r;
    }

    /** 无知识库命中且 Mock 时的常识回答（调用方会追加非官方温馨提示） */
    public String generalAnswer(String question) {
        String q = question == null ? "" : question.trim();
        if (q.contains("天气") || q.contains("下雨")) {
            return "天气类问题请以当地气象预报为准；出门记得看雨具安排。小区事务如有疑问可联系物业值班室。";
        }
        if (q.contains("快递") || q.contains("外卖")) {
            return "一般小区会设快递柜或门岗代收，具体点位与时段以本小区公示为准，也可询问物业前台。";
        }
        return "我先按常识帮您说明一下。若涉及本小区收费、规章或办事流程，建议您再向物业核实最新规定。您也可以问我访客登记、报修或查欠费。";
    }

    /** @deprecated 已改为允许常识作答，保留方法以免外部误用编译失败 */
    public AiChatResponse softReject(String question) {
        String tip = generalAnswer(question);
        if (!tip.contains("不属于本小区官方规定")) {
            tip = tip + "\n\n⚠温馨提示：该内容不属于本小区官方规定，仅供参考，小区相关事务建议联系物业核实。";
        }
        AiChatResponse r = AiChatResponse.ofRag(tip, "llm_general", providerGateway.activeMode(), false);
        r.setMode("LLM");
        return r;
    }

    private String localFriendly(String q) {
        String lower = q.toLowerCase(Locale.ROOT);
        if (THANKS.matcher(q).matches()) {
            return "不客气呀～有制度问题或要办访客、报修、查账单，随时叫我「小智」就好。";
        }
        if (q.contains("你是谁") || q.contains("你会什么") || q.contains("能做什么")) {
            return "我是社区智能管家「小智」～可以解答物业制度，也能帮您登记访客、发起报修、查询欠费和车辆。办理前都会请您确认哦。";
        }
        if (GREET.matcher(q).matches() || lower.contains("hello") || lower.contains("hi")) {
            return "哈喽～我是智能管家小智！问制度、登记访客、报修、查欠费都可以找我，直接说就行。";
        }
        return "我在呢～您可以问社区制度，或说「帮我登记访客」「我要报修」「查一下欠费」。";
    }
}
