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

    /** 当前页请求日志记录列表 */
    private List<RequestLog> records;
    /** 查询结果总条数 */
    private long total;
    /** 当前页码（从 1 开始） */
    private int page;
    /** 每页条数 */
    private int size;
}
