package com.example.wechat.springboot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 默认配置下应用应能正常启动（两个通道默认关闭、无需填写密钥）.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class WeChatAlertApplicationTest {

    @Test
    void contextLoads() {
    }
}
