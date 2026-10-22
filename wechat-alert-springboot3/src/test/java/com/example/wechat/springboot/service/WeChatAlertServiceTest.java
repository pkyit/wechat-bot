package com.example.wechat.springboot.service;

import com.example.wechat.core.exception.WeChatException;
import com.example.wechat.core.model.WeChatMessage;
import com.example.wechat.core.model.WeChatResponse;
import com.example.wechat.springboot.client.WeChatAppBotClient;
import com.example.wechat.springboot.client.WeChatGroupBotClient;
import com.example.wechat.springboot.config.WeChatProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeChatAlertServiceTest {

    @Test
    void shouldSendToGroupOnlyWhenAppChannelDisabled() {
        FakeGroupClient group = new FakeGroupClient(false);
        WeChatAlertService service = new WeChatAlertService(new WeChatProperties(),
                provider(WeChatGroupBotClient.class, group), provider(WeChatAppBotClient.class, null));

        WeChatAlertService.AlertOutcome outcome = service.alert("标题", "内容");

        assertTrue(outcome.groupSent());
        assertFalse(outcome.appSent());
        assertTrue(outcome.failures().isEmpty());
        assertEquals(1, group.calls);
    }

    @Test
    void shouldReportPartialFailureWhenAppChannelFails() {
        WeChatProperties properties = new WeChatProperties();
        properties.getApp().setDefaultOwner("owner");
        FakeGroupClient group = new FakeGroupClient(false);
        FakeAppClient app = new FakeAppClient(true);
        WeChatAlertService service = new WeChatAlertService(properties,
                provider(WeChatGroupBotClient.class, group), provider(WeChatAppBotClient.class, app));

        WeChatAlertService.AlertOutcome outcome = service.alert("标题", "内容");

        assertTrue(outcome.groupSent());
        assertFalse(outcome.appSent());
        assertEquals(1, outcome.failures().size());
        assertTrue(outcome.failures().get(0).startsWith("自建应用"), outcome.failures().get(0));
    }

    @Test
    void shouldThrowWhenAllEnabledChannelsFail() {
        WeChatProperties properties = new WeChatProperties();
        properties.getApp().setDefaultOwner("owner");
        WeChatAlertService service = new WeChatAlertService(properties,
                provider(WeChatGroupBotClient.class, new FakeGroupClient(true)),
                provider(WeChatAppBotClient.class, new FakeAppClient(true)));

        assertThrows(WeChatException.class, () -> service.alert("标题", "内容"));
    }

    @Test
    void shouldThrowWhenNoChannelEnabled() {
        WeChatAlertService service = new WeChatAlertService(new WeChatProperties(),
                provider(WeChatGroupBotClient.class, null), provider(WeChatAppBotClient.class, null));

        assertThrows(IllegalStateException.class, () -> service.alert("标题", "内容"));
    }

    @Test
    void shouldValidateRequestArguments() {
        WeChatAlertService service = new WeChatAlertService(new WeChatProperties(),
                provider(WeChatGroupBotClient.class, new FakeGroupClient(false)),
                provider(WeChatAppBotClient.class, null));

        assertThrows(IllegalArgumentException.class, () -> service.alert(" ", "内容"));
        assertThrows(IllegalArgumentException.class, () -> service.alert("标题", " "));
        assertThrows(IllegalArgumentException.class, () -> service.alertToGroup(null));
        assertThrows(IllegalArgumentException.class, () -> service.alertToUser(" ", "内容"));
    }

    @Test
    void shouldThrowWhenChannelNotEnabled() {
        WeChatAlertService service = new WeChatAlertService(new WeChatProperties(),
                provider(WeChatGroupBotClient.class, null), provider(WeChatAppBotClient.class, null));

        assertThrows(IllegalStateException.class, () -> service.alertToGroup("内容"));
        assertThrows(IllegalStateException.class, () -> service.alertToUser("zhangsan", "内容"));
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
        private int calls;

        private FakeGroupClient(boolean fail) {
            super("http://localhost/webhook", RestClient.create());
            this.fail = fail;
        }

        @Override
        public WeChatResponse send(WeChatMessage message) {
            calls++;
            if (fail) {
                throw new WeChatException(93000, "webhook failed");
            }
            return new WeChatResponse(0, "ok", null);
        }
    }

    private static final class FakeAppClient extends WeChatAppBotClient {

        private final boolean fail;

        private FakeAppClient(boolean fail) {
            super("corp", 1000002, "secret", RestClient.create());
            this.fail = fail;
        }

        @Override
        public WeChatResponse send(String toUser, WeChatMessage message) {
            if (fail) {
                throw new WeChatException(81013, "user invalid");
            }
            return new WeChatResponse(0, "ok", null);
        }
    }
}
