package com.demo.scs.core.strategy;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.cloud.stream.binder.Binder;
import org.springframework.cloud.stream.config.BindingProperties;

import com.demo.scs.core.CustomizeScsPropertiesHolder;
import com.demo.scs.core.constant.SupportMQType;

/**
 * @Author: Hu Xin
 * @Date: 2023/4/11 10:16
 * @Desc:
 **/
public class ChannelBinderHandlerFactory {
    public static ChannelBinderHandler createChannelBinderHandler(String binderType, String binderName, Binder binder,
        Map<String, BindingProperties> allBindingsProps) {
        ConcurrentHashMap<String, String> binderAndTypeMap =
            CustomizeScsPropertiesHolder.getSingleton().getBinderAndTypeMap();

        if (binderType.equalsIgnoreCase(SupportMQType.rocketmq.name())) {

            return new RocketMQChannelBinderHandleStrategy(binder, allBindingsProps);
        } else if (binderType.equalsIgnoreCase(SupportMQType.kafka.name())) {

            return new KafkaChannelBinderHandleStrategy(binder, allBindingsProps);
        }

        return null;
    }
}