package cn.qx.domain.security.service.scanner;

import cn.qx.domain.security.adapter.port.ISubScanner;
import cn.qx.domain.security.model.valobj.ScanFinding;
import cn.qx.domain.security.model.valobj.ScanSettings;
import cn.qx.domain.security.model.valobj.ScanText;
import cn.qx.domain.security.model.valobj.SecurityCategory;
import cn.qx.domain.security.model.valobj.SecuritySeverity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 危险工具扫描器：命令动词与危险参数形态。
 */
@Component
public class ToolRiskScanner implements ISubScanner {

    private static final String NAME = "tool";
    private static final List<SecurityCategory> CATEGORIES = List.of(SecurityCategory.COMMAND);
    private static final List<Rule> RULES = List.of(
            rule("tool_curl", "curl command", "(?i)\\bcurl\\b", SecuritySeverity.MEDIUM),
            rule("tool_wget", "wget command", "(?i)\\bwget\\b", SecuritySeverity.MEDIUM),
            rule("tool_bash_c", "Shell command execution",
                    "(?i)\\b(?:bash|sh|zsh)\\s+-c\\b", SecuritySeverity.HIGH),
            rule("tool_powershell", "PowerShell execution",
                    "(?i)\\b(?:powershell|pwsh)\\b", SecuritySeverity.HIGH),
            rule("tool_python_c", "Python inline execution",
                    "(?i)\\bpython3?\\s+-c\\b", SecuritySeverity.HIGH),
            rule("tool_rm_rf", "Recursive forced delete",
                    "(?i)\\brm\\s+-rf\\b", SecuritySeverity.HIGH),
            rule("tool_mkfs", "Filesystem formatting",
                    "(?i)\\bmkfs\\b", SecuritySeverity.HIGH),
            rule("tool_dd", "Raw disk write",
                    "(?i)\\bdd\\s+if=", SecuritySeverity.HIGH),
            rule("tool_chmod_777", "World-writable permissions",
                    "(?i)\\bchmod\\s+777\\b", SecuritySeverity.HIGH),
            rule("tool_netcat", "Netcat usage",
                    "(?i)\\b(?:nc|netcat)\\b", SecuritySeverity.HIGH),
            rule("tool_nmap", "Network scanning",
                    "(?i)\\bnmap\\b", SecuritySeverity.HIGH),
            rule("tool_exfil_curl", "Command-line data exfiltration",
                    "(?i)(?:\\b(?:curl|wget)\\b[^\\n]*?(?:-d\\s+@|--data(?:-binary)?\\s+@|-F\\s+\\w+=@)"
                            + "|\\|\\s*base64\\b)", SecuritySeverity.HIGH),
            rule("tool_exfil_scp", "Remote file copy",
                    "(?i)\\b(?:scp|sftp\\s+put)\\b", SecuritySeverity.HIGH),
            rule("tool_revshell", "Reverse shell pattern",
                    "(?i)(?:bash\\s+-i\\s*>\\s*&\\s*/dev/tcp/|nc\\s+-e\\b"
                            + "|curl\\b[^\\n]*\\|\\s*sh\\b|wget\\b[^\\n]*\\|\\s*sh\\b"
                            + "|powershell\\s+-enc\\b|IEX[^\\n]*DownloadString)",
                    SecuritySeverity.CRITICAL),
            rule("tool_decode", "Decode or archive sensitive data",
                    "(?i)(?:cat\\b[^\\n]*\\|\\s*base64\\b|base64\\s+-w0\\b|\\bxxd\\b"
                            + "|\\btar\\s+czf\\s+-\\b)", SecuritySeverity.HIGH),
            rule("tool_exec", "Dynamic code execution",
                    "(?i)\\b(?:eval|exec|os\\.system|subprocess|Runtime\\.exec|ProcessBuilder"
                            + "|node\\s+-e|perl\\s+-e)\\b", SecuritySeverity.MEDIUM)
    );

    @Override
    public String scannerName() {
        return NAME;
    }

    @Override
    public boolean enabled(ScanSettings settings) {
        return settings != null && settings.isToolRisk();
    }

    /** 敏感文件/环境变量读取特征（对齐安全审计引擎组合检测）。 */
    private static final List<String> SENSITIVE_READS = List.of(
            "cat .env", "cat ~/.ssh", "printenv", "base64 ~/.ssh");

    /** 网络外发特征。 */
    private static final List<String> NETWORK_OUT = List.of(
            "curl ", "wget ", "http://", "https://");

    @Override
    public List<ScanFinding> scan(ScanText text) {
        if (text == null || text.text() == null || text.text().isEmpty()) {
            return List.of();
        }
        List<ScanFinding> findings = new ArrayList<>();
        for (Rule rule : RULES) {
            Matcher matcher = rule.pattern().matcher(text.text());
            while (matcher.find()) {
                findings.add(ScannerSupport.finding(
                        NAME, rule.ruleId(), rule.title(),
                        "Detected a potentially dangerous command pattern",
                        rule.severity(), CATEGORIES, matcher.group(),
                        text.locationAt(matcher.start(), matcher.end()),
                        rule.title() + " detected", false));
            }
        }
        exfiltrationCombination(text).forEach(findings::add);
        return findings;
    }

    /**
     * 组合检测（行为链）：敏感文件读取 + 网络外发同时出现 → 数据外传 Critical。
     * 单个特征可能只是正常使用，组合出现才是高危信号。
     */
    private static List<ScanFinding> exfiltrationCombination(ScanText text) {
        String lower = text.text().toLowerCase(Locale.ROOT);
        boolean readsSensitive = SENSITIVE_READS.stream().anyMatch(lower::contains);
        boolean networkOut = NETWORK_OUT.stream().anyMatch(lower::contains);
        if (!readsSensitive || !networkOut) {
            return List.of();
        }
        int snippetEnd = Math.min(text.text().length(), 80);
        String snippet = snippetEnd == text.text().length()
                ? text.text() : text.text().substring(0, snippetEnd) + "...";
        return List.of(ScannerSupport.finding(
                NAME, "tool_shell_exfiltration", "Suspected data exfiltration command",
                "Content contains both sensitive file/env reads and external network transfer, "
                        + "indicating severe data leakage risk",
                SecuritySeverity.CRITICAL, CATEGORIES, snippet,
                text.locationAt(0, snippetEnd),
                "Suspected data exfiltration command detected", false));
    }

    /**
     * 单条危险工具扫描规则。
     * @param ruleId   规则 ID（与内置规则表一致）
     * @param title    人读规则标题
     * @param pattern  危险工具调用特征匹配正则
     * @param severity 命中后的严重度
     */
    private record Rule(String ruleId, String title, Pattern pattern, SecuritySeverity severity) {
    }

    private static Rule rule(String ruleId,
                             String title,
                             String regex,
                             SecuritySeverity severity) {
        return new Rule(ruleId, title, Pattern.compile(regex), severity);
    }
}
