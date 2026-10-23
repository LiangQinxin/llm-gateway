package cn.qx.types.common;

import cn.qx.domain.audit.model.entity.RequestLog;
import cn.qx.domain.channel.model.entity.Channel;
import cn.qx.domain.channel.model.valobj.ChannelConfig;
import cn.qx.domain.identity.model.entity.ApiKey;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * String 时间/ID 与 JSON 列便捷解析建模测试。
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class StringModelingTest {

    /**
     * String 时间/ID 契约校验。
     * <pre>
     * ISO-8601 带偏移 32 字符 → OffsetDateTime 可解析；UUID 36 字符可往返；
     * Z 偏移规范化为 +08:00 微秒格式；无偏移时间拒绝
     * </pre>
     */
    @Test
    public void time_and_id_follow_string_contract() {
        String now = TimeUtils.now();
        log.info("生成时间: {}, 生成ID: {}", now, TimeUtils.uuid());
        assertEquals("ISO-8601 带偏移微秒格式应为 32 字符", 32, now.length());
        assertNotNull("时间应可被 OffsetDateTime 解析", OffsetDateTime.parse(now));

        String id = TimeUtils.uuid();
        assertEquals("UUID 应为标准 36 字符", 36, id.length());
        assertEquals("UUID 解析后应保持不变", id, UUID.fromString(id).toString());
        assertEquals("Z 偏移应规范化为 +08:00 微秒格式",
                "2026-10-20T12:00:00.000000+08:00",
                TimeUtils.normalize("2026-10-20T04:00:00Z"));
        assertThrows("无偏移时间应拒绝", IllegalArgumentException.class,
                () -> TimeUtils.normalize("2026-10-20 12:00:00"));
    }

    /**
     * JSON 列建模契约：库表列保持原始 String，便捷方法按需解析。
     * <p>覆盖 Channel（activeModels / activeModelMapping / toChannelConfig）、
     * ApiKey（allowsChannel / allowsModel）与 RequestLog（requestBodyJson / responseChoicesJson）。
     */
    @Test
    public void json_columns_keep_raw_string_and_parse_on_demand() {
        Channel channel = Channel.builder()
                .models("[\"deepseek-flash\",\"deepseek-chat\"]")
                .modelMapping("{\"gpt-4o\":\"deepseek-flash\"}")
                .config("{\"timeout_secs\":42}")
                .build();
        log.info("渠道 JSON 原始值: models={}, mapping={}, config={}",
                channel.getModels(), channel.getModelMapping(), channel.getConfig());
        assertEquals(List.of("deepseek-flash", "deepseek-chat"), channel.activeModels());
        assertEquals("deepseek-flash", channel.activeModelMapping().getString("gpt-4o"));
        ChannelConfig config = channel.toChannelConfig();
        assertEquals(42, config.getTimeoutSecs());
        assertEquals("[\"deepseek-flash\",\"deepseek-chat\"]", channel.getModels());

        ApiKey apiKey = ApiKey.builder()
                .allowedModels("[\"deepseek-flash\"]")
                .allowedChannels("[]")
                .build();
        assertTrue("空白名单=全放行渠道", apiKey.allowsChannel("any-channel"));
        assertTrue("模型白名单应命中", apiKey.allowsModel("deepseek-flash"));
        assertFalse("模型白名单应拒绝未列模型", apiKey.allowsModel("gpt-4o"));

        RequestLog requestLog = RequestLog.builder()
                .requestBody("{\"model\":\"deepseek-flash\"}")
                .responseChoices("[{\"index\":0}]")
                .build();
        assertEquals("deepseek-flash", requestLog.requestBodyJson().getString("model"));
        assertEquals(1, requestLog.responseChoicesJson().size());
    }
}
