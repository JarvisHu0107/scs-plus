package com.demo.scs.util;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;


/**
 * @Author: Hu Xin
 * @Date: 2023/1/9 10:18
 * @Desc:
 **/
public class MQSenderUtil {


    /**
     * 通过output发送消息
     *
     * @param outputName 发送消息通道名
     * @param msg
     * @return
     */
    public static boolean sendMsg(String outputName, Message msg) {
        MessageChannel messageChannel = SpringContextHolder.getBean(outputName, MessageChannel.class);
        return messageChannel.send(msg);
    }


    /**
     * 通过通道发送消息
     *
     * @param messageChannel 发送消息通道
     * @param msg
     * @return
     */
    public static boolean sendMsg(MessageChannel messageChannel, Message msg) {
        return messageChannel.send(msg);
    }

}
