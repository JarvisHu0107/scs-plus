package com.demo.scs.core;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.stream.config.BinderProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * 自定义扩展配置,原生配置{@see org.springframework.cloud.stream.config.BindingServiceProperties}
 * 沿用了scs原生的binder/bindings配置，扩展了原生的BindingProperties，只是将spring.cloud.stream的前缀替换程了jarvis.scs，内部层级不变
 * jarvis.scs可以任意修改
 * @Author: Hu Xin
 * @Date: 2023/2/14 14:06
 * @Desc:
 **/
@Data
@Component
@ConfigurationProperties(prefix = "jarvis.scs")
public class ScsExtensionProperties {

    private Map<String, BinderProperties> binders = new HashMap<>();

    private Map<String, ExtensionBindingProperties> bindings =
        new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

}
