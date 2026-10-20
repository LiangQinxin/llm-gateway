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

    private String model;
    private String apiKeyId;
    private String channelId;
    private Integer statusCode;
    private String createdFrom;
    private String createdTo;
    private int page;
    private int size;

    /** MyBatis OFFSET。 */
    public long getOffset() {
        return (long) (Math.max(1, page) - 1) * Math.max(1, size);
    }
}
