package com.demo.scs.core.strategy;

import java.util.List;
import java.util.Map;

import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.protocol.heartbeat.MessageModel;
import org.springframework.cloud.stream.binder.Binder;
import org.springframework.cloud.stream.config.BindingProperties;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.alibaba.cloud.stream.binder.rocketmq.RocketMQMessageChannelBinder;
import com.alibaba.cloud.stream.binder.rocketmq.properties.RocketMQConsumerProperties;
import com.alibaba.cloud.stream.binder.rocketmq.properties.RocketMQProducerProperties;
import com.demo.scs.core.CustomizeScsPropertiesHolder;
import com.demo.scs.core.constant.ConsumerModel;
import com.demo.scs.core.constant.ScsPlusConstant;
import com.demo.scs.core.constant.StartOffset;

/**
 * @Author: Hu Xin
 * @Date: 2023/4/11 10:18
 * @Desc:
 **/
public class RocketMQChannelBinderHandleStrategy extends ChannelBinderHandler {

    public RocketMQChannelBinderHandleStrategy(Binder binder, Map<String, BindingProperties> allBindingsProps) {

        super(binder, allBindingsProps);
    }

    @Override
    public void handleProducer(List<String> outputBindings) {
        if (!CollectionUtils.isEmpty(outputBindings)) {
            for (String out : outputBindings) {

                // rocketmq 指定默认的groupName
                RocketMQProducerProperties rocketMQProducerProperties = ((RocketMQMessageChannelBinder) binder).getExtendedProducerProperties(out);
                if (!StringUtils.hasText(rocketMQProducerProperties.getGroup())) {
                    rocketMQProducerProperties.setGroup(out + "-" + ScsPlusConstant.DEFAULT_PRODUCER_GROUP);
                }

            }
        }
    }

    @Override
    public void handleConsumer(List<String> inputBindings) {
        CustomizeScsPropertiesHolder customizePropsHolder = CustomizeScsPropertiesHolder.getSingleton();
        for (String input : inputBindings) {
            RocketMQConsumerProperties rocketMQConsumerProperties = ((RocketMQMessageChannelBinder) binder).getExtendedConsumerProperties(input);

            String orderlyKey = ScsPlusConstant.getBindingPropKey(input, "orderly");
            if (customizePropsHolder.getBindingsProps().containsKey(orderlyKey)) {
                Boolean orderly = (Boolean) customizePropsHolder.getBindingsProps().getOrDefault(orderlyKey, Boolean.FALSE);
                if (orderly.equals(Boolean.TRUE)) {
                    // 默认是push模式
                    rocketMQConsumerProperties.getPush().setOrderly(Boolean.TRUE);
                }

            }
            String startOffsetKey = ScsPlusConstant.getBindingPropKey(input, "startOffset");
            // startOffset不配置的情况，使用默认的CONSUME_FROM_LAST_OFFSET
            if (customizePropsHolder.getBindingsProps().containsKey(startOffsetKey)) {
                String startOffset = (String) customizePropsHolder.getBindingsProps().getOrDefault(startOffsetKey, null);
                if (StringUtils.hasText(startOffset) && startOffset.equals(StartOffset.earliest.toString())) {
                    rocketMQConsumerProperties.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
                } else if (StringUtils.hasText(startOffset) && startOffset.equals(StartOffset.latest.toString())) {
                    rocketMQConsumerProperties.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
                } else {
                    throw new UnsupportedOperationException("startOffset:" + startOffset + "not supported");
                }
            }

            String messageModelKey = ScsPlusConstant.getBindingPropKey(input, "messageModel");
            // 默认使用CLUSTERING模式
            if (customizePropsHolder.getBindingsProps().containsKey(messageModelKey)) {
                String messageModel = (String) customizePropsHolder.getBindingsProps().getOrDefault(messageModelKey, null);
                if (messageModel.equalsIgnoreCase(ConsumerModel.BROADCASTING.toString())) {
                    rocketMQConsumerProperties.setMessageModel(MessageModel.BROADCASTING.getModeCN());
                }
            }

        }
    }
}
