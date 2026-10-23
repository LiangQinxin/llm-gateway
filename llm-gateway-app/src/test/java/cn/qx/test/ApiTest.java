package cn.qx.test;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * 冒烟测试：验证 Spring 容器能完整启动。
 * <p>{@code @SpringBootTest} 起真实容器连本地 MySQL（llm_api），
 * 仅打印一条日志确认工程装配无误，不验证任何业务行为。
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class ApiTest {

    /**
     * 冒烟用例：容器启动成功后打印完成日志。
     */
    @Test
    public void test() {
        log.info("测试完成");
    }

}
