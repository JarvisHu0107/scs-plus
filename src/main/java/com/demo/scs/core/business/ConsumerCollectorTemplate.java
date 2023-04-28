package com.demo.scs.core.business;

import org.apache.commons.lang3.StringUtils;
import org.springframework.messaging.Message;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.demo.scs.core.constant.ScsPlusConstant;
import lombok.extern.slf4j.Slf4j;

/**
 * @Author: Hu Xin
 * @Date: 2023/1/9 10:55
 * @Desc:
 **/

@Slf4j
public class ConsumerCollectorTemplate {

    private MQConsumerDispatcher mqConsumerDispatcher;

    public ConsumerCollectorTemplate(MQConsumerDispatcher mqConsumerDispatcher) {
        this.mqConsumerDispatcher = mqConsumerDispatcher;
    }

    /**
     * 定义StreamListener------ 都用这个doConsume来进行消息消费，然后在根据channel的beanName分发给业务
     */
    public void doConsume(Message<String> msg) throws Exception {
        // Message<String>; payload == String
        // Message; payload == byte[]
        // 找到消费的inputValue
        String inputChannelBeanName = null;
        if (!msg.getHeaders().isEmpty() && msg.getHeaders().containsKey(ScsPlusConstant.INPUT_CHANNEL_KEY)) {
            inputChannelBeanName = msg.getHeaders().get(ScsPlusConstant.INPUT_CHANNEL_KEY, String.class);
        }
        if (StringUtils.isEmpty(inputChannelBeanName)) {
            log.error("SCS,ConsumerCollector#doConsume,msg->headers(inputChannelKey) is empty.");
            ObjectMapper ob = new ObjectMapper();
            throw new RuntimeException("SCS,ConsumerCollector#doConsume,msg->headers(inputChannelKey) is empty,msg:["
                + ob.writeValueAsString(msg) + "]");
        }
        mqConsumerDispatcher.dispatchEvent(msg, inputChannelBeanName);

    }

}
