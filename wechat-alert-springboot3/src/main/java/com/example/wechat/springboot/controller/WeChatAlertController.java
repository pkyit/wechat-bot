package com.example.wechat.springboot.controller;

import com.example.wechat.core.model.WeChatResponse;
import com.example.wechat.springboot.service.WeChatAlertService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 企业微信告警 REST 接口.
 *
 * <p>提供简单的 HTTP 接口，方便其他系统调用或手动测试。
 * 参数缺失或内容超过企业微信长度限制时返回 400；企业微信接口失败返回 502。
 */
@RestController
@RequestMapping("/api/wechat")
public class WeChatAlertController {

    private final WeChatAlertService alertService;

    public WeChatAlertController(WeChatAlertService alertService) {
        this.alertService = alertService;
    }

    /**
     * 发送群消息.
     *
     * <pre>
     * POST /api/wechat/group
     * Content-Type: application/json
     *
     * { "content": "告警内容" }
     * </pre>
     */
    @PostMapping("/group")
    public WeChatResponse sendToGroup(@RequestBody MessageRequest request) {
        requireBody(request);
        return alertService.alertToGroup(request.content());
    }

    /**
     * 发送消息给指定人.
     *
     * <pre>
     * POST /api/wechat/user?toUser=zhangsan
     * Content-Type: application/json
     *
     * { "content": "告警内容" }
     * </pre>
     */
    @PostMapping("/user")
    public WeChatResponse sendToUser(@RequestParam String toUser,
                                     @RequestBody MessageRequest request) {
        requireBody(request);
        return alertService.alertToUser(toUser, request.content());
    }

    /**
     * 全通道告警（群 + 私信）.
     *
     * <pre>
     * POST /api/wechat/alert
     * Content-Type: application/json
     *
     * { "title": "告警标题", "content": "告警详情" }
     * </pre>
     *
     * <p>返回 {@code status=ok} 表示全部通道成功，{@code status=partial} 表示部分通道失败，
     * 具体失败原因见 {@code failures}。所有通道失败时返回 502。
     */
    @PostMapping("/alert")
    public Map<String, Object> alert(@RequestBody AlertRequest request) {
        requireBody(request);
        WeChatAlertService.AlertOutcome outcome = alertService.alert(request.title(), request.content());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", outcome.failures().isEmpty() ? "ok" : "partial");
        result.put("groupSent", outcome.groupSent());
        result.put("appSent", outcome.appSent());
        result.put("failures", outcome.failures());
        result.put("message", outcome.failures().isEmpty() ? "告警已发送" : "告警已部分发送");
        return result;
    }

    /**
     * 单通道消息请求.
     *
     * @param content 消息内容
     */
    public record MessageRequest(String content) {
    }

    /**
     * 全通道告警请求.
     *
     * @param title   告警标题
     * @param content 告警详情
     */
    public record AlertRequest(String title, String content) {
    }

    private static void requireBody(Object body) {
        if (body == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
    }
}
