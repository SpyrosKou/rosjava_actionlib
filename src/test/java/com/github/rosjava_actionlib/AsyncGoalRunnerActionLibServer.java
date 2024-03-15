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
import actionlib_tutorials.FibonacciActionFeedback;
import actionlib_tutorials.FibonacciActionGoal;
import actionlib_tutorials.FibonacciActionResult;
import com.google.common.util.concurrent.Runnables;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.ros.namespace.GraphName;
import org.ros.node.AbstractNodeMain;
import org.ros.node.ConnectedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * @deprecated does not work properly
 * A server that tries to calculate fibonnacis async
 *
 * @author Spyros Koukas
 */
@Deprecated
class AsyncGoalRunnerActionLibServer extends AbstractNodeMain implements ActionServerListener<FibonacciActionGoal> {
    static final String GRAPH_NAME = "fibonacci_test_server";
    private final FibonacciCalculator fibonacciCalculator = new FibonacciCalculator();

    static {
        // comment this line if you want logs activated
        System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog");
    }

    private final boolean runAsync;

    AsyncGoalRunnerActionLibServer(final boolean runAsyncGoals) {
        this.runAsync = runAsyncGoals;
    }

    public static final String DEFAULT_ACTION_NAME = "/fibonacci";
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private ActionServer<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult> actionServer = null;
    private static final long SLEEP_MILLIS = 100;
    private final ConcurrentHashMap<String, CompletableFuture<Void>> goals = new ConcurrentHashMap<>();


    @Override
    public GraphName getDefaultNodeName() {
        return GraphName.of(GRAPH_NAME);
    }

    @Override
    public void onStart(final ConnectedNode node) {


        this.actionServer = new ActionServer<>(node, this, DEFAULT_ACTION_NAME, FibonacciActionGoal._TYPE, FibonacciActionFeedback._TYPE, FibonacciActionResult._TYPE);

    }

    @Override
    /**
     *
     */
    public final void goalReceived(final FibonacciActionGoal goal) {
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
            final CompletableFuture<Void> preexistingGoal = this.goals.get(id);
            // If we don't have a goal, accept it. Otherwise, reject it.
            if (preexistingGoal == null) {
                LOGGER.trace("Goal not found");

            } else {
                //preexistingGoal.cancel(true);
                // goals.remove(id);
            }
            this.actionServer.setCancelRequested(id);
        }

    }

    /**
     * @param goal
     * @return
     * @throws InterruptedException
     */
    private final CompletableFuture<Void> calculateAsync(final FibonacciActionGoal goal) throws ExecutionException, InterruptedException {
        final CompletableFuture<FibonacciActionGoal> futureTask = new CompletableFuture<>();

        final CompletableFuture<Void> completableFuture = futureTask.runAsync(() -> {
            final String id = goal.getGoalId().getId();
            LOGGER.trace("Starting the execution of GOAL:" + id);
            this.actionServer.setAccepted(id);

            final int order = Math.max(0, goal.getGoal().getOrder());

            final Consumer<List<Integer>> feedbackProvider = list -> {

                final FibonacciActionFeedback feedback = this.actionServer.newFeedbackMessage();
                feedback.getFeedback().setSequence(list.stream().mapToInt(Integer::intValue).toArray());
                this.actionServer.sendFeedback(feedback);

            };


            final FibonacciActionResult result = actionServer.newResultMessage();
            result.getStatus().setGoalId(goal.getGoalId());
            final Set<Byte> cancelingStatuses = Set.of(GoalStatus.PREEMPTING, GoalStatus.RECALLING);
            final Supplier<Boolean> shouldCancel = () -> cancelingStatuses.contains(this.actionServer.getGoalStatus(id));
            final Runnable onCancel=()->{ this.actionServer.setCancel(id);this.actionServer.sendStatusTick();
                LOGGER.info("Cancelled goal:"+id);
            };
            result.getResult().setSequence(fibonacciCalculator.fibonacciSequence(order, shouldCancel, Runnables::doNothing,feedbackProvider, order / 5));
            LOGGER.trace("Sending result...");

            if(!cancelingStatuses.contains(this.actionServer.getGoalStatus(id))){
                this.actionServer.setSucceed(id);

                LOGGER.trace("Succeeded goal:"+id);
            }else{
                this.actionServer.setCancel(id);
                result.getStatus().setStatus(GoalStatus.PREEMPTED);
                LOGGER.trace("Canceled goal:"+id);
            }
            this.actionServer.sendStatusTick();
            this.actionServer.sendResult(result);


            this.goals.remove(id);
            LOGGER.trace("Finishing the execution of GOAL:" + id);
        });
        if (!this.runAsync) {
            completableFuture.get();
        }

        return completableFuture;
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
     * @return
     */
    @Override
    public Optional<Boolean> acceptGoal(final FibonacciActionGoal goal) {
        try {
            if (goal != null && goal.getGoalId() != null) {
                final String id = goal.getGoalId().getId();

                final CompletableFuture<Void> preexistingGoal = this.goals.putIfAbsent(id, this.calculateAsync(goal));
                // If there is no current goal, accept it. Otherwise, reject it.
                if (preexistingGoal == null) {
                    LOGGER.trace("Goal will be manually processed:" + goal);
                    return Optional.empty();
                } else {
                    LOGGER.trace("Goal already exists. Goal Rejected:" + goal);
                    return Optional.of(Boolean.FALSE);
                }
            } else {
                return Optional.of(Boolean.FALSE);
            }
        } catch (final Exception exception) {
            LOGGER.error(ExceptionUtils.getStackTrace(exception));
            LOGGER.error("Rejecting Goal due to exception:" + goal.getGoalId().getId());
            return Optional.of(Boolean.FALSE);
        }
    }


}
