package com.community.common.feign.dto;

import lombok.Data;

import java.util.List;

@Data
public class AiAuditResponse {
    private Boolean pass;
    private String summary;
    private List<String> hitWords;
    private String source;
}
