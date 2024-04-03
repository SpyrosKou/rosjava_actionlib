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

/**
 * Demonstrates the {@link ActionFuture} usage
 *
 * @author Spyros Koukas
 */
final class FutureBasedClientNode extends AbstractNodeMain implements ActionClientListener<FibonacciActionFeedback, FibonacciActionResult> {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final int MAX_PRINT_SEQUENCE_ELEMENTS = 100;

    final ActionClient<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult> getActionClient() {
        return this.actionClient;
    }

    private ActionClient<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult> actionClient = null;


    @Override
    public GraphName getDefaultNodeName() {
        return GraphName.of(FibonacciGraphNames.CLIENT_NODE_GRAPH_NAME);
    }

    private final CountDownLatch onStartCountDownLatch = new CountDownLatch(1);


    public final boolean waitForStart(final long timeout, final TimeUnit timeUnit) {

        try {
            return this.onStartCountDownLatch.await(timeout, timeUnit);

        } catch (final Exception e) {
            LOGGER.error(ExceptionUtils.getStackTrace(e));
        }
        return false;

    }

    /**
     * Will first {@link FutureBasedClientNode#waitForStart(long, TimeUnit)}
     *
     * @param timeout
     * @param timeUnit
     * @return
     */
    public final boolean waitForClientStartAndServerConnection(final long timeout, final TimeUnit timeUnit) {

        final Stopwatch stopwatch = Stopwatch.createStarted();

        LOGGER.trace("Waiting for action server to start...");
        try {

            final boolean nodeStarted = this.waitForStart(Math.max(0, timeout - stopwatch.elapsed(timeUnit)), timeUnit);
            if (nodeStarted) {
                final boolean serverConnection = this.actionClient.waitForServerConnection(Math.max(0, timeout - stopwatch.elapsed(timeUnit)), timeUnit);
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Action client serverConnected:[" + serverConnection + "] nodeStarted:[" + nodeStarted + "]");
                }

                return serverConnection;
            } else {
                return false;
            }
        } catch (final InterruptedException interruptedException) {
                LOGGER.error(ExceptionUtils.getStackTrace(interruptedException));
                throw interruptedException;
            }catch (final Exception exception) {
                LOGGER.error(ExceptionUtils.getStackTrace(exception));
                return false;
            }
        }


    /**
     * @param order
     * @return a future of the fibonacci
     */
    public final ActionFuture<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult> invoke(final int order) {
        // Create Fibonacci goal message
        final FibonacciActionGoal goalMessage = this.actionClient.newGoalMessage();
        final FibonacciGoal fibonacciGoal = goalMessage.getGoal();
        // set Fibonacci parameter
        fibonacciGoal.setOrder(order);
        LOGGER.trace("Sending goal for order:" + order);
        final ActionFuture<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult> result = actionClient.sendGoal(goalMessage);
        return result;
    }


    @Override
    public final void onStart(final ConnectedNode connectedNode) {
        this.actionClient = new ActionClient<>(connectedNode, FibonacciGraphNames.ACTION_GRAPH_NAME, FibonacciActionGoal._TYPE, FibonacciActionFeedback._TYPE, FibonacciActionResult._TYPE, this::getOnConnection);
        // Attach listener for the callbacks
        this.actionClient.addActionClientListener(this);

        this.onStartCountDownLatch.countDown();
        LOGGER.trace("Node started. Client active:" + this.actionClient.isActive() + " goalState:" + this.actionClient.getGoalState());
    }

    /**
     * @param message
     */
    @Override
    public final void resultReceived(final FibonacciActionResult message) {

        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Result Received, size:" + message.getResult().getSequence().length);
        }
        if (LOGGER.isTraceEnabled()) {

            final FibonacciResult result = message.getResult();
            int[] sequence = result.getSequence();
            final StringJoiner stringJoiner = new StringJoiner(",", "Finonacci sequence:{", "}");
            for (int i = 0; i < Math.min(sequence.length, MAX_PRINT_SEQUENCE_ELEMENTS); i++) {
                stringJoiner.add(sequence[i] + " ");
            }
            if (sequence.length > MAX_PRINT_SEQUENCE_ELEMENTS) {
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
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Result Received, size:" + message.getFeedback().getSequence().length);
        }
        if (LOGGER.isTraceEnabled()) {
            final FibonacciFeedback messageFeedback = message.getFeedback();
            int[] sequence = messageFeedback.getSequence();
            final StringJoiner stringJoiner = new StringJoiner(",", "Feedback Fibonacci sequence:{", "}");
            for (int i = 0; i < Math.min(sequence.length, MAX_PRINT_SEQUENCE_ELEMENTS); i++) {
                stringJoiner.add(sequence[i] + " ");
            }
            if (sequence.length > MAX_PRINT_SEQUENCE_ELEMENTS) {
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
            final GoalStatusToString goalStatusToString = new GoalStatusToString();
            final List<GoalStatus> statusList = status.getStatusList();
            for (final GoalStatus goalStatus : statusList) {
                LOGGER.info("GoalID: " + goalStatus.getGoalId().getId() + " GoalStatus: " + goalStatus.getStatus() + " - " + goalStatus.getText() + " GoalStatus:" + goalStatusToString.getStatus(goalStatus.getStatus()));
            }

        }
    }

    public final void setOnConnection(final Runnable onConnection) {
        Objects.requireNonNull(onConnection);
        this.onConnection = onConnection;
    }

    private final Runnable getOnConnection() {
        return this.onConnection;
    }

    private Runnable onConnection = Runnables::doNothing;


}
