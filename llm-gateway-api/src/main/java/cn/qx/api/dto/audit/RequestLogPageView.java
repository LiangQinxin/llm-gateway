package cn.qx.api.dto.audit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 请求日志分页管理面输出对象（Controller 层组装，records 为布尔视图）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestLogPageView {

    /** 当前页请求日志记录列表（管理面视图：isStream/isRetry/sanitized 为布尔） */
    private List<RequestLogView> records;
    /** 查询结果总条数 */
    private long total;
    /** 当前页码（从 1 开始） */
    private int page;
    /** 每页条数 */
    private int size;
}
