package com.demo.scs.util;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * @Author: Hu Xin
 * @Date: 2023/1/30 10:10
 * @Desc:
 **/
@Component
public class SpringContextHolder implements ApplicationContextAware {


    private static ApplicationContext CONTEXT;

    public static <T> T getBean(String outputName, Class<T> clazz) {

        return CONTEXT.getBean(outputName, clazz);

    }


    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.CONTEXT = applicationContext;
    }


}
