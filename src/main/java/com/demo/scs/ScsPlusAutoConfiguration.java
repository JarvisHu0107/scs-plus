package com.demo.scs;

import com.demo.scs.core.ScsConfiguration;
import com.demo.scs.core.ScsExtensionProperties;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * @Author: Hu Xin
 * @Date: 2023/4/26 17:19
 * @Desc:
 **/
@ComponentScan(basePackages = {
        "com.demo.scs"
})
@Configuration
public class ScsPlusAutoConfiguration {
}
