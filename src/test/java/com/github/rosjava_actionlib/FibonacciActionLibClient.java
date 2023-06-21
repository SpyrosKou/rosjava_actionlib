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


import actionlib_msgs.GoalID;
import actionlib_msgs.GoalStatus;
import actionlib_msgs.GoalStatusArray;
import actionlib_tutorials.*;
import com.google.common.base.Stopwatch;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.ros.namespace.GraphName;
import org.ros.node.AbstractNodeMain;
import org.ros.node.ConnectedNode;
import org.ros.node.topic.Subscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * Class to test the actionlib client.
 *
 * @author Spyros Koukas
 */
final class FibonacciActionLibClient extends AbstractNodeMain implements ActionClientListener<FibonacciActionFeedback, FibonacciActionResult> {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    public ActionClient<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult> getActionClient() {
        return actionClient;
    }

    private ActionClient<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult> actionClient = null;
    private Subscriber<FibonacciActionResult> actionResultSubscriber;
    private volatile AtomicBoolean resultReceived = new AtomicBoolean(false);
    private final AtomicBoolean isStarted = new AtomicBoolean(false);
    private final CountDownLatch countDownLatch = new CountDownLatch(1);

    public final Runnable getOnConnection() {
        return onConnection;
    }

    public final void setOnConnection(final Runnable onConnection) {
        Objects.requireNonNull(onConnection);
        this.onConnection = onConnection;
    }

    private Runnable onConnection = () -> {
    };

    @Override
    public GraphName getDefaultNodeName() {
        return GraphName.of("fibonacci_test_client");
    }


    /**
     * @param timeout  the maximum time to wait before the client is started
     * @param timeUnit the unit of time
     * @return
     */
    public final boolean waitForServerConnection(final long timeout, final TimeUnit timeUnit) {
        final Stopwatch stopwatch = Stopwatch.createStarted();
        final boolean clientStarted = this.waitForClientStart(timeout, timeUnit);
        return clientStarted && this.actionClient.waitForServerConnection(stopwatch.elapsed(timeUnit) - timeout, timeUnit);

    }



    /**
     * Demonstrates using the client in a node
     * This is a rather simple set of sequential calls.
     */
    public CountDownLatch callNormal() {

        if (!this.waitForServerConnection(20, TimeUnit.SECONDS)) {
            throw new RuntimeException("Did not connect on time");
        }

        final CountDownLatch resultReceived = new CountDownLatch(1);
        // Create Fibonacci goal message
        final FibonacciActionGoal goalMessage = this.actionClient.newGoalMessage();
        final FibonacciGoal fibonacciGoal = goalMessage.getGoal();
        // set Fibonacci parameter
        fibonacciGoal.setOrder(3);
        final ActionClientResultListener<FibonacciActionResult> resultListener = fibonacciActionResult -> resultReceived.countDown();
        this.actionClient.addListener(resultListener);
        this.actionClient.sendGoal(goalMessage);


        return resultReceived;
    }


    /**
     * Demonstrates using the client in a node
     * This is a rather simple set of sequential calls.
     */
    public CountDownLatch callCancelled() {
        final CountDownLatch startTasksCountDown = new CountDownLatch(1);
        if (!this.waitForServerConnection(20, TimeUnit.SECONDS)) {
            throw new RuntimeException("Did not connect on time");
        }
        // Create Fibonacci goal message
        final FibonacciActionGoal goalMessage = actionClient.newGoalMessage();
        final FibonacciGoal fibonacciGoal = goalMessage.getGoal();
        // set Fibonacci parameter
        fibonacciGoal.setOrder(3);
        LOGGER.trace("Sending a new goal...");
        actionClient.sendGoal(goalMessage);
        final GoalID gid2 = goalMessage.getGoalId();
        LOGGER.trace("Sent goal with ID: " + gid2.getId());
        LOGGER.trace("Cancelling this goal...");
        actionClient.sendCancel(gid2);

        LOGGER.trace("Goal cancelled successfully.\n");
        startTasksCountDown.countDown();
        return startTasksCountDown;
    }


    @Override
    public final void onStart(final ConnectedNode connectedNode) {
        this.actionClient = new ActionClient<>(connectedNode, "/fibonacci", FibonacciActionGoal._TYPE, FibonacciActionFeedback._TYPE, FibonacciActionResult._TYPE, this.getOnConnection());

        this.isStarted.set(true);
        // Attach listener for the callbacks
        this.actionClient.addListener(this);
        this.countDownLatch.countDown();
        this.actionResultSubscriber = connectedNode.newSubscriber(this.actionClient.getResultTopicName(), FibonacciActionResult._TYPE);
        this.actionResultSubscriber.addMessageListener(resultReceived ->
                        LOGGER.debug("New Results:" + resultReceived)
                , 100);

    }

    /**
     * Wait for the client node to start.
     *
     * @param timeout
     * @param timeUnit
     * @return true if the client has started, false
     */
    public final boolean waitForClientStart(final long timeout, final TimeUnit timeUnit) {
        final Stopwatch stopwatch = Stopwatch.createStarted();
        while (!this.isStarted.get()) {
            try {
                final boolean result = this.countDownLatch.await(Math.max(0, timeout - stopwatch.elapsed(timeUnit)), timeUnit);
                return result;
            } catch (final Exception exception) {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Interrupted:" + ExceptionUtils.getStackTrace(exception));
                }
            }
        }
        return this.isStarted.get();
    }


    /**
     * @param message
     */
    @Override
    public void resultReceived(final FibonacciActionResult message) {
        final FibonacciResult result = message.getResult();
        int[] sequence = result.getSequence();
        int i;

        this.resultReceived.set(true);
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

}
