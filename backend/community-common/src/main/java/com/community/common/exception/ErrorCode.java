package com.community.common.exception;

/**
 * 统一业务错误码
 */
public enum ErrorCode {
    SUCCESS(0, "success"),
    FAIL(1, "操作失败"),
    UNAUTHORIZED(401, "未登录或登录已失效"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    BAD_REQUEST(400, "请求参数错误"),
    TENANT_REQUIRED(40001, "请选择所属社区"),
    TENANT_MISMATCH(40003, "禁止跨社区访问"),
    USER_NOT_FOUND(40101, "用户不存在"),
    USER_DISABLED(40102, "账号已禁用"),
    BAD_CREDENTIALS(40103, "手机号或密码错误"),
    PHONE_EXISTS(40104, "该手机号已注册"),
    ROLE_INVALID(40105, "角色不合法"),
    SMS_INVALID(40106, "验证码错误或已过期"),
    TENANT_DISABLED(40107, "租户已禁用，无法登录"),
    TENANT_NOT_FOUND(40201, "租户不存在"),
    PACKAGE_NOT_FOUND(40301, "套餐不存在"),
    ORDER_NOT_FOUND(40302, "订单不存在"),
    FEATURE_DENIED(40303, "当前套餐未开放该功能"),
    /** 与 FEATURE_DENIED 同码，供前后端统一识别 */
    FEATURE_NOT_AVAILABLE(40303, "当前套餐未包含此功能");

    private final int code;
    private final String msg;

    ErrorCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public int getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }
}
