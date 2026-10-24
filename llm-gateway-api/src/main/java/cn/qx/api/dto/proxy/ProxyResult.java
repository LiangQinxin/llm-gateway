package cn.qx.api.dto.proxy;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.InputStream;

/**
 * 数据面协议无关响应：原始文本或上游字节流。
 * <p>case 编排产出、直接交给 trigger 的输出对象（分层约定：输出对象归 api/dto）。
 * 取消句柄以 Runnable 承载（仅调用 cancel()），api 层不引入 okhttp 框架类型。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProxyResult {

    /** HTTP 状态码 */
    private int statusCode;
    /** 响应 Content-Type */
    private String contentType;
    /** 非流式响应原始文本 */
    private String raw;
    /** 流式响应字节流 */
    private InputStream stream;
    /** 上游调用取消句柄（用于取消流式请求） */
    private Runnable cancel;
    /** 流式审计会话标识（case 内部上下文，不透出领域执行对象） */
    private String auditSessionId;
}
