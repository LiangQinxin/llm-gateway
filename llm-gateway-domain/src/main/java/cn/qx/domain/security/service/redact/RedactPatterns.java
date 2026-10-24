package cn.qx.domain.security.service.redact;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 转发级脱敏使用的密钥前缀规则。
 * <p>与凭证扫描器共享同一组前缀语义，避免出现"扫描命中但脱敏遗漏"。
 */
public final class RedactPatterns {

    public static final String OPENAI_API_KEY =
            "\\bsk-(?:proj-)?[a-zA-Z0-9]{20,}\\b";
    public static final String ANTHROPIC_API_KEY =
            "\\bsk-ant-[a-zA-Z0-9_-]{20,}\\b";
    public static final String GITHUB_PAT = "\\bghp_[a-zA-Z0-9]{36}\\b";
    public static final String GITHUB_OAUTH = "\\bgho_[a-zA-Z0-9]{36}\\b";
    public static final String GITHUB_FINE_GRAINED = "\\bgithub_pat_[a-zA-Z0-9_]{20,}\\b";
    public static final String GOOGLE_API_KEY = "\\bAIza[a-zA-Z0-9_-]{35}\\b";
    public static final String AWS_ACCESS_KEY = "\\bAKIA[0-9A-Z]{16}\\b";
    public static final String SLACK_TOKEN = "\\bxox[baprs]-[a-zA-Z0-9-]{10,}\\b";
    public static final String BEARER_TOKEN =
            "(?i)\\bbearer\\s+[a-zA-Z0-9._~+/=-]{20,}\\b";
    public static final String JWT =
            "\\beyJ[a-zA-Z0-9_-]{5,}\\.[a-zA-Z0-9_-]{5,}\\.[a-zA-Z0-9_-]{5,}\\b";
    public static final String PRIVATE_KEY_HEADER =
            "-----BEGIN (?:RSA |EC |OPENSSH |DSA |PGP |ENCRYPTED )?PRIVATE KEY-----";

    public static final Pattern PRIVATE_KEY_BLOCK = Pattern.compile(
            PRIVATE_KEY_HEADER
                    + "[\\s\\S]*?-----END (?:RSA |EC |OPENSSH |DSA |PGP |ENCRYPTED )?PRIVATE KEY-----");

    public static final List<Pattern> SECRET_TOKENS = List.of(
            Pattern.compile(OPENAI_API_KEY),
            Pattern.compile(ANTHROPIC_API_KEY),
            Pattern.compile(GITHUB_PAT),
            Pattern.compile(GITHUB_OAUTH),
            Pattern.compile(GITHUB_FINE_GRAINED),
            Pattern.compile(GOOGLE_API_KEY),
            Pattern.compile(AWS_ACCESS_KEY),
            Pattern.compile(SLACK_TOKEN),
            Pattern.compile(BEARER_TOKEN),
            Pattern.compile(JWT)
    );

    private RedactPatterns() {
    }
}
