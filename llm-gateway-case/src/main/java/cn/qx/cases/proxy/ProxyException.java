package cn.qx.cases.proxy;

import cn.qx.types.exception.AppException;

/**
 * 数据面代理异常：携带 OpenAI 风格 HTTP 状态码。
 */
public class ProxyException extends AppException {

    /** HTTP 状态码（OpenAI 风格，如 400 / 404 / 502） */
    private final int statusCode;
    /** OpenAI 错误类型（如 invalid_request_error / upstream_error） */
    private final String errorType;

    public ProxyException(int statusCode, String code, String errorType, String message) {
        super(code, message);
        this.statusCode = statusCode;
        this.errorType = errorType;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getErrorType() {
        return errorType;
    }
}
