package cn.qx.domain.security.service.redact;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 转发级脱敏使用的密钥前缀规则。
 * <p>与凭证扫描器共享同一组前缀语义，避免出现"扫描命中但脱敏遗漏"。
 */
public final class RedactPatterns {

    /**
     * OpenAI 密钥前缀正则：sk- 或 sk-proj- 后跟 20+ 位字母数字。
     * 不使用 \b 锚定：Java \b 把中文字符视为 word 字符，
     * 会导致中文紧贴密钥时（如"我的 sk-ant-xxx"）结尾边界判定失败。
     * 前缀本身已足够特异，直接字面匹配即可。
     */
    public static final String OPENAI_API_KEY =
            "sk-(?:proj-)?[a-zA-Z0-9]{20,}";
    /** Anthropic 密钥前缀正则：sk-ant- 开头后跟 20+ 位字符。 */
    public static final String ANTHROPIC_API_KEY =
            "sk-ant-[a-zA-Z0-9_-]{20,}";
    /** GitHub 个人访问令牌正则：ghp_ 前缀后跟 36 位字符。 */
    public static final String GITHUB_PAT = "ghp_[a-zA-Z0-9]{36}";
    /** GitHub OAuth 令牌正则：gho_ 前缀后跟 36 位字符。 */
    public static final String GITHUB_OAUTH = "gho_[a-zA-Z0-9]{36}";
    /** GitHub 细粒度 PAT 正则：github_pat_ 前缀后跟 20+ 位字符。 */
    public static final String GITHUB_FINE_GRAINED = "github_pat_[a-zA-Z0-9_]{20,}";
    /** Google API 密钥正则：AIza 前缀后跟 35 位字符。 */
    public static final String GOOGLE_API_KEY = "AIza[a-zA-Z0-9_-]{35}";
    /** AWS 访问密钥 ID 正则：AKIA 前缀后跟 16 位大写字母数字。 */
    public static final String AWS_ACCESS_KEY = "AKIA[0-9A-Z]{16}";
    /** Slack 令牌正则：xox[baprs]- 前缀后跟 10+ 位字符。 */
    public static final String SLACK_TOKEN = "xox[baprs]-[a-zA-Z0-9-]{10,}";
    /**
     * Bearer 授权头令牌正则：不区分大小写，bearer 后接 20+ 位 token 字符。
     * 不用 \b：bearer 后必须跟空白，已自然分隔；去掉 \b 以兼容中文紧贴场景。
     */
    public static final String BEARER_TOKEN =
            "(?i)bearer\\s+[a-zA-Z0-9._~+/=-]{20,}";
    /** JWT 三段式正则：eyJ 开头的 header.payload.signature。 */
    public static final String JWT =
            "eyJ[a-zA-Z0-9_-]{5,}\\.[a-zA-Z0-9_-]{5,}\\.[a-zA-Z0-9_-]{5,}";
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
