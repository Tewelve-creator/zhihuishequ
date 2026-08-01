package com.community.auth.dto;

import lombok.Data;

@Data
public class OrderPayRequest {
    /**
     * 支付方式：wechat / alipay / bank / mock
     * 演示环境四种均可即时到账，生产需对接真实渠道。
     */
    private String payMethod;
}
