package com.demo.scs.core;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanExpressionContext;
import org.springframework.beans.factory.config.BeanExpressionResolver;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.cloud.stream.annotation.Input;
import org.springframework.cloud.stream.annotation.Output;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.cloud.stream.binding.StreamListenerErrorMessages;
import org.springframework.cloud.stream.binding.StreamListenerParameterAdapter;
import org.springframework.cloud.stream.binding.StreamListenerResultAdapter;
import org.springframework.cloud.stream.config.SpringIntegrationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.integration.context.IntegrationContextUtils;
import org.springframework.integration.handler.AbstractReplyProducingMessageHandler;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.core.DestinationResolver;
import org.springframework.messaging.handler.annotation.support.MessageHandlerMethodFactory;
import org.springframework.messaging.handler.invocation.InvocableHandlerMethod;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import com.demo.scs.core.business.ConsumerCollectorTemplate;
import com.demo.scs.core.constant.ScsPlusConstant;
import com.demo.scs.core.sourcecode.DispatchingStreamListenerMessageHandlerExt;
import com.demo.scs.core.sourcecode.StreamListenerMessageHandlerExt;
import com.demo.scs.core.sourcecode.StreamListenerMethodUtils;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 * @Author:
 * @Date: 2023/1/15 19:19
 * @Desc:
 **/
@Slf4j
public class StreamListenerAnnotationAutoGenerateBeanPostProcessor
    implements BeanPostProcessor, ApplicationContextAware, SmartInitializingSingleton {


    /**
     * {@link ConsumerCollectorTemplate}
     */
    public final static String CONSUMER_COLLECTOR_CLASS = "com.demo.scs.core.business.ConsumerCollectorTemplate";

    private static final SpelExpressionParser SPEL_EXPRESSION_PARSER = new SpelExpressionParser();

    // @checkstyle:off
    private final MultiValueMap<String, StreamListenerHandlerMethodMapping> mappedListenerMethods =
        new LinkedMultiValueMap<>();

    // @checkstyle:on

    private final Set<Runnable> streamListenerCallbacks = new HashSet<>();

    // == dependencies that are injected in 'afterSingletonsInstantiated' to avoid early
    // initialization
    private DestinationResolver<MessageChannel> binderAwareChannelResolver;

    private MessageHandlerMethodFactory messageHandlerMethodFactory;

    // == end dependencies
    private SpringIntegrationProperties springIntegrationProperties;

    private ConfigurableApplicationContext applicationContext;

    private BeanExpressionResolver resolver;

    private BeanExpressionContext expressionContext;

    private Set<StreamListenerSetupMethodOrchestratorAdaptor> streamListenerSetupMethodOrchestrators =
        new LinkedHashSet<>();

    private boolean streamListenerPresent;

    @Override
    public final void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = (ConfigurableApplicationContext)applicationContext;
        this.resolver = this.applicationContext.getBeanFactory().getBeanExpressionResolver();
        this.expressionContext = new BeanExpressionContext(this.applicationContext.getBeanFactory(), null);
    }

    @Override
    public final void afterSingletonsInstantiated() {
        if (!this.streamListenerPresent) {
            return;
        }
        this.injectAndPostProcessDependencies();
        EvaluationContext evaluationContext =
            IntegrationContextUtils.getEvaluationContext(this.applicationContext.getBeanFactory());
        for (Map.Entry<String, List<StreamListenerHandlerMethodMapping>> mappedBindingEntry : this.mappedListenerMethods
            .entrySet()) {
            ArrayList<
                DispatchingStreamListenerMessageHandlerExt.ConditionalStreamListenerMessageHandlerWrapper> handlers;
            handlers = new ArrayList<>();
            for (StreamListenerHandlerMethodMapping mapping : mappedBindingEntry.getValue()) {
                final InvocableHandlerMethod invocableHandlerMethod =
                    this.messageHandlerMethodFactory.createInvocableHandlerMethod(mapping.getTargetBean(),
                        checkProxy(mapping.getMethod(), mapping.getTargetBean()));
                StreamListenerMessageHandlerExt streamListenerMessageHandlerExt = new StreamListenerMessageHandlerExt(
                    invocableHandlerMethod, resolveExpressionAsBoolean(mapping.getCopyHeaders(), "copyHeaders"),
                    this.springIntegrationProperties.getMessageHandlerNotPropagatedHeaders());
                streamListenerMessageHandlerExt.setApplicationContext(this.applicationContext);
                streamListenerMessageHandlerExt.setBeanFactory(this.applicationContext.getBeanFactory());
                if (StringUtils.hasText(mapping.getDefaultOutputChannel())) {
                    streamListenerMessageHandlerExt.setOutputChannelName(mapping.getDefaultOutputChannel());
                }
                streamListenerMessageHandlerExt.afterPropertiesSet();
                if (StringUtils.hasText(mapping.getCondition())) {
                    String conditionAsString = resolveExpressionAsString(mapping.getCondition(), "condition");
                    Expression condition = SPEL_EXPRESSION_PARSER.parseExpression(conditionAsString);
                    handlers.add(
                        new DispatchingStreamListenerMessageHandlerExt.ConditionalStreamListenerMessageHandlerWrapper(
                            condition, streamListenerMessageHandlerExt));
                } else {
                    handlers.add(
                        new DispatchingStreamListenerMessageHandlerExt.ConditionalStreamListenerMessageHandlerWrapper(
                            null, streamListenerMessageHandlerExt));
                }
            }
            if (handlers.size() > 1) {
                for (DispatchingStreamListenerMessageHandlerExt.ConditionalStreamListenerMessageHandlerWrapper handler : handlers) {
                    Assert.isTrue(handler.isVoid(), StreamListenerErrorMessages.MULTIPLE_VALUE_RETURNING_METHODS);
                }
            }
            AbstractReplyProducingMessageHandler handler;

            if (handlers.size() > 1 || handlers.get(0).getCondition() != null) {
                handler = new DispatchingStreamListenerMessageHandlerExt(handlers, evaluationContext);
            } else {
                handler = handlers.get(0).getStreamListenerMessageHandler();
            }
            handler.setApplicationContext(this.applicationContext);
            handler.setChannelResolver(this.binderAwareChannelResolver);
            handler.afterPropertiesSet();
            this.applicationContext.getBeanFactory()
                .registerSingleton(handler.getClass().getSimpleName() + handler.hashCode(), handler);
            this.applicationContext.getBean(mappedBindingEntry.getKey(), SubscribableChannel.class).subscribe(handler);
        }
        this.mappedListenerMethods.clear();
    }

    @SneakyThrows
    @Override
    public final Object postProcessAfterInitialization(Object bean, final String beanName) throws BeansException {
        // 这里只处理增强的注册，根据自定义的配置新增一些streamListener
        Class<?> targetClass = AopUtils.isAopProxy(bean) ? AopUtils.getTargetClass(bean) : bean.getClass();

        if (bean.getClass().getName().equals(CONSUMER_COLLECTOR_CLASS)) {
            // 用相同的doConsume方法进行消费处理
            Method method = ReflectionUtils.findMethod(targetClass, "doConsume", Message.class);
            if (method != null) {
                ScsExtensionProperties scsExtensionProperties =
                    applicationContext.getBean("scsExtensionProperties", ScsExtensionProperties.class);

                if (ObjectUtils.isEmpty(scsExtensionProperties)
                    || CollectionUtils.isEmpty(scsExtensionProperties.getBindings())) {
                    log.warn(
                        "scs中，StreamListenerAnnotationAutoGenerateBeanPostProcessor # postProcessAfterInitialization 没有找到自定义的bindings信息");
                } else {
                    // 根据自定义配置中的input-bindings来循环生成@streamListener的信息，不需要hardcode
                    Map<String, ExtensionBindingProperties> extensionBindingProperties =
                        scsExtensionProperties.getBindings();
                    for (Map.Entry<String,
                        ExtensionBindingProperties> entry : extensionBindingProperties
                            .entrySet()) {
                        ExtensionBindingProperties binding = entry.getValue();
                        if (!StringUtils.hasLength(binding.getType())) {
                            throw new RuntimeException(
                                "bindings:[" + entry.getKey() + "] type is empty, choose input or output");
                        }
                        if (!checkBindingType(binding.getType())) {
                            throw new IllegalArgumentException("bindings:[" + entry.getKey() + "] - type:["
                                + binding.getType() + "] is illegal, choose input or output");
                        }
                        if (binding.getType().equalsIgnoreCase(ScsPlusConstant.BINDING_TYPE_INPUT)) {
                            // 如果是消费者-input，加入streamListener的声明
                            StreamListenerMeta streamListener = new StreamListenerMeta();
                            streamListener.setValue(entry.getKey());
                            this.streamListenerPresent = true;
                            this.streamListenerCallbacks.add(() -> {
                                this.doPostProcess(streamListener, method, bean);
                            });
                        }
                    }
                }
            }
        }
        return bean;
    }

    /**
     * Extension point, allowing subclasses to customize the {@link StreamListener} annotation detected by the
     * postprocessor.
     *
     * @param originalAnnotation
     *            the original annotation
     * @param annotatedMethod
     *            the method on which the annotation has been found
     * @return the postprocessed {@link StreamListener} annotation
     */
    protected StreamListenerMeta postProcessAnnotation(StreamListenerMeta originalAnnotation, Method annotatedMethod) {
        return originalAnnotation;
    }

    private void doPostProcess(StreamListenerMeta streamListener, Method method, Object bean) {
        streamListener = postProcessAnnotation(streamListener, method);
        Optional<StreamListenerSetupMethodOrchestratorAdaptor> orchestratorOptional;
        orchestratorOptional =
            this.streamListenerSetupMethodOrchestrators.stream().filter(t -> t.supports(method)).findFirst();
        Assert.isTrue(orchestratorOptional.isPresent(),
            "A matching StreamListenerSetupMethodOrchestrator must be present");
        StreamListenerSetupMethodOrchestratorAdaptor streamListenerSetupMethodOrchestrator = orchestratorOptional.get();
        streamListenerSetupMethodOrchestrator.orchestrateStreamListenerSetupMethod(streamListener, method, bean);
    }

    private Method checkProxy(Method methodArg, Object bean) {
        Method method = methodArg;
        if (AopUtils.isJdkDynamicProxy(bean)) {
            try {
                // Found a @StreamListener method on the target class for this JDK proxy
                // ->
                // is it also present on the proxy itself?
                method = bean.getClass().getMethod(method.getName(), method.getParameterTypes());
                Class<?>[] proxiedInterfaces = ((Advised)bean).getProxiedInterfaces();
                for (Class<?> iface : proxiedInterfaces) {
                    try {
                        method = iface.getMethod(method.getName(), method.getParameterTypes());
                        break;
                    } catch (NoSuchMethodException noMethod) {
                    }
                }
            } catch (SecurityException ex) {
                ReflectionUtils.handleReflectionException(ex);
            } catch (NoSuchMethodException ex) {
                throw new IllegalStateException(String.format(
                    "@StreamListener method '%s' found on bean target class '%s', "
                        + "but not found in any interface(s) for bean JDK proxy. Either "
                        + "pull the method up to an interface or switch to subclass (CGLIB) "
                        + "proxies by setting proxy-target-class/proxyTargetClass attribute to 'true'",
                    method.getName(), method.getDeclaringClass().getSimpleName()), ex);
            }
        }
        return method;
    }

    private String resolveExpressionAsString(String value, String property) {
        Object resolved = resolveExpression(value);
        if (resolved instanceof String) {
            return (String)resolved;
        } else {
            throw new IllegalStateException(
                "Resolved " + property + " to [" + resolved.getClass() + "] instead of String for [" + value + "]");
        }
    }

    private boolean resolveExpressionAsBoolean(String value, String property) {
        Object resolved = resolveExpression(value);
        if (resolved == null) {
            return false;
        } else if (resolved instanceof String) {
            return Boolean.parseBoolean((String)resolved);
        } else if (resolved instanceof Boolean) {
            return (Boolean)resolved;
        } else {
            throw new IllegalStateException("Resolved " + property + " to [" + resolved.getClass()
                + "] instead of String or Boolean for [" + value + "]");
        }
    }

    private String resolveExpression(String value) {
        String resolvedValue = this.applicationContext.getBeanFactory().resolveEmbeddedValue(value);
        if (resolvedValue.startsWith("#{") && value.endsWith("}")) {
            resolvedValue = (String)this.resolver.evaluate(resolvedValue, this.expressionContext);
        }
        return resolvedValue;
    }

    /**
     * This operations ensures that required dependencies are not accidentally injected early given that this bean is
     * BPP.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void injectAndPostProcessDependencies() {
        Collection<StreamListenerParameterAdapter> streamListenerParameterAdapters =
            this.applicationContext.getBeansOfType(StreamListenerParameterAdapter.class).values();
        Collection<StreamListenerResultAdapter> streamListenerResultAdapters =
            this.applicationContext.getBeansOfType(StreamListenerResultAdapter.class).values();
        this.binderAwareChannelResolver =
            this.applicationContext.getBean("binderAwareChannelResolver", DestinationResolver.class);
        this.messageHandlerMethodFactory = this.applicationContext.getBean(MessageHandlerMethodFactory.class);
        this.springIntegrationProperties = this.applicationContext.getBean(SpringIntegrationProperties.class);

        // 目前没有配置StreamListenerSetupMethodOrchestrator的Bean
        // this.streamListenerSetupMethodOrchestrators.addAll(this.applicationContext
        // .getBeansOfType(StreamListenerSetupMethodOrchestrator.class).values());

        // Default orchestrator for StreamListener method invocation is added last into
        // the LinkedHashSet.
        this.streamListenerSetupMethodOrchestrators.add(new DefaultStreamListenerSetupMethodOrchestrator(
            this.applicationContext, streamListenerParameterAdapters, streamListenerResultAdapters));

        this.streamListenerCallbacks.forEach(Runnable::run);
    }

    private static class StreamListenerHandlerMethodMapping {

        private final Object targetBean;

        private final Method method;

        private final String condition;

        private final String defaultOutputChannel;

        private final String copyHeaders;

        StreamListenerHandlerMethodMapping(Object targetBean, Method method, String condition,
            String defaultOutputChannel, String copyHeaders) {
            this.targetBean = targetBean;
            this.method = method;
            this.condition = condition;
            this.defaultOutputChannel = defaultOutputChannel;
            this.copyHeaders = copyHeaders;
        }

        Object getTargetBean() {
            return this.targetBean;
        }

        Method getMethod() {
            return this.method;
        }

        String getCondition() {
            return this.condition;
        }

        String getDefaultOutputChannel() {
            return this.defaultOutputChannel;
        }

        public String getCopyHeaders() {
            return this.copyHeaders;
        }

    }

    @SuppressWarnings("rawtypes")
    private final class DefaultStreamListenerSetupMethodOrchestrator
        implements StreamListenerSetupMethodOrchestratorAdaptor {

        private final ConfigurableApplicationContext applicationContext;

        private final Collection<StreamListenerParameterAdapter> streamListenerParameterAdapters;

        private final Collection<StreamListenerResultAdapter> streamListenerResultAdapters;

        private DefaultStreamListenerSetupMethodOrchestrator(ConfigurableApplicationContext applicationContext,
            Collection<StreamListenerParameterAdapter> streamListenerParameterAdapters,
            Collection<StreamListenerResultAdapter> streamListenerResultAdapters) {
            this.applicationContext = applicationContext;
            this.streamListenerParameterAdapters = streamListenerParameterAdapters;
            this.streamListenerResultAdapters = streamListenerResultAdapters;
        }

        @Override
        public void orchestrateStreamListenerSetupMethod(StreamListenerMeta streamListener, Method method,
            Object bean) {
            String methodAnnotatedInboundName = streamListener.getValue();

            String methodAnnotatedOutboundName = StreamListenerMethodUtils.getOutboundBindingTargetName(method);
            int inputAnnotationCount = StreamListenerMethodUtils.inputAnnotationCount(method);
            int outputAnnotationCount = StreamListenerMethodUtils.outputAnnotationCount(method);
            boolean isDeclarative =
                checkDeclarativeMethod(method, methodAnnotatedInboundName, methodAnnotatedOutboundName);
            StreamListenerMethodUtils.validateStreamListenerMethod(method, inputAnnotationCount, outputAnnotationCount,
                methodAnnotatedInboundName, methodAnnotatedOutboundName, isDeclarative, streamListener.getCondition());
            if (isDeclarative) {
                StreamListenerParameterAdapter[] toSlpaArray;
                toSlpaArray = new StreamListenerParameterAdapter[this.streamListenerParameterAdapters.size()];
                Object[] adaptedInboundArguments = adaptAndRetrieveInboundArguments(method, methodAnnotatedInboundName,
                    this.applicationContext, this.streamListenerParameterAdapters.toArray(toSlpaArray));
                invokeStreamListenerResultAdapter(method, bean, methodAnnotatedOutboundName, adaptedInboundArguments);
            } else {
                registerHandlerMethodOnListenedChannel(method, streamListener, bean);
            }
        }

        @Override
        public boolean supports(Method method) {
            // default catch all orchestrator
            return true;
        }

        @Override
        public void orchestrateStreamListenerSetupMethod(StreamListener streamListener, Method method, Object bean) {
            // 因接口定义的入参被修改为自定义的StreamListenerMeta,所以override一个空方法
        }

        @Override
        public Object[] adaptAndRetrieveInboundArguments(Method method, String inboundName,
            ApplicationContext applicationContext, StreamListenerParameterAdapter... streamListenerParameterAdapters) {
            // 因接口定义的入参被修改为自定义的StreamListenerMeta,所以override一个空方法
            return StreamListenerSetupMethodOrchestratorAdaptor.super.adaptAndRetrieveInboundArguments(method,
                inboundName, applicationContext, streamListenerParameterAdapters);
        }

        @SuppressWarnings("unchecked")
        private void invokeStreamListenerResultAdapter(Method method, Object bean, String outboundName,
            Object... arguments) {
            try {
                if (Void.TYPE.equals(method.getReturnType())) {
                    method.invoke(bean, arguments);
                } else {
                    Object result = method.invoke(bean, arguments);
                    if (!StringUtils.hasText(outboundName)) {
                        for (int parameterIndex = 0; parameterIndex < method.getParameterCount(); parameterIndex++) {
                            MethodParameter methodParameter = MethodParameter.forExecutable(method, parameterIndex);
                            if (methodParameter.hasParameterAnnotation(Output.class)) {
                                outboundName = methodParameter.getParameterAnnotation(Output.class).value();
                            }
                        }
                    }
                    Object targetBean = this.applicationContext.getBean(outboundName);
                    for (StreamListenerResultAdapter streamListenerResultAdapter : this.streamListenerResultAdapters) {
                        if (streamListenerResultAdapter.supports(result.getClass(), targetBean.getClass())) {
                            streamListenerResultAdapter.adapt(result, targetBean);
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                throw new BeanInitializationException("Cannot setup StreamListener for " + method, e);
            }
        }

        private void registerHandlerMethodOnListenedChannel(Method method, StreamListenerMeta streamListener,
            Object bean) {
            Assert.hasText(streamListener.getValue(), "The binding name cannot be null");
            if (!StringUtils.hasText(streamListener.getValue())) {
                throw new BeanInitializationException("A bound component name must be specified");
            }
            final String defaultOutputChannel = StreamListenerMethodUtils.getOutboundBindingTargetName(method);
            if (Void.TYPE.equals(method.getReturnType())) {
                Assert.isTrue(StringUtils.isEmpty(defaultOutputChannel),
                    "An output channel cannot be specified for a method that does not return a value");
            } else {
                Assert.isTrue(!StringUtils.isEmpty(defaultOutputChannel),
                    "An output channel must be specified for a method that can return a value");
            }
            StreamListenerMethodUtils.validateStreamListenerMessageHandler(method);
            StreamListenerAnnotationAutoGenerateBeanPostProcessor.this.mappedListenerMethods
                .add(streamListener.getValue(), new StreamListenerHandlerMethodMapping(bean, method,
                    streamListener.getCondition(), defaultOutputChannel, streamListener.getCopyHeaders()));
        }

        private boolean checkDeclarativeMethod(Method method, String methodAnnotatedInboundName,
            String methodAnnotatedOutboundName) {
            int methodArgumentsLength = method.getParameterCount();
            for (int parameterIndex = 0; parameterIndex < methodArgumentsLength; parameterIndex++) {
                MethodParameter methodParameter = MethodParameter.forExecutable(method, parameterIndex);
                if (methodParameter.hasParameterAnnotation(Input.class)) {
                    String inboundName =
                        (String)AnnotationUtils.getValue(methodParameter.getParameterAnnotation(Input.class));
                    Assert.isTrue(StringUtils.hasText(inboundName), StreamListenerErrorMessages.INVALID_INBOUND_NAME);
                    Assert.isTrue(isDeclarativeMethodParameter(inboundName, methodParameter),
                        StreamListenerErrorMessages.INVALID_DECLARATIVE_METHOD_PARAMETERS);
                    return true;
                } else if (methodParameter.hasParameterAnnotation(Output.class)) {
                    String outboundName =
                        (String)AnnotationUtils.getValue(methodParameter.getParameterAnnotation(Output.class));
                    Assert.isTrue(StringUtils.hasText(outboundName), StreamListenerErrorMessages.INVALID_OUTBOUND_NAME);
                    Assert.isTrue(isDeclarativeMethodParameter(outboundName, methodParameter),
                        StreamListenerErrorMessages.INVALID_DECLARATIVE_METHOD_PARAMETERS);
                    return true;
                } else if (StringUtils.hasText(methodAnnotatedOutboundName)) {
                    return isDeclarativeMethodParameter(methodAnnotatedOutboundName, methodParameter);
                } else if (StringUtils.hasText(methodAnnotatedInboundName)) {
                    return isDeclarativeMethodParameter(methodAnnotatedInboundName, methodParameter);
                }
            }
            return false;
        }

        /**
         * Determines if method parameters signify an imperative or declarative listener definition. <br>
         * Imperative - where handler method is invoked on each message by the handler infrastructure provided by the
         * framework <br>
         * Declarative - where handler is provided by the method itself. <br>
         * Declarative method parameter could either be {@link MessageChannel} or any other Object for which there is a
         * {@link StreamListenerParameterAdapter} (i.e., {@link reactor.core.publisher.Flux}). Declarative method is
         * invoked only once during initialization phase.
         *
         * @param targetBeanName
         *            name of the bean
         * @param methodParameter
         *            method parameter
         * @return {@code true} when the method parameter is declarative
         */
        @SuppressWarnings("unchecked")
        private boolean isDeclarativeMethodParameter(String targetBeanName, MethodParameter methodParameter) {
            boolean declarative = false;
            if (!methodParameter.getParameterType().isAssignableFrom(Object.class)
                && this.applicationContext.containsBean(targetBeanName)) {
                declarative = MessageChannel.class.isAssignableFrom(methodParameter.getParameterType());
                if (!declarative) {
                    Class<?> targetBeanClass = this.applicationContext.getType(targetBeanName);
                    declarative = this.streamListenerParameterAdapters.stream()
                        .anyMatch(slpa -> slpa.supports(targetBeanClass, methodParameter));
                }
            }
            return declarative;
        }

    }

    private boolean checkBindingType(String type) {
        return ScsPlusConstant.BINDING_TYPE_INPUT.equalsIgnoreCase(type)
            || ScsPlusConstant.BINDING_TYPE_OUTPUT.equalsIgnoreCase(type);
    }

}
