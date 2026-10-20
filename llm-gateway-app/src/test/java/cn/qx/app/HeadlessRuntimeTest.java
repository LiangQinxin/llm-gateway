package cn.qx.app;

import jakarta.annotation.Resource;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.ClassUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 纯 headless MVC 运行形态与并发配置测试。
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class HeadlessRuntimeTest {

    @LocalServerPort
    private int port;

    @Resource
    private Environment environment;

    @Test
    public void headless_mvc_runtime_and_async_limits() {
        assertTrue("应启动真实 HTTP 端口", port > 0);
        assertEquals("600000", environment.getProperty("spring.mvc.async.request-timeout"));
        assertEquals("200", environment.getProperty("server.tomcat.threads.max"));
        assertFalse("不应引入 WebFlux 响应式栈",
                ClassUtils.isPresent("org.springframework.web.reactive.DispatcherHandler", getClass().getClassLoader()));
        assertFalse("不应引入 JavaFX 桌面壳",
                ClassUtils.isPresent("javafx.application.Application", getClass().getClassLoader()));
    }
}
