package cn.qx.infrastructure.gateway;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 网关共享 OkHttp 客户端：连接池/线程池复用，仅按请求调整读超时。
 */
@Component
public class GatewayHttpClient {

    /** 连接超时 */
    private static final long CONNECT_TIMEOUT_SECONDS = 10;
    /** 流式读超时下限 */
    private static final long MIN_STREAM_READ_SECONDS = 600;

    /** 非流式基础客户端 */
    private final OkHttpClient unaryClient;
    /** 流式基础客户端 */
    private final OkHttpClient streamingClient;

    public GatewayHttpClient() {
        this.unaryClient = new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();
        this.streamingClient = new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(MIN_STREAM_READ_SECONDS, TimeUnit.SECONDS)
                .build();
    }

    /** 创建上游调用。 */
    public Call newCall(Request request, boolean stream, long configuredTimeoutSecs) {
        long timeout = Math.max(1, configuredTimeoutSecs);
        OkHttpClient base = stream ? streamingClient : unaryClient;
        long readTimeout = stream ? Math.max(MIN_STREAM_READ_SECONDS, timeout) : timeout;
        return base.newBuilder()
                .readTimeout(readTimeout, TimeUnit.SECONDS)
                .build()
                .newCall(request);
    }
}
