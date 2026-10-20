package cn.qx.cases.audit;

import cn.qx.domain.audit.model.entity.RequestLog;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 请求日志分页结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestLogPageResult {

    private List<RequestLog> records;
    private long total;
    private int page;
    private int size;
}
