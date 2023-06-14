/**
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
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.ros.message.Duration;
import org.ros.namespace.GraphName;
import org.ros.node.AbstractNodeMain;
import org.ros.node.ConnectedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Demonstrates the {@link ActionFuture} usage
 *
 * @author Spyros Koukas
 */
class FutureBasedClient extends AbstractNodeMain implements ActionClientListener<FibonacciActionFeedback, FibonacciActionResult> {
private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private ActionClient actionClient = null;

    private volatile boolean isStarted = false;


    @Override
    public GraphName getDefaultNodeName() {
        return GraphName.of("fibonacci_future_client");
    }


    /**
     * @param seconds the maximum time to wait before the client is started
     *
     * @return
     */
    public final boolean waitForServerConnection(final double seconds) {
        final Stopwatch stopwatch = Stopwatch.createStarted();
        while (!this.isStarted && stopwatch.elapsed(TimeUnit.SECONDS) <= seconds) {
            this.sleep(100);
        }
        if (this.isStarted) {
            final Duration serverTimeout = new Duration(Math.max(0.1, seconds - stopwatch.elapsed(TimeUnit.SECONDS)));
            boolean serverStarted = false;
            LOGGER.trace("Waiting for action server to start...");
            serverStarted = this.actionClient.waitForActionServerToStart(serverTimeout);
            if (serverStarted) {
                LOGGER.trace("Action server started.\n");
                return true;
            } else {
                LOGGER.trace("No actionlib server found after waiting for " + serverTimeout.totalNsecs() / 1e9 + " seconds!");
                return false;
            }
        } else {
            return false;
        }

    }

    /**
     * @param order
     *
     * @return a future of the fibonacci
     */
    public final ActionFuture<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult> invoke(final int order) {
        // Create Fibonacci goal message
        final FibonacciActionGoal goalMessage = (FibonacciActionGoal) actionClient.newGoalMessage();
        final FibonacciGoal fibonacciGoal = goalMessage.getGoal();
        // set Fibonacci parameter
        fibonacciGoal.setOrder(order);
        LOGGER.trace("Sending goal for order:" + order);
        final ActionFuture<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult> result = actionClient.sendGoal(goalMessage);
        return result;
    }


    @Override
    public void onStart(final ConnectedNode node) {
        this.actionClient = new ActionClient<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult>(node, "/fibonacci", FibonacciActionGoal._TYPE, FibonacciActionFeedback._TYPE, FibonacciActionResult._TYPE);
        this.isStarted = true;
        // Attach listener for the callbacks
        this.actionClient.addListener(this);

    }

    /**
     * @param message
     */
    @Override
    public void resultReceived(final FibonacciActionResult message) {
        FibonacciResult result = message.getResult();
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
    public void feedbackReceived(final FibonacciActionFeedback message) {
        FibonacciFeedback result = message.getFeedback();
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
    public void statusReceived(final GoalStatusArray status) {
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
