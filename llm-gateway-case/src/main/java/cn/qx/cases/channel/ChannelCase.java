package cn.qx.cases.channel;

import cn.qx.domain.channel.adapter.port.IChannelAdaptorRegistry;
import cn.qx.domain.channel.adapter.port.ILlmAdaptor;
import cn.qx.domain.channel.adapter.repository.IChannelRepository;
import cn.qx.domain.channel.model.entity.Channel;
import cn.qx.domain.channel.model.valobj.ChannelConfig;
import cn.qx.domain.channel.model.valobj.TestResult;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 渠道用例——管理面渠道操作编排。
 * <p>testChannel：读渠道 → Channel.toChannelConfig() → 适配器连通性测试 → 回写 last_test_*。
 */
@Slf4j
@Service
public class ChannelCase {

    /** 渠道仓储端口 */
    @Resource
    private IChannelRepository channelRepository;

    /** 渠道适配器注册表端口 */
    @Resource
    private IChannelAdaptorRegistry adaptorRegistry;

    /**
     * 渠道连通性测试（真实上游端点）。
     *
     * @param id 渠道 ID
     * @return 测试结果
     */
    public TestResult testChannel(String id) {
        Channel channel = channelRepository.getById(id);
        if (channel == null) {
            throw new IllegalArgumentException("渠道不存在: " + id);
        }
        ChannelConfig config = channel.toChannelConfig();
        ILlmAdaptor adaptor = adaptorRegistry.getAdaptor(channel.getType());
        TestResult result = adaptor.test(config);
        channelRepository.updateLastTest(id, result.isSuccess(), now());
        log.info("渠道连通性测试完成: id={}, result={}", id, JSON.toJSONString(result));
        return result;
    }

    /** 时间格式：ISO-8601 带偏移（毫秒精度，适配 varchar(32) 列宽） */
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    private String now() {
        return OffsetDateTime.now().format(TIME_FMT);
    }
}
