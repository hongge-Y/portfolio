package com.campus.portfolio.common;

import java.util.UUID;

/**
 * 统一响应包：{ code, message, data, traceId }
 * code=200 表示成功，其余见 ErrorCode。
 */
public class Result<T> {

    private int code;
    private String message;
    private T data;
    private String traceId;

    public Result() {
        this.traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.code = 200;
        r.message = "ok";
        r.data = data;
        return r;
    }

    public static <T> Result<T> fail(int code, String message) {
        Result<T> r = new Result<>();
        r.code = code;
        r.message = message;
        return r;
    }

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
}
