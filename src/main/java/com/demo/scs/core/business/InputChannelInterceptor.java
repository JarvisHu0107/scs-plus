package com.demo.scs.core.business;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.ObjectUtils;
import org.springframework.cloud.stream.messaging.DirectWithAttributesChannel;
import org.springframework.integration.support.DefaultMessageBuilderFactory;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.ChannelInterceptor;

import com.demo.scs.core.constant.ScsPlusConstant;
import lombok.extern.slf4j.Slf4j;

/**
 * 消费者channel拦截器：消费的时候给message增加消息头【会经过哪个channel】，便于消费的时候进行路由
 *
 * @Author: Hu Xin
 * @Date: 2023/1/16 11:32
 * @Desc:
 **/
@Slf4j
public class InputChannelInterceptor implements ChannelInterceptor {

    /**
     * 消费者channel拦截器
     *
     * @param message
     * @param channel
     * @return
     */
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        String beanName = ((DirectWithAttributesChannel)channel).getBeanName();
        log.debug("CustomizeChannelInterceptor#preSend,messageHeaders:{},channelName:{}", message.getHeaders(),
            beanName);
        DefaultMessageBuilderFactory factory = new DefaultMessageBuilderFactory();

        MessageHeaders headers = message.getHeaders();
        Map<String, Object> mapHeaders = new HashMap<>(headers.size() + 1);
        if (ObjectUtils.isNotEmpty(headers) && !headers.isEmpty()) {
            headers.keySet().forEach(h -> {
                mapHeaders.putIfAbsent(h, headers.get(h));
            });
        }
        // important
        mapHeaders.putIfAbsent(ScsPlusConstant.INPUT_CHANNEL_KEY, beanName);
        MessageBuilder<?> builder = factory.withPayload(message.getPayload()).copyHeaders(mapHeaders);
        return builder.build();
    }
}
