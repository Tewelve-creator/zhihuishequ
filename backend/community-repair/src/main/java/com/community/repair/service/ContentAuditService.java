package com.community.repair.service;

import com.community.common.exception.BizException;
import com.community.common.feign.AiFeignClient;
import com.community.common.feign.dto.AiAuditRequest;
import com.community.common.feign.dto.AiAuditResponse;
import com.community.common.feign.dto.AiImageRepairRequest;
import com.community.common.feign.dto.AiImageRepairResponse;
import com.community.common.result.R;
import com.community.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 内容审核：优先 Feign 调用 community-ai，失败则本地敏感词兜底
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentAuditService {

    private final JdbcTemplate jdbcTemplate;

    @Autowired(required = false)
    private AiFeignClient aiFeignClient;

    public Map<String, Object> audit(String content) {
        if (content == null) {
            content = "";
        }
        // Feign → AI 模块（摘要 + 敏感词）
        if (aiFeignClient != null) {
            try {
                AiAuditRequest req = new AiAuditRequest();
                req.setContent(content);
                req.setBizType("notice");
                req.setTenantId(TenantContext.getTenantId());
                R<AiAuditResponse> r = aiFeignClient.noticeSummary(req);
                if (r != null && r.getData() != null) {
                    AiAuditResponse data = r.getData();
                    if (!Boolean.TRUE.equals(data.getPass())) {
                        throw new BizException(400, "内容含敏感词，禁止发布："
                                + String.join(",", data.getHitWords() == null ? List.of() : data.getHitWords()));
                    }
                    Map<String, Object> result = new HashMap<>();
                    result.put("pass", true);
                    result.put("hitWords", data.getHitWords());
                    result.put("summary", data.getSummary());
                    result.put("source", "feign-ai");
                    return result;
                }
            } catch (BizException e) {
                throw e;
            } catch (Exception e) {
                log.warn("Feign AI audit fallback local: {}", e.getMessage());
            }
        }
        return localAudit(content);
    }

    /** Feign 示例：报修识图自动填充故障类型 */
    public AiImageRepairResponse recognizeRepairImage(String imageUrl) {
        if (aiFeignClient == null) {
            throw new BizException("AI Feign 未启用");
        }
        AiImageRepairRequest req = new AiImageRepairRequest();
        req.setImageUrl(imageUrl);
        req.setTenantId(TenantContext.getTenantId());
        req.setUserId(TenantContext.getUserId());
        R<AiImageRepairResponse> r = aiFeignClient.imageRepair(req);
        if (r == null || r.getData() == null) {
            throw new BizException("AI识图失败");
        }
        return r.getData();
    }

    private Map<String, Object> localAudit(String content) {
        List<String> words;
        try {
            words = jdbcTemplate.queryForList("select word from sys_sensitive_word", String.class);
        } catch (Exception e) {
            words = List.of("赌博", "色情", "诈骗");
        }
        List<String> hit = new ArrayList<>();
        for (String w : words) {
            if (w != null && !w.isBlank() && content.contains(w)) {
                hit.add(w);
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("pass", hit.isEmpty());
        result.put("hitWords", hit);
        result.put("summary", content.length() > 50 ? content.substring(0, 50) + "..." : content);
        result.put("source", "local");
        if (!hit.isEmpty()) {
            throw new BizException(400, "内容含敏感词，禁止发布：" + String.join(",", hit));
        }
        return result;
    }
}
