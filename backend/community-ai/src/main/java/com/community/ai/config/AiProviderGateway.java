package com.community.ai.config;

import com.community.ai.mock.ThirdPartyAiClient;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Mock / 真实 LLM 自动切换：
 * - auto（默认）：配置了 ai.api-key 则走真实模型，否则 Mock
 * - mock：强制 Mock
 * - http：强制真实模型（无 key 时回退 Mock）
 */
@Component
@RequiredArgsConstructor
public class AiProviderGateway {

    private final ThirdPartyAiClient thirdPartyAiClient;

    @Value("${ai.provider:auto}")
    private String provider;

    public boolean useLlm() {
        String p = provider == null ? "auto" : provider.trim().toLowerCase();
        if ("mock".equals(p)) {
            return false;
        }
        if ("http".equals(p)) {
            return thirdPartyAiClient.available();
        }
        // auto
        return thirdPartyAiClient.available();
    }

    public String activeMode() {
        return useLlm() ? "http" : "mock";
    }

    public String configuredProvider() {
        return provider;
    }
}
