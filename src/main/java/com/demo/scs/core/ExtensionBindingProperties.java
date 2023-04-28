package com.demo.scs.core;

import org.springframework.cloud.stream.config.BindingProperties;

/**
 * 对scs原生BindingProperties的扩展
 * 
 * @Author: Hu Xin
 * @Date: 2023/2/14 16:02
 * @Desc:
 **/

public class ExtensionBindingProperties extends BindingProperties {

    /**
     * 定义类型:input,output
     */
    private String type;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

}
