package com.demo.scs.core;

/**
 * @Author: Hu Xin
 * @Date: 2023/1/15 22:50
 * @Desc:
 **/
public class StreamListenerMeta {

    private String value = "";

    private String condition = "";

    private String target = "";

    private String copyHeaders = "true";

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getCopyHeaders() {
        return copyHeaders;
    }

    public void setCopyHeaders(String copyHeaders) {
        this.copyHeaders = copyHeaders;
    }
}
