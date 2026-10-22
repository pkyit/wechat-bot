package com.example.wechat.jdk17;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 本地模拟企业微信接口的测试服务器.
 *
 * <p>按入队顺序返回预设响应，未入队时返回默认的成功响应；同时记录收到的请求。
 */
final class TestWeComServer implements AutoCloseable {

    /** 记录到的请求 */
    record Recorded(String method, String path, String query, String body) {
    }

    /** 预设响应 */
    record Reply(int status, String body) {

        static Reply ok() {
            return new Reply(200, "{\"errcode\":0,\"errmsg\":\"ok\"}");
        }

        static Reply json(String body) {
            return new Reply(200, body);
        }
    }

    private final HttpServer server;
    private final Queue<Reply> replies = new ConcurrentLinkedQueue<>();
    private final List<Recorded> requests = new CopyOnWriteArrayList<>();
    private final Reply defaultReply;

    TestWeComServer() throws IOException {
        this(Reply.ok());
    }

    TestWeComServer(Reply defaultReply) throws IOException {
        this.defaultReply = defaultReply;
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        this.server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            URI uri = exchange.getRequestURI();
            requests.add(new Recorded(exchange.getRequestMethod(), uri.getPath(), uri.getRawQuery(), body));

            Reply reply = replies.poll();
            if (reply == null) {
                reply = defaultReply;
            }
            byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(reply.status(), bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        this.server.start();
    }

    TestWeComServer enqueue(Reply reply) {
        replies.add(reply);
        return this;
    }

    TestWeComServer enqueue(String body) {
        return enqueue(Reply.json(body));
    }

    /** API 基址，例如 http://127.0.0.1:12345 */
    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    List<Recorded> requests() {
        return List.copyOf(requests);
    }

    Recorded request(int index) {
        return requests.get(index);
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
