package cn.qx.domain.security.service.rule;

import cn.qx.domain.security.model.valobj.SecuritySeverity;

import java.util.List;

/**
 * 内置规则出厂目录：与六个子扫描器的 ruleId/severity 一一对应。
 */
public final class SecurityRuleCatalog {

    private static final String T_UNICODE = "security.scan_unicode";
    private static final String T_NETWORK = "security.scan_network";
    private static final String T_TOOLS = "security.scan_tools";
    private static final String T_TRACKING = "security.scan_tracking";

    private static final List<RuleMetadata> DEFAULTS = List.of(
            rule("credential_apikey_openai", "credential", SecuritySeverity.HIGH, null,
                    "OpenAI API Key"),
            rule("credential_apikey_anthropic", "credential", SecuritySeverity.HIGH, null,
                    "Anthropic API Key"),
            rule("credential_apikey_generic", "credential", SecuritySeverity.HIGH, null,
                    "Generic API Key"),
            rule("credential_apikey_google", "credential", SecuritySeverity.HIGH, null,
                    "Google API Key"),
            rule("credential_jwt", "credential", SecuritySeverity.HIGH, null,
                    "JSON Web Token"),
            rule("credential_aws_access", "credential", SecuritySeverity.CRITICAL, null,
                    "AWS Access Key"),
            rule("credential_aws_secret", "credential", SecuritySeverity.CRITICAL, null,
                    "AWS Secret Key"),
            rule("credential_github_pat", "credential", SecuritySeverity.CRITICAL, null,
                    "GitHub Personal Access Token"),
            rule("credential_github_oauth", "credential", SecuritySeverity.CRITICAL, null,
                    "GitHub OAuth Token"),
            rule("credential_github_pat_v2", "credential", SecuritySeverity.CRITICAL, null,
                    "GitHub Fine-grained Token"),
            rule("credential_slack_token", "credential", SecuritySeverity.HIGH, null,
                    "Slack Token"),
            rule("credential_bearer", "credential", SecuritySeverity.HIGH, null,
                    "Bearer Token"),
            rule("credential_private_key", "credential", SecuritySeverity.CRITICAL, null,
                    "Private Key Header"),
            rule("credential_named_field", "credential", SecuritySeverity.HIGH, null,
                    "Sensitive credential field"),
            rule("credential_password", "credential", SecuritySeverity.MEDIUM, null,
                    "Password Assignment"),
            rule("credential_token", "credential", SecuritySeverity.MEDIUM, null,
                    "Token Assignment"),
            rule("credential_secret", "credential", SecuritySeverity.MEDIUM, null,
                    "Secret Assignment"),

            rule("path_sensitive_unix", "file", SecuritySeverity.HIGH, null,
                    "Sensitive Unix Path"),
            rule("path_sensitive_windows", "file", SecuritySeverity.HIGH, null,
                    "Sensitive Windows Path"),
            rule("path_sensitive_project", "file", SecuritySeverity.MEDIUM, null,
                    "Sensitive Project File"),
            rule("path_traversal", "file", SecuritySeverity.MEDIUM, null,
                    "Path Traversal"),

            rule("unicode_zero_width", "unicode", SecuritySeverity.MEDIUM, T_UNICODE,
                    "Zero-width character"),
            rule("unicode_bidi", "unicode", SecuritySeverity.HIGH, T_UNICODE,
                    "Bidi control character"),
            rule("unicode_variation", "unicode", SecuritySeverity.MEDIUM, T_UNICODE,
                    "Variation selector"),
            rule("unicode_pua", "unicode", SecuritySeverity.MEDIUM, T_UNICODE,
                    "Private use character"),
            rule("unicode_homoglyph", "unicode", SecuritySeverity.MEDIUM, T_UNICODE,
                    "Mixed-script homoglyph token"),

            rule("network_exfil_target", "network", SecuritySeverity.HIGH, T_NETWORK,
                    "External exfiltration target"),
            rule("network_ip_probe", "network", SecuritySeverity.HIGH, T_NETWORK,
                    "Public IP probe service"),
            rule("network_internal_probe", "network", SecuritySeverity.MEDIUM, T_NETWORK,
                    "Internal network probe"),

            rule("tool_curl", "tool", SecuritySeverity.MEDIUM, T_TOOLS, "curl command"),
            rule("tool_wget", "tool", SecuritySeverity.MEDIUM, T_TOOLS, "wget command"),
            rule("tool_bash_c", "tool", SecuritySeverity.HIGH, T_TOOLS,
                    "Shell command execution"),
            rule("tool_powershell", "tool", SecuritySeverity.HIGH, T_TOOLS,
                    "PowerShell execution"),
            rule("tool_python_c", "tool", SecuritySeverity.HIGH, T_TOOLS,
                    "Python inline execution"),
            rule("tool_rm_rf", "tool", SecuritySeverity.HIGH, T_TOOLS,
                    "Recursive forced delete"),
            rule("tool_mkfs", "tool", SecuritySeverity.HIGH, T_TOOLS,
                    "Filesystem formatting"),
            rule("tool_dd", "tool", SecuritySeverity.HIGH, T_TOOLS, "Raw disk write"),
            rule("tool_chmod_777", "tool", SecuritySeverity.HIGH, T_TOOLS,
                    "World-writable permissions"),
            rule("tool_netcat", "tool", SecuritySeverity.HIGH, T_TOOLS, "Netcat usage"),
            rule("tool_nmap", "tool", SecuritySeverity.HIGH, T_TOOLS, "Network scanning"),
            rule("tool_exfil_curl", "tool", SecuritySeverity.HIGH, T_TOOLS,
                    "Command-line data exfiltration"),
            rule("tool_exfil_scp", "tool", SecuritySeverity.HIGH, T_TOOLS,
                    "Remote file copy"),
            rule("tool_revshell", "tool", SecuritySeverity.CRITICAL, T_TOOLS,
                    "Reverse shell pattern"),
            rule("tool_decode", "tool", SecuritySeverity.HIGH, T_TOOLS,
                    "Decode or archive sensitive data"),
            rule("tool_exec", "tool", SecuritySeverity.MEDIUM, T_TOOLS,
                    "Dynamic code execution"),
            rule("tool_shell_exfiltration", "tool", SecuritySeverity.CRITICAL, T_TOOLS,
                    "Suspected data exfiltration command"),

            rule("tracking_pixel_gif", "tracking", SecuritySeverity.LOW, T_TRACKING,
                    "Tracking pixel"),
            rule("tracking_pixel_filename", "tracking", SecuritySeverity.LOW, T_TRACKING,
                    "Tracking pixel filename"),
            rule("tracking_google_analytics", "tracking", SecuritySeverity.LOW, T_TRACKING,
                    "Google Analytics"),
            rule("tracking_gtm", "tracking", SecuritySeverity.LOW, T_TRACKING,
                    "Google Tag Manager"),
            rule("tracking_facebook", "tracking", SecuritySeverity.LOW, T_TRACKING,
                    "Facebook tracking"),
            rule("tracking_hotjar", "tracking", SecuritySeverity.LOW, T_TRACKING,
                    "Hotjar tracking"),
            rule("tracking_mixpanel", "tracking", SecuritySeverity.LOW, T_TRACKING,
                    "Mixpanel tracking"),
            rule("tracking_shortlink", "tracking", SecuritySeverity.LOW, T_TRACKING,
                    "Short link"),

            rule("fingerprint_canvas", "prompt", SecuritySeverity.MEDIUM, T_TRACKING,
                    "Canvas fingerprinting"),
            rule("fingerprint_webgl", "prompt", SecuritySeverity.MEDIUM, T_TRACKING,
                    "WebGL fingerprinting"),
            rule("fingerprint_useragent", "prompt", SecuritySeverity.MEDIUM, T_TRACKING,
                    "User-agent fingerprinting"),
            rule("fingerprint_plugins", "prompt", SecuritySeverity.MEDIUM, T_TRACKING,
                    "Plugin fingerprinting"),
            rule("fingerprint_screen", "prompt", SecuritySeverity.MEDIUM, T_TRACKING,
                    "Screen fingerprinting"),
            rule("fingerprint_audio", "prompt", SecuritySeverity.MEDIUM, T_TRACKING,
                    "Audio fingerprinting"),
            rule("fingerprint_device", "prompt", SecuritySeverity.MEDIUM, T_TRACKING,
                    "Device fingerprinting"),
            rule("fingerprint_risk", "prompt", SecuritySeverity.MEDIUM, T_TRACKING,
                    "Risk context collection"),
            rule("fingerprint_fraud", "prompt", SecuritySeverity.MEDIUM, T_TRACKING,
                    "Fraud detection collection"),
            rule("fingerprint_captcha", "prompt", SecuritySeverity.MEDIUM, T_TRACKING,
                    "Captcha token collection"),
            rule("tracking_geolocation", "prompt", SecuritySeverity.MEDIUM, T_TRACKING,
                    "Geolocation tracking")
    );

    private SecurityRuleCatalog() {
    }

    public static List<RuleMetadata> defaults() {
        return DEFAULTS.stream().map(SecurityRuleCatalog::copy).toList();
    }

    private static RuleMetadata rule(String ruleId,
                                     String category,
                                     SecuritySeverity severity,
                                     String toggleKey,
                                     String title) {
        return RuleMetadata.builder()
                .id("builtin-" + ruleId)
                .ruleId(ruleId)
                .category(category)
                .name(title)
                .severity(severity)
                .title(title)
                .description("Built-in rule: " + title)
                .toggleKey(toggleKey)
                .enabled(true)
                .build();
    }

    private static RuleMetadata copy(RuleMetadata source) {
        return source.toBuilder().build();
    }
}
