package cn.qx.cases.proxy;

import cn.qx.api.dto.proxy.ProxyResult;
import cn.qx.domain.channel.model.valobj.ProxyRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

/**
 * 数据面代理用例接口。
 * <p>就近定义于 cases.proxy（与其他 case 接口放 api 根包不同）：签名含 servlet/spring-web
 * 类型（ResponseBodyEmitter/HttpServletResponse），留在 case 层避免污染 api 支撑模块。
 */
public interface IProxyCase {

    /** 解析请求并按 body.stream（缺省 Accept）确定响应形态。 */
    ProxyRequest prepare(String rawBody, String acceptHeader);

    /** 一次调用渠道门面并转换为字节中立结果。 */
    ProxyResult forward(ProxyRequest request);

    /** 将结果写入 ResponseBodyEmitter；流式结果绑定取消句柄。 */
    void write(ProxyResult result, ResponseBodyEmitter emitter, HttpServletResponse response);

    /** 数据面 OpenAI 风格错误响应。 */
    void writeError(ProxyException exception, ResponseBodyEmitter emitter, HttpServletResponse response);

    /** 尚未发送任何字节时的系统失败，回退为 OpenAI 风格 JSON。 */
    void writeUpstreamFailure(ResponseBodyEmitter emitter, HttpServletResponse response, Throwable error);
}
