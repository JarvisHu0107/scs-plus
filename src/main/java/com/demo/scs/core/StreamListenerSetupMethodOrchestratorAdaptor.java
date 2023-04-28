package com.demo.scs.core;

import java.lang.reflect.Method;

import org.springframework.cloud.stream.annotation.Input;
import org.springframework.cloud.stream.annotation.Output;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.cloud.stream.binding.StreamListenerErrorMessages;
import org.springframework.cloud.stream.binding.StreamListenerParameterAdapter;
import org.springframework.context.ApplicationContext;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * @Author: Hu Xin
 * @Date: 2023/1/15 23:24
 * @Desc: 因StreamListenerSetupMethodOrchestrator方法参数是streamListener, 改为streamListenerMeta，产生的适配类
 *        {@link com.demo.scs.core.StreamListenerAnnotationAutoGenerateBeanPostProcessor}
 **/
public interface StreamListenerSetupMethodOrchestratorAdaptor {
    /**
     * 为了适配StreamListenerMeta参数，提供的接口方法
     *
     * @param streamListener
     * @param method
     * @param bean
     */
    void orchestrateStreamListenerSetupMethod(StreamListenerMeta streamListener, Method method,
        Object bean);

    boolean supports(Method method);

    /**
     * Method that allows custom orchestration on the {@link StreamListener} setup method.
     *
     * @param streamListener
     *            reference to the {@link StreamListener} annotation on the method
     * @param method
     *            annotated with {@link StreamListener}
     * @param bean
     *            that contains the StreamListener method
     */
    void orchestrateStreamListenerSetupMethod(StreamListener streamListener, Method method, Object bean);

    /**
     * Default implementation for adapting each of the incoming method arguments using an available
     * {@link StreamListenerParameterAdapter} and provide the adapted collection of arguments back to the caller.
     *
     * @param method
     *            annotated with {@link StreamListener}
     * @param inboundName
     *            inbound binding
     * @param applicationContext
     *            spring application context
     * @param streamListenerParameterAdapters
     *            used for adapting the method arguments
     * @return adapted incoming arguments
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    default Object[] adaptAndRetrieveInboundArguments(Method method, String inboundName,
        ApplicationContext applicationContext, StreamListenerParameterAdapter... streamListenerParameterAdapters) {
        Object[] arguments = new Object[method.getParameterTypes().length];
        for (int parameterIndex = 0; parameterIndex < arguments.length; parameterIndex++) {
            MethodParameter methodParameter = MethodParameter.forExecutable(method, parameterIndex);
            Class<?> parameterType = methodParameter.getParameterType();
            Object targetReferenceValue = null;
            if (methodParameter.hasParameterAnnotation(Input.class)) {
                targetReferenceValue = AnnotationUtils.getValue(methodParameter.getParameterAnnotation(Input.class));
            } else if (methodParameter.hasParameterAnnotation(Output.class)) {
                targetReferenceValue = AnnotationUtils.getValue(methodParameter.getParameterAnnotation(Output.class));
            } else if (arguments.length == 1 && StringUtils.hasText(inboundName)) {
                targetReferenceValue = inboundName;
            }
            if (targetReferenceValue != null) {
                Assert.isInstanceOf(String.class, targetReferenceValue, "Annotation value must be a String");
                Object targetBean = applicationContext.getBean((String)targetReferenceValue);
                // Iterate existing parameter adapters first
                for (StreamListenerParameterAdapter streamListenerParameterAdapter : streamListenerParameterAdapters) {
                    if (streamListenerParameterAdapter.supports(targetBean.getClass(), methodParameter)) {
                        arguments[parameterIndex] = streamListenerParameterAdapter.adapt(targetBean, methodParameter);
                        break;
                    }
                }
                if (arguments[parameterIndex] == null && parameterType.isAssignableFrom(targetBean.getClass())) {
                    arguments[parameterIndex] = targetBean;
                }
                Assert.notNull(arguments[parameterIndex], "Cannot convert argument " + parameterIndex + " of " + method
                    + "from " + targetBean.getClass() + " to " + parameterType);
            } else {
                throw new IllegalStateException(StreamListenerErrorMessages.INVALID_DECLARATIVE_METHOD_PARAMETERS);
            }
        }
        return arguments;
    }

}
