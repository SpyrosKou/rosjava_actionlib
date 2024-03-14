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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Ernesto Corbellini ecorbellini@ekumenlabs.com
 * @author Spyros Koukas
 * @deprecated see {@link FutureBasedClient} which utilizes the {@link ActionFuture}
 * Class to test the actionlib server.
 * This is a simple server with the ability to process only a single goal.
 */
@Deprecated
final class FibonacciActionLibServer extends AbstractNodeMain implements ActionServerListener<FibonacciActionGoal> {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private ActionServer<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult> actionServer = null;
    private volatile FibonacciActionGoal currentGoal = null;
    private final FibonacciCalculator fibonacciCalculator = new FibonacciCalculator();
    private final AtomicBoolean isStarted = new AtomicBoolean(false);
    private final CountDownLatch startCountDownLatch = new CountDownLatch(1);

    @Override
    public final GraphName getDefaultNodeName() {
        return GraphName.of("fibonacci_test_server");
    }

    /**
     * Getter for isStarted
     *
     * @return isStarted
     **/
    public final void waitForStart() {
        if (!this.isStarted.get()) {
            try {
                this.startCountDownLatch.await();
            } catch (final InterruptedException ie) {
                LOGGER.error(ExceptionUtils.getStackTrace(ie));
            } catch (final Exception e) {
                LOGGER.error(ExceptionUtils.getStackTrace(e));
            }
        }
    }

    @Override
    public final void onStart(final ConnectedNode node) {

        this.actionServer = new ActionServer<>(node, this, "/fibonacci", FibonacciActionGoal._TYPE,
                FibonacciActionFeedback._TYPE, FibonacciActionResult._TYPE);

        this.isStarted.set(true);
        this.startCountDownLatch.countDown();
    }

    @Override
    public final void goalReceived(final FibonacciActionGoal goal) {
        LOGGER.trace("Goal received.");
        final FibonacciActionResult result = this.actionServer.newResultMessage();
        copyGoal(goal.getGoalId(), result.getStatus().getGoalId());
        final int input = goal.getGoal().getOrder();
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Renaming Thread" + Thread.currentThread().getName());
            Thread.currentThread().setName("GoalReceived " + Thread.currentThread().getName());
        }
        final int[] output = fibonacciCalculator.fibonacciSequence(input);
        result.getResult().setSequence(output);
        this.actionServer.sendResult(result);
    }


    private static final void copyGoal(final GoalID from, final GoalID to) {
        to.setId(from.getId());
        to.setStamp(from.getStamp());
    }

    @Override
    public final void cancelReceived(GoalID id) {
        LOGGER.trace("Cancel received.");
    }

    @Override
    public final boolean acceptGoal(FibonacciActionGoal goal) {
        // If we don't have a goal, accept it. Otherwise, reject it.
        if (currentGoal == null) {
            currentGoal = goal;
            LOGGER.trace("Goal accepted.");
            return true;
        } else {
            LOGGER.trace("We already have a goal! New goal reject.");
            return false;
        }
    }


}