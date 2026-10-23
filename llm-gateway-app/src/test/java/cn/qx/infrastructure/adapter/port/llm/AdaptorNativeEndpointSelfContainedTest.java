package cn.qx.infrastructure.adapter.port.llm;

import cn.qx.domain.channel.adapter.port.ILlmAdaptor;
import cn.qx.domain.channel.model.valobj.NativeEndpoint;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * 各厂商适配器原生端点自包含断言：能力矩阵与厂商真实兼容性一致，无 OpenAI 兼容中间层默认假设。
 * <p>纯内存断言（bean 声明的 nativeEndpoints 表），不依赖外部网关在线。
 * <p>能力矩阵（与 ADR-0011 对齐）：
 * <ul>
 *   <li>openai：cc + rp（双端点）</li>
 *   <li>deepseek：cc + ms（anthropic 兼容）</li>
 *   <li>google：仅 cc（Gemini OpenAI 兼容端点，不支持 rp）</li>
 *   <li>custom：cc + rp</li>
 *   <li>anthropic：仅 ms</li>
 * </ul>
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class AdaptorNativeEndpointSelfContainedTest {

    /** 按渠道类型索引的适配器表。 */
    @Resource
    private Map<String, ILlmAdaptor> adaptors;

    private void assertEndpoints(String channelType, String expected) {
        ILlmAdaptor adaptor = adaptors.get(channelType);
        assertNotNull("应存在适配器: " + channelType, adaptor);
        String actual = adaptor.nativeEndpoints().stream()
                .map(this::describe)
                .collect(Collectors.joining(", "));
        log.info("{} 原生端点: [{}]", channelType, actual);
        assertEquals("type=" + channelType + " 原生端点应自包含声明", expected, actual);
    }

    private String describe(NativeEndpoint endpoint) {
        return endpoint.getProtocol() + "@" + endpoint.getPath() + ":" + endpoint.getAuthStyle();
    }

    @Test
    public void each_vendor_declares_only_its_own_native_endpoints() {
        assertEndpoints("openai", "CC@/v1/chat/completions:BEARER, RP@/v1/responses:BEARER");
        assertEndpoints("deepseek", "CC@/v1/chat/completions:BEARER, MS@/anthropic/v1/messages:X_API_KEY");
        assertEndpoints("google", "CC@/v1/chat/completions:BEARER");
        assertEndpoints("custom", "CC@/v1/chat/completions:BEARER, RP@/v1/responses:BEARER");
        assertEndpoints("anthropic", "MS@/v1/messages:X_API_KEY");
    }

    @Test
    public void no_openai_compatible_intermediate_assumption() {
        List<ILlmAdaptor> all = List.copyOf(adaptors.values());
        for (ILlmAdaptor adaptor : all) {
            assertNotNull("每个适配器的 primary 端点必须存在: " + adaptor.channelType(),
                    adaptor.nativeEndpoints().get(0));
        }
    }
}
