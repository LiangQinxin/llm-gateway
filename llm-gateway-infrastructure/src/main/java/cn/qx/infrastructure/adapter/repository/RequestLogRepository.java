package cn.qx.infrastructure.adapter.repository;

import cn.qx.domain.audit.adapter.repository.IRequestLogRepository;
import cn.qx.domain.audit.model.entity.RequestLog;
import cn.qx.domain.audit.model.valobj.RequestLogQuery;
import cn.qx.infrastructure.dao.RequestLogMapper;
import cn.qx.infrastructure.dao.po.RequestLogPO;
import jakarta.annotation.Resource;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 请求日志仓储实现——MyBatis 读写 request_logs 表。
 * <p>PO（RequestLogPO）与领域实体（RequestLog）字段同构，出入参在此转换。
 */
@Repository
public class RequestLogRepository implements IRequestLogRepository {

    /** 请求日志表 Mapper */
    @Resource
    private RequestLogMapper requestLogMapper;

    @Override
    public void insert(RequestLog requestLog) {
        RequestLogPO po = toPO(requestLog);
        requestLogMapper.insert(po);
        // useGeneratedKeys 将自增 seq 回填到 PO，需同步回写领域实体
        requestLog.setSeq(po.getSeq());
    }

    @Override
    public RequestLog getById(String id) {
        return toEntity(requestLogMapper.queryById(id));
    }

    @Override
    public long count(RequestLogQuery query) {
        return requestLogMapper.count(query);
    }

    @Override
    public List<RequestLog> page(RequestLogQuery query) {
        return requestLogMapper.queryPage(query).stream().map(RequestLogRepository::toEntity).toList();
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

    private static RequestLogPO toPO(RequestLog entity) {
        RequestLogPO po = new RequestLogPO();
        BeanUtils.copyProperties(entity, po);
        return po;
    }

    private static RequestLog toEntity(RequestLogPO po) {
        if (po == null) {
            return null;
        }
        RequestLog entity = new RequestLog();
        BeanUtils.copyProperties(po, entity);
        return entity;
    }
}
