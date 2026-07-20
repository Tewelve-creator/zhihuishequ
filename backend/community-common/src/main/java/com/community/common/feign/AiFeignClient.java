package com.community.common.feign;

import com.community.common.feign.dto.*;
import com.community.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * community-ai Feign 接口（供 saas / repair 调用）
 * <p>url 本地直连：services.ai-url；生产可改为 name=community-ai + Nacos
 */
@FeignClient(name = "community-ai", url = "${services.ai-url:http://127.0.0.1:8084}", path = "/ai")
public interface AiFeignClient {

    @PostMapping("/chat")
    R<AiChatResponse> chat(@RequestBody AiChatRequest request);

    @PostMapping("/audit")
    R<AiAuditResponse> audit(@RequestBody AiAuditRequest request);

    @PostMapping("/notice/summary")
    R<AiAuditResponse> noticeSummary(@RequestBody AiAuditRequest request);

    @PostMapping("/image")
    R<AiImageRepairResponse> imageRepair(@RequestBody AiImageRepairRequest request);

    @PostMapping("/emotion")
    R<AiEmotionResponse> emotion(@RequestBody AiEmotionRequest request);

    @PostMapping("/lost/match")
    R<AiLostMatchResponse> lostMatch(@RequestBody AiLostMatchRequest request);
}
