package cn.qx.domain.security.service.scanner;

import cn.qx.domain.security.model.valobj.ScanFinding;
import cn.qx.domain.security.model.valobj.SecurityCategory;
import cn.qx.domain.security.model.valobj.SecuritySeverity;

import java.util.List;

/**
 * 子扫描器共用的发现构造与文本工具。
 */
final class ScannerSupport {

    private ScannerSupport() {
    }

    static ScanFinding finding(String scanner,
                               String ruleId,
                               String title,
                               String description,
                               SecuritySeverity severity,
                               List<SecurityCategory> categories,
                               String evidence,
                               String location,
                               String message,
                               boolean redactable) {
        return ScanFinding.builder()
                .ruleId(ruleId)
                .scanner(scanner)
                .title(title)
                .description(description)
                .severity(severity)
                .categories(List.copyOf(categories))
                .evidence(evidence)
                .evidenceMasked(EvidenceMasker.mask(evidence))
                .location(location)
                .message(message)
                .redactable(redactable)
                .build();
    }

    static double entropy(String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        int[] frequencies = new int[256];
        int length = value.length();
        for (int i = 0; i < length; i++) {
            int ch = value.charAt(i);
            frequencies[ch & 0xFF]++;
        }
        double entropy = 0;
        for (int frequency : frequencies) {
            if (frequency == 0) {
                continue;
            }
            double probability = frequency / (double) length;
            entropy -= probability * (Math.log(probability) / Math.log(2));
        }
        return entropy;
    }

    static boolean hasLetterAndDigit(String value) {
        boolean letter = false;
        boolean digit = false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            letter |= Character.isLetter(ch);
            digit |= Character.isDigit(ch);
        }
        return letter && digit;
    }

    static String context(String text, int start, int end) {
        int from = Math.max(0, start - 6);
        int to = Math.min(text.length(), end + 6);
        return text.substring(from, to);
    }
}
