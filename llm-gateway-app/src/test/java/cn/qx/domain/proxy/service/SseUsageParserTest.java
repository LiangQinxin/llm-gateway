package cn.qx.domain.proxy.service;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.nio.charset.StandardCharsets;
import java.util.OptionalLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * SSE usage 旁路解析行为测试：分块输入、字段容错与 best-effort 红线。
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class SseUsageParserTest {

    /** 解析器提供器 */
    @Resource
    private ObjectProvider<ISseUsageParserService> usageParserProvider;

    @Test
    public void extracts_total_tokens_across_arbitrary_chunks() {
        ISseUsageParserService parser = usageParserProvider.getObject();
        parser.accept("data: {\"usage\":{\"prompt_tokens\":2,".getBytes(StandardCharsets.UTF_8));
        parser.accept("\"completion_tokens\":5,\"total_tokens\":9}}\n\n".getBytes(StandardCharsets.UTF_8));
        parser.finish();

        log.info("total_tokens 解析结果: {}", parser.usageTokens());
        assertEquals(OptionalLong.of(9), parser.usageTokens());
    }

    @Test
    public void falls_back_to_prompt_plus_completion() {
        ISseUsageParserService parser = usageParserProvider.getObject();
        parser.accept(("data: {\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":4}}\n\n"
                + "data: [DONE]\n\n").getBytes(StandardCharsets.UTF_8));
        parser.finish();

        log.info("求和回退解析结果: {}", parser.usageTokens());
        assertEquals(OptionalLong.of(7), parser.usageTokens());
    }

    @Test
    public void malformed_or_missing_usage_is_ignored_without_throwing() {
        ISseUsageParserService parser = usageParserProvider.getObject();
        parser.accept("event: message\n".getBytes(StandardCharsets.UTF_8));
        parser.accept("data: {not-json}\n\n".getBytes(StandardCharsets.UTF_8));
        parser.accept("data: {\"choices\":[]}\n\n".getBytes(StandardCharsets.UTF_8));
        parser.finish();

        log.info("无 usage 帧解析结果: {}", parser.usageTokens());
        assertTrue("解析失败应静默跳过", parser.usageTokens().isEmpty());
    }
}
