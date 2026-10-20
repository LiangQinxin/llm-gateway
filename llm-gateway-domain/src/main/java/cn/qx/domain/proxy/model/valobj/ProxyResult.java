package cn.qx.domain.proxy.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import okhttp3.Call;

import java.io.InputStream;

/**
 * 数据面协议无关响应：原始文本或上游字节流。
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

    /** 上游调用句柄（用于取消流式请求） */
    private Call cancel;
}
