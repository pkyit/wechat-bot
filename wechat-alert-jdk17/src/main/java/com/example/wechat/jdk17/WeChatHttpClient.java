package com.example.wechat.jdk17;

import com.example.wechat.core.exception.WeChatException;
import com.example.wechat.core.model.WeChatResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 企业微信 HTTP 客户端底层封装.
 *
 * <p>基于 JDK 11+ 原生 {@link HttpClient}，统一处理：
 * <ul>
 *   <li>JSON 序列化 / 反序列化</li>
 *   <li>请求超时与异常转换</li>
 *   <li>errcode 非 0 时抛出 {@link WeChatException}</li>
 * </ul>
 *
 * <p>无状态、线程安全，可在多个 Bot 实例间共享。
 */
public final class WeChatHttpClient {

    private static final Logger log = LoggerFactory.getLogger(WeChatHttpClient.class);

    /** 日志中的 URL 敏感参数脱敏：webhook key 与 access_token 绝不落盘 */
    private static final Pattern SENSITIVE_PARAM =
            Pattern.compile("((?:key|access_token|corpsecret)=)[^&\\s]+");

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public WeChatHttpClient() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build());
    }

    public WeChatHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
    }

    public ObjectMapper objectMapper() {
        return objectMapper;
    }

    /**
     * POST JSON 请求并解析响应.
     *
     * @param url  请求地址
     * @param body 请求体（会被序列化为 JSON）
     * @return 企业微信响应
     */
    public WeChatResponse post(String url, Object body) {
        try {
            String json = objectMapper.writeValueAsString(body);
            log.debug("POST {} body={}", maskUrl(url), json);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                throw new WeChatException("HTTP " + response.statusCode() + ": " + response.body());
            }

            WeChatResponse result = objectMapper.readValue(response.body(), WeChatResponse.class);
            log.debug("response: errcode={}, errmsg={}", result.errcode(), result.errmsg());
            return result;
        } catch (WeChatException e) {
            throw e;
        } catch (Exception e) {
            throw new WeChatException("请求企业微信接口失败: " + e.getMessage(), e);
        }
    }

    /**
     * GET 请求并返回原始 JSON 字符串（用于获取 access_token 等）.
     */
    public Map<String, Object> get(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new WeChatException("HTTP " + response.statusCode() + ": " + response.body());
            }
            return objectMapper.readValue(response.body(), new TypeReference<>() {});
        } catch (WeChatException e) {
            throw e;
        } catch (Exception e) {
            throw new WeChatException("请求企业微信接口失败: " + e.getMessage(), e);
        }
    }

    /**
     * URL 敏感参数脱敏，仅用于日志输出.
     */
    static String maskUrl(String url) {
        if (url == null) {
            return null;
        }
        return SENSITIVE_PARAM.matcher(url).replaceAll("$1****");
    }
}
