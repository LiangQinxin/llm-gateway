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

/**
 * Unicode 扫描器：不可见字符、方向控制符、私用区与同形字混排。
 */
@Component
public class UnicodeScanner implements ISubScanner {

    /** 子扫描器名称（注册到引擎）。 */
    private static final String NAME = "unicode";
    /** 本扫描器发现挂载的风险类别（隐写）。 */
    private static final List<SecurityCategory> CATEGORIES =
            List.of(SecurityCategory.STEGANOGRAPHY);

    @Override
    public String scannerName() {
        return NAME;
    }

    @Override
    public boolean enabled(ScanSettings settings) {
        return settings != null && settings.isUnicode();
    }

    @Override
    public List<ScanFinding> scan(ScanText text) {
        if (text == null || text.text() == null || text.text().isEmpty()) {
            return List.of();
        }
        List<ScanFinding> findings = new ArrayList<>();
        int offset = 0;
        while (offset < text.text().length()) {
            int codePoint = text.text().codePointAt(offset);
            int width = Character.charCount(codePoint);
            Classification classification = classify(codePoint);
            if (classification != null) {
                String evidence = classification.ruleId() + " U+"
                        + String.format("%04X", codePoint) + " "
                        + ScannerSupport.context(text.text(), offset, offset + width);
                findings.add(ScannerSupport.finding(
                        NAME, classification.ruleId(), classification.title(),
                        "Detected Unicode steganography or control characters",
                        classification.severity(), CATEGORIES, evidence,
                        text.locationAt(offset, offset + width),
                        classification.title() + " detected", true));
            }
            offset += width;
        }
        findings.addAll(mixedScriptFindings(text));
        return findings;
    }

    private static Classification classify(int codePoint) {
        if (codePoint == 0x200B || codePoint == 0x200C || codePoint == 0x200D
                || codePoint == 0x2060 || codePoint == 0xFEFF) {
            return new Classification("unicode_zero_width", "Zero-width character",
                    SecuritySeverity.MEDIUM);
        }
        if ((codePoint >= 0x202A && codePoint <= 0x202E)
                || (codePoint >= 0x2066 && codePoint <= 0x2069)) {
            return new Classification("unicode_bidi", "Bidi control character",
                    SecuritySeverity.HIGH);
        }
        if ((codePoint >= 0xFE00 && codePoint <= 0xFE0F)
                || (codePoint >= 0xE0100 && codePoint <= 0xE01EF)) {
            return new Classification("unicode_variation", "Variation selector",
                    SecuritySeverity.MEDIUM);
        }
        if (codePoint >= 0xE000 && codePoint <= 0xF8FF) {
            return new Classification("unicode_pua", "Private use character",
                    SecuritySeverity.MEDIUM);
        }
        return null;
    }

    private static List<ScanFinding> mixedScriptFindings(ScanText text) {
        List<ScanFinding> findings = new ArrayList<>();
        int offset = 0;
        while (offset < text.text().length()) {
            if (!Character.isLetter(text.text().codePointAt(offset))) {
                offset += Character.charCount(text.text().codePointAt(offset));
                continue;
            }
            int start = offset;
            boolean latin = false;
            boolean confusable = false;
            while (offset < text.text().length()) {
                int codePoint = text.text().codePointAt(offset);
                if (!Character.isLetter(codePoint)) {
                    break;
                }
                Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
                latin |= script == Character.UnicodeScript.LATIN;
                confusable |= script == Character.UnicodeScript.CYRILLIC
                        || script == Character.UnicodeScript.GREEK;
                offset += Character.charCount(codePoint);
            }
            if (latin && confusable) {
                String evidence = text.text().substring(start, offset);
                findings.add(ScannerSupport.finding(
                        NAME, "unicode_homoglyph", "Mixed-script homoglyph token",
                        "Detected Latin mixed with Cyrillic or Greek in one token",
                        SecuritySeverity.MEDIUM, CATEGORIES, evidence,
                        text.locationAt(start, offset), "Mixed-script token detected", false));
            }
        }
        return findings;
    }

    /**
     * 单个 Unicode 码点的分类结果。
     * @param ruleId   命中规则 ID（如 unicode_zero_width / unicode_bidi）
     * @param title    人读分类标题
     * @param severity 命中后的严重度
     */
    private record Classification(String ruleId, String title, SecuritySeverity severity) {
    }
}
