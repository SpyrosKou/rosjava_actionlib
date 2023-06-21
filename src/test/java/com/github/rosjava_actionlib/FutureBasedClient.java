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
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.ros.namespace.GraphName;
import org.ros.node.AbstractNodeMain;
import org.ros.node.ConnectedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.List;
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
        this.actionClient = new ActionClient<>(connectedNode, "/fibonacci", FibonacciActionGoal._TYPE, FibonacciActionFeedback._TYPE, FibonacciActionResult._TYPE);
        // Attach listener for the callbacks
        this.actionClient.addListener(this);
        this.connectionCountDownLatch.countDown();
    }

    /**
     * @param message
     */
    @Override
    public final void resultReceived(final FibonacciActionResult message) {
        final FibonacciResult result = message.getResult();
        int[] sequence = result.getSequence();
        int i;


        LOGGER.trace("Got Fibonacci result sequence: ");
        for (i = 0; i < sequence.length; i++)
            LOGGER.trace(Integer.toString(sequence[i]) + " ");
        LOGGER.trace("");
    }

    /**
     * @param message
     */
    @Override
    public final void feedbackReceived(final FibonacciActionFeedback message) {
        final FibonacciFeedback result = message.getFeedback();
        int[] sequence = result.getSequence();
        int i;

        LOGGER.trace("Feedback from Fibonacci server: ");
        for (i = 0; i < sequence.length; i++)
            LOGGER.trace(Integer.toString(sequence[i]) + " ");
        LOGGER.trace("\n");
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


    /**
     * @param msec
     */
    private final void sleep(final long msec) {
        try {
            Thread.sleep(msec);
        } catch (InterruptedException ex) {
        }
    }

    final ActionClient<FibonacciActionGoal,FibonacciActionFeedback,FibonacciActionResult> getActionClient() {
        return this.actionClient;
    }
}
