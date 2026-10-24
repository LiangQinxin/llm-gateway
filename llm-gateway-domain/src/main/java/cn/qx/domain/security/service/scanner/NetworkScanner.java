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
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 网络扫描器：外联目标、IP 探测与内网地址。公网普通 URL 不命中。
 */
@Component
public class NetworkScanner implements ISubScanner {

    private static final String NAME = "network";
    private static final List<SecurityCategory> EXFIL_CATEGORIES =
            List.of(SecurityCategory.EXFIL);
    private static final Set<String> EXFIL_DOMAINS = Set.of(
            "webhook.site", "requestbin", "ngrok.io", "ngrok-free.app", "transfer.sh",
            "0x0.st", "pastebin.com", "ipfs.io", "interact.sh");
    private static final Set<String> PROBE_DOMAINS = Set.of(
            "ifconfig.me", "icanhazip.com", "ipinfo.io", "ip-api.com", "ipify.org",
            "checkip.amazonaws.com");
    private static final Set<String> BENIGN_DOMAINS = Set.of(
            "localhost", "localhost.localdomain");

    private static final Pattern DOMAIN = Pattern.compile(
            "(?i)\\b(?:https?://)?([a-z0-9](?:[a-z0-9.-]*[a-z0-9])?\\.[a-z]{2,})\\b");
    private static final Pattern IPV4 = Pattern.compile("\\b(\\d{1,3}(?:\\.\\d{1,3}){3})\\b");
    private static final Pattern IPV6 = Pattern.compile(
            "(?i)\\b((?:fe[89ab][0-9a-f]:[0-9a-f:]*|fd[0-9a-f:]*))\\b");

    @Override
    public String scannerName() {
        return NAME;
    }

    @Override
    public boolean enabled(ScanSettings settings) {
        return settings != null && settings.isNetwork();
    }

    @Override
    public List<ScanFinding> scan(ScanText text) {
        if (text == null || text.text() == null || text.text().isEmpty()) {
            return List.of();
        }
        List<ScanFinding> findings = new ArrayList<>();
        scanDomains(text, findings);
        scanIpv4(text, findings);
        scanIpv6(text, findings);
        return findings;
    }

    private static void scanDomains(ScanText text, List<ScanFinding> findings) {
        Matcher matcher = DOMAIN.matcher(text.text());
        while (matcher.find()) {
            String domain = matcher.group(1).toLowerCase(Locale.ROOT);
            String bare = stripWww(domain);
            if (benign(bare)) {
                continue;
            }
            if (matchesDomainTable(bare, EXFIL_DOMAINS)) {
                findings.add(finding(text, matcher, "network_exfil_target",
                        "External exfiltration target",
                        SecuritySeverity.HIGH, EXFIL_CATEGORIES));
                continue;
            }
            if (matchesDomainTable(bare, PROBE_DOMAINS)) {
                findings.add(finding(text, matcher, "network_ip_probe",
                        "Public IP probe service",
                        SecuritySeverity.HIGH, EXFIL_CATEGORIES));
                continue;
            }
            if (bare.endsWith(".local") || bare.endsWith(".internal") || bare.endsWith(".lan")) {
                findings.add(finding(text, matcher, "network_internal_probe",
                        "Internal network probe", SecuritySeverity.MEDIUM, List.of()));
            }
        }
    }

    private static void scanIpv4(ScanText text, List<ScanFinding> findings) {
        Matcher matcher = IPV4.matcher(text.text());
        while (matcher.find()) {
            String ip = matcher.group(1);
            if (benignIpv4(ip)) {
                continue;
            }
            int[] octets = parseIpv4(ip);
            if (octets == null) {
                continue;
            }
            SecuritySeverity severity = internalSeverity(octets);
            if (severity != null) {
                findings.add(finding(text, matcher, "network_internal_probe",
                        "Internal IP address", severity, List.of()));
            }
        }
    }

    private static void scanIpv6(ScanText text, List<ScanFinding> findings) {
        Matcher matcher = IPV6.matcher(text.text());
        while (matcher.find()) {
            String ip = matcher.group(1).toLowerCase(Locale.ROOT);
            if ("::1".equals(ip) || ip.startsWith("fe80::") || ip.startsWith("fd")) {
                findings.add(finding(text, matcher, "network_internal_probe",
                        "Internal IPv6 address", SecuritySeverity.MEDIUM, List.of()));
            }
        }
    }

    private static ScanFinding finding(ScanText text,
                                       Matcher matcher,
                                       String ruleId,
                                       String title,
                                       SecuritySeverity severity,
                                       List<SecurityCategory> categories) {
        return ScannerSupport.finding(
                NAME, ruleId, title, "Detected a network target requiring review",
                severity, categories, matcher.group(),
                text.locationAt(matcher.start(), matcher.end()),
                title + " detected", false);
    }

    private static SecuritySeverity internalSeverity(int[] octets) {
        if (octets[0] == 169 && octets[1] == 254 && octets[2] == 169 && octets[3] == 254) {
            return SecuritySeverity.HIGH;
        }
        if (octets[0] == 10
                || (octets[0] == 172 && octets[1] >= 16 && octets[1] <= 31)
                || (octets[0] == 192 && octets[1] == 168)
                || (octets[0] == 169 && octets[1] == 254)) {
            return SecuritySeverity.MEDIUM;
        }
        return null;
    }

    private static int[] parseIpv4(String value) {
        String[] parts = value.split("\\.");
        int[] octets = new int[4];
        for (int i = 0; i < parts.length; i++) {
            try {
                octets[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                return null;
            }
            if (octets[i] < 0 || octets[i] > 255) {
                return null;
            }
        }
        return octets;
    }

    private static boolean benignIpv4(String value) {
        return value.startsWith("127.") || "0.0.0.0".equals(value);
    }

    private static boolean benign(String domain) {
        return BENIGN_DOMAINS.contains(domain) || "0.0.0.0".equals(domain);
    }

    private static boolean matchesDomainTable(String domain, Set<String> table) {
        return table.stream().anyMatch(target -> domain.equals(target) || domain.endsWith("." + target));
    }

    private static String stripWww(String domain) {
        return domain.startsWith("www.") ? domain.substring(4) : domain;
    }
}
