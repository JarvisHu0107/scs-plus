package com.demo.scs.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.cloud.stream.config.BinderProperties;
import org.springframework.cloud.stream.config.BindingProperties;
import org.springframework.cloud.stream.config.BindingServiceProperties;
import org.springframework.core.annotation.Order;
import org.springframework.util.CollectionUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * 1. para.scs自定义配置加入配置 2.简化原生的多层配置。 各不同类型mq的properties配置项，增强配置实现
 * {@link com.demo.scs.core.BinderFactoryListener}
 *
 * @Author: Hu Xin
 * @Date: 2023/1/17 11:05
 * @Desc:
 **/
@Slf4j
@Order(Integer.MIN_VALUE + 10)
public class ConfigurationPropertiesBeanPostProcessor implements BeanPostProcessor {

    /**
     * 自定义的scs配置
     */
    private ScsExtensionProperties scsExtensionProperties;

    public ConfigurationPropertiesBeanPostProcessor(ScsExtensionProperties scsExtensionProperties) {
        this.scsExtensionProperties = scsExtensionProperties;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        /**
         * BindingServiceProperties里面包含了bindings和binders的信息，在这里进行配置扩充 从这个里面BindingServiceProperties 可以拿到所有的binder Map
         */
        if (BindingServiceProperties.class.isAssignableFrom(bean.getClass())) {
            // 增加自定义的binder和bindings
            BindingServiceProperties properties = (BindingServiceProperties)bean;

            Map<String, BinderProperties> binders = properties.getBinders();

            Map<String, BindingProperties> bindings = properties.getBindings();

            // 校验是否存在相同的binder、binding名字,如果出现2个相同名字的，就会throw Exception
            // add binders extension config
            if (ObjectUtils.isEmpty(scsExtensionProperties)
                || CollectionUtils.isEmpty(scsExtensionProperties.getBinders())) {
                log.warn("未配置任何【自定义的binder-MQ实例】信息...");
            } else {
                if (!binders.isEmpty()) {
                    for (String binderName : scsExtensionProperties.getBinders().keySet()) {
                        if (binders.containsKey(binderName)) {
                            throw new RuntimeException("存在相同名字的binder:" + binderName);
                        }
                    }
                }
                binders.putAll(scsExtensionProperties.getBinders());
            }
            // add bindings extension config
            if (ObjectUtils.isEmpty(scsExtensionProperties)
                || CollectionUtils.isEmpty(scsExtensionProperties.getBindings())) {
                log.warn("未配置任何【自定义的bindings】信息...");
            } else {
                if (!bindings.isEmpty()) {
                    for (String bindingName : scsExtensionProperties.getBindings().keySet()) {
                        if (bindings.containsKey(bindingName)) {
                            throw new RuntimeException("存在相同名字的bindings:" + bindingName);
                        }
                    }
                }
                bindings.putAll(scsExtensionProperties.getBindings());
            }

            // binding(input/output)和binder关系分组
            Set<String> binderNameSet = binders.keySet();
            CustomizeScsPropertiesHolder customizePropsHolder = CustomizeScsPropertiesHolder.getSingleton();
            CopyOnWriteArrayList<String> needEnhancedInputBindings = customizePropsHolder.getInputBindings();
            CopyOnWriteArrayList<String> needEnhancedOutputBindings = customizePropsHolder.getOutputBindings();
            String defaultBinder = properties.getDefaultBinder();

            for (String binder : binderNameSet) {
                Set<Map.Entry<String, BindingProperties>> entries = bindings.entrySet();
                for (Map.Entry<String, BindingProperties> entry : entries) {
                    BindingProperties bindingProperties = entry.getValue();
                    if (StringUtils.isNotEmpty(defaultBinder) && StringUtils.isEmpty(bindingProperties.getBinder())) {
                        bindingProperties.setBinder(defaultBinder);
                    }
                    if (StringUtils.isEmpty(bindingProperties.getBinder())) {
                        throw new RuntimeException("请配置binding:【" + entry.getKey() + "】,所属的binder");
                    }
                    if (bindingProperties.getBinder().equals(binder)) {
                        if (needEnhancedInputBindings.contains(entry.getKey())) {
                            List<String> inputs = customizePropsHolder.getBinderAndInputBindingMaps()
                                .computeIfAbsent(binder, k -> new ArrayList<>());
                            if (!inputs.contains(entry.getKey())) {
                                inputs.add(entry.getKey());
                            }

                        } else if (needEnhancedOutputBindings.contains(entry.getKey())) {
                            List<String> outputs = customizePropsHolder.getBinderAndOutputBindingMaps()
                                .computeIfAbsent(binder, k -> new ArrayList<>());
                            if (!outputs.contains(entry.getKey())) {
                                outputs.add(entry.getKey());
                            }
                        }

                    }
                }
            }
        }
        return bean;
    }
}
