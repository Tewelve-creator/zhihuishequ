package com.community.ai.biz;

import lombok.Data;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 居民业务办理会话（Redis）
 */
@Data
public class BizSession implements Serializable {
    private String intent;
    /** collecting | awaiting_confirm */
    private String status;
    private Map<String, Object> slots = new LinkedHashMap<>();
    private String actionToken;
    private Long tenantId;
    private Long userId;
    private Long houseId;
}
