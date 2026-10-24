package cn.qx.domain.security.service.redact;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 转发级脱敏使用的密钥前缀规则。
 * <p>与凭证扫描器共享同一组前缀语义，避免出现"扫描命中但脱敏遗漏"。
 */
public final class RedactPatterns {

    /** OpenAI 密钥前缀正则：sk- 或 sk-proj- 后跟 20+ 位字母数字。 */
    public static final String OPENAI_API_KEY =
            "\\bsk-(?:proj-)?[a-zA-Z0-9]{20,}\\b";
    /** Anthropic 密钥前缀正则：sk-ant- 开头后跟 20+ 位字符。 */
    public static final String ANTHROPIC_API_KEY =
            "\\bsk-ant-[a-zA-Z0-9_-]{20,}\\b";
    /** GitHub 个人访问令牌正则：ghp_ 前缀后跟 36 位字符。 */
    public static final String GITHUB_PAT = "\\bghp_[a-zA-Z0-9]{36}\\b";
    /** GitHub OAuth 令牌正则：gho_ 前缀后跟 36 位字符。 */
    public static final String GITHUB_OAUTH = "\\bgho_[a-zA-Z0-9]{36}\\b";
    /** GitHub 细粒度 PAT 正则：github_pat_ 前缀后跟 20+ 位字符。 */
    public static final String GITHUB_FINE_GRAINED = "\\bgithub_pat_[a-zA-Z0-9_]{20,}\\b";
    /** Google API 密钥正则：AIza 前缀后跟 35 位字符。 */
    public static final String GOOGLE_API_KEY = "\\bAIza[a-zA-Z0-9_-]{35}\\b";
    /** AWS 访问密钥 ID 正则：AKIA 前缀后跟 16 位大写字母数字。 */
    public static final String AWS_ACCESS_KEY = "\\bAKIA[0-9A-Z]{16}\\b";
    /** Slack 令牌正则：xox[baprs]- 前缀后跟 10+ 位字符。 */
    public static final String SLACK_TOKEN = "\\bxox[baprs]-[a-zA-Z0-9-]{10,}\\b";
    /** Bearer 授权头令牌正则：不区分大小写，bearer 后接 20+ 位 token 字符。 */
    public static final String BEARER_TOKEN =
            "(?i)\\bbearer\\s+[a-zA-Z0-9._~+/=-]{20,}\\b";
    /** JWT 三段式正则：eyJ 开头的 header.payload.signature。 */
    public static final String JWT =
            "\\beyJ[a-zA-Z0-9_-]{5,}\\.[a-zA-Z0-9_-]{5,}\\.[a-zA-Z0-9_-]{5,}\\b";
    /** PEM 私钥块头部正则：匹配各类私钥 BEGIN 标记。 */
    public static final String PRIVATE_KEY_HEADER =
            "-----BEGIN (?:RSA |EC |OPENSSH |DSA |PGP |ENCRYPTED )?PRIVATE KEY-----";

    /** 完整 PEM 私钥块（BEGIN 到对应 END）匹配 Pattern，命中即整块替换。 */
    public static final Pattern PRIVATE_KEY_BLOCK = Pattern.compile(
            PRIVATE_KEY_HEADER
                    + "[\\s\\S]*?-----END (?:RSA |EC |OPENSSH |DSA |PGP |ENCRYPTED )?PRIVATE KEY-----");

    /** 参与文本内密钥前缀打码的 Pattern 列表（不含整值替换的私钥块）。 */
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
