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
import org.apache.commons.lang3.exception.ExceptionUtils;
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


/**
 * Class to test the actionlib client.
 *
 * @author Ernesto Corbellini ecorbellini@ekumenlabs.com
 * @author Spyros Koukas
 */
class ActionLibClientFeedback extends AbstractNodeMain implements ActionClientListener<FibonacciActionFeedback, FibonacciActionResult> {
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
     *
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
     *Sample method only to test client communication.
     */
    public void getFibonnaciBlocking(final int order) {
        Duration serverTimeout = new Duration(20);
        boolean serverStarted;


        // Attach listener for the callbacks

        LOGGER.trace("Waiting for action server to start...");
        serverStarted = actionClient.waitForActionServerToStart(new Duration(200));
        if (serverStarted) {
            LOGGER.trace("Action server started.\n");
        } else {
            LOGGER.trace("No actionlib server found after waiting for " + serverTimeout.totalNsecs() / 1e9 + " seconds!");
//            System.exit(1);
        }

        // Create Fibonacci goal message
        final FibonacciActionGoal goalMessage = (FibonacciActionGoal) actionClient.newGoalMessage();
        final FibonacciGoal fibonacciGoal = goalMessage.getGoal();
        // set Fibonacci parameter
        fibonacciGoal.setOrder(order);
        LOGGER.trace("Sending goal...");
        actionClient.sendGoal(goalMessage);
        final GoalID gid1 = goalMessage.getGoalId();
        LOGGER.trace("Sent goal with ID: " + gid1.getId());
        LOGGER.trace("Waiting for goal to complete...");

        while (actionClient.getGoalState() != ClientState.DONE) {
            sleep(1);
        }
        LOGGER.trace("Goal completed!\n");
    }

    /**
     *Sample method only to test client communication.
     */
    public void getFibonnaciBlockingWithCancelation(final int order) {
        Duration serverTimeout = new Duration(20);
        boolean serverStarted;


        // Attach listener for the callbacks

        LOGGER.trace("Waiting for action server to start...");
        serverStarted = actionClient.waitForActionServerToStart(new Duration(200));
        if (serverStarted) {
            LOGGER.trace("Action server started.\n");
        } else {
            LOGGER.trace("No actionlib server found after waiting for " + serverTimeout.totalNsecs() / 1e9 + " seconds!");
//            System.exit(1);
        }

        // Create Fibonacci goal message
        final FibonacciActionGoal goalMessage = (FibonacciActionGoal) actionClient.newGoalMessage();
        final FibonacciGoal fibonacciGoal = goalMessage.getGoal();
        // set Fibonacci parameter
        fibonacciGoal.setOrder(order);

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


    }

    @Override
    public void onStart(ConnectedNode node) {
        actionClient = new ActionClient<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult>(node, ActionLibServerFeedback.DEFAULT_ACTION_NAME, FibonacciActionGoal._TYPE, FibonacciActionFeedback._TYPE, FibonacciActionResult._TYPE);
//       Log log = node.getLog();
        this.isStarted = true;
        actionClient.addListener(this);

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
            stringBuilder.append(Integer.toString(sequence[i]));
            stringBuilder.append("");
        }
        LOGGER.trace(stringBuilder.toString());
    }

    /**
     * This is invoked periodically from the server.
     * @param status The status message received from the server.
     */
    @Override
    public void statusReceived(final GoalStatusArray status) {
        List<GoalStatus> statusList = status.getStatusList();
        for (GoalStatus gs : statusList) {
            LOGGER.trace("GoalID: " + gs.getGoalId().getId() + " -- GoalStatus: " + gs.getStatus() + " -- " + gs.getText());
        }
        LOGGER.trace("Goal Current state: " + actionClient.getGoalState());
    }

    private void sleep(long msec) {
        try {
            Thread.sleep(msec);
        } catch (InterruptedException ex) {
        }
    }
}
