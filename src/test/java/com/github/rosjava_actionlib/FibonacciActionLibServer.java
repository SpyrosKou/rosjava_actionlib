package com.github.rosjava_actionlib;

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
import std_msgs.Bool;

import java.lang.invoke.MethodHandles;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Ernesto Corbellini ecorbellini@ekumenlabs.com
 * @author Spyros Koukas
 * <p>
 * Class to test the actionlib server.
 * This is a simple server with the ability to process only a single goal.
 */
final class FibonacciActionLibServer extends AbstractNodeMain implements ActionServerListener<FibonacciActionGoal> {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private ActionServer<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult> actionServer = null;
    private volatile FibonacciActionGoal currentGoal = null;
    private final FibonacciCalculator fibonacciCalculator = new FibonacciCalculator();
    private final CountDownLatch startCountDownLatch = new CountDownLatch(1);
    private final Set<String> cancelledGoalIds = new ConcurrentSkipListSet<>();

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
                FibonacciActionFeedback._TYPE, FibonacciActionResult._TYPE);

        this.startCountDownLatch.countDown();
    }

    @Override
    public final void goalReceived(final FibonacciActionGoal goal) {
        LOGGER.trace("Goal received: " + goal.getGoalId().getId());

    }

    private final boolean shouldCancelGoal(final FibonacciActionGoal goal) {
        return this.cancelledGoalIds.contains(goal.getGoalId().getId());
    }

    private static final void copyGoal(final GoalID from, final GoalID to) {
        to.setId(from.getId());
        to.setStamp(from.getStamp());
    }

    @Override
    public final void cancelReceived(final GoalID id) {
        this.cancelledGoalIds.add(id.getId());
        LOGGER.trace("Cancel received for goal:" + id);
    }

    @Override
    public final Optional<Boolean> acceptGoal(final FibonacciActionGoal goal) {
        // If we don't have a goal, accept it. Otherwise, reject it.
        if (this.currentGoal == null) {
            this.currentGoal = goal;
            this.actionServer.setAccepted(this.currentGoal.getGoalId().getId());
            LOGGER.trace("Goal accepted.");
            final FibonacciActionFeedback feedback = this.actionServer.newFeedbackMessage();
            feedback.getStatus().setStatus(GoalStatus.ACTIVE);
            this.actionServer.sendFeedback(feedback);

            final FibonacciActionResult result = this.actionServer.newResultMessage();
            copyGoal(goal.getGoalId(), result.getStatus().getGoalId());
            final int input = goal.getGoal().getOrder();
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Renaming Thread" + Thread.currentThread().getName());
                Thread.currentThread().setName("GoalReceived " + Thread.currentThread().getName());
            }
            final int[] output = fibonacciCalculator.fibonacciSequence(input, () -> this.shouldCancelGoal(goal), Runnables::doNothing);
            result.getResult().setSequence(output);


            if (this.shouldCancelGoal(goal)) {
                this.cancelledGoalIds.remove(goal.getGoalId().getId());
            } else {
                result.getStatus().setStatus(GoalStatus.SUCCEEDED);
                this.actionServer.setSucceed(goal.getGoalId().getId());
            }
            this.actionServer.sendResult(result);
            this.currentGoal=null;
            return Optional.empty();
        } else {
            LOGGER.trace("We already have a goal! New goal reject.");
            return Optional.of(Boolean.FALSE);
        }
    }


}
