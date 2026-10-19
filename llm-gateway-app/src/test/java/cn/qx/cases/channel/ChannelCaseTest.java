package cn.qx.cases.channel;

import cn.qx.domain.channel.adapter.repository.IChannelRepository;
import cn.qx.domain.channel.model.entity.Channel;
import cn.qx.domain.channel.model.valobj.TestResult;
import com.alibaba.fastjson2.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * ChannelCase 真实链路测试。
 * <p>{@code @SpringBootTest} 起真实容器连本地 MySQL(llm_api) 跑整链路：
 * <pre>
 * 插渠道 → 读渠道 → 转 ChannelConfig → 适配器 test（真实端点）→ 回写 last_test_*
 * </pre>
 * 不做手工装配、不过度 mock，入参出参 log+JSON 打印。
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class ChannelCaseTest {

    /** 上游基础 URL（含 /v1） */
    private static final String BASE_URL = "http://127.0.0.1:8777/v1";

    /** 上游 API 密钥 */
    private static final String API_KEY = "sk-waliapi-04dc74ce989d458e848a2c1616bf5611";

    /** 渠道用例 */
    @Resource
    private ChannelCase channelCase;

    /** 渠道仓储 */
    @Resource
    private IChannelRepository channelRepository;

    /** 时间格式：ISO-8601 带偏移（毫秒精度，适配 varchar(32) 列宽） */
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    @Test
    public void test_channel_real_endpoint() {
        String id = "test-" + UUID.randomUUID().toString().replace("-", "");
        String now = OffsetDateTime.now().format(TIME_FMT);
        Channel channel = Channel.builder()
                .id(id).name("测试-DeepSeek").type("deepseek")
                .baseUrl(BASE_URL).apiKey(API_KEY)
                .models("[\"deepseek-flash\"]")
                .status(1).priority(0).weight(1)
                .config("{}").modelMapping("{}")
                .createdAt(now).updatedAt(now)
                .build();
        channelRepository.insert(channel);
        log.info("已插入测试渠道: id={}", id);
        try {
            TestResult result = channelCase.testChannel(id);
            log.info("test_channel 结果: {}", JSON.toJSONString(result));
            assertTrue("真实端点连通性测试应成功", result.isSuccess());

            Channel after = channelRepository.getById(id);
            log.info("回写检查: last_test_at={}, last_test_ok={}", after.getLastTestAt(), after.getLastTestOk());
            assertTrue("last_test_ok 应回写为 1", Integer.valueOf(1).equals(after.getLastTestOk()));
        } finally {
            channelRepository.deleteById(id);
            log.info("已清理测试渠道: id={}", id);
        }
    }

    @Test
    public void test_channel_not_found() {
        assertThrows("渠道不存在时应抛 IllegalArgumentException",
                IllegalArgumentException.class,
                () -> channelCase.testChannel("not-exist-" + UUID.randomUUID()));
    }
}
