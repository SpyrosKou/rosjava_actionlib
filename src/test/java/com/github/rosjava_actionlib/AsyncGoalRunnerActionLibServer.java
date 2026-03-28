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
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * @author Spyros Koukas
 * A server that tries to calculate fibonnacis async
 */
final class AsyncGoalRunnerActionLibServer extends AbstractNodeMain implements ActionServerListener<FibonacciActionGoal> {

    private final FibonacciCalculator fibonacciCalculator = new FibonacciCalculator();


    private final boolean runAsync;
    private CountDownLatch connectCountDownLatch = new CountDownLatch(1);

    AsyncGoalRunnerActionLibServer(final boolean runAsyncGoals) {
        this.runAsync = runAsyncGoals;
    }


    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private ActionServer<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult> actionServer = null;

    private final ConcurrentHashMap<String, CompletableFuture<FibonacciActionResult>> goals = new ConcurrentHashMap<>();


    @Override
    public final GraphName getDefaultNodeName() {
        return GraphName.of(FibonacciGraphNames.SERVER_NODE_GRAPH_NAME);
    }

    @Override
    public final void onStart(final ConnectedNode node) {


        this.actionServer = new ActionServer<>(node, this, FibonacciGraphNames.ACTION_GRAPH_NAME, FibonacciActionGoal._TYPE, FibonacciActionFeedback._TYPE, FibonacciActionResult._TYPE);

        this.connectCountDownLatch.countDown();

    }

    @Override
    /**
     * goal is handled in {@link AsyncGoalRunnerActionLibServer#acceptGoal}
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
    public final void cancelReceived(final GoalID goalId) {
        LOGGER.trace("Cancel received for ID:" + goalId);
    }

    private final boolean shouldCancelGoal(final String goalId) {
        return ActionServer.isCancelRequestedStatus(this.actionServer.getGoalStatus(goalId));
    }

    private final boolean isCancelledGoal(final String goalId) {
        return ActionServer.isCancelledStatus(this.actionServer.getGoalStatus(goalId));
    }

    /**
     * @param goal
     * @return
     * @throws InterruptedException
     */
    private final CompletableFuture<FibonacciActionResult> calculateAsync(final FibonacciActionGoal goal) throws ExecutionException, InterruptedException {
        final CompletableFuture<FibonacciActionResult> futureTask = CompletableFuture.completedFuture(goal)
                .thenApplyAsync(goalIn -> {
                    final String id = goalIn.getGoalId().getId();
                    LOGGER.trace("Starting the execution of GOAL:" + id);
                    this.actionServer.setAccepted(id);

                    final int order = Math.max(0, goalIn.getGoal().getOrder());

                    final Consumer<List<Integer>> feedbackProvider = list -> {

                        final FibonacciActionFeedback feedback = this.actionServer.newFeedbackMessage();
                        feedback.getFeedback().setSequence(list.stream().mapToInt(Integer::intValue).toArray());
                        this.actionServer.sendFeedback(feedback);

                    };


                    final FibonacciActionResult result = actionServer.newResultMessage();
                    result.getStatus().setGoalId(goalIn.getGoalId());
                    final Supplier<Boolean> shouldCancel = () -> this.shouldCancelGoal(id);
                    result.getResult().setSequence(fibonacciCalculator.fibonacciSequence(order, shouldCancel, Runnables::doNothing, feedbackProvider, order / 5));
                    LOGGER.trace("Sending result...");

                    if (this.shouldCancelGoal(id)) {
                        this.actionServer.setCancel(id);
                    }
                    if (!this.isCancelledGoal(id)) {
                        this.actionServer.setSucceed(id);
                        result.getStatus().setStatus(GoalStatus.SUCCEEDED);
                        LOGGER.trace("Succeeded goal:" + id);
                    } else {
                        result.getStatus().setStatus(GoalStatus.PREEMPTED);
                        LOGGER.trace("Canceled goal:" + id);
                    }
                    this.actionServer.sendStatusTick();
                    this.actionServer.sendResult(result);

                    this.goals.remove(id);
                    LOGGER.trace("Finishing the execution of GOAL:" + id);
                    return result;
                });
        if (!this.runAsync) {
            futureTask.get();
        }

        return futureTask;
    }

    public final boolean waitForStart(final long timeout, final TimeUnit timeUnit) {
        Boolean connected = null;

        while (connected == null) {
            try {
                connected = this.connectCountDownLatch.await(timeout, timeUnit);
                return connected;
            } catch (final InterruptedException ie) {
                LOGGER.error(ExceptionUtils.getStackTrace(ie));

            } catch (final Exception e) {
                LOGGER.error(ExceptionUtils.getStackTrace(e));
            }
        }
        return connected != null && connected;
    }

    /**
     * @param goal The action goal received.
     * @return
     */
    @Override
    public Optional<Boolean> acceptGoal(final FibonacciActionGoal goal) {
        LOGGER.trace("Received Goal:" + goal);
        try {
            if (goal != null && goal.getGoalId() != null) {
                final String id = goal.getGoalId().getId();

                final CompletableFuture<FibonacciActionResult> preexistingGoal = this.goals.putIfAbsent(id, this.calculateAsync(goal));
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
