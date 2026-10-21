package cn.qx.cases.channel;

import cn.qx.api.dto.channel.ModelInfo;
import cn.qx.api.dto.channel.ModelListResult;
import cn.qx.api.IChannelCase;
import cn.qx.domain.channel.adapter.port.ILlmAdaptor;
import cn.qx.domain.channel.adapter.repository.IChannelRepository;
import cn.qx.domain.channel.model.entity.Channel;
import cn.qx.domain.channel.model.valobj.ChannelConfig;
import cn.qx.domain.channel.model.valobj.TestResult;
import cn.qx.types.common.TimeUtils;
import cn.qx.types.enums.ResponseCode;
import cn.qx.types.exception.AppException;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * 渠道用例——管理面渠道操作编排。
 * <p>testChannel：读渠道 → Channel.toChannelConfig() → 适配器连通性测试 → 回写 last_test_*。
 */
@Slf4j
@Service
public class ChannelCase implements IChannelCase {

    /** 渠道仓储端口 */
    @Resource
    private IChannelRepository channelRepository;

    /** 按渠道类型索引的适配器表。 */
    @Resource
    private Map<String, ILlmAdaptor> adaptors;

    /** 新建渠道。ID、时间与缺省配置由用例生成。 */
    @Override
    public Channel create(Channel channel) {
        if (channel == null) {
            throw new IllegalArgumentException("渠道不能为空");
        }
        if (isBlank(channel.getName()) || isBlank(channel.getType())) {
            throw new IllegalArgumentException("渠道名称和类型不能为空");
        }
        if (channelRepository.getByName(channel.getName()) != null) {
            throw new AppException(ResponseCode.CONFLICT.getCode(), "渠道名称已存在: " + channel.getName());
        }
        validateChannelType(channel.getType());
        String now = TimeUtils.now();
        channel.setId(isBlank(channel.getId()) ? TimeUtils.uuid() : channel.getId());
        channel.setStatus(channel.getStatus() == null ? 1 : channel.getStatus());
        channel.setPriority(channel.getPriority() == null ? 0 : channel.getPriority());
        channel.setWeight(channel.getWeight() == null ? 1 : channel.getWeight());
        channel.setModels(defaultJson(channel.getModels(), "[]"));
        channel.setConfig(defaultJson(channel.getConfig(), "{}"));
        channel.setModelMapping(defaultJson(channel.getModelMapping(), "{}"));
        channel.setCreatedAt(now);
        channel.setUpdatedAt(now);
        channelRepository.insert(channel);
        log.info("渠道已创建: {}", JSON.toJSONString(channel));
        return channel;
    }

    /** 查询全部渠道。 */
    @Override
    public List<Channel> list() {
        return channelRepository.listAll();
    }

    /** 查询启用渠道并按路由顺序排序。 */
    @Override
    public List<Channel> listEnabledOrdered() {
        return channelRepository.listEnabledOrdered();
    }

    /** 查询渠道详情。 */
    @Override
    public Channel get(String id) {
        Channel channel = channelRepository.getById(id);
        if (channel == null) {
            throw new IllegalArgumentException("渠道不存在: " + id);
        }
        return channel;
    }

    /** 更新渠道全部配置。 */
    @Override
    public Channel update(Channel channel) {
        if (channel == null || isBlank(channel.getId())) {
            throw new IllegalArgumentException("渠道 ID 不能为空");
        }
        Channel current = get(channel.getId());
        validateChannelType(channel.getType());
        Channel sameName = channelRepository.getByName(channel.getName());
        if (sameName != null && !sameName.getId().equals(channel.getId())) {
            throw new AppException(ResponseCode.CONFLICT.getCode(), "渠道名称已存在: " + channel.getName());
        }
        channel.setCreatedAt(current.getCreatedAt());
        channel.setStatus(channel.getStatus() == null ? current.getStatus() : channel.getStatus());
        channel.setUpdatedAt(TimeUtils.now());
        channelRepository.update(channel);
        log.info("渠道已更新: {}", JSON.toJSONString(channel));
        return channel;
    }

    /** 启用或停用渠道。 */
    @Override
    public Channel toggle(String id, boolean enabled) {
        get(id);
        channelRepository.updateStatus(id, enabled ? 1 : 0, TimeUtils.now());
        return get(id);
    }

    /** 删除渠道；不存在视为幂等成功。 */
    @Override
    public void delete(String id) {
        channelRepository.deleteById(id);
    }

    /**
     * 渠道连通性测试（真实上游端点）。
     *
     * @param id 渠道 ID
     * @return 测试结果
     */
    @Override
    public TestResult testChannel(String id) {
        Channel channel = channelRepository.getById(id);
        if (channel == null) {
            throw new IllegalArgumentException("渠道不存在: " + id);
        }
        ChannelConfig config = channel.toChannelConfig();
        ILlmAdaptor adaptor = getAdaptor(channel.getType());
        TestResult result = adaptor.test(config);
        channelRepository.updateLastTest(id, result.isSuccess(), now());
        log.info("渠道连通性测试完成: id={}, result={}", id, JSON.toJSONString(result));
        return result;
    }

    private String now() {
        return TimeUtils.now();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String defaultJson(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    private ILlmAdaptor getAdaptor(String channelType) {
        ILlmAdaptor adaptor = adaptors.get(channelType);
        if (adaptor == null) {
            throw new IllegalArgumentException("不支持的渠道类型: " + channelType);
        }
        return adaptor;
    }

    private void validateChannelType(String channelType) {
        try {
            getAdaptor(channelType);
        } catch (IllegalArgumentException e) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(),
                    "不支持的渠道类型: " + channelType);
        }
    }

    /** 聚合启用渠道的模型目录，按渠道路由顺序去重。 */
    @Override
    public ModelListResult listModelCatalog() {
        List<ModelInfo> models = listEnabledOrdered().stream()
                .flatMap(channel -> channel.activeModels().stream()
                        .map(model -> ModelInfo.builder()
                                .id(model)
                                .object("model")
                                .ownedBy(channel.getType())
                                .build()))
                .collect(java.util.stream.Collectors.toMap(
                        ModelInfo::getId, model -> model, (first, ignored) -> first,
                        java.util.LinkedHashMap::new))
                .values().stream().toList();
        return ModelListResult.builder().data(models).build();
    }
}
