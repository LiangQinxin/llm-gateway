package cn.qx.domain.audit.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 请求日志分页筛选条件。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestLogQuery {

    /** 按请求模型名筛选 */
    private String model;
    /** 按 API 密钥 ID 筛选（UUID） */
    private String apiKeyId;
    /** 按渠道 ID 筛选（UUID） */
    private String channelId;
    /** 按上游 HTTP 状态码筛选 */
    private Integer statusCode;
    /** 创建时间起（ISO-8601 带偏移，闭区间） */
    private String createdFrom;
    /** 创建时间止（ISO-8601 带偏移，闭区间） */
    private String createdTo;
    /** 页码（从 1 开始，小于 1 按 1 处理） */
    private int page;
    /** 每页条数（小于 1 按 1 处理） */
    private int size;

    /** MyBatis OFFSET。 */
    public long getOffset() {
        return (long) (Math.max(1, page) - 1) * Math.max(1, size);
    }
}
