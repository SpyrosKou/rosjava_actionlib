/**
 * Copyright 2015 Ekumen www.ekumenlabs.com
 * Copyright 2020 Spyros Koukas
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.rosjava_actionlib;

import actionlib_msgs.GoalStatus;
import actionlib_msgs.GoalStatusArray;
import actionlib_tutorials.*;
import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.Runnables;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.ros.namespace.GraphName;
import org.ros.node.AbstractNodeMain;
import org.ros.node.ConnectedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Demonstrates the {@link ActionFuture} usage
 *
 * @author Spyros Koukas
 */
final class FutureBasedClient extends AbstractNodeMain implements ActionClientListener<FibonacciActionFeedback, FibonacciActionResult> {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final int MAX_PRINT_SEQUENCE_ELEMENTS = 100;

    final ActionClient<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult> getActionClient() {
        return this.actionClient;
    }

    private ActionClient<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult> actionClient = null;


    @Override
    public GraphName getDefaultNodeName() {
        return GraphName.of("fibonacci_future_client");
    }

    private final CountDownLatch connectionCountDownLatch = new CountDownLatch(1);
    private final AtomicBoolean connectedToServerCache = new AtomicBoolean(false);

    public final boolean waitForServerConnection(final long timeout, final TimeUnit timeUnit) {
        if (this.connectedToServerCache.get()) {
            return true;
        } else {
            final Stopwatch stopwatch = Stopwatch.createStarted();

            LOGGER.trace("Waiting for action server to start...");
            try {
                final boolean nodeStarted = this.connectionCountDownLatch.await(Math.max(0, timeout - stopwatch.elapsed(timeUnit)), timeUnit);
                final boolean clientStarted = nodeStarted && this.actionClient.waitForActionServerToStart(Math.max(0, timeout - stopwatch.elapsed(timeUnit)), timeUnit);
                final boolean serverConnection = clientStarted && this.actionClient.waitForServerConnection(Math.max(0, timeout - stopwatch.elapsed(timeUnit)), timeUnit);
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Action client started:[" + clientStarted + "] serverConnected:[" + serverConnection + "] nodeStarted:[" + nodeStarted + "]");
                }
                if (serverConnection) {
                    this.connectedToServerCache.compareAndSet(false, true);
                }
                return serverConnection;
            } catch (final Exception exception) {
                LOGGER.error(ExceptionUtils.getStackTrace(exception));
                return false;
            }
        }

    }

    /**
     * @param order
     * @return a future of the fibonacci
     */
    public final ActionFuture<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult> invoke(
            final int order) {
        // Create Fibonacci goal message
        final FibonacciActionGoal goalMessage = actionClient.newGoalMessage();
        final FibonacciGoal fibonacciGoal = goalMessage.getGoal();
        // set Fibonacci parameter
        fibonacciGoal.setOrder(order);
        LOGGER.trace("Sending goal for order:" + order);
        final ActionFuture<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult> result = actionClient.sendGoal(goalMessage);
        return result;
    }


    @Override
    public final void onStart(final ConnectedNode connectedNode) {
        this.actionClient = new ActionClient<>(connectedNode, "/fibonacci", FibonacciActionGoal._TYPE, FibonacciActionFeedback._TYPE, FibonacciActionResult._TYPE,this::getOnConnection);
        // Attach listener for the callbacks
        this.actionClient.addListener(this);
        this.connectionCountDownLatch.countDown();
    }

    /**
     * @param message
     */
    @Override
    public final void resultReceived(final FibonacciActionResult message) {

        if(LOGGER.isInfoEnabled()){
            LOGGER.info("Result Received, size:"+message.getResult().getSequence().length);
        }
        if (LOGGER.isTraceEnabled()) {

            final FibonacciResult result = message.getResult();
            int[] sequence = result.getSequence();
            final StringJoiner stringJoiner = new StringJoiner(",", "Finonacci sequence:{", "}");
            for (int i = 0; i < Math.min(sequence.length, MAX_PRINT_SEQUENCE_ELEMENTS); i++) {
                stringJoiner.add(sequence[i] + " ");
            }
            if(sequence.length> MAX_PRINT_SEQUENCE_ELEMENTS){
                stringJoiner.add("Showing only first 100 elements");
            }
            LOGGER.trace(stringJoiner.toString());
        }


    }

    /**
     * @param message
     */
    @Override
    public final void feedbackReceived(final FibonacciActionFeedback message) {
        if(LOGGER.isInfoEnabled()){
            LOGGER.info("Result Received, size:"+message.getFeedback().getSequence().length);
        }
        if (LOGGER.isTraceEnabled()) {
            final FibonacciFeedback messageFeedback = message.getFeedback();
            int[] sequence = messageFeedback.getSequence();
            final StringJoiner stringJoiner = new StringJoiner(",", "Feedback Fibonacci sequence:{", "}");
            for (int i = 0; i < Math.min(sequence.length, MAX_PRINT_SEQUENCE_ELEMENTS); i++) {
                stringJoiner.add(sequence[i] + " ");
            }
            if(sequence.length> MAX_PRINT_SEQUENCE_ELEMENTS){
                stringJoiner.add("Showing only first 100 elements");
            }
            LOGGER.trace(stringJoiner.toString());
        }
    }

    /**
     * @param status The status message received from the server.
     */
    @Override
    public final void statusReceived(final GoalStatusArray status) {
        if (LOGGER.isInfoEnabled()) {
            final List<GoalStatus> statusList = status.getStatusList();
            for (final GoalStatus goalStatus : statusList) {
                LOGGER.info("GoalID: " + goalStatus.getGoalId().getId() + " GoalStatus: " + goalStatus.getStatus() + " - " + goalStatus.getText());
            }

        }
    }
    public final void setOnConnection(final Runnable onConnection) {
        Objects.requireNonNull(onConnection);
        this.onConnection = onConnection;
    }
    private final Runnable getOnConnection(){
        return this.onConnection;
    }
    private Runnable onConnection = Runnables::doNothing;
}
