package com.community.repair.service;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeWapPayRequest;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.community.common.exception.BizException;
import com.community.repair.config.AlipayProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

/**
 * 支付宝沙盒：电脑网站支付 / 手机网站支付 / 验签 / 查单
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlipaySandboxService {

    private final AlipayProperties props;
    private final ObjectMapper objectMapper;

    private AlipayClient client;

    @PostConstruct
    public void init() {
        if (!props.isConfigured()) {
            log.info("Alipay sandbox disabled or keys missing (alipay.enabled=false / empty keys)");
            return;
        }
        client = new DefaultAlipayClient(
                props.getGateway(),
                props.getAppId(),
                props.getPrivateKey(),
                "json",
                "UTF-8",
                props.getAlipayPublicKey(),
                "RSA2");
        log.info("Alipay client ready, sandbox={}, appId={}", props.isSandbox(), props.getAppId());
    }

    public boolean ready() {
        return props.isConfigured() && client != null;
    }

    /**
     * @param product page=电脑网站支付（浏览器演示推荐） / wap=手机网站支付
     * @return HTML form，前端写入页面自动提交即可跳转收银台
     */
    public String buildPayForm(String orderNo, BigDecimal amount, String subject, String product) {
        ensureReady();
        String amt = amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
        String subj = StringUtils.hasText(subject) ? subject : ("物业缴费-" + orderNo);
        if (subj.length() > 64) {
            subj = subj.substring(0, 64);
        }
        try {
            Map<String, Object> biz = new HashMap<>();
            biz.put("out_trade_no", orderNo);
            biz.put("total_amount", amt);
            biz.put("subject", subj);

            if ("wap".equalsIgnoreCase(product)) {
                biz.put("product_code", "QUICK_WAP_WAY");
                AlipayTradeWapPayRequest req = new AlipayTradeWapPayRequest();
                if (StringUtils.hasText(props.getNotifyUrl())) {
                    req.setNotifyUrl(props.getNotifyUrl());
                }
                req.setReturnUrl(props.getReturnUrl());
                req.setBizContent(objectMapper.writeValueAsString(biz));
                return client.pageExecute(req).getBody();
            }

            biz.put("product_code", "FAST_INSTANT_TRADE_PAY");
            AlipayTradePagePayRequest req = new AlipayTradePagePayRequest();
            if (StringUtils.hasText(props.getNotifyUrl())) {
                req.setNotifyUrl(props.getNotifyUrl());
            }
            req.setReturnUrl(props.getReturnUrl());
            req.setBizContent(objectMapper.writeValueAsString(biz));
            return client.pageExecute(req).getBody();
        } catch (Exception e) {
            log.warn("alipay buildPayForm fail: {}", e.getMessage());
            throw new BizException(500, "生成支付宝收银台失败：" + e.getMessage());
        }
    }

    public boolean verifyNotify(Map<String, String> params) {
        ensureReady();
        try {
            return AlipaySignature.rsaCheckV1(params, props.getAlipayPublicKey(), "UTF-8", "RSA2");
        } catch (AlipayApiException e) {
            log.warn("alipay verify fail: {}", e.getMessage());
            return false;
        }
    }

    /** 主动查单：本地无公网 notify 时，靠 return / 前端轮询触发 */
    public AlipayTradeQueryResponse queryTrade(String orderNo) {
        ensureReady();
        try {
            AlipayTradeQueryRequest req = new AlipayTradeQueryRequest();
            Map<String, Object> biz = new HashMap<>();
            biz.put("out_trade_no", orderNo);
            req.setBizContent(objectMapper.writeValueAsString(biz));
            return client.execute(req);
        } catch (Exception e) {
            log.warn("alipay query fail: {}", e.getMessage());
            throw new BizException(500, "查询支付宝订单失败：" + e.getMessage());
        }
    }

    public boolean isTradeSuccess(AlipayTradeQueryResponse resp) {
        if (resp == null || !resp.isSuccess()) {
            return false;
        }
        String st = resp.getTradeStatus();
        return "TRADE_SUCCESS".equals(st) || "TRADE_FINISHED".equals(st);
    }

    public String frontendResultRedirect(String orderNo) {
        return appendOrderId(props.getFrontendResultUrl(),
                "http://127.0.0.1:5180/#/pages/payment/result", orderNo);
    }

    public String frontendStoreResultRedirect(String orderNo) {
        return appendOrderId(props.getFrontendStoreResultUrl(),
                "http://127.0.0.1:5180/#/pages/mall/pay-result", orderNo);
    }

    private static String appendOrderId(String base, String fallback, String orderNo) {
        if (!StringUtils.hasText(base)) {
            base = fallback;
        }
        String sep = base.contains("?") ? "&" : "?";
        return base + sep + "orderId=" + (orderNo == null ? "" : orderNo);
    }

    private void ensureReady() {
        if (!ready()) {
            throw new BizException(400,
                    "支付宝沙盒未配置：请在 community-repair 的 application-local.yml 填写 alipay.app-id / private-key / alipay-public-key，并设置 alipay.enabled=true");
        }
        if (client == null) {
            init();
        }
        if (client == null) {
            throw new BizException(500, "支付宝客户端初始化失败");
        }
    }
}
