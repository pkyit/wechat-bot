package com.example.wechat.core.model;

import com.example.wechat.core.enums.MsgType;

/**
 * 企业微信消息抽象.
 *
 * <p>群机器人与自建应用的消息体结构略有差异（自建应用多了 touser/agentid 等字段），
 * 但核心的 {@code msgtype} + 消息内容是一致的。该接口只描述消息本身，
 * 接收人等上下文由发送器在发送时附加。
 */
public interface WeChatMessage {

    /** 消息类型 */
    MsgType msgType();

    /** 消息体内容（不含 msgtype 外壳，由发送器组装） */
    Object body();
}
