/**
 * Copyright 2015 Ekumen www.ekumenlabs.com
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
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Class to test the actionlib client.
 *
 * @author Spyros Koukas
 */
class SimpleClient extends AbstractNodeMain implements ActionClientListener<FibonacciActionFeedback, FibonacciActionResult> {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private ActionClient actionClient = null;
    private volatile AtomicBoolean resultReceived = new AtomicBoolean(false);
    private final AtomicBoolean isStarted = new AtomicBoolean(false);
    private final CountDownLatch countDownLatch = new CountDownLatch(1);


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
        boolean debugShown=false;
        final Stopwatch stopwatch = Stopwatch.createStarted();
        boolean result = this.waitForClientStart(timeout, timeUnit);
        if (!result &&!debugShown&& LOGGER.isDebugEnabled()) {
            debugShown=true;
            LOGGER.debug("waitForClientStart did not connect after:" + stopwatch.elapsed(timeUnit) + " " + timeUnit.name() + " while timeout=" + timeout + " " + timeUnit.name());
        }
        result = result && this.actionClient.waitForServerPublishers(Math.max(timeout - stopwatch.elapsed(timeUnit), 0), timeUnit);
        if (!result &&!debugShown&& LOGGER.isDebugEnabled()) {
            debugShown=true;
            LOGGER.debug("waitForServerPublishers did not connect after:" + stopwatch.elapsed(timeUnit) + " " + timeUnit.name() + " while timeout=" + timeout + " " + timeUnit.name());
        }
        result = result && this.actionClient.waitForClientSubscribers(Math.max(timeout - stopwatch.elapsed(timeUnit), 0), timeUnit);
        if (!result &&!debugShown&& LOGGER.isDebugEnabled()) {
            debugShown=true;
            LOGGER.debug("waitForClientSubscribers did not connect after:" + stopwatch.elapsed(timeUnit) + " " + timeUnit.name() + " while timeout=" + timeout + " " + timeUnit.name());
        }
        return result;
    }


    /**
     * Demonstrates using the client in a node
     * This is a rather simple set of sequential calls.
     */
    public void startTasks() {
        if (!this.waitForServerConnection(20, TimeUnit.SECONDS)) {
            System.exit(1);
        }

        // Create Fibonacci goal message
        final FibonacciActionGoal goalMessage = (FibonacciActionGoal) actionClient.newGoalMessage();
        final FibonacciGoal fibonacciGoal = goalMessage.getGoal();
        // set Fibonacci parameter
        fibonacciGoal.setOrder(3);
        LOGGER.trace("Sending goal...");
        actionClient.sendGoal(goalMessage);

        final GoalID gid1 = goalMessage.getGoalId();
        LOGGER.trace("Sent goal with ID: " + gid1.getId());
        LOGGER.trace("Waiting for goal to complete...");
        while (actionClient.getGoalState() != ClientState.DONE) {
            sleep(1);
        }
        LOGGER.trace("Goal completed!\n");

        LOGGER.trace("Sending a new goal...");
        actionClient.sendGoal(goalMessage);
        final GoalID gid2 = goalMessage.getGoalId();
        LOGGER.trace("Sent goal with ID: " + gid2.getId());
        LOGGER.trace("Cancelling this goal...");
        actionClient.sendCancel(gid2);
        while (actionClient.getGoalState() != ClientState.DONE) {
            sleep(1);
        }
        LOGGER.trace("Goal cancelled successfully.\n");
        LOGGER.trace("Bye!");

    }


    @Override
    public void onStart(final ConnectedNode connectedNode) {
        this.actionClient = new ActionClient<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult>(connectedNode, "/fibonacci", FibonacciActionGoal._TYPE, FibonacciActionFeedback._TYPE, FibonacciActionResult._TYPE);
        this.isStarted.set(true);
        // Attach listener for the callbacks
        this.actionClient.addListener(this);
        this.countDownLatch.countDown();

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

            }
        }
        return this.isStarted.get();
    }


    /**
     * @param message
     */
    @Override
    public void resultReceived(final FibonacciActionResult message) {
        FibonacciResult result = message.getResult();
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
    public void feedbackReceived(final FibonacciActionFeedback message) {
        FibonacciFeedback result = message.getFeedback();
        int[] sequence = result.getSequence();
        int i;

        LOGGER.trace("Feedback from Fibonacci server: ");
        for (i = 0; i < sequence.length; i++)
            LOGGER.trace(Integer.toString(sequence[i]) + " ");
        LOGGER.trace("\n");
    }

    @Override
    public final void statusReceived(final GoalStatusArray status) {
        if (LOGGER.isInfoEnabled()) {
            final List<GoalStatus> statusList = status.getStatusList();
            for (GoalStatus gs : statusList) {
                LOGGER.info("GoalID: " + gs.getGoalId().getId() + " -- GoalStatus: " + gs.getStatus() + " -- " + gs.getText());
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
