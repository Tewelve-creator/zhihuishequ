package com.community.common.feign.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class AiChatResponse {
    private String answer;
    private String source;
    private String provider;
    private Boolean transferHuman;

    /** RAG | BIZ_ASK | BIZ_CONFIRM | BIZ_RESULT | BIZ_CANCEL */
    private String mode;
    /** VISIT_CREATE / REPAIR_CREATE / FEE_UNPAID / CAR_LIST / VISIT_LIST */
    private String intent;
    private Boolean needConfirm;
    /** 确认执行用令牌，禁止未确认写库 */
    private String actionToken;
    private Map<String, Object> slots;
    /** 确认弹窗展示字段 */
    private List<Map<String, String>> preview;
    private List<String> missingFields;
    /** 查询类确认后的结构化数据 */
    private Object data;
    /** 命中社区规则等文档时的来源文件名 */
    private List<String> sourceDocs;
    /** 落库后的对话日志 ID，供评价 */
    private Long logId;

    /** RAG 召回：相似度得分（0~1） */
    private Double ragScore;
    /** RAG 召回片段正文 */
    private String ragChunk;
    /** RAG 命中文档/问答标题 */
    private String ragDocTitle;
    /** qa | document */
    private String ragHitType;

    public static AiChatResponse ofRag(String answer, String source, String provider, boolean transfer) {
        AiChatResponse r = new AiChatResponse();
        r.setAnswer(answer);
        r.setSource(source);
        r.setProvider(provider);
        r.setTransferHuman(transfer);
        r.setMode("RAG");
        r.setNeedConfirm(false);
        return r;
    }

    public AiChatResponse withSlots(Map<String, Object> s) {
        this.slots = s == null ? null : new LinkedHashMap<>(s);
        return this;
    }
}
