package com.demo.scs.core.strategy;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.cloud.stream.binder.Binder;
import org.springframework.cloud.stream.binder.ExtendedConsumerProperties;
import org.springframework.cloud.stream.binder.ExtendedProducerProperties;
import org.springframework.cloud.stream.binder.kafka.KafkaMessageChannelBinder;
import org.springframework.cloud.stream.binder.kafka.properties.KafkaConsumerProperties;
import org.springframework.cloud.stream.binder.kafka.properties.KafkaProducerProperties;
import org.springframework.cloud.stream.config.BindingProperties;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import com.demo.scs.core.CustomizeScsPropertiesHolder;
import com.demo.scs.core.constant.ConsumerModel;
import com.demo.scs.core.constant.ScsPlusConstant;
import com.demo.scs.core.constant.StartOffset;

/**
 * @Author: Hu Xin
 * @Date: 2023/4/11 10:21
 * @Desc:
 **/
public class KafkaChannelBinderHandleStrategy extends ChannelBinderHandler {

    public KafkaChannelBinderHandleStrategy(Binder binder, Map<String, BindingProperties> allBindingsProps) {
        super(binder, allBindingsProps);
    }

    @Override
    public void handleProducer(List<String> outputBindings) {
        for (String output : outputBindings) {
            Object extensionProducer = ((KafkaMessageChannelBinder)binder).getExtendedProducerProperties(output);
            ExtendedProducerProperties extendedProducerProperties = new ExtendedProducerProperties<>(extensionProducer);
            KafkaProducerProperties kafkaProducerProperties =
                (KafkaProducerProperties)extendedProducerProperties.getExtension();

        }
    }

    @Override
    public void handleConsumer(List<String> inputBindings) {
        CustomizeScsPropertiesHolder customizePropsHolder = CustomizeScsPropertiesHolder.getSingleton();
        for (String input : inputBindings) {
            Object extensionConsumer = ((KafkaMessageChannelBinder)binder).getExtendedConsumerProperties(input);
            ExtendedConsumerProperties extendedConsumerProperties = new ExtendedConsumerProperties<>(extensionConsumer);
            KafkaConsumerProperties kafkaConsumerProperties =
                (KafkaConsumerProperties)extendedConsumerProperties.getExtension();

            String startOffsetKey = ScsPlusConstant.getBindingPropKey(input, "startOffset");
            // startOffset不配置的情况使用默认的earliest
            if (customizePropsHolder.getBindingsProps().containsKey(startOffsetKey)) {
                String startOffset = (String)customizePropsHolder.getBindingsProps().getOrDefault(startOffsetKey, null);
                if (StringUtils.hasText(startOffset) && startOffset.equals(StartOffset.earliest.toString())) {
                    kafkaConsumerProperties.setStartOffset(KafkaConsumerProperties.StartOffset.earliest);
                } else if (StringUtils.hasText(startOffset) && startOffset.equals(StartOffset.latest.toString())) {
                    kafkaConsumerProperties.setStartOffset(KafkaConsumerProperties.StartOffset.latest);
                } else {
                    throw new UnsupportedOperationException("startOffset:" + startOffset + "not supported");
                }
            }

            String messageModelKey = ScsPlusConstant.getBindingPropKey(input, "messageModel");
            // 默认使用CLUSTERING模式
            if (customizePropsHolder.getBindingsProps().containsKey(messageModelKey)) {
                String messageModel =
                    (String)customizePropsHolder.getBindingsProps().getOrDefault(messageModelKey, null);
                if (messageModel.equalsIgnoreCase(ConsumerModel.BROADCASTING.toString())) {
                    BindingProperties originalBindingProps = allBindingsProps.getOrDefault(input, null);
                    if (!ObjectUtils.isEmpty(originalBindingProps)) {
                        String consumerGroupName = originalBindingProps.getGroup();
                        originalBindingProps
                            .setGroup(consumerGroupName + "_" + UUID.randomUUID().toString().replaceAll("-", ""));
                    } else {
                        throw new RuntimeException("original scs input " + " named  [ " + input
                            + " ] properties not found, cannot change consumer group id");
                    }
                }
            }

        }
    }

}
