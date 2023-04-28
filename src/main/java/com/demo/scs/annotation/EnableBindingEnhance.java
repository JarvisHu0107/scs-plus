package com.demo.scs.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.cloud.stream.config.BinderFactoryAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.integration.config.EnableIntegration;

import com.demo.scs.core.ScsConfiguration;

/**
 * @Author: Hu Xin
 * @Date: 2023/1/16 16:31
 * @Desc:
 **/
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@Configuration
@Import({BindingBeansEnhanceRegistrar.class, BinderFactoryAutoConfiguration.class, ScsConfiguration.class})
@EnableIntegration
public @interface EnableBindingEnhance {

    Class<?>[] value() default {};

}
