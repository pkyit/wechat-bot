package com.example.wechat.springboot.controller;

import com.example.wechat.core.exception.WeChatException;
import com.example.wechat.core.model.WeChatMessage;
import com.example.wechat.core.model.WeChatResponse;
import com.example.wechat.springboot.client.WeChatAppBotClient;
import com.example.wechat.springboot.client.WeChatGroupBotClient;
import com.example.wechat.springboot.config.WeChatProperties;
import com.example.wechat.springboot.service.WeChatAlertService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WeChatAlertControllerTest {

    @Test
    void shouldSendGroupMessage() throws Exception {
        mockMvc(false).perform(post("/api/wechat/group")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"告警内容\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errcode").value(0));
    }

    @Test
    void shouldRejectBlankContent() throws Exception {
        mockMvc(false).perform(post("/api/wechat/group")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldRejectMalformedJson() throws Exception {
        mockMvc(false).perform(post("/api/wechat/group")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请求体格式错误，应为合法的 JSON"));
    }

    @Test
    void shouldReturnPartialResultForGroupOnlyAlert() throws Exception {
        mockMvc(false).perform(post("/api/wechat/alert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"告警标题\",\"content\":\"告警详情\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.groupSent").value(true))
                .andExpect(jsonPath("$.appSent").value(false));
    }

    @Test
    void shouldRejectAlertWithoutTitle() throws Exception {
        mockMvc(false).perform(post("/api/wechat/alert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"告警详情\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldReturnBadRequestWhenAppChannelDisabled() throws Exception {
        mockMvc(false).perform(post("/api/wechat/user")
                        .param("toUser", "zhangsan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"告警内容\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "自建应用未启用，请配置 wechat.alert.app.enabled=true"));
    }

    @Test
    void shouldReturnBadGatewayOnWeComFailure() throws Exception {
        mockMvc(true).perform(post("/api/wechat/group")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"告警内容\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errcode").value(93000));
    }

    private static MockMvc mockMvc(boolean groupFail) {
        WeChatAlertService service = new WeChatAlertService(
                new WeChatProperties(),
                provider(WeChatGroupBotClient.class, new FakeGroupClient(groupFail)),
                provider(WeChatAppBotClient.class, null));

        return MockMvcBuilders.standaloneSetup(new WeChatAlertController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static <T> ObjectProvider<T> provider(Class<T> type, T bean) {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        if (bean != null) {
            factory.registerSingleton("testBean", bean);
        }
        return factory.getBeanProvider(type);
    }

    private static final class FakeGroupClient extends WeChatGroupBotClient {

        private final boolean fail;

        private FakeGroupClient(boolean fail) {
            super("http://localhost/webhook", RestClient.create());
            this.fail = fail;
        }

        @Override
        public WeChatResponse send(WeChatMessage message) {
            if (fail) {
                throw new WeChatException(93000, "invalid webhook url");
            }
            return new WeChatResponse(0, "ok", null);
        }
    }
}
