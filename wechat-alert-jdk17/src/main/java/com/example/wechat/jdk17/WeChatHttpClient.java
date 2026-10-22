package com.example.wechat.jdk17;

import com.example.wechat.core.exception.WeChatException;
import com.example.wechat.core.model.WeChatResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 企业微信 HTTP 客户端底层封装.
 *
 * <p>基于 JDK 11+ 原生 {@link HttpClient}，统一处理：
 * <ul>
 *   <li>JSON 序列化 / 反序列化</li>
 *   <li>请求超时与异常转换（含线程中断标记的恢复）</li>
 *   <li>HTTP 非 2xx 与 errcode 非 0 的错误封装</li>
 * </ul>
 *
 * <p>无状态、线程安全，可在多个 Bot 实例间共享。
 */
public final class WeChatHttpClient {

    private static final Logger log = LoggerFactory.getLogger(WeChatHttpClient.class);

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public WeChatHttpClient() {
        this(HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build());
    }

    public WeChatHttpClient(HttpClient httpClient) {
        if (httpClient == null) {
            throw new IllegalArgumentException("httpClient 不能为空");
        }
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
     * @throws WeChatException 网络异常、HTTP 非 2xx 或响应无法解析时抛出
     */
    public WeChatResponse post(String url, Object body) {
        try {
            String json = objectMapper.writeValueAsString(body);
            // webhook key / secret 都在 URL 上，日志里必须打码
            log.debug("POST {} body={}", maskUrl(url), json);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .timeout(REQUEST_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new WeChatException("HTTP " + response.statusCode() + ": " + response.body());
            }

            WeChatResponse result = objectMapper.readValue(response.body(), WeChatResponse.class);
            log.debug("response: errcode={}, errmsg={}", result.errcode(), result.errmsg());
            return result;
        } catch (WeChatException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WeChatException("请求企业微信接口被中断", e);
        } catch (Exception e) {
            throw new WeChatException("请求企业微信接口失败: " + e.getMessage(), e);
        }
    }

    /**
     * GET 请求并反序列化为指定类型（用于获取 access_token 等）.
     *
     * @param url          请求地址
     * @param responseType 响应类型
     * @param <T>          响应类型
     * @return 反序列化后的响应
     */
    public <T> T get(String url, Class<T> responseType) {
        try {
            log.debug("GET {}", maskUrl(url));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new WeChatException("HTTP " + response.statusCode() + ": " + response.body());
            }
            return objectMapper.readValue(response.body(), responseType);
        } catch (WeChatException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WeChatException("请求企业微信接口被中断", e);
        } catch (Exception e) {
            throw new WeChatException("请求企业微信接口失败: " + e.getMessage(), e);
        }
    }

    /** 隐藏 URL 查询串（webhook key、access_token 等敏感信息）. */
    private static String maskUrl(String url) {
        int queryIndex = url.indexOf('?');
        return queryIndex < 0 ? url : url.substring(0, queryIndex) + "?***";
    }
}
