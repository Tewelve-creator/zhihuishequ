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
}
