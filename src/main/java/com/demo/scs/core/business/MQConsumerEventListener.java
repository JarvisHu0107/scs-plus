package com.demo.scs.core.business;

import java.lang.reflect.Type;

import org.springframework.core.MethodParameter;

/**
 * MQ消息业务监听抽象类
 *
 * @Author: Hu Xin
 * @Date: 2023/1/9 11:13
 * @Desc:
 **/
public abstract class MQConsumerEventListener<T> {

    /**
     * 消息Class
     */
    private Type messageType;

    /**
     * 兼容2层泛型数据转换
     */
    private MethodParameter methodParameter;

    public void setMessageType(Type messageType) {
        this.messageType = messageType;
    }

    public Type getMessageType() {
        return this.messageType;
    }

    public void setMethodParameter(MethodParameter messageType) {
        this.methodParameter = messageType;
    }

    public MethodParameter getMethodParameter() {
        return methodParameter;
    }

    /**
     * @param msg
     */
    protected abstract void onMessage(T msg);

}
