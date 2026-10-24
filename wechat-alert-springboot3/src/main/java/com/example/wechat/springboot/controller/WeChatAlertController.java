package com.example.wechat.springboot.controller;

import com.example.wechat.core.model.WeChatResponse;
import com.example.wechat.springboot.service.WeChatAlertService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 企业微信告警 REST 接口.
 *
 * <p>提供简单的 HTTP 接口，方便其他系统调用或手动测试。
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
    public WeChatResponse sendToGroup(@RequestBody Map<String, String> body) {
        return alertService.alertToGroup(requireContent(body));
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
                                     @RequestBody Map<String, String> body) {
        if (toUser == null || toUser.isBlank()) {
            throw new IllegalArgumentException("toUser 不能为空");
        }
        return alertService.alertToUser(toUser, requireContent(body));
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
     */
    @PostMapping("/alert")
    public Map<String, String> alert(@RequestBody Map<String, String> body) {
        String title = body.get("title");
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title 不能为空");
        }
        alertService.alert(title, requireContent(body));
        return Map.of("status", "ok", "message", "告警已发送");
    }

    /** 边界校验：请求体中的 content 必须存在且非空白 */
    private static String requireContent(Map<String, String> body) {
        String content = body == null ? null : body.get("content");
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content 不能为空");
        }
        return content;
    }
}
