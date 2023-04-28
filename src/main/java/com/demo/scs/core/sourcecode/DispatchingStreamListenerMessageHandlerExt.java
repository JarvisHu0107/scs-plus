/*
 * Copyright 2017-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.demo.scs.core.sourcecode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.integration.handler.AbstractReplyProducingMessageHandler;
import org.springframework.messaging.Message;
import org.springframework.util.Assert;

import lombok.extern.slf4j.Slf4j;

/**
 * An {@link AbstractReplyProducingMessageHandler} that delegates to a collection of internal
 * {@link ConditionalStreamListenerMessageHandlerWrapper} instances, executing the ones that match the given expression.
 *
 * @author Marius Bogoevici
 * @since 1.2
 */
@Slf4j
public class DispatchingStreamListenerMessageHandlerExt extends AbstractReplyProducingMessageHandler {

    private final List<ConditionalStreamListenerMessageHandlerWrapper> handlerMethods;

    private final boolean evaluateExpressions;

    private final EvaluationContext evaluationContext;

    public DispatchingStreamListenerMessageHandlerExt(
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

    public static class ConditionalStreamListenerMessageHandlerWrapper {

        private final Expression condition;

        private final StreamListenerMessageHandlerExt streamListenerMessageHandlerExt;

        public ConditionalStreamListenerMessageHandlerWrapper(Expression condition,
            StreamListenerMessageHandlerExt streamListenerMessageHandlerExt) {
            Assert.notNull(streamListenerMessageHandlerExt, "the message handler cannot be null");
            Assert.isTrue(condition == null || streamListenerMessageHandlerExt.isVoid(),
                "cannot specify a condition and a return value at the same time");
            this.condition = condition;
            this.streamListenerMessageHandlerExt = streamListenerMessageHandlerExt;
        }

        public Expression getCondition() {
            return this.condition;
        }

        public boolean isVoid() {
            return this.streamListenerMessageHandlerExt.isVoid();
        }

        public StreamListenerMessageHandlerExt getStreamListenerMessageHandler() {
            return this.streamListenerMessageHandlerExt;
        }

    }

}
