package cn.qx.cases.proxy;

import cn.qx.types.exception.AppException;

/**
 * 数据面代理异常：携带 OpenAI 风格 HTTP 状态码。
 */
public class ProxyException extends AppException {

    private final int statusCode;
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
