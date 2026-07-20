package com.community.common.feign.dto;

import lombok.Data;

@Data
public class AiChatResponse {
    private String answer;
    private String source;
    private String provider;
    private Boolean transferHuman;
}
