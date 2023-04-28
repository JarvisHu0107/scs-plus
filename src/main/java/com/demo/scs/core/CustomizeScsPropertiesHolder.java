package com.demo.scs.core;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 以下所有的binder和binding都是自定义配置中的名字，无原生scs的配置 1. 持有所有的自定义jarvis.scs.binder/jarvis.scs.bindings配置k,v 2.
 * 管理的bindings,binder名字 3. Map< binderName, binderType > 4. Map< binderName,List< inputBinding >> 5. Map<
 * binderName,List< outputBinding >>
 *
 * @Author: Hu Xin
 * @Date: 2023/2/16 16:15
 * @Desc:
 **/

public class CustomizeScsPropertiesHolder {

    private volatile static CustomizeScsPropertiesHolder singleton;

    /**
     * propKey -- propValue
     */
    private ConcurrentHashMap<String, Object> bindingsProps = new ConcurrentHashMap<>();
    /**
     * propKey -- propValue
     */
    private ConcurrentHashMap<String, Object> binderProps = new ConcurrentHashMap<>();

    /**
     * binderName <-> binderType
     */
    private ConcurrentHashMap<String, String> binderAndTypeMap = new ConcurrentHashMap<>();

    private CopyOnWriteArrayList<String> outputBindings = new CopyOnWriteArrayList<>();

    private CopyOnWriteArrayList<String> inputBindings = new CopyOnWriteArrayList<>();

    /**
     * binderName <-> [inputBindingName1,inputBindingName2,...,inputBindingNameN]
     */
    private ConcurrentHashMap<String, List<String>> binderAndInputBindingMaps = new ConcurrentHashMap<>(4);

    /**
     * binderName <-> [outputBindingName1,outputBindingName2,...,outBindingNameN]
     */
    private ConcurrentHashMap<String, List<String>> binderAndOutputBindingMaps = new ConcurrentHashMap<>(4);

    private CustomizeScsPropertiesHolder() {}

    public static CustomizeScsPropertiesHolder getSingleton() {
        if (singleton == null) {
            synchronized (CustomizeScsPropertiesHolder.class) {
                if (singleton == null) {
                    singleton = new CustomizeScsPropertiesHolder();
                }
            }
        }
        return singleton;
    }

    public ConcurrentHashMap<String, Object> getBindingsProps() {
        return bindingsProps;
    }

    public void setBindingsProps(ConcurrentHashMap<String, Object> bindingsProps) {
        this.bindingsProps = bindingsProps;
    }

    public ConcurrentHashMap<String, Object> getBinderProps() {
        return binderProps;
    }

    public void setBinderProps(ConcurrentHashMap<String, Object> binderProps) {
        this.binderProps = binderProps;
    }

    public CopyOnWriteArrayList<String> getOutputBindings() {
        return outputBindings;
    }

    public void setOutputBindings(CopyOnWriteArrayList<String> outputBindings) {
        this.outputBindings = outputBindings;
    }

    public CopyOnWriteArrayList<String> getInputBindings() {
        return inputBindings;
    }

    public void setInputBindings(CopyOnWriteArrayList<String> inputBindings) {
        this.inputBindings = inputBindings;
    }

    public ConcurrentHashMap<String, String> getBinderAndTypeMap() {
        return binderAndTypeMap;
    }

    public void setBinderAndTypeMap(ConcurrentHashMap<String, String> binderAndTypeMap) {
        this.binderAndTypeMap = binderAndTypeMap;
    }

    public ConcurrentHashMap<String, List<String>> getBinderAndInputBindingMaps() {
        return binderAndInputBindingMaps;
    }

    public ConcurrentHashMap<String, List<String>> getBinderAndOutputBindingMaps() {
        return binderAndOutputBindingMaps;
    }
}
