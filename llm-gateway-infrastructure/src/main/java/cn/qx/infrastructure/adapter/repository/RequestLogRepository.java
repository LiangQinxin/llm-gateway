package cn.qx.infrastructure.adapter.repository;

import cn.qx.domain.audit.adapter.repository.IRequestLogRepository;
import cn.qx.domain.audit.model.entity.RequestLog;
import cn.qx.domain.audit.model.valobj.RequestLogQuery;
import cn.qx.infrastructure.persistent.dao.RequestLogMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 请求日志仓储实现。
 */
@Repository
public class RequestLogRepository implements IRequestLogRepository {

    /** 请求日志表 Mapper */
    @Resource
    private RequestLogMapper requestLogMapper;

    @Override
    public void insert(RequestLog requestLog) {
        requestLogMapper.insert(requestLog);
    }

    @Override
    public RequestLog getById(String id) {
        return requestLogMapper.queryById(id);
    }

    @Override
    public long count(RequestLogQuery query) {
        return requestLogMapper.count(query);
    }

    @Override
    public List<RequestLog> page(RequestLogQuery query) {
        return requestLogMapper.queryPage(query);
    }

    @Override
    public int deleteById(String id) {
        return requestLogMapper.deleteById(id);
    }

    @Override
    public int deleteBefore(String createdAt) {
        return requestLogMapper.deleteBefore(createdAt);
    }

    @Override
    public int clearAll() {
        return requestLogMapper.clearAll();
    }
}
