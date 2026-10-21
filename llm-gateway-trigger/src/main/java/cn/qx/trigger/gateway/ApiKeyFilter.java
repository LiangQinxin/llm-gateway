package cn.qx.trigger.gateway;

import com.alibaba.fastjson2.JSONObject;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 数据面鉴权过滤器：只做路径白名单与调用方密钥存在性快速校验。
 */
@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    /** 请求属性：调用方 API Key */
    public static final String REQUEST_ATTRIBUTE = ApiKeyFilter.class.getName() + ".apiKey";

    /** Bearer 前缀 */
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!isDataPlanePath(path) || isPublicPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String apiKey = extractApiKey(request, path);
        if (apiKey == null || apiKey.isBlank()) {
            writeUnauthorized(response, path);
            return;
        }
        request.setAttribute(REQUEST_ATTRIBUTE, apiKey);
        filterChain.doFilter(request, response);
    }

    private static boolean isDataPlanePath(String path) {
        return path != null && path.startsWith("/v1/");
    }

    private static boolean isPublicPath(String path) {
        return "/health".equals(path)
                || path.startsWith("/api/v1/")
                || "/mcp".equals(path)
                || path.startsWith("/mcp/sse")
                || "/v1/models".equals(path)
                || path.startsWith("/v1/models/");
    }

    private static String extractApiKey(HttpServletRequest request, String path) {
        if ("/v1/messages".equals(path)) {
            return request.getHeader("x-api-key");
        }
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return authorization.substring(BEARER_PREFIX.length()).trim();
    }

    private static void writeUnauthorized(HttpServletResponse response, String path) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String message = "缺少调用方 API Key";
        String errorType = "invalid_request_error";
        if ("/v1/messages".equals(path)) {
            response.getWriter().write(anthropicError(message, "authentication_error"));
            return;
        }
        JSONObject error = new JSONObject();
        error.put("message", message);
        error.put("type", errorType);
        error.put("code", "invalid_api_key");
        JSONObject body = new JSONObject();
        body.put("error", error);
        response.getWriter().write(body.toJSONString());
    }

    private static String anthropicError(String message, String type) {
        JSONObject error = new JSONObject();
        error.put("type", type);
        error.put("message", message);
        JSONObject body = new JSONObject();
        body.put("type", "error");
        body.put("error", error);
        return body.toJSONString();
    }
}
