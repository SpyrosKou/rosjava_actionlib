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
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


/**
 * Class to test the actionlib client.
 *
 * @author Ernesto Corbellini ecorbellini@ekumenlabs.com
 * @author Spyros Koukas
 */
class ActionLibClientFeedbackListener extends AbstractNodeMain implements ActionClientListener<FibonacciActionFeedback, FibonacciActionResult> {
    private final GoalStatusToString goalStatusToString=new GoalStatusToString();
    static {
        // comment this line if you want logs activated
        System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog");
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());


    private ActionClient actionClient = null;
    private volatile boolean isStarted = false;


    @Override
    public GraphName getDefaultNodeName() {
        return GraphName.of("fibonacci_test_client");
    }

    /**
     * @return true if the node is started now
     */
    public final boolean isStarted() {
        return this.isStarted;
    }

    /**
     * @return isStarted
     **/
    public void waitForStart() {
        while (!this.isStarted) {
            try {
                Thread.sleep(5);
            } catch (final InterruptedException ie) {
                LOGGER.error(ExceptionUtils.getStackTrace(ie));
            } catch (final Exception e) {
                LOGGER.error(ExceptionUtils.getStackTrace(e));
            }
        }
    }

    /**
     * @deprecated Legacy
     * Sample method only to test client communication.
     */
    @Deprecated
    public final FibonacciActionResult getFibonnaciBlocking(final int order) throws ExecutionException, InterruptedException, TimeoutException {
        final Stopwatch stopwatch = Stopwatch.createStarted();
        // Attach listener for the callbacks

        LOGGER.trace("Waiting for action server to start...");
        final boolean serverStarted = actionClient.waitForServerConnection(200, TimeUnit.MILLISECONDS);
        if (serverStarted) {
            LOGGER.trace("Action server started.\n");
        } else {
            LOGGER.trace("No actionlib server found after waiting for " + stopwatch.elapsed(TimeUnit.MILLISECONDS) + " milliseconds!");
            throw new RuntimeException("Not Connected");
        }

        // Create Fibonacci goal message
        final FibonacciActionGoal goalMessage = (FibonacciActionGoal) actionClient.newGoalMessage();
        final FibonacciGoal fibonacciGoal = goalMessage.getGoal();
        // set Fibonacci parameter
        fibonacciGoal.setOrder(order);
        LOGGER.trace("Sending goal...");
        final ActionFuture<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult> resultFuture = this.actionClient.sendGoal(goalMessage);
        final GoalID gid1 = goalMessage.getGoalId();
        LOGGER.trace("Sent goal with ID: " + gid1.getId());
        LOGGER.trace("Waiting for goal to complete...");

        final FibonacciActionResult result = resultFuture.get(100, TimeUnit.SECONDS);

        LOGGER.trace("Goal completed!\n");
        return result;
    }

    /**
     * @deprecated legacy test
     * Sample method only to test client communication.
     */
    @Deprecated
    public final FibonacciActionResult getFibonnaciBlockingWithCancelation(final int order) throws ExecutionException, InterruptedException {
        final Stopwatch stopwatch = Stopwatch.createStarted();
        LOGGER.trace("Waiting for action server to start...");
        final boolean serverStarted = this.actionClient.waitForServerConnection(200, TimeUnit.MILLISECONDS);
        if (serverStarted) {
            LOGGER.trace("Action server started.\n");
        } else {
            LOGGER.trace("No actionlib server found after waiting for " + stopwatch.elapsed(TimeUnit.MILLISECONDS) + " milliseconds!");
            throw new RuntimeException("Not connected");
        }

        // Create Fibonacci goal message
        final FibonacciActionGoal goalMessage = (FibonacciActionGoal) this.actionClient.newGoalMessage();
        final FibonacciGoal fibonacciGoal = goalMessage.getGoal();
        // set Fibonacci parameter
        fibonacciGoal.setOrder(order);

        LOGGER.trace("Sending a new goal...");
        final ActionFuture<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult> resulFuture = this.actionClient.sendGoal(goalMessage);
        final GoalID gid2 = goalMessage.getGoalId();
        LOGGER.trace("Sent goal with ID: " + gid2.getId());
        LOGGER.trace("Cancelling this goal...");
        this.actionClient.sendCancel(gid2);

        final var result = resulFuture.get();

        LOGGER.trace("Goal cancelled successfully.\n");
        return result;


    }

    @Override
    public void onStart(ConnectedNode node) {
        this.actionClient = new ActionClient<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult>(node, AsyncGoalRunnerActionLibServer.DEFAULT_ACTION_NAME, FibonacciActionGoal._TYPE, FibonacciActionFeedback._TYPE, FibonacciActionResult._TYPE);
//       Log log = node.getLog();
        this.isStarted = true;
        this.actionClient.addListener(this);

//        System.exit(0);
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
            final StringJoiner stringJoiner=new StringJoiner(",","Status:{","}");
            for (final GoalStatus goalStatus : status.getStatusList()) {
//                LOGGER.trace("GoalID: " + gs.getGoalId().getId() + " -- GoalStatus: " + gs.getStatus() + " -- " + gs.getText());
                stringJoiner.add("GoalID: " + goalStatus.getGoalId().getId() + " -- GoalStatus: " + goalStatus.getStatus()+"("+this.goalStatusToString.getStatus(goalStatus.getStatus()) + ") -- " + goalStatus.getText());
            }
            LOGGER.trace(stringJoiner.toString());
        }
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Goal Current state: " + actionClient.getGoalState());
        }

    }

}
