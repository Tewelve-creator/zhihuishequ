package com.community.common.result;

import com.community.common.exception.ErrorCode;
import lombok.Data;

import java.io.Serializable;

/**
 * 统一 API 返回体：code=0 成功
 */
@Data
public class R<T> implements Serializable {
    private int code;
    private String msg;
    private T data;

    public static <T> R<T> ok() {
        return ok(null);
    }

    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.code = ErrorCode.SUCCESS.getCode();
        r.msg = ErrorCode.SUCCESS.getMsg();
        r.data = data;
        return r;
    }

    public static <T> R<T> fail(String msg) {
        return fail(ErrorCode.FAIL.getCode(), msg);
    }

    public static <T> R<T> fail(ErrorCode errorCode) {
        return fail(errorCode.getCode(), errorCode.getMsg());
    }

    public static <T> R<T> fail(ErrorCode errorCode, String msg) {
        return fail(errorCode.getCode(), msg == null ? errorCode.getMsg() : msg);
    }

    public static <T> R<T> fail(int code, String msg) {
        R<T> r = new R<>();
        r.code = code;
        r.msg = msg;
        return r;
    }
}
