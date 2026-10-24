package cn.qx.domain.security.service.redact;

import cn.qx.domain.security.model.valobj.ScanSettings;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 转发前递归脱敏：敏感键名整值、字符串内密钥、PEM 私钥块；非法 JSON 走正则兜底。
 */
@Component
public class RecursiveRedactorServiceImpl implements IRecursiveRedactorService {

    private static final String PRIVATE_KEY_REPLACEMENT = "[REDACTED PRIVATE KEY]";

    private static final Set<String> SECRET_FIELDS = Set.of(
            "api_key", "apikey", "secret", "secret_key", "access_key", "access_token",
            "auth_token", "token", "password", "passwd", "authorization", "cookie",
            "session", "sessionid", "private_key", "client_secret", "aws_secret_access_key",
            "secretkey");

    private static final Pattern ASSIGNMENT = Pattern.compile(
            "(?i)(\\b(?:api[_-]?key|secret(?:[_-]?key)?|access[_-]?key|access[_-]?token"
                    + "|auth[_-]?token|token|password|passwd|authorization|cookie|sessionid"
                    + "|private[_-]?key|client[_-]?secret|aws[_-]?secret[_-]?access[_-]?key)\\b"
                    + "\\s*[=:]\\s*)([\"']?)([^\"',}\\]\\s]{5,})");

    /**
     * 对请求体做脱敏；任何解析或改写失败都降级到原文正则兜底，不抛异常。
     */
    @Override
    public String redact(String body, ScanSettings settings) {
        if (body == null || body.isEmpty() || settings == null || !settings.isEnabled()) {
            return body;
        }
        try {
            Object parsed = JSON.parse(body);
            Object redacted = redactValue(parsed, null);
            return JSON.toJSONString(redacted, JSONWriter.Feature.WriteNulls);
        } catch (Exception ignored) {
            return regexFallback(body);
        }
    }

    private Object redactValue(Object value, String key) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> redacted = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String childKey = String.valueOf(entry.getKey());
                redacted.put(childKey, redactValue(entry.getValue(), childKey));
            }
            return redacted;
        }
        if (value instanceof List<?> list) {
            List<Object> redacted = new ArrayList<>(list.size());
            for (Object item : list) {
                redacted.add(redactValue(item, null));
            }
            return redacted;
        }
        if (value instanceof String text) {
            if (isSecretField(key) && text.length() > 4) {
                return maskString(text);
            }
            return redactText(text);
        }
        return value;
    }

    private static boolean isSecretField(String key) {
        return key != null && SECRET_FIELDS.contains(key.toLowerCase(Locale.ROOT));
    }

    private static String redactText(String text) {
        String redacted = RedactPatterns.PRIVATE_KEY_BLOCK
                .matcher(text)
                .replaceAll(Matcher.quoteReplacement(PRIVATE_KEY_REPLACEMENT));
        for (Pattern pattern : RedactPatterns.SECRET_TOKENS) {
            Matcher matcher = pattern.matcher(redacted);
            StringBuilder result = new StringBuilder();
            while (matcher.find()) {
                matcher.appendReplacement(result,
                        Matcher.quoteReplacement(maskString(matcher.group())));
            }
            matcher.appendTail(result);
            redacted = result.toString();
        }
        return redacted;
    }

    private static String regexFallback(String body) {
        String redacted = RedactPatterns.PRIVATE_KEY_BLOCK
                .matcher(body)
                .replaceAll(Matcher.quoteReplacement(PRIVATE_KEY_REPLACEMENT));
        Matcher assignment = ASSIGNMENT.matcher(redacted);
        StringBuilder result = new StringBuilder();
        while (assignment.find()) {
            String value = assignment.group(3);
            assignment.appendReplacement(result,
                    Matcher.quoteReplacement(
                            assignment.group(1) + assignment.group(2) + maskString(value)));
        }
        assignment.appendTail(result);
        return redactText(result.toString());
    }

    private static String maskString(String value) {
        if (value == null || value.length() <= 8) {
            return "";
        }
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }
}
