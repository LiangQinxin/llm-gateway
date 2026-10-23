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
 * 路径扫描器：敏感文件引用与路径穿越。
 */
@Component
public class PathScanner implements ISubScanner {

    private static final String NAME = "path";
    private static final List<SecurityCategory> CATEGORIES =
            List.of(SecurityCategory.SENSITIVE_FILE);

    private static final List<Rule> RULES = List.of(
            new Rule("path_sensitive_unix", "Sensitive Unix Path",
                    Pattern.compile("(?<![\\w])(?:~?/\\.ssh/(?:id_rsa(?:\\.pub)?|authorized_keys|config|known_hosts)"
                            + "|/etc/(?:passwd|shadow|hosts)|(?:~|/root)/\\.aws/credentials"
                            + "|~/\\.bash_history|/proc/self/environ)(?![\\w])"),
                    SecuritySeverity.HIGH),
            new Rule("path_sensitive_windows", "Sensitive Windows Path",
                    Pattern.compile("(?i)(?:[A-Z]:\\\\Users\\\\[^\\\\\\s]+\\\\\\.ssh\\\\"
                            + "|C:\\\\Windows\\\\System32\\\\config\\\\SAM"
                            + "|AppData\\\\Roaming\\\\Microsoft\\\\Credentials|%APPDATA%)"),
                    SecuritySeverity.HIGH),
            new Rule("path_sensitive_project", "Sensitive Project File",
                    Pattern.compile("(?i)(?<![\\w.-])(?:\\.env|database\\.ya?ml"
                            + "|application[^\\\\/\\s]*\\.ya?ml|service-account\\.json"
                            + "|firebase-adminsdk\\.json|config[/\\\\]secrets\\.[^\\s]+)(?![\\w.-])"),
                    SecuritySeverity.MEDIUM),
            new Rule("path_traversal", "Path Traversal",
                    Pattern.compile("(?i)(?:\\.\\.[/\\\\]){2,}"
                            + "|\\.\\.[/\\\\](?:etc|windows|users|root|proc|home|system32)\\b"
                            + "|(?:%2e%2e(?:%2f|%5c)){2,}"),
                    SecuritySeverity.MEDIUM)
    );

    @Override
    public String scannerName() {
        return NAME;
    }

    @Override
    public boolean enabled(ScanSettings settings) {
        return settings != null && settings.isPath();
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
                        "Detected a sensitive file path or traversal sequence",
                        rule.severity(), CATEGORIES, matcher.group(),
                        text.locationAt(matcher.start(), matcher.end()),
                        rule.title() + " detected", false));
            }
        }
        return findings;
    }

    private record Rule(String ruleId, String title, Pattern pattern, SecuritySeverity severity) {
    }
}
