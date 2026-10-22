package com.example.wechat.core.exception;

/**
 * 企业微信告警异常.
 *
 * <p>用于封装网络错误、序列化失败、接口返回非 0 错误码等场景。
 */
public class WeChatException extends RuntimeException {

    private final int errcode;

    public WeChatException(String message) {
        super(message);
        this.errcode = -1;
    }

    public WeChatException(String message, Throwable cause) {
        super(message, cause);
        this.errcode = -1;
    }

    public WeChatException(int errcode, String errmsg) {
        super("企业微信接口返回错误: errcode=" + errcode + ", errmsg=" + errmsg);
        this.errcode = errcode;
    }

    public int errcode() {
        return errcode;
    }
}
