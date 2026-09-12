package com.gov.gw.common;

/** 业务异常：code 非 0，message 直接展示给前端 */
public class ApiException extends RuntimeException {
    private final int code;

    public ApiException(String message) {
        this(400, message);
    }

    public ApiException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() { return code; }

    public static ApiException notFound(String what) {
        return new ApiException(404, what + "不存在");
    }

    public static ApiException forbidden(String what) {
        return new ApiException(403, what);
    }
}
