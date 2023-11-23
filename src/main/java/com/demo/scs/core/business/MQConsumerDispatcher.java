package com.demo.scs.core.business;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;


import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.ByteArrayMessageConverter;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.converter.SmartMessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.CollectionUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * 消息消费分发给各个业务处理
 *
 * @Author: Hu Xin
 * @Date: 2023/1/10 13:46
 * @Desc:
 **/
@Slf4j
public class MQConsumerDispatcher implements InitializingBean {

    /**
     * 路由表里会包含所有MQConsumerEventListener的实例，但是只会帮自定义配置中的input生成@StreamListener的回调方法
     */
    @Autowired(required = false)
    private List<MQConsumerEventListener> businessEventListeners;

    private ConcurrentHashMap<String, MQConsumerEventListener> businessEventListenersMap;

    private MessageConverter messageConverter;

    @Override
    public void afterPropertiesSet() throws Exception {
        businessEventListenersMap = new ConcurrentHashMap<>(8);
        if (!CollectionUtils.isEmpty(businessEventListeners)) {
            businessEventListeners.forEach(e -> {
                boolean annotationPresent = e.getClass().isAnnotationPresent(BusinessMQEventListener.class);
                if (annotationPresent) {
                    BusinessMQEventListener annotation = e.getClass().getAnnotation(BusinessMQEventListener.class);
                    String value = annotation.value();

                    e.setMessageType(getMessageType(e));
                    e.setMethodParameter(getMessageParameter(e));

                    businessEventListenersMap.putIfAbsent(value, e);
                }

            });
        }

        if (this.messageConverter == null) {
            List<MessageConverter> messageConverters = new ArrayList<>();
            ByteArrayMessageConverter byteArrayMessageConverter = new ByteArrayMessageConverter();
            byteArrayMessageConverter.setContentTypeResolver(null);
            messageConverters.add(byteArrayMessageConverter);
            messageConverters.add(new StringMessageConverter());
            messageConverters.add(new MappingJackson2MessageConverter());
            this.messageConverter = new CompositeMessageConverter(messageConverters);
        }

    }

    public void setMessageConverter(MessageConverter messageConverter) {
        this.messageConverter = messageConverter;
    }

    /**
     * 分配到对应的业务处理类的onMessage方法
     *
     * @param msg
     * @param inputValue
     *            消费者实例名字
     * @throws Exception
     */
    public void dispatchEvent(Message<String> msg, String inputValue) throws Exception {
        // 默认使用String接受，再进行数据转换
        // 找到调用此方法的streamListener注解,获取标注的inputName
        MQConsumerEventListener consumerListenerHandler = businessEventListenersMap.getOrDefault(inputValue, null);
        if (null == consumerListenerHandler) {
            log.error("消费者实例:[{}]找不到，对应的业务处理类，消息：{}", inputValue, msg);
            throw new RuntimeException("找不到消费者实例:[" + inputValue + "]，对应的业务处理类");
        } else {
            Object o = null;
            try {
                if (consumerListenerHandler.getMessageType() instanceof Class) {
                    // 普通类型转换(非嵌套)，如String
                    // if the messageType has not Generic Parameter
                    o = messageConverter.fromMessage(
                        MessageBuilder.withPayload(msg.getPayload()).copyHeaders(msg.getHeaders()).build(),
                        (Class<?>)consumerListenerHandler.getMessageType());
                } else {
                    Class<?> rawType =
                        (Class<?>)((ParameterizedType)consumerListenerHandler.getMessageType()).getRawType();
                    Type[] actualTypeArguments =
                        ((ParameterizedType)consumerListenerHandler.getMessageType()).getActualTypeArguments();
                    if (rawType.isAssignableFrom(Message.class)) {
                        // onMessage入参，用Message包裹的情况
                        if (actualTypeArguments.length == 1) {
                            Type argType = ((ParameterizedType)consumerListenerHandler.getMessageType())
                                .getActualTypeArguments()[0];
                            if (argType instanceof Class) {
                                if (((Class)argType).isAssignableFrom(String.class)) {
                                    // onMessage入参是Message<String>，不再继续进行反序列化
                                    o = msg;
                                } else {
                                    // 支持入参Message<A>
                                    Object payload = ((SmartMessageConverter)messageConverter).fromMessage(
                                        MessageBuilder.withPayload(msg.getPayload()).build(), rawType,
                                        consumerListenerHandler.getMethodParameter());
                                    Message<Object> msgAfterConverted =
                                        MessageBuilder.createMessage(payload, msg.getHeaders());
                                    o = msgAfterConverted;
                                }
                            } else {
                                // 不支持Message<A<B>> 多层嵌套
                                throw new UnsupportedOperationException("不支持此类型["
                                    + consumerListenerHandler.getMessageType() + "]的转换，请使用Message<String>类型接收....");
                            }
                        } else {
                            throw new UnsupportedOperationException("不支持此类型[" + consumerListenerHandler.getMessageType()
                                + "]的转换，请使用Message<String>类型接收....");
                        }

                    } else {
                        // 只关心业务数据payload
                        if (SmartMessageConverter.class.isAssignableFrom(messageConverter.getClass())) {
                            // if the messageType has Generic Parameter, then use SmartMessageConverter#fromMessage with
                            // third parameter "conversionHint".
                            // we have validate the MessageConverter is SmartMessageConverter in
                            // this#getMethodParameter.
                            o = ((SmartMessageConverter)messageConverter).fromMessage(
                                MessageBuilder.withPayload(msg.getPayload()).build(), rawType,
                                consumerListenerHandler.getMethodParameter());
                        } else {
                            o = messageConverter.fromMessage(MessageBuilder.withPayload(msg.getPayload()).build(),
                                (Class<?>)consumerListenerHandler.getMessageType());
                        }
                    }
                }
            } catch (Exception e) {
                log.error("convert failed. str:{}, msgType:{}", msg, consumerListenerHandler.getMessageType());
                throw new RuntimeException("cannot convert message to " + consumerListenerHandler.getMessageType(), e);
            }
            consumerListenerHandler.onMessage(o);
        }

    }

    private Type getMessageType(MQConsumerEventListener evt) {
        Type genericSuperclass = evt.getClass().getGenericSuperclass();
        if (genericSuperclass instanceof ParameterizedType) {
            // 参数化类型
            return ((ParameterizedType)genericSuperclass).getActualTypeArguments()[0];
        } else {
            return genericSuperclass;
        }
    }

    private MethodParameter getMessageParameter(MQConsumerEventListener evt) {
        Class clazz = null;
        Type messageType = evt.getMessageType();
        if (messageType instanceof ParameterizedType) {
            clazz = (Class)((ParameterizedType)messageType).getRawType();
        } else if (messageType instanceof Class) {
            clazz = (Class)messageType;
        } else {
            throw new RuntimeException("parameterType:" + messageType + " of onMessage method is not supported");
        }
        try {
            // 获取onMessage的方法，参数：T的原始类
            final Method method = evt.getClass().getDeclaredMethod("onMessage", clazz);
            return new MethodParameter(method, 0);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
            throw new RuntimeException("parameterType:" + messageType + " of onMessage method is not supported");
        }
    }

}
