package cn.qx.domain.security.service.redact;

import cn.qx.domain.security.model.valobj.ScanSettings;
import cn.qx.domain.security.service.rule.CustomRuleMeta;
import cn.qx.domain.security.service.rule.ISecurityRuleCacheService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 转发前递归脱敏：敏感键名整值、字符串内密钥、PEM 私钥块；非法 JSON 走正则兜底。
 */
@Component
public class RecursiveRedactorServiceImpl implements IRecursiveRedactorService {

    private static final String PRIVATE_KEY_REPLACEMENT = "[REDACTED PRIVATE KEY]";
    private static final String REDACTED = "[REDACTED]";

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

    /** 规则缓存：自定义黑名单 pattern 与扫描器共享同一份快照。 */
    private final ISecurityRuleCacheService ruleCache;

    public RecursiveRedactorServiceImpl(ISecurityRuleCacheService ruleCache) {
        this.ruleCache = ruleCache;
    }

    /**
     * 对请求体做脱敏；任何解析或改写失败都降级到原文正则兜底，不抛异常。
     */
    @Override
    public String redact(String body, ScanSettings settings) {
        if (body == null || body.isEmpty() || settings == null || !settings.isEnabled()) {
            return body;
        }
        List<Pattern> customPatterns = customPatterns();
        try {
            Object parsed = JSON.parse(body);
            Object redacted = redactValue(parsed, null, customPatterns);
            return JSON.toJSONString(redacted, JSONWriter.Feature.WriteNulls);
        } catch (Exception ignored) {
            return regexFallback(body, customPatterns);
        }
    }

    private Object redactValue(Object value, String key, List<Pattern> customPatterns) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> redacted = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String childKey = String.valueOf(entry.getKey());
                redacted.put(childKey, redactValue(entry.getValue(), childKey, customPatterns));
            }
            return redacted;
        }
        if (value instanceof List<?> list) {
            List<Object> redacted = new ArrayList<>(list.size());
            for (Object item : list) {
                redacted.add(redactValue(item, null, customPatterns));
            }
            return redacted;
        }
        if (value instanceof String text) {
            if (isSecretField(key) && text.length() > 4) {
                return maskString(text);
            }
            return redactText(text, customPatterns);
        }
        return value;
    }

    private static boolean isSecretField(String key) {
        return key != null && SECRET_FIELDS.contains(key.toLowerCase(Locale.ROOT));
    }

    private static String redactText(String text, List<Pattern> customPatterns) {
        String redacted = RedactPatterns.PRIVATE_KEY_BLOCK
                .matcher(text)
                .replaceAll(Matcher.quoteReplacement(PRIVATE_KEY_REPLACEMENT));
        for (Pattern pattern : RedactPatterns.SECRET_TOKENS) {
            redacted = replaceMatches(redacted, pattern,
                    RecursiveRedactorServiceImpl::maskString);
        }
        for (Pattern pattern : customPatterns) {
            redacted = replaceMatches(redacted, pattern,
                    matched -> matched.length() < 12 ? REDACTED : maskString(matched));
        }
        return redacted;
    }

    private static String replaceMatches(String value,
                                         Pattern pattern,
                                         Function<String, String> replacement) {
        Matcher matcher = pattern.matcher(value);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(result,
                    Matcher.quoteReplacement(replacement.apply(matcher.group())));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String regexFallback(String body, List<Pattern> customPatterns) {
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
        return redactText(result.toString(), customPatterns);
    }

    private List<Pattern> customPatterns() {
        return ruleCache.listCustom("blacklist").stream()
                .filter(CustomRuleMeta::isEnabled)
                .map(CustomRuleMeta::getPattern)
                .filter(pattern -> pattern != null && !pattern.isBlank())
                .map(pattern -> Pattern.compile(Pattern.quote(pattern),
                        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE))
                .toList();
    }

    private static String maskString(String value) {
        if (value == null || value.length() <= 8) {
            return "";
        }
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }
}
