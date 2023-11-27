package com.demo.scs.core;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.stream.messaging.DirectWithAttributesChannel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import com.demo.scs.core.business.ConsumerCollectorTemplate;
import com.demo.scs.core.business.InputChannelInterceptor;
import com.demo.scs.core.business.MQConsumerDispatcher;
import com.demo.scs.core.constant.ScsPlusConstant;

/**
 * @Author: Hu Xin
 * @Date: 2023/1/15 23:34
 * @Desc:
 **/

@Configuration
@EnableConfigurationProperties({ScsExtensionProperties.class})
public class ScsConfiguration {

    private ScsExtensionProperties scsExtensionProperties;


    /**
     * streamListener自动生成
     *
     * @return
     */
    @Bean(name = "streamListenerAutoGenerateAnnotationBeanPostProcessor")
    public static StreamListenerAnnotationAutoGenerateBeanPostProcessor
    streamListenerAutoGenerateAnnotationBeanPostProcessor(ScsExtensionProperties scsExtensionProperties) {
        return new StreamListenerAnnotationAutoGenerateBeanPostProcessor(scsExtensionProperties);
    }

    /**
     * binder/bindings添加扩展
     *
     * @return
     */
    @Bean
    public ConfigurationPropertiesBeanPostProcessor configurationPropertiesBeanPostProcessor(
         ScsExtensionProperties scsExtensionProperties) {
        return new ConfigurationPropertiesBeanPostProcessor(scsExtensionProperties);
    }

    /**
     * binder生产监听器
     * 
     * @return
     */
    @Bean
    public BinderFactoryListener binderFactoryListener() {
        return new BinderFactoryListener();
    }

    @Bean
    public MQConsumerDispatcher mqConsumerDispatcher() {

        return new MQConsumerDispatcher();
    }

    @DependsOn("mqConsumerDispatcher")
    @Bean
    public ConsumerCollectorTemplate consumerCollectorTemplate(MQConsumerDispatcher mqConsumerDispatcher) {
        return new ConsumerCollectorTemplate(mqConsumerDispatcher);
    }

    @Bean
    public InputChannelInterceptor inputChannelInterceptor() {
        return new InputChannelInterceptor();
    }

    @Bean
    public BeanPostProcessor channelsConfigurer(InputChannelInterceptor inputChannelInterceptor) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
                return bean;
            }

            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof DirectWithAttributesChannel) {
                    DirectWithAttributesChannel messageChannel = (DirectWithAttributesChannel)bean;
                    if (messageChannel.getAttribute("type").equals(ScsPlusConstant.BINDING_TYPE_INPUT)) {
                        messageChannel.addInterceptor(inputChannelInterceptor);
                    }
                }

                return bean;
            }
        };

    }

}
