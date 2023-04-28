package com.demo.scs.core.sourcecode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.springframework.cloud.stream.binding.StreamListenerMessageHandler;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.integration.handler.AbstractReplyProducingMessageHandler;
import org.springframework.messaging.Message;
import org.springframework.util.Assert;

import lombok.extern.slf4j.Slf4j;

/**
 * @Author: Hu Xin
 * @Date: 2023/1/15 19:28
 * @Desc:
 **/
@Slf4j
public class DispatchingCustomizeStreamListenerMessageHandlerExt extends AbstractReplyProducingMessageHandler {

    private final List<ConditionalStreamListenerMessageHandlerWrapper> handlerMethods;

    private final boolean evaluateExpressions;

    private final EvaluationContext evaluationContext;

    DispatchingCustomizeStreamListenerMessageHandlerExt(
        Collection<ConditionalStreamListenerMessageHandlerWrapper> handlerMethods,
        EvaluationContext evaluationContext) {
        Assert.notEmpty(handlerMethods, "'handlerMethods' cannot be empty");
        this.handlerMethods = Collections.unmodifiableList(new ArrayList<>(handlerMethods));
        boolean evaluateExpressions = false;
        for (ConditionalStreamListenerMessageHandlerWrapper handlerMethod : handlerMethods) {
            if (handlerMethod.getCondition() != null) {
                evaluateExpressions = true;
                break;
            }
        }
        this.evaluateExpressions = evaluateExpressions;
        if (evaluateExpressions) {
            Assert.notNull(evaluationContext, "'evaluationContext' cannot be null if conditions are used");
        }
        this.evaluationContext = evaluationContext;
    }

    @Override
    protected boolean shouldCopyRequestHeaders() {
        return false;
    }

    @Override
    protected Object handleRequestMessage(Message<?> requestMessage) {
        List<ConditionalStreamListenerMessageHandlerWrapper> matchingHandlers =
            this.evaluateExpressions ? findMatchingHandlers(requestMessage) : this.handlerMethods;
        if (matchingHandlers.size() == 0) {
            if (log.isWarnEnabled()) {
                log.warn("Cannot find a @StreamListener matching for message with id: "
                    + requestMessage.getHeaders().getId());
            }
            return null;
        } else if (matchingHandlers.size() > 1) {
            for (ConditionalStreamListenerMessageHandlerWrapper matchingMethod : matchingHandlers) {
                matchingMethod.getStreamListenerMessageHandler().handleMessage(requestMessage);
            }
            return null;
        } else {
            final ConditionalStreamListenerMessageHandlerWrapper singleMatchingHandler = matchingHandlers.get(0);
            singleMatchingHandler.getStreamListenerMessageHandler().handleMessage(requestMessage);
            return null;
        }
    }

    private List<ConditionalStreamListenerMessageHandlerWrapper> findMatchingHandlers(Message<?> message) {
        ArrayList<ConditionalStreamListenerMessageHandlerWrapper> matchingMethods = new ArrayList<>();
        for (ConditionalStreamListenerMessageHandlerWrapper wrapper : this.handlerMethods) {
            if (wrapper.getCondition() == null) {
                matchingMethods.add(wrapper);
            } else {
                boolean conditionMetOnMessage =
                    wrapper.getCondition().getValue(this.evaluationContext, message, Boolean.class);
                if (conditionMetOnMessage) {
                    matchingMethods.add(wrapper);
                }
            }
        }
        return matchingMethods;
    }

    static class ConditionalStreamListenerMessageHandlerWrapper {

        private final Expression condition;

        private final StreamListenerMessageHandler streamListenerMessageHandler;

        ConditionalStreamListenerMessageHandlerWrapper(Expression condition,
            StreamListenerMessageHandler streamListenerMessageHandler) {
            Assert.notNull(streamListenerMessageHandler, "the message handler cannot be null");
            Assert.isTrue(condition == null || streamListenerMessageHandler.isVoid(),
                "cannot specify a condition and a return value at the same time");
            this.condition = condition;
            this.streamListenerMessageHandler = streamListenerMessageHandler;
        }

        public Expression getCondition() {
            return this.condition;
        }

        public boolean isVoid() {
            return this.streamListenerMessageHandler.isVoid();
        }

        public StreamListenerMessageHandler getStreamListenerMessageHandler() {
            return this.streamListenerMessageHandler;
        }

    }
}
