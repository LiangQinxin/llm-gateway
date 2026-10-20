package cn.qx.cases.identity;

import cn.qx.domain.identity.model.entity.ApiKey;
import cn.qx.types.common.TimeUtils;
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
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * ApiKeyCase 真实链路测试。
 * <p>{@code @SpringBootTest} 起真实容器连本地 MySQL(llm_api)：
 * 创建 → 鉴权/白名单 → 配额封顶 → 启停/修改/删除。
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class ApiKeyCaseTest {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX");

    @Resource
    private ApiKeyCase apiKeyCase;

    @Test
    public void api_key_lifecycle_authentication_and_quota() {
        ApiKey created = apiKeyCase.create(ApiKey.builder()
                .name("测试密钥")
                .allowedModels("[\"deepseek-flash\"]")
                .allowedChannels("[\"channel-a\"]")
                .quotaLimit(10L)
                .quotaUsed(0L)
                .expiresAt(OffsetDateTime.now().plusDays(1).format(TIME_FMT))
                .build());
        log.info("创建密钥: {}", JSON.toJSONString(created));
        try {
            assertNotNull("应自动生成密钥", created.getKey());
            assertTrue("应使用 sk-waliapi 前缀", created.getKey().startsWith("sk-waliapi-"));
            assertEquals("默认启用", Integer.valueOf(1), created.getStatus());
            assertEquals("默认配额使用为 0", Long.valueOf(0), created.getQuotaUsed());

            ApiKey authenticated = apiKeyCase.authenticate(created.getKey(), "deepseek-flash", "channel-a");
            assertEquals("鉴权应返回对应密钥", created.getId(), authenticated.getId());

            assertThrows("模型不在白名单", AppException.class,
                    () -> apiKeyCase.authenticate(created.getKey(), "gpt-4o", "channel-a"));
            assertThrows("渠道不在白名单", AppException.class,
                    () -> apiKeyCase.authenticate(created.getKey(), "deepseek-flash", "channel-b"));

            apiKeyCase.recordUsage(created.getId(), 8);
            apiKeyCase.recordUsage(created.getId(), 5);
            assertEquals("配额应封顶到上限", Long.valueOf(10), apiKeyCase.get(created.getId()).getQuotaUsed());

            apiKeyCase.toggle(created.getId(), false);
            assertThrows("禁用密钥不能鉴权", AppException.class,
                    () -> apiKeyCase.authenticate(created.getKey(), "deepseek-flash", "channel-a"));
            apiKeyCase.toggle(created.getId(), true);

            ApiKey updated = apiKeyCase.update(ApiKey.builder()
                    .id(created.getId())
                    .name("测试密钥-已修改")
                    .key(created.getKey())
                    .allowedModels("[]")
                    .allowedChannels("[]")
                    .quotaLimit(0L)
                    .quotaUsed(10L)
                    .build());
            assertEquals("名称应更新", "测试密钥-已修改", apiKeyCase.get(created.getId()).getName());
            assertEquals("空白名单应允许任意模型", updated.getId(),
                    apiKeyCase.authenticate(created.getKey(), "any-model", "any-channel").getId());

            apiKeyCase.recordUsage(created.getId(), 7);
            assertEquals("0=不限时应继续累加", Long.valueOf(17), apiKeyCase.get(created.getId()).getQuotaUsed());
        } finally {
            apiKeyCase.delete(created.getId());
        }
        assertThrows("删除后查询应报不存在", AppException.class, () -> apiKeyCase.get(created.getId()));
    }

    @Test
    public void duplicate_key_is_rejected_and_expired_key_cannot_authenticate() {
        String duplicate = "sk-waliapi-test-" + UUID.randomUUID();
        ApiKey first = apiKeyCase.create(ApiKey.builder().name("重复一").key(duplicate).build());
        ApiKey expired = apiKeyCase.create(ApiKey.builder()
                .name("已过期")
                .expiresAt(OffsetDateTime.now().minusMinutes(1).format(TIME_FMT))
                .build());
        try {
            assertThrows("重复 key 应返回业务异常", AppException.class,
                    () -> apiKeyCase.create(ApiKey.builder().name("重复二").key(duplicate).build()));
            assertThrows("过期密钥不能鉴权", AppException.class,
                    () -> apiKeyCase.authenticate(expired.getKey(), "any", "any"));
            assertThrows("key 必须大小写精确匹配", AppException.class,
                    () -> apiKeyCase.authenticate(first.getKey().toUpperCase(), "any", "any"));
        } finally {
            apiKeyCase.delete(first.getId());
            apiKeyCase.delete(expired.getId());
            assertNotNull(TimeUtils.now());
        }
    }
}
