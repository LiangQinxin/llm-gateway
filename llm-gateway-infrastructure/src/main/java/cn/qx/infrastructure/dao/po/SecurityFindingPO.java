package cn.qx.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * security_audit_finding 表持久化对象，字段与库表列一一对应。
 * <p>列名 → 属性名：id、log_id→logId、phase、category、rule_id→ruleId、
 * severity、title、description、location、evidence_masked→evidenceMasked、
 * evidence_hash→evidenceHash、action、created_at→createdAt。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityFindingPO {

    /** id 列：发现 ID（UUID） */
    private String id;
    /** log_id 列：关联 request_logs.id */
    private String logId;
    /** phase 列：request / response */
    private String phase;
    /** category 列：类别集合逗号拼接 */
    private String category;
    /** rule_id 列：规则 ID */
    private String ruleId;
    /** severity 列：info / low / medium / high / critical */
    private String severity;
    /** title 列：规则标题 */
    private String title;
    /** description 列：规则描述 */
    private String description;
    /** location 列：JSONPath 或 @raw[起..止] */
    private String location;
    /** evidence_masked 列：打码后证据，不存原文 */
    private String evidenceMasked;
    /** evidence_hash 列：证据 SHA-256 */
    private String evidenceHash;
    /** action 列：allow / warn / redact / block */
    private String action;
    /** created_at 列：ISO-8601 带偏移 */
    private String createdAt;
}
