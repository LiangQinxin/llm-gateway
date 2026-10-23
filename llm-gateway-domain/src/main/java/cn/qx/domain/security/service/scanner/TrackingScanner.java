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
 * 追踪扫描器：追踪域名、短链、指纹与定位代码。追踪类别不参与组合加分。
 */
@Component
public class TrackingScanner implements ISubScanner {

    private static final String NAME = "tracking";
    private static final List<SecurityCategory> CATEGORIES = List.of(SecurityCategory.TRACKING);
    private static final List<Rule> RULES = List.of(
            rule("tracking_pixel_gif", "Tracking pixel", "(?i)R0lGODlhAQABAIA",
                    SecuritySeverity.LOW),
            rule("tracking_pixel_filename", "Tracking pixel filename",
                    "(?i)(?:pixel\\.gif|tracking-pixel)", SecuritySeverity.LOW),
            rule("tracking_google_analytics", "Google Analytics",
                    "(?i)google-analytics\\.com", SecuritySeverity.LOW),
            rule("tracking_gtm", "Google Tag Manager",
                    "(?i)googletagmanager\\.com", SecuritySeverity.LOW),
            rule("tracking_facebook", "Facebook tracking",
                    "(?i)facebook\\.net/tr", SecuritySeverity.LOW),
            rule("tracking_hotjar", "Hotjar tracking",
                    "(?i)hotjar\\.com", SecuritySeverity.LOW),
            rule("tracking_mixpanel", "Mixpanel tracking",
                    "(?i)mixpanel\\.com", SecuritySeverity.LOW),
            rule("tracking_shortlink", "Short link",
                    "(?i)\\b(?:bit\\.ly|t\\.co|goo\\.gl|tinyurl\\.com|is\\.gd|shorturl\\.at|t\\.cn)\\b",
                    SecuritySeverity.LOW),
            rule("fingerprint_canvas", "Canvas fingerprinting",
                    "(?i)canvas\\.toDataURL\\(\\)", SecuritySeverity.MEDIUM),
            rule("fingerprint_webgl", "WebGL fingerprinting",
                    "(?i)webglRenderingContext", SecuritySeverity.MEDIUM),
            rule("fingerprint_useragent", "User-agent fingerprinting",
                    "(?i)navigator\\.userAgent", SecuritySeverity.MEDIUM),
            rule("fingerprint_plugins", "Plugin fingerprinting",
                    "(?i)navigator\\.plugins", SecuritySeverity.MEDIUM),
            rule("fingerprint_screen", "Screen fingerprinting",
                    "(?i)screen\\.width.*screen\\.height", SecuritySeverity.MEDIUM),
            rule("fingerprint_audio", "Audio fingerprinting",
                    "(?i)AudioContext.*fingerprint", SecuritySeverity.MEDIUM),
            rule("fingerprint_device", "Device fingerprinting",
                    "(?i)deviceFingerprint", SecuritySeverity.MEDIUM),
            rule("fingerprint_risk", "Risk context collection",
                    "(?i)riskContext", SecuritySeverity.MEDIUM),
            rule("fingerprint_fraud", "Fraud detection collection",
                    "(?i)fraudDetection", SecuritySeverity.MEDIUM),
            rule("fingerprint_captcha", "Captcha token collection",
                    "(?i)captchaToken", SecuritySeverity.MEDIUM),
            rule("tracking_geolocation", "Geolocation tracking",
                    "(?i)(?:navigator\\.geolocation|getCurrentPosition|watchPosition"
                            + "|ipapi\\.co|ipstack\\.com|ip2location\\.com|ip-api\\.com|geoip)",
                    SecuritySeverity.MEDIUM)
    );

    @Override
    public String scannerName() {
        return NAME;
    }

    @Override
    public boolean enabled(ScanSettings settings) {
        return settings != null && settings.isTracking();
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
                        "Detected tracking or fingerprinting behavior",
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
