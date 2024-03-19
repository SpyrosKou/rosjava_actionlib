/**
 * Copyright 2019 Spyros Koukas
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.StringJoiner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;



class ActionLibClientFeedbackListenerNode extends AbstractNodeMain implements ActionClientListener<FibonacciActionFeedback, FibonacciActionResult> {
    private final GoalStatusToString goalStatusToString = new GoalStatusToString();


    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final CountDownLatch connectCountDownLatch = new CountDownLatch(1);

    private ActionClient actionClient = null;


    @Override
    public GraphName getDefaultNodeName() {
        return GraphName.of(FibonacciGraphNames.CLIENT_NODE_GRAPH_NAME);
    }


    public final boolean waitForStart(final long timeout, final TimeUnit timeUnit) {
        Boolean nodeConnected = null;
        final Stopwatch stopwatch = Stopwatch.createStarted();
        while (nodeConnected == null) {
            try {
                nodeConnected = this.connectCountDownLatch.await(timeout, timeUnit);
                return nodeConnected;
            } catch (final InterruptedException ie) {
                LOGGER.error(ExceptionUtils.getStackTrace(ie));

            } catch (final Exception e) {
                LOGGER.error(ExceptionUtils.getStackTrace(e));
            }
        }

        if (nodeConnected != null && nodeConnected) {

            final boolean serverStarted = this.actionClient.waitForServerConnection(timeout - stopwatch.elapsed(TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS);
            LOGGER.trace("Action server started.\n");
            return serverStarted;

        } else {
            LOGGER.trace("No actionlib server found after waiting for " + stopwatch.elapsed(TimeUnit.MILLISECONDS) + " milliseconds!");
            return false;
        }

    }

    /**
     * @deprecated Legacy
     * Sample method only to test client communication.
     */
    public final ActionFuture<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult> getFibonnaciFuture(final int order) {

        // Create Fibonacci goal message
        final FibonacciActionGoal goalMessage = (FibonacciActionGoal) this.actionClient.newGoalMessage();
        final FibonacciGoal fibonacciGoal = goalMessage.getGoal();
        // set Fibonacci parameter
        fibonacciGoal.setOrder(order);
        LOGGER.trace("Sending goal...");
        final ActionFuture<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult> resultFuture = this.actionClient.sendGoal(goalMessage);

        final GoalID gid1 = goalMessage.getGoalId();
        LOGGER.trace("Sent goal with ID: " + gid1.getId());
        LOGGER.trace("Waiting for goal to complete...");

        return resultFuture;

    }

    /**
     * Sample method only to test client communication.
     */
    public final ActionFuture<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult> getFibonnaciCanceledFuture(final int order) {


        // Create Fibonacci goal message
        final FibonacciActionGoal goalMessage = (FibonacciActionGoal) this.actionClient.newGoalMessage();
        final FibonacciGoal fibonacciGoal = goalMessage.getGoal();
        // set Fibonacci parameter
        fibonacciGoal.setOrder(order);

        LOGGER.trace("Sending new goal");
        final ActionFuture<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult> resulFuture = this.actionClient.sendGoal(goalMessage);
        final GoalID gid2 = goalMessage.getGoalId();
        LOGGER.trace("Sent goal with ID: " + gid2.getId());
        LOGGER.trace("Cancelling goal with ID: " + gid2.getId());
        this.actionClient.sendCancel(gid2);
        LOGGER.trace("Cancel Request sent for goal with ID: " + gid2.getId());
        return resulFuture;


    }

    @Override
    public void onStart(final ConnectedNode connectedNode) {
        this.actionClient = new ActionClient<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult>(connectedNode, FibonacciGraphNames.ACTION_GRAPH_NAME, FibonacciActionGoal._TYPE, FibonacciActionFeedback._TYPE, FibonacciActionResult._TYPE);

        this.actionClient.addActionClientListener(this);
        this.connectCountDownLatch.countDown();

    }

    /**
     *
     */
    @Override
    public void resultReceived(FibonacciActionResult message) {
        final FibonacciResult result = message.getResult();
        final int[] sequence = result.getSequence();

        final StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Got Fibonacci result sequence: ");
        for (int i = 0; i < sequence.length; i++) {
            stringBuilder.append(Integer.toString(sequence[i]));
            stringBuilder.append("");
        }

        LOGGER.trace(stringBuilder.toString());
    }

    @Override
    public void feedbackReceived(FibonacciActionFeedback message) {
        final FibonacciFeedback result = message.getFeedback();
        final int[] sequence = result.getSequence();


        final StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Got Fibonacci feedback sequence: ");
        for (int i = 0; i < sequence.length; i++) {
            stringBuilder.append(sequence[i]);
            stringBuilder.append("");
        }
        LOGGER.trace(stringBuilder.toString());
    }

    /**
     * This is invoked periodically from the server.
     *
     * @param status The status message received from the server.
     */
    @Override
    public void statusReceived(final GoalStatusArray status) {
        if (LOGGER.isTraceEnabled()) {
            final StringJoiner stringJoiner = new StringJoiner(",", "Status:{", "}");
            for (final GoalStatus goalStatus : status.getStatusList()) {
//                LOGGER.trace("GoalID: " + gs.getGoalId().getId() + " -- GoalStatus: " + gs.getStatus() + " -- " + gs.getText());
                stringJoiner.add("GoalID: " + goalStatus.getGoalId().getId() + " -- GoalStatus: " + goalStatus.getStatus() + "(" + this.goalStatusToString.getStatus(goalStatus.getStatus()) + ") -- " + goalStatus.getText());
            }
            LOGGER.trace(stringJoiner.toString());
        }
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Goal Current state: " + actionClient.getGoalState());
        }

    }

}
