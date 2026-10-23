package cn.qx.infrastructure.adapter.repository;

import cn.qx.domain.security.model.valobj.ScanFinding;
import cn.qx.domain.security.model.valobj.ScanPhase;
import cn.qx.domain.security.model.valobj.SecurityCategory;
import cn.qx.infrastructure.dao.po.SecurityFindingPO;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * ScanFinding → SecurityFindingPO 映射：类别拼接、证据打码与哈希。
 */
public final class SecurityFindingPersistenceMapper {

    private SecurityFindingPersistenceMapper() {
    }

    public static SecurityFindingPO toPO(String logId,
                                         ScanPhase phase,
                                         ScanFinding finding,
                                         String createdAt) {
        return SecurityFindingPO.builder()
                .id(UUID.randomUUID().toString())
                .logId(logId)
                .phase((phase == null ? ScanPhase.REQUEST : phase).name().toLowerCase())
                .category(finding.getCategories() == null ? "" : finding.getCategories().stream()
                        .map(SecurityCategory::code)
                        .collect(Collectors.joining(",")))
                .ruleId(finding.getRuleId())
                .severity(finding.getSeverity() == null ? "" : finding.getSeverity().code())
                .title(finding.getTitle())
                .description(finding.getDescription())
                .location(finding.getLocation())
                .evidenceMasked(maskEvidence(finding))
                .evidenceHash(hash(finding.getEvidence()))
                .action(finding.getAction() == null ? "allow" : finding.getAction().code())
                .createdAt(createdAt)
                .build();
    }

    private static String maskEvidence(ScanFinding finding) {
        String evidence = finding.getEvidence() == null ? "" : finding.getEvidence();
        evidence = evidence
                .replaceAll("[\\u200B-\\u200D\\uFEFF\\u202A-\\u202E\\u2066-\\u2069\\uFE00-\\uFE0F\\uE000-\\uF8FF]", "")
                .replaceAll("[A-Za-z0-9_+\\-/=]{6,}", "***");
        return evidence;
    }

    private static String hash(String evidence) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((evidence == null ? "" : evidence).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
