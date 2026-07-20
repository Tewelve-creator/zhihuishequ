package com.community.saas.service;

import com.community.common.exception.BizException;
import com.community.common.feign.AiFeignClient;
import com.community.common.feign.dto.AiAuditRequest;
import com.community.common.feign.dto.AiAuditResponse;
import com.community.common.feign.dto.AiChatRequest;
import com.community.common.feign.dto.AiChatResponse;
import com.community.common.result.R;
import com.community.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Feign 调用 community-ai 示例（saas 侧）
 */
@Service
@RequiredArgsConstructor
public class AiFeignExampleService {

    private final AiFeignClient aiFeignClient;

    /** 示例：运营预览公告 AI 摘要 + 敏感词 */
    public AiAuditResponse previewNotice(String content) {
        AiAuditRequest req = new AiAuditRequest();
        req.setContent(content);
        req.setBizType("notice");
        req.setTenantId(TenantContext.getTenantId());
        R<AiAuditResponse> r = aiFeignClient.noticeSummary(req);
        if (r == null || r.getData() == null) {
            throw new BizException("AI摘要调用失败");
        }
        return r.getData();
    }

    /** 示例：代居民问答（运营客服） */
    public AiChatResponse proxyChat(String question) {
        AiChatRequest req = new AiChatRequest();
        req.setQuestion(question);
        req.setTenantId(TenantContext.getTenantId());
        req.setUserId(TenantContext.getUserId());
        R<AiChatResponse> r = aiFeignClient.chat(req);
        if (r == null || r.getData() == null) {
            throw new BizException("AI问答调用失败");
        }
        return r.getData();
    }
}
