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
        return findings;
    }

    private record Rule(String ruleId, String title, Pattern pattern, SecuritySeverity severity) {
    }

    private static Rule rule(String ruleId,
                             String title,
                             String regex,
                             SecuritySeverity severity) {
        return new Rule(ruleId, title, Pattern.compile(regex), severity);
    }
}
