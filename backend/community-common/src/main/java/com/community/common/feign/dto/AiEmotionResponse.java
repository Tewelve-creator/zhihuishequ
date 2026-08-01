package com.community.common.feign.dto;

import lombok.Data;

@Data
public class AiEmotionResponse {
    private String sentiment;
    private Double score;
    private Boolean needGrid;
    private String advice;
    private Boolean appealCreated;
    private Long appealId;
    /** 细分类：愤怒/焦虑/中性/正面 等，逗号分隔 */
    private String emotionTags;
    /** 独立调解工单 id（ai_emotion_case） */
    private Long caseId;
}
