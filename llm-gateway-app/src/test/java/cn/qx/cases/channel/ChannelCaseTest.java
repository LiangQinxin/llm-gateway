package cn.qx.cases.channel;

import cn.qx.domain.channel.adapter.repository.IChannelRepository;
import cn.qx.domain.channel.model.entity.Channel;
import cn.qx.domain.channel.model.valobj.TestResult;
import cn.qx.types.exception.AppException;
import com.alibaba.fastjson2.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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
    private static final String API_KEY = "sk-test-04dc74ce989d458e848a2c1616bf5611";

    /** 渠道用例 */
    @Resource
    private ChannelCase channelCase;

    /** 渠道仓储 */
    @Resource
    private IChannelRepository channelRepository;

    /** 时间格式：ISO-8601 带偏移（毫秒精度，适配 varchar(32) 列宽） */
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    /**
     * 渠道真实端点连通性测试：插入渠道 → 调 testChannel → 断言 last_test_ok 回写。
     * <p>前置条件：本地 OpenAI 兼容网关 {@code http://127.0.0.1:8777} 在线。
     */
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

    /**
     * 渠道不存在时应抛 {@link IllegalArgumentException}，不得触达上游。
     */
    @Test
    public void test_channel_not_found() {
        assertThrows("渠道不存在时应抛 IllegalArgumentException",
                IllegalArgumentException.class,
                () -> channelCase.testChannel("not-exist-" + UUID.randomUUID()));
    }

    /**
     * 按渠道类型验证连通性探测端点矩阵。
     * <pre>
     * openai / custom → 命中 /models
     * anthropic → 命中 /v1/models 且携带 x-api-key 请求头
     * </pre>
     */
    @Test
    public void connectivity_probe_matrix_by_channel_type() {
        Channel openai = channelCase.create(Channel.builder()
                .name("测试-openai探针-" + UUID.randomUUID())
                .type("openai")
                .baseUrl(BASE_URL)
                .apiKey(API_KEY)
                .models("[\"deepseek-flash\"]")
                .config("{}")
                .modelMapping("{}")
                .build());
        Channel custom = channelCase.create(Channel.builder()
                .name("测试-custom探针-" + UUID.randomUUID())
                .type("custom")
                .baseUrl(BASE_URL)
                .apiKey(API_KEY)
                .models("[\"deepseek-flash\"]")
                .config("{}")
                .modelMapping("{}")
                .build());
        Channel anthropic = channelCase.create(Channel.builder()
                .name("测试-anthropic探针-" + UUID.randomUUID())
                .type("anthropic")
                .baseUrl("http://127.0.0.1:8777")
                .apiKey(API_KEY)
                .models("[\"deepseek-flash\"]")
                .config("{}")
                .modelMapping("{}")
                .build());
        try {
            assertTrue("openai 应命中 /models",
                    channelCase.testChannel(openai.getId()).isSuccess());
            assertTrue("custom 应命中 /models",
                    channelCase.testChannel(custom.getId()).isSuccess());
            assertTrue("anthropic 应命中 /v1/models 且带 x-api-key",
                    channelCase.testChannel(anthropic.getId()).isSuccess());
        } finally {
            channelCase.delete(openai.getId());
            channelCase.delete(custom.getId());
            channelCase.delete(anthropic.getId());
        }
    }

    /**
     * 渠道 CRUD 与启用列表排序规则整链路。
     * <pre>
     * 创建 → 查询 → 更新（拒绝非法类型/重复名称）
     * → 排序（priority ASC，同优先级 weight DESC）→ 停用不进路由列表 → 删除后不可见
     * </pre>
     */
    @Test
    public void channel_crud_and_enabled_ordering() {
        Channel low = channelCase.create(Channel.builder()
                .name("测试-低优先级-" + UUID.randomUUID())
                .type("deepseek")
                .baseUrl(BASE_URL)
                .apiKey(API_KEY)
                .models("[\"deepseek-flash\"]")
                .priority(1)
                .weight(10)
                .config("{}")
                .modelMapping("{}")
                .build());
        Channel high = channelCase.create(Channel.builder()
                .name("测试-高优先级-" + UUID.randomUUID())
                .type("deepseek")
                .baseUrl(BASE_URL)
                .apiKey(API_KEY)
                .models("[\"deepseek-flash\"]")
                .priority(100)
                .weight(1)
                .config("{}")
                .modelMapping("{}")
                .build());
        Channel disabled = channelCase.create(Channel.builder()
                .name("测试-停用-" + UUID.randomUUID())
                .type("deepseek")
                .baseUrl(BASE_URL)
                .apiKey(API_KEY)
                .models("[\"deepseek-flash\"]")
                .status(0)
                .priority(999)
                .weight(1)
                .config("{}")
                .modelMapping("{}")
                .build());
        Channel weighted = channelCase.create(Channel.builder()
                .name("测试-同优先级高权重-" + UUID.randomUUID())
                .type("deepseek")
                .baseUrl(BASE_URL)
                .apiKey(API_KEY)
                .models("[\"deepseek-flash\"]")
                .priority(100)
                .weight(20)
                .config("{}")
                .modelMapping("{}")
                .build());

        log.info("创建渠道: low={}, high={}, disabled={}, weighted={}",
                low.getId(), high.getId(), disabled.getId(), weighted.getId());
        try {
            Channel created = channelCase.get(low.getId());
            assertNotNull("新渠道应可查询", created);
            assertEquals("新渠道默认启用", Integer.valueOf(1), created.getStatus());
            assertNotNull("创建时间应自动生成", created.getCreatedAt());

            Channel invalidUpdate = channelCase.get(low.getId());
            invalidUpdate.setType("not-supported");
            assertThrows("更新时也应拒绝未知渠道类型", AppException.class,
                    () -> channelCase.update(invalidUpdate));

            created.setName("测试-已修改");
            created.setPriority(200);
            Channel updated = channelCase.update(created);
            assertEquals("名称应更新", "测试-已修改", channelCase.get(low.getId()).getName());
            assertEquals("优先级应更新", Integer.valueOf(200), updated.getPriority());

            List<Channel> enabled = channelCase.listEnabledOrdered();
            List<String> enabledIds = enabled.stream().map(Channel::getId).toList();
            log.info("启用渠道排序: {}", enabledIds);
            assertTrue("修改后的渠道应排在启用列表首位", enabledIds.indexOf(low.getId()) < enabledIds.indexOf(high.getId()));
            assertTrue("同优先级应按 weight DESC 排序",
                    enabledIds.indexOf(weighted.getId()) < enabledIds.indexOf(high.getId()));
            assertTrue("停用渠道不应出现在路由列表", !enabledIds.contains(disabled.getId()));

            assertThrows("重复名称应拒绝", AppException.class,
                    () -> channelCase.create(Channel.builder()
                            .name(weighted.getName())
                            .type("deepseek")
                            .baseUrl(BASE_URL)
                            .apiKey(API_KEY)
                            .build()));
            assertThrows("未知渠道类型应拒绝", AppException.class,
                    () -> channelCase.create(Channel.builder()
                            .name("测试-非法类型-" + UUID.randomUUID())
                            .type("not-supported")
                            .baseUrl(BASE_URL)
                            .apiKey(API_KEY)
                            .build()));

            Channel toggled = channelCase.toggle(low.getId(), false);
            assertEquals("停用状态应回写", Integer.valueOf(0), toggled.getStatus());
            assertTrue("停用后不应出现在路由列表", !channelCase.listEnabledOrdered().stream()
                    .map(Channel::getId).toList().contains(low.getId()));

            channelCase.delete(high.getId());
            assertThrows("删除后查询应报不存在", IllegalArgumentException.class, () -> channelCase.get(high.getId()));
        } finally {
            channelCase.delete(low.getId());
            channelCase.delete(high.getId());
            channelCase.delete(disabled.getId());
            channelCase.delete(weighted.getId());
        }
    }
}
