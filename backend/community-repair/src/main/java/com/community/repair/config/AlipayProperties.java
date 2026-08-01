package com.community.repair.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 支付宝沙盒 / 正式环境配置（密钥放 application-local.yml，勿提交仓库）
 */
@Data
@Component
@ConfigurationProperties(prefix = "alipay")
public class AlipayProperties {

    /** 是否启用真实支付宝（false 时支付宝渠道仍走本地模拟） */
    private boolean enabled = false;

    /** 沙盒环境 */
    private boolean sandbox = true;

    private String appId = "";

    /** 应用私钥（RSA2，PKCS8，无头尾） */
    private String privateKey = "";

    /** 支付宝公钥（非应用公钥） */
    private String alipayPublicKey = "";

    /** 沙盒网关 */
    private String gateway = "https://openapi-sandbox.dl.alipaydev.com/gateway.do";

    /**
     * 异步通知（需公网可达，本地可用 ngrok）。
     * 例：https://xxxx.ngrok-free.app/fee/alipay/notify
     */
    private String notifyUrl = "";

    /**
     * 同步回跳（浏览器支付完成跳转，可指向本机网关）。
     * 例：http://127.0.0.1:8080/fee/alipay/return
     */
    private String returnUrl = "http://127.0.0.1:8080/fee/alipay/return";

    /**
     * 支付完成最终前端页（后端 return 接口会 302 到这里）。
     * 例：http://127.0.0.1:5180/#/pages/payment/result
     */
    private String frontendResultUrl = "http://127.0.0.1:5180/#/pages/payment/result";

    /**
     * 商城支付完成前端页。
     * 例：http://127.0.0.1:5180/#/pages/mall/pay-result
     */
    private String frontendStoreResultUrl = "http://127.0.0.1:5180/#/pages/mall/pay-result";

    public boolean isConfigured() {
        return enabled
                && appId != null && !appId.isBlank()
                && privateKey != null && !privateKey.isBlank()
                && alipayPublicKey != null && !alipayPublicKey.isBlank();
    }
}
