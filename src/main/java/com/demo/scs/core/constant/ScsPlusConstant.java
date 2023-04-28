package com.demo.scs.core.constant;

import org.springframework.util.StringUtils;

/**
 * @Author: Hu Xin
 * @Date: 2023/1/16 12:32
 * @Desc:
 **/
public class ScsPlusConstant {

    public final static String INPUT_CHANNEL_KEY = "inputChannelKey";

    public final static String BINDING_TYPE_INPUT = "input";

    public final static String BINDING_TYPE_OUTPUT = "output";

    public static final String BINDINGS_PREFIX = "para.scs.bindings";
    public static final String BINDERS_PREFIX = "para.scs.binders";

    public static final char DOT = '.';

    public static final String DEFAULT_PRODUCER_GROUP = "DF";

    /**
     * 获取自定义配置中binding的配置值
     *
     * @param bindingName
     * @param suffix
     * @return
     */
    public static String getBindingPropKey(String bindingName, String suffix) {

        return ScsPlusConstant.BINDINGS_PREFIX + ScsPlusConstant.DOT + bindingName
                + (StringUtils.hasText(suffix) ? ScsPlusConstant.DOT + suffix : "");

    }

    /**
     * 获取自定义配置中binder的配置值
     *
     * @param binderName
     * @param suffix
     * @return
     */
    public static String getBinderPropKey(String binderName, String suffix) {
        return ScsPlusConstant.BINDERS_PREFIX + ScsPlusConstant.DOT + binderName
                + (StringUtils.hasText(suffix) ? ScsPlusConstant.DOT + suffix : "");

    }

}
