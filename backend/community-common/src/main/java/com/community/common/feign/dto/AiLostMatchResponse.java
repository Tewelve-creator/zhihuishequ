package com.community.common.feign.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class AiLostMatchResponse {
    private List<Map<String, Object>> matched;
    private String tip;
    private String provider;
}
