package com.community.ai.mock;

import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.community.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.Locale;

/**
 * 真实第三方大模型对接：兼容通义 DashScope compatible-mode / OpenAI Chat Completions。
 * <p>识图需使用带视觉能力的模型（如 qwen3.6-plus、qwen-vl-plus）。
 */
@Slf4j
@Component
public class ThirdPartyAiClient {

    @Value("${ai.api-url:https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions}")
    private String apiUrl;

    @Value("${ai.api-key:}")
    private String apiKey;

    @Value("${ai.model:qwen3.6-plus}")
    private String model;

    @Value("${ai.http-timeout-ms:30000}")
    private int timeoutMs;

    /** 内网拉取本地上传图，转 base64（云端模型访问不到 localhost） */
    @Value("${ai.internal-file-base:http://127.0.0.1:8080}")
    private String internalFileBase;

    public boolean available() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String chatCompletion(String systemPrompt, String userPrompt) {
        if (!available()) {
            throw new BizException("未配置 ai.api-key，无法调用真实大模型");
        }
        try {
            JSONObject body = new JSONObject();
            body.set("model", model);
            JSONArray messages = new JSONArray();
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                messages.add(JSONUtil.createObj().set("role", "system").set("content", systemPrompt));
            }
            messages.add(JSONUtil.createObj().set("role", "user").set("content", userPrompt));
            body.set("messages", messages);

            return extractContent(postChat(body));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("大模型调用失败：" + e.getMessage());
        }
    }

    /**
     * 多模态识图：把图片以 image_url（公网或 data URL）发给视觉模型。
     */
    public String visionDescribe(String imageUrl, String prompt) {
        if (!available()) {
            throw new BizException("未配置 ai.api-key，无法调用真实大模型");
        }
        try {
            String resolved = resolveImageRef(imageUrl);
            JSONObject body = new JSONObject();
            body.set("model", model);

            JSONArray content = new JSONArray();
            content.add(JSONUtil.createObj()
                    .set("type", "image_url")
                    .set("image_url", JSONUtil.createObj().set("url", resolved)));
            content.add(JSONUtil.createObj()
                    .set("type", "text")
                    .set("text", prompt));

            JSONArray messages = new JSONArray();
            messages.add(JSONUtil.createObj()
                    .set("role", "system")
                    .set("content", "你是社区物业设施报修识图助手，只根据图片内容判断，不要编造看不见的细节。"));
            messages.add(JSONUtil.createObj().set("role", "user").set("content", content));
            body.set("messages", messages);

            return extractContent(postChat(body));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("visionDescribe failed: {}", e.getMessage());
            throw new BizException("识图调用失败：" + e.getMessage());
        }
    }

    private String postChat(JSONObject body) {
        String raw = HttpRequest.post(apiUrl)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(body.toString())
                .timeout(timeoutMs)
                .execute()
                .body();
        JSONObject json = JSONUtil.parseObj(raw);
        if (json.containsKey("error")) {
            String msg = json.getByPath("error.message", String.class);
            throw new BizException("大模型错误：" + (msg != null ? msg : raw));
        }
        return raw;
    }

    private String extractContent(String raw) {
        JSONObject json = JSONUtil.parseObj(raw);
        String content = json.getByPath("choices[0].message.content", String.class);
        if (content == null || content.isBlank()) {
            log.warn("LLM empty response: {}", raw);
            throw new BizException("大模型返回为空");
        }
        return content.trim();
    }

    /** 相对路径转可给模型的 data URL；公网 http(s) 原样使用 */
    private String resolveImageRef(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new BizException("图片地址为空");
        }
        String u = imageUrl.trim();
        if (u.startsWith("data:image")) {
            return u;
        }
        if (u.startsWith("http://") || u.startsWith("https://")) {
            // 云端模型访问不到本机网关，仍转 base64
            if (isLocalHost(u)) {
                return toDataUrl(fetchBytes(u));
            }
            return u;
        }
        String path = u.startsWith("/") ? u : "/" + u;
        String fetchUrl = internalFileBase.replaceAll("/$", "") + path;
        return toDataUrl(fetchBytes(fetchUrl));
    }

    private boolean isLocalHost(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.contains("://127.0.0.1") || lower.contains("://localhost") || lower.contains("://0.0.0.0");
    }

    private byte[] fetchBytes(String url) {
        try {
            byte[] bytes = HttpRequest.get(url).timeout(timeoutMs).execute().bodyBytes();
            if (bytes == null || bytes.length == 0) {
                throw new BizException("拉取图片失败：空内容 " + url);
            }
            return bytes;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("拉取图片失败：" + e.getMessage());
        }
    }

    private String toDataUrl(byte[] bytes) {
        String mime = sniffMime(bytes);
        return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    private String sniffMime(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8) {
            return "image/jpeg";
        }
        if (bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 0x50) {
            return "image/png";
        }
        if (bytes.length >= 6 && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F') {
            return "image/gif";
        }
        if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F') {
            return "image/webp";
        }
        return "image/jpeg";
    }
}
