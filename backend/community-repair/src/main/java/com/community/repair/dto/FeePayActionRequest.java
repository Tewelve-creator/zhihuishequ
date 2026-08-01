package com.community.repair.dto;

import lombok.Data;

@Data
public class FeePayActionRequest {
    /** success / fail / cancel / pending */
    private String action;
}
