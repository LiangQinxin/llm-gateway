package cn.qx.domain.channel.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import okhttp3.Call;

import java.io.InputStream;

/**
 * 流式转发结果——持有上游 SSE 字节流。
 * <p>由调用方逐段透传，并负责关闭流。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StreamResult {

    /** 上游 HTTP 状态码 */
    private int statusCode;

    /** 上游响应体字节流（SSE） */
    private InputStream body;

    /** 上游调用取消句柄；客户端断连时取消。 */
    private Call cancel;
}
