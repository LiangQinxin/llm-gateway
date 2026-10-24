package cn.qx.domain.security.service.scanner;

import cn.qx.domain.security.adapter.port.ISubScanner;
import cn.qx.domain.security.model.valobj.ScanFinding;
import cn.qx.domain.security.model.valobj.ScanSettings;
import cn.qx.domain.security.model.valobj.ScanText;
import cn.qx.domain.security.model.valobj.SecurityCategory;
import cn.qx.domain.security.model.valobj.SecuritySeverity;
import cn.qx.domain.security.service.redact.RedactPatterns;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 凭证扫描器：已知前缀密钥、私钥块与账号密码键值对。
 */
@Component
public class CredentialScanner implements ISubScanner {

    private static final String NAME = "credential";
    private static final List<SecurityCategory> CATEGORIES = List.of(SecurityCategory.CREDENTIAL);
    private static final Set<String> EXCLUDED_VALUES = Set.of(
            "***", "xxxx", "example", "changeme", "123456", "test", "your-key", "your-token");

    private static final List<Rule> BARE_RULES = List.of(
            rule("credential_apikey_openai", "OpenAI API Key",
                    RedactPatterns.OPENAI_API_KEY, SecuritySeverity.HIGH, false),
            rule("credential_apikey_anthropic", "Anthropic API Key",
                    RedactPatterns.ANTHROPIC_API_KEY, SecuritySeverity.HIGH, false),
            rule("credential_apikey_generic", "Generic API Key",
                    "\\bkey-[a-zA-Z0-9]{20,}\\b", SecuritySeverity.HIGH, true),
            rule("credential_apikey_google", "Google API Key",
                    RedactPatterns.GOOGLE_API_KEY, SecuritySeverity.HIGH, false),
            rule("credential_jwt", "JSON Web Token",
                    RedactPatterns.JWT,
                    SecuritySeverity.HIGH, false),
            rule("credential_aws_access", "AWS Access Key",
                    RedactPatterns.AWS_ACCESS_KEY, SecuritySeverity.CRITICAL, false),
            rule("credential_aws_secret", "AWS Secret Key",
                    "(?i)\\baws_secret_access_key\\b\\s*[=:]\\s*[A-Za-z0-9+/=]{20,}",
                    SecuritySeverity.CRITICAL, false),
            rule("credential_github_pat", "GitHub Personal Access Token",
                    RedactPatterns.GITHUB_PAT, SecuritySeverity.CRITICAL, false),
            rule("credential_github_oauth", "GitHub OAuth Token",
                    RedactPatterns.GITHUB_OAUTH, SecuritySeverity.CRITICAL, false),
            rule("credential_github_pat_v2", "GitHub Fine-grained Token",
                    RedactPatterns.GITHUB_FINE_GRAINED, SecuritySeverity.CRITICAL, false),
            rule("credential_slack_token", "Slack Token",
                    RedactPatterns.SLACK_TOKEN, SecuritySeverity.HIGH, false),
            rule("credential_bearer", "Bearer Token",
                    RedactPatterns.BEARER_TOKEN, SecuritySeverity.HIGH, false),
            rule("credential_private_key", "Private Key Header",
                    RedactPatterns.PRIVATE_KEY_HEADER,
                    SecuritySeverity.CRITICAL, false)
    );

    private static final List<AssignmentRule> ASSIGNMENT_RULES = List.of(
            new AssignmentRule("credential_password", "Password Assignment",
                    "(?i)(?<![A-Za-z0-9_])(password|passwd|pwd|密码|口令)(?![A-Za-z0-9_])[\"']?\\s*[=:：]\\s*[\"']?([A-Za-z0-9_\\-./+=@]{4,})"),
            new AssignmentRule("credential_token", "Token Assignment",
                    "(?i)(?<![A-Za-z0-9_])(token|access_token|refresh_token|auth_token|api_key|apikey|api-key|密钥|令牌)(?![A-Za-z0-9_])[\"']?\\s*[=:：]\\s*[\"']?([A-Za-z0-9_\\-./+=@]{4,})"),
            new AssignmentRule("credential_secret", "Secret Assignment",
                    "(?i)(?<![A-Za-z0-9_])(secret|client_secret|机密)(?![A-Za-z0-9_])[\"']?\\s*[=:：]\\s*[\"']?([A-Za-z0-9_\\-./+=@]{4,})")
    );

    /** 敏感字段名（对齐安全审计引擎 credential.named_secret）。 */
    private static final List<String> NAMED_FIELDS = List.of(
            "authorization:", "cookie:", "sessionid=", "auth_token=",
            "secret_key", "access_key", "database_url");

    @Override
    public String scannerName() {
        return NAME;
    }

    @Override
    public boolean enabled(ScanSettings settings) {
        return settings != null && settings.isCredential();
    }

    @Override
    public List<ScanFinding> scan(ScanText text) {
        if (text == null || text.text() == null || text.text().isEmpty()) {
            return List.of();
        }
        List<ScanFinding> findings = new ArrayList<>();
        for (Rule rule : BARE_RULES) {
            Matcher matcher = rule.pattern().matcher(text.text());
            while (matcher.find()) {
                String evidence = matcher.group();
                if (rule.entropyRequired() && ScannerSupport.entropy(evidence) < 3.5) {
                    continue;
                }
                findings.add(finding(rule, text, matcher.start(), matcher.end(), evidence,
                        rule.severity()));
            }
        }
        for (AssignmentRule rule : ASSIGNMENT_RULES) {
            Matcher matcher = rule.pattern().matcher(text.text());
            while (matcher.find()) {
                String value = matcher.group(2);
                if (!strongAssignmentValue(matcher.group(1), value)) {
                    continue;
                }
                SecuritySeverity severity = value.length() >= 8
                        && (ScannerSupport.hasLetterAndDigit(value)
                        || ScannerSupport.entropy(value) >= 3.5)
                        ? SecuritySeverity.HIGH : SecuritySeverity.MEDIUM;
                findings.add(finding(rule.ruleId(), rule.title(), text,
                        matcher.start(), matcher.end(), matcher.group(), severity));
            }
        }
        namedFieldFindings(text).forEach(findings::add);
        return findings;
    }

    private static List<ScanFinding> namedFieldFindings(ScanText text) {
        String lower = text.text().toLowerCase(Locale.ROOT);
        for (String field : NAMED_FIELDS) {
            int index = lower.indexOf(field);
            if (index >= 0) {
                return List.of(finding(
                        "credential_named_field", "Sensitive credential field",
                        text, index, index + field.length(), field, SecuritySeverity.HIGH));
            }
        }
        return List.of();
    }

    private static boolean strongAssignmentValue(String key, String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.equals(key.toLowerCase(Locale.ROOT))) {
            return false;
        }
        if (EXCLUDED_VALUES.contains(normalized)
                || normalized.startsWith("your-")
                || (normalized.startsWith("<") && normalized.endsWith(">"))) {
            return false;
        }
        return value.length() >= 4;
    }

    private static ScanFinding finding(Rule rule,
                                       ScanText text,
                                       int start,
                                       int end,
                                       String evidence,
                                       SecuritySeverity severity) {
        return finding(rule.ruleId(), rule.title(), text, start, end, evidence, severity);
    }

    private static ScanFinding finding(String ruleId,
                                       String title,
                                       ScanText text,
                                       int start,
                                       int end,
                                       String evidence,
                                       SecuritySeverity severity) {
        return ScannerSupport.finding(
                NAME, ruleId, title, "Detected credential material in request content",
                severity, CATEGORIES, evidence, text.locationAt(start, end),
                title + " detected", true);
    }

    /**
     * 单条凭证扫描规则。
     * @param ruleId          规则 ID（与内置规则表一致）
     * @param title           人读规则标题
     * @param pattern         凭证特征匹配正则
     * @param severity        命中后的严重度
     * @param entropyRequired 是否要求命中后再做高熵校验（降低普通键值误报）
     */
    private record Rule(String ruleId,
                        String title,
                        Pattern pattern,
                        SecuritySeverity severity,
                        boolean entropyRequired) {
    }

    /**
     * 键值赋值类凭证规则（如 password=xxx），构造时把正则串编译为 Pattern。
     * @param ruleId  规则 ID（与内置规则表一致）
     * @param title   人读规则标题
     * @param pattern 键值匹配正则
     */
    private record AssignmentRule(String ruleId, String title, Pattern pattern) {
        AssignmentRule(String ruleId, String title, String regex) {
            this(ruleId, title, Pattern.compile(regex));
        }
    }

    private static Rule rule(String ruleId,
                             String title,
                             String regex,
                             SecuritySeverity severity,
                             boolean entropyRequired) {
        return new Rule(ruleId, title, Pattern.compile(regex), severity, entropyRequired);
    }
}
