package com.demo.scs.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.BeansException;
import org.springframework.cloud.stream.binder.Binder;
import org.springframework.cloud.stream.binder.DefaultBinderFactory;
import org.springframework.cloud.stream.config.BinderProperties;
import org.springframework.cloud.stream.config.BindingProperties;
import org.springframework.cloud.stream.config.BindingServiceProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import com.demo.scs.core.strategy.ChannelBinderHandler;
import com.demo.scs.core.strategy.ChannelBinderHandlerFactory;

/**
 * @Author: Hu Xin
 * @Date: 2023/2/15 17:51
 * @Desc: allows the registration of additional configuration 自定义的配置在此处给各个binding做加强，此处统一各种MQ的特异性配置如:
 *        rocketmq的group自动填充，rocketmq的orderly,broadcasting...etc
 **/
public class BinderFactoryListener implements DefaultBinderFactory.Listener, ApplicationContextAware {

    private ApplicationContext context;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.context = applicationContext;
    }

    @Override
    public void afterBinderContextInitialized(String configurationName, ConfigurableApplicationContext binderContext) {
        Binder binder = binderContext.getBean(Binder.class);
        // 只对para.scs下自定义配置使用增强
        CustomizeScsPropertiesHolder customizePropsHolder = CustomizeScsPropertiesHolder.getSingleton();

        BindingServiceProperties bindingServiceProperties = context.getBean(BindingServiceProperties.class);

        // scs的全量bindings & binders配置信息
        Map<String, BindingProperties> bindings = bindingServiceProperties.getBindings();
        Map<String, BinderProperties> binders = bindingServiceProperties.getBinders();
        BinderProperties binderProperties = binders.getOrDefault(configurationName, null);
        if (CollectionUtils.isEmpty(bindings)) {
            throw new IllegalStateException("SpringCloudStream  BindingServiceProperties is empty");
        }
        if (CollectionUtils.isEmpty(binders) && !StringUtils.hasText(bindingServiceProperties.getDefaultBinder())) {
            throw new IllegalStateException("SpringCloudStream  BinderProperties is empty");
        }
        // 需要加强的生产者
        List<String> need2EnhancedOutputBindings =
            customizePropsHolder.getBinderAndOutputBindingMaps().getOrDefault(configurationName, new ArrayList<>());

        // 需要加强的消费者
        List<String> need2EnhancedInputBindings =
            customizePropsHolder.getBinderAndInputBindingMaps().getOrDefault(configurationName, new ArrayList<>());

        if (!CollectionUtils.isEmpty(need2EnhancedOutputBindings)
            || !CollectionUtils.isEmpty(need2EnhancedInputBindings)) {
            // 根据binder的类型，生成不同的处理类：（进行生产者和消费者的加强）
            ChannelBinderHandler channelBinderHandler = ChannelBinderHandlerFactory
                .createChannelBinderHandler(binderProperties.getType(), configurationName, binder, bindings);

            if (ObjectUtils.isEmpty(channelBinderHandler)) {
                throw new UnsupportedOperationException(
                    "binderName:" + configurationName + ", 不支持的binder类型。找不到对应的ChannelBinderHandler");
            }

            if (!CollectionUtils.isEmpty(need2EnhancedOutputBindings)) {
                channelBinderHandler.handleProducer(need2EnhancedOutputBindings);
            }

            if (!CollectionUtils.isEmpty(need2EnhancedInputBindings)) {
                channelBinderHandler.handleConsumer(need2EnhancedInputBindings);
            }
        }

    }

}
