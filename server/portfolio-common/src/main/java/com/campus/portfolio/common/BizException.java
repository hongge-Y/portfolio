package com.campus.portfolio.common;

/**
 * 业务异常：code 会被原样返回给前端（400/401/403...），message 可直接展示。
 */
public class BizException extends RuntimeException {

    private final int code;
    /** 业务子码，如 LEAVE_IN_FLIGHT_LIMIT，前端可用于分支处理 */
    private final String subCode;

    public BizException(int code, String message) {
        this(code, null, message);
    }

    public BizException(int code, String subCode, String message) {
        // subCode 前置，前端可据此分支；同时保留可读文案
        super(subCode == null ? message : subCode + ":" + message);
        this.code = code;
        this.subCode = subCode;
    }

    public static BizException badRequest(String message) {
        return new BizException(400, message);
    }

    public static BizException badRequest(String subCode, String message) {
        return new BizException(400, subCode, message);
    }

    public static BizException unauthorized(String message) {
        return new BizException(401, message);
    }

    public static BizException forbidden(String message) {
        return new BizException(403, message);
    }

    public static BizException notFound(String message) {
        return new BizException(404, message);
    }

    public int getCode() { return code; }
    public String getSubCode() { return subCode; }
}
