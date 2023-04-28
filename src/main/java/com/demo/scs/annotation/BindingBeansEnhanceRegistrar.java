package com.demo.scs.annotation;

import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.cloud.stream.binding.BindingBeanDefinitionRegistryUtils;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import com.demo.scs.core.ByteCodeUtils;
import com.demo.scs.core.CustomizeScsPropertiesHolder;
import com.demo.scs.core.constant.ScsPlusConstant;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 * @Author: Hu Xin
 * @Date: 2023/1/13 15:01
 * @Desc:
 **/
@Slf4j
public class BindingBeansEnhanceRegistrar implements ImportBeanDefinitionRegistrar, EnvironmentAware {

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @SneakyThrows
    @Override
    public void registerBeanDefinitions(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {

        AnnotationAttributes attrs = AnnotatedElementUtils.getMergedAnnotationAttributes(
            ClassUtils.resolveClassName(metadata.getClassName(), null),
            com.demo.scs.annotation.EnableBindingEnhance.class);
        for (Class<?> type : collectClasses(attrs, metadata.getClassName())) {
            if (!registry.containsBeanDefinition(type.getName())) {
                BindingBeanDefinitionRegistryUtils.registerBindingTargetBeanDefinitions(type, type.getName(), registry);
                BindingBeanDefinitionRegistryUtils.registerBindingTargetsQualifiedBeanDefinitions(
                    ClassUtils.resolveClassName(metadata.getClassName(), null), type, registry);
            }
        }
        try {
            // 根据[jarvis.scs.bindings/binders]前缀配置，1.汇总binder和bindings的信息
            MutablePropertySources sources = ((AbstractEnvironment)environment).getPropertySources();
            CustomizeScsPropertiesHolder customizeScsPropertiesHolder = CustomizeScsPropertiesHolder.getSingleton();
            for (PropertySource<?> source : sources) {
                if (source instanceof EnumerablePropertySource) {
                    EnumerablePropertySource propertySource = (EnumerablePropertySource)source;
                    for (String s : propertySource.getPropertyNames()) {
                        if (s.startsWith(ScsPlusConstant.BINDINGS_PREFIX)) {
                            // 自定义bindings汇总
                            Object v = propertySource.getProperty(s);
                            if (!ObjectUtils.isEmpty(v)) {
                                // 保存bindings的所有自定义的配置项和值
                                customizeScsPropertiesHolder.getBindingsProps().put(s, v);
                            }
                            String bindingsName = getBindingName(s);
                            if (StringUtils.hasLength(bindingsName)) {
                                String bindingType = ScsPlusConstant.getBindingPropKey(bindingsName, "type");
                                // bindings type : input/output
                                String type = environment.getProperty(bindingType);
                                if (!StringUtils.hasLength(type)) {
                                    throw new RuntimeException(
                                        "bindings:[" + bindingsName + "] type is empty, choose input or output");
                                }
                                if (ScsPlusConstant.BINDING_TYPE_INPUT.equalsIgnoreCase(type)) {
                                    customizeScsPropertiesHolder.getInputBindings().addIfAbsent(bindingsName);
                                    // 2.校验自定义配置中的合法性，如消费者必须配置group和destination
                                    String destinationKey =
                                        ScsPlusConstant.getBindingPropKey(bindingsName, "destination");
                                    String dest = environment.getProperty(destinationKey);
                                    if (!StringUtils.hasText(dest)) {
                                        throw new IllegalArgumentException(
                                            "binding name [" + bindingsName + "] doesn't have destination ");
                                    }
                                    String groupKey = ScsPlusConstant.getBindingPropKey(bindingsName, "group");
                                    String consumerGroupId = environment.getProperty(groupKey);
                                    if (!StringUtils.hasText(consumerGroupId)) {
                                        throw new IllegalArgumentException(
                                            "binding name [" + bindingsName + "] doesn't have consumerGroup ");
                                    }
                                } else if (ScsPlusConstant.BINDING_TYPE_OUTPUT.equalsIgnoreCase(type)) {
                                    customizeScsPropertiesHolder.getOutputBindings().addIfAbsent(bindingsName);
                                } else {
                                    throw new IllegalArgumentException(
                                        "binding name [" + bindingsName + "] is illegal ");
                                }

                            }
                        } else if (s.startsWith(ScsPlusConstant.BINDERS_PREFIX)) {
                            // 自定义binders汇总
                            Object v = propertySource.getProperty(s);
                            if (!ObjectUtils.isEmpty(v)) {
                                // 保存binders的所有自定义的配置项和值
                                customizeScsPropertiesHolder.getBinderProps().putIfAbsent(s, v);
                            }
                            String binderName = getBinderName(s);
                            String binderType = ScsPlusConstant.getBinderPropKey(binderName, "type");
                            // binder type
                            String type = environment.getProperty(binderType);
                            if (!StringUtils.hasLength(type)) {
                                throw new RuntimeException(
                                    "binders:[" + binderName + "] type is empty, choose rocketmq/kafka etc...");
                            }
                            customizeScsPropertiesHolder.getBinderAndTypeMap().putIfAbsent(binderName, type);
                        }
                    }
                }
            }
            // 3.修改字节码在CustomizeProcessor生成一些input和output
            if (!CollectionUtils.isEmpty(customizeScsPropertiesHolder.getInputBindings())
                || !CollectionUtils.isEmpty(customizeScsPropertiesHolder.getOutputBindings())) {

                Class<?> type = ByteCodeUtils.generateMethodForInputAndOutputBinding(
                    customizeScsPropertiesHolder.getInputBindings(), customizeScsPropertiesHolder.getOutputBindings());

                BindingBeanDefinitionRegistryUtils.registerBindingTargetBeanDefinitions(type, type.getName(), registry);
                BindingBeanDefinitionRegistryUtils.registerBindingTargetsQualifiedBeanDefinitions(
                    ClassUtils.resolveClassName(metadata.getClassName(), null), type, registry);
            }

        } catch (Exception e) {
            log.error("BindingBeansEnhanceRegistrar # registerBeanDefinitions，error", e);
            throw e;
        }
    }

    private Class<?>[] collectClasses(AnnotationAttributes attrs, String className) {
        com.demo.scs.annotation.EnableBindingEnhance enableBinding = AnnotationUtils.synthesizeAnnotation(attrs,
            com.demo.scs.annotation.EnableBindingEnhance.class, ClassUtils.resolveClassName(className, null));
        return enableBinding.value();
    }

    private String getBindingName(String propName) {
        String substring = propName.substring(ScsPlusConstant.BINDINGS_PREFIX.length() + 1);
        int dotIndex = substring.indexOf(".");
        if (dotIndex == -1) {
            throw new RuntimeException("bindings config: [" + propName + "] cannot find binding name");
        }
        return substring.substring(0, dotIndex);
    }

    private String getBinderName(String propName) {
        String substring = propName.substring(ScsPlusConstant.BINDERS_PREFIX.length() + 1);
        int dotIndex = substring.indexOf(".");
        if (dotIndex == -1) {
            throw new RuntimeException("binders config: [" + propName + "] cannot find binder name");
        }
        return substring.substring(0, dotIndex);
    }

}
