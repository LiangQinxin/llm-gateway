package cn.qx.types.exception;

/**
 * 协议转换遇到目标协议不支持的功能性字段。
 */
public class UnsupportedProtocolFieldException extends RuntimeException {

    public UnsupportedProtocolFieldException(String field) {
        super("目标协议不支持字段: " + field);
    }
}
