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

    private int statusCode;
    private String contentType;
    private String raw;
    private InputStream stream;
    private Call cancel;
}
