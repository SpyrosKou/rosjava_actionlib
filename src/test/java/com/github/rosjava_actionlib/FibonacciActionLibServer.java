/**
 * Copyright 2015 Ekumen www.ekumenlabs.com
 * Copyright 2023 Spyros Koukas
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
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author Ernesto Corbellini ecorbellini@ekumenlabs.com
 * @author Spyros Koukas
 * <p>
 * Class to test the actionlib server.
 * This is a simple server with the ability to process only a single goal.
 */
final class FibonacciActionLibServer extends AbstractNodeMain implements ActionServerListener<FibonacciActionGoal> {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final long DEFAULT_TERMINAL_STATUS_RETENTION_MILLIS = 5000;
    private ActionServer<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult> actionServer = null;
    private volatile FibonacciActionGoal currentGoal = null;
    private final FibonacciCalculator fibonacciCalculator = new FibonacciCalculator();
    private final CountDownLatch startCountDownLatch = new CountDownLatch(1);
    private final boolean setExplicitResultStatus;
    private final long terminalStatusRetention;
    private final TimeUnit terminalStatusRetentionTimeUnit;

    FibonacciActionLibServer() {
        this(true);
    }

    FibonacciActionLibServer(final boolean setExplicitResultStatus) {
        this(setExplicitResultStatus, DEFAULT_TERMINAL_STATUS_RETENTION_MILLIS, TimeUnit.MILLISECONDS);
    }

    FibonacciActionLibServer(final boolean setExplicitResultStatus, final long terminalStatusRetention, final TimeUnit terminalStatusRetentionTimeUnit) {
        this.setExplicitResultStatus = setExplicitResultStatus;
        this.terminalStatusRetention = terminalStatusRetention;
        this.terminalStatusRetentionTimeUnit = terminalStatusRetentionTimeUnit;
    }

    @Override
    public final GraphName getDefaultNodeName() {
        return GraphName.of(FibonacciGraphNames.SERVER_NODE_GRAPH_NAME);
    }

    /**
     * Getter for isStarted
     *
     * @return isStarted
     **/
    public final boolean waitForStart(final long timeout, final TimeUnit timeUnit) {

        try {
            return this.startCountDownLatch.await(timeout, timeUnit);

        } catch (final Exception e) {
            LOGGER.error(ExceptionUtils.getStackTrace(e));
        }
        return false;

    }


    @Override
    public final void onStart(final ConnectedNode node) {

        this.actionServer = new ActionServer<>(node, this, FibonacciGraphNames.ACTION_GRAPH_NAME, FibonacciActionGoal._TYPE,
                FibonacciActionFeedback._TYPE, FibonacciActionResult._TYPE,
                this.terminalStatusRetention, this.terminalStatusRetentionTimeUnit);

        this.startCountDownLatch.countDown();
    }

    @Override
    public final void goalReceived(final FibonacciActionGoal goal) {
        LOGGER.trace("Goal received: " + goal.getGoalId().getId());

    }

    private final boolean shouldCancelGoal(final String goalId) {
        return ActionServer.isCancelRequestedStatus(this.actionServer.getGoalStatus(goalId));
    }

    private final boolean isCancelledGoal(final String goalId) {
        return ActionServer.isCancelledStatus(this.actionServer.getGoalStatus(goalId));
    }

    private static final void copyGoal(final GoalID from, final GoalID to) {
        to.setId(from.getId());
        to.setStamp(from.getStamp());
    }

    @Override
    public final void cancelReceived(final GoalID id) {
        LOGGER.trace("Cancel received for goal:" + id);
    }

    @Override
    public final Optional<Boolean> acceptGoal(final FibonacciActionGoal goal) {
        // If we don't have a goal, accept it. Otherwise, reject it.
        if (this.currentGoal == null) {
            this.currentGoal = goal;
            final String goalId = this.currentGoal.getGoalId().getId();
            this.actionServer.setAccepted(goalId);
            LOGGER.trace("Goal accepted with goalId:[" + goalId + "]");
            final FibonacciActionFeedback feedback = this.actionServer.newFeedbackMessage();
            feedback.getStatus().setStatus(GoalStatus.ACTIVE);
            this.actionServer.sendFeedback(feedback);

            final FibonacciActionResult result = this.actionServer.newResultMessage();
            copyGoal(goal.getGoalId(), result.getStatus().getGoalId());
            LOGGER.trace("Constructed result for goalId:[" + result.getStatus().getGoalId().getId() + "] from goalId:[" + goal.getGoalId().getId() + "]");
            final int input = goal.getGoal().getOrder();
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Renaming Thread" + Thread.currentThread().getName());
                Thread.currentThread().setName("GoalReceived " + Thread.currentThread().getName());
            }
            final int[] output = fibonacciCalculator.fibonacciSequence(input, () -> this.shouldCancelGoal(goalId), Runnables::doNothing);
            result.getResult().setSequence(output);

            if (this.shouldCancelGoal(goalId)) {
                this.actionServer.setCancel(goalId);
            }
            if (!this.isCancelledGoal(goalId)) {
                if (this.setExplicitResultStatus) {
                    result.getStatus().setStatus(GoalStatus.SUCCEEDED);
                }
                this.actionServer.setSucceed(goalId);
            }
            LOGGER.trace("About to publish result for goalId:[" + result.getStatus().getGoalId().getId() + "] status:[" + result.getStatus().getStatus() + "]");
            this.actionServer.sendResult(result);
            this.currentGoal = null;
            return Optional.empty();
        } else {
            LOGGER.trace("We already have a goal! New goal reject.");
            return Optional.of(Boolean.FALSE);
        }
    }


}
