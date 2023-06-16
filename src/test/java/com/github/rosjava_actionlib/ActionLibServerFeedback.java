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
import actionlib_tutorials.FibonacciActionFeedback;
import actionlib_tutorials.FibonacciActionGoal;
import actionlib_tutorials.FibonacciActionResult;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.ros.namespace.GraphName;
import org.ros.node.AbstractNodeMain;
import org.ros.node.ConnectedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Does not send a Fibonacci, it just counts.
 * Testing the Action Lib Server
 *
 * @author Spyros Koukas
 */
class ActionLibServerFeedback extends AbstractNodeMain implements ActionServerListener<FibonacciActionGoal> {
    static final String GRAPH_NAME = "fibonacci_test_server";

    static {
        // comment this line if you want logs activated
        System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog");
    }

    public static final String DEFAULT_ACTION_NAME = "/fibonacci";
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private ActionServer<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult> actionServer = null;
    private volatile boolean isStarted = false;
    private static final long SLEEP_MILLIS = 100;
    private final ConcurrentHashMap<String, CompletableFuture<FibonacciActionGoal>> goals = new ConcurrentHashMap<>();


    /**
     * Getter for isStarted
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
                LOGGER.error(org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace(e));
            }
        }
    }

    @Override
    public GraphName getDefaultNodeName() {
        return GraphName.of(GRAPH_NAME);
    }

    @Override
    public void onStart(final ConnectedNode node) {


        this.actionServer = new ActionServer<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult>(node,this, DEFAULT_ACTION_NAME, FibonacciActionGoal._TYPE, FibonacciActionFeedback._TYPE, FibonacciActionResult._TYPE);
        this.isStarted = true;

    }

    @Override
    /**
     *
     */
    public void goalReceived(final FibonacciActionGoal goal) {
        if (goal != null) {
            LOGGER.trace("Goal received : " + goal);
        } else {
            LOGGER.debug("Goal is null");
        }

    }


    /**
     *
     */
    @Override
    public void cancelReceived(final GoalID goalId) {
        LOGGER.trace("Cancel received for ID:" + goalId);
        if (goalId != null && goalId.getId() != null) {
            final String id = goalId.getId();
            final CompletableFuture<FibonacciActionGoal> preexistingGoal = goals.get(id);
            // If we don't have a goal, accept it. Otherwise, reject it.
            if (preexistingGoal == null) {
                LOGGER.trace("Goal not found");

            } else {
                preexistingGoal.cancel(true);
                goals.remove(id);
            }
        }
    }

    /**
     * @param goal
     *
     * @return
     *
     * @throws InterruptedException
     */
    private CompletableFuture<FibonacciActionGoal> calculateAsync(final FibonacciActionGoal goal) {
        final CompletableFuture<FibonacciActionGoal> futureTask = new CompletableFuture<>();

        futureTask.runAsync(() -> {
            final String id = goal.getGoalId().getId();
            LOGGER.trace("Starting the execution of GOAL:" + id);
            sleep(SLEEP_MILLIS);


            final int order = Math.max(0, goal.getGoal().getOrder());
            final int limit = Math.max(0, order - 1);
            for (int i = 0; i < limit; i++) {
                final FibonacciActionFeedback feedback = actionServer.newFeedbackMessage();
                feedback.getFeedback().setSequence(fibonacciSequence(i));
                actionServer.sendFeedback(feedback);
                sleep(SLEEP_MILLIS);
            }





            final FibonacciActionResult result = actionServer.newResultMessage();

            result.getResult().setSequence(fibonacciSequence(order));

            actionServer.setSucceed(id);
            actionServer.setGoalStatus(result.getStatus(), id);

            LOGGER.trace("Sending result...");
            actionServer.sendResult(result);
            goals.remove(id);
            LOGGER.trace("Finishing the execution of GOAL:" + id);
        });


        return futureTask;
    }

    /**
     * @param millis
     */
    private static final void sleep(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (final Exception e) {
        }

    }

    /**
     * @param goal The action goal received.
     *
     * @return
     */
    @Override
    public boolean acceptGoal(final FibonacciActionGoal goal) {
        if (goal != null && goal.getGoalId() != null) {
            final String id = goal.getGoalId().getId();
            final CompletableFuture<FibonacciActionGoal> preexistingGoal = goals.putIfAbsent(id, calculateAsync(goal));
            // If there is no current goal, accept it. Otherwise, reject it.
            if (preexistingGoal == null) {
                LOGGER.trace("Goal accepted:" + goal);
                return true;
            } else {
                LOGGER.trace("Goal already exists. Goal Rejected:" + goal);
                return false;
            }
        } else {
            return false;
        }
    }


    private int[] fibonacciSequence(int order) {
        int i;
        int[] fib = new int[order + 2];

        fib[0] = 0;
        fib[1] = 1;

        for (i = 2; i < (order + 2); i++) {
            fib[i] = fib[i - 1] + fib[i - 2];
        }
        return fib;
    }


}
