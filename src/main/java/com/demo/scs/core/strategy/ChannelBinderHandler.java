package com.demo.scs.core.strategy;

import java.util.List;
import java.util.Map;

import org.springframework.cloud.stream.binder.Binder;
import org.springframework.cloud.stream.config.BindingProperties;

/**
 * @Author: Hu Xin
 * @Date: 2023/4/11 10:17
 * @Desc:
 **/
public abstract class ChannelBinderHandler {

    protected Binder binder;

    /**
     * scs"所有"的binding配置信息
     */
    protected Map<String, BindingProperties> allBindingsProps;

    public ChannelBinderHandler(Binder binder, Map<String, BindingProperties> allBindingsProps) {
        this.binder = binder;
        this.allBindingsProps = allBindingsProps;

    }

    /**
     * 处理需要加强的生产者
     *
     * @param outputBindings
     */
    public abstract void handleProducer(List<String> outputBindings);

    /**
     * 处理需要加强的消费者
     *
     * @param inputBindings
     */
    public abstract void handleConsumer(List<String> inputBindings);

}
