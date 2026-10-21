package cn.qx.trigger.gateway;

import cn.qx.api.dto.proxy.ProxyResult;
import cn.qx.cases.proxy.IProxyCase;
import cn.qx.cases.proxy.ProxyException;
import cn.qx.domain.channel.model.valobj.ProxyRequest;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

/**
 * OpenAI 兼容数据面入口。
 */
@Slf4j
@RestController
public class ProxyController {

    /** 数据面代理用例 */
    @Resource
    private IProxyCase proxyCase;

    /** 异步任务执行器（applicationTaskExecutor，流式转发用） */
    @Resource(name = "applicationTaskExecutor")
    private TaskExecutor taskExecutor;

    @PostMapping("/v1/chat/completions")
    public ResponseBodyEmitter chatCompletions(@RequestBody String body,
                                               HttpServletRequest request,
                                               HttpServletResponse response) {
        return proxy(body, request, response);
    }

    @PostMapping("/v1/completions")
    public ResponseBodyEmitter completions(@RequestBody String body,
                                           HttpServletRequest request,
                                           HttpServletResponse response) {
        return proxy(body, request, response);
    }

    @PostMapping("/v1/embeddings")
    public ResponseBodyEmitter embeddings(@RequestBody String body,
                                          HttpServletRequest request,
                                          HttpServletResponse response) {
        return proxy(body, request, response);
    }

    @PostMapping("/v1/responses")
    public ResponseBodyEmitter responses(@RequestBody String body,
                                         HttpServletRequest request,
                                         HttpServletResponse response) {
        return proxy(body, request, response);
    }

    private ResponseBodyEmitter proxy(String body, HttpServletRequest request, HttpServletResponse response) {
        ResponseBodyEmitter emitter = new ResponseBodyEmitter();
        taskExecutor.execute(() -> {
            try {
                ProxyRequest proxyRequest = proxyCase.prepare(
                        body, request.getHeader("Accept"), request.getRequestURI());
                log.info("数据面请求: path={}, model={}, stream={}",
                        request.getRequestURI(), proxyRequest.getModel(), proxyRequest.isStream());
                String apiKey = (String) request.getAttribute(ApiKeyFilter.REQUEST_ATTRIBUTE);
                ProxyResult result = proxyCase.forward(proxyRequest, apiKey);
                proxyCase.write(result, emitter, response);
            } catch (ProxyException e) {
                proxyCase.writeError(e, emitter, response);
            } catch (Exception e) {
                log.error("数据面转发异常", e);
                proxyCase.writeUpstreamFailure(emitter, response, e);
            }
        });
        return emitter;
    }
}
