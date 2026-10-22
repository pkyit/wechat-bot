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
        return alertService.alertToGroup(body.get("content"));
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
        return alertService.alertToUser(toUser, body.get("content"));
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
        alertService.alert(body.get("title"), body.get("content"));
        return Map.of("status", "ok", "message", "告警已发送");
    }
}
