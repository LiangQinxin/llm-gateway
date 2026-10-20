package cn.qx.types.common;

import cn.qx.domain.audit.model.entity.RequestLog;
import cn.qx.domain.channel.model.entity.Channel;
import cn.qx.domain.channel.model.valobj.ChannelConfig;
import cn.qx.domain.identity.model.entity.ApiKey;
import org.junit.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * String 时间/ID 与 JSON 列便捷解析建模测试。
 */
public class StringModelingTest {

    @Test
    public void time_and_id_follow_string_contract() {
        String now = TimeUtils.now();
        assertEquals("ISO-8601 带偏移微秒格式应为 32 字符", 32, now.length());
        assertNotNull("时间应可被 OffsetDateTime 解析", OffsetDateTime.parse(now));

        String id = TimeUtils.uuid();
        assertEquals("UUID 应为标准 36 字符", 36, id.length());
        assertEquals("UUID 解析后应保持不变", id, UUID.fromString(id).toString());
    }

    @Test
    public void json_columns_keep_raw_string_and_parse_on_demand() {
        Channel channel = Channel.builder()
                .models("[\"deepseek-flash\",\"deepseek-chat\"]")
                .modelMapping("{\"gpt-4o\":\"deepseek-flash\"}")
                .config("{\"timeout_secs\":42}")
                .build();
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
