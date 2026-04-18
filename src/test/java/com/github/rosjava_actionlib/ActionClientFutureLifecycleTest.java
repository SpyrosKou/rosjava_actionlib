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
import eu.test.utils.RosExecutor;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.ros.namespace.GraphName;
import org.ros.node.AbstractNodeMain;
import org.ros.node.ConnectedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author Spyros Koukas
 */
public class ActionClientFutureLifecycleTest extends BaseTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final long TIMEOUT = 30;
    private static final TimeUnit TIME_UNIT = TimeUnit.SECONDS;

    private FutureBasedClientNode futureBasedClientNode = null;
    private NeverCompletingActionLibServer neverCompletingActionLibServer = null;

    @Test
    public final void testAbandonedFutureCanBeGarbageCollectedAndUnregistered() {
        try {
            final ActionClient<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult> actionClient =
                    this.futureBasedClientNode.getActionClient();
            final int initialResultListenerCount = getListenerCount(actionClient, "callbackResultTargets");
            final int initialFeedbackListenerCount = getListenerCount(actionClient, "callbackFeedbackTargets");

            ActionFuture<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult> createdFuture =
                    this.futureBasedClientNode.invoke(TestInputs.TEST_INPUT);
            final WeakReference<ActionFuture<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult>> weakReference =
                    new WeakReference<>(createdFuture);

            Assertions.assertEquals(initialResultListenerCount + 1, getListenerCount(actionClient, "callbackResultTargets"));
            Assertions.assertEquals(initialFeedbackListenerCount + 1, getListenerCount(actionClient, "callbackFeedbackTargets"));

            createdFuture = null;
            waitForGarbageCollection(weakReference);

            Assertions.assertNull(weakReference.get(), "The future should be garbage collectable after user code drops it");
            Assertions.assertEquals(initialResultListenerCount, getListenerCount(actionClient, "callbackResultTargets"),
                    "Result listener registrations should be removed when the future is abandoned");
            Assertions.assertEquals(initialFeedbackListenerCount, getListenerCount(actionClient, "callbackFeedbackTargets"),
                    "Feedback listener registrations should be removed when the future is abandoned");
        } catch (final Exception exception) {
            Assertions.fail(ExceptionUtils.getStackTrace(exception));
        }
    }

    @Override
    void beforeCustom(final RosExecutor rosExecutor, final Optional<String> rosMasterUri) {
        try {
            Assumptions.assumeTrue(rosExecutor != null);
            Assumptions.assumeTrue(rosMasterUri.isPresent());

            this.neverCompletingActionLibServer = new NeverCompletingActionLibServer();
            this.futureBasedClientNode = new FutureBasedClientNode();

            rosExecutor.startNodeMain(this.neverCompletingActionLibServer, this.neverCompletingActionLibServer.getDefaultNodeName().toString(), rosMasterUri.get());
            Assertions.assertTrue(this.neverCompletingActionLibServer.waitForStart(TIMEOUT, TIME_UNIT), "Server could not connect");

            rosExecutor.startNodeMain(this.futureBasedClientNode, this.futureBasedClientNode.getDefaultNodeName().toString(), rosMasterUri.get());
            final boolean clientStarted = this.futureBasedClientNode.waitForClientStartAndServerConnection(TIMEOUT, TIME_UNIT);
            Assumptions.assumeTrue(clientStarted, "Client could not connect");
        } catch (final Exception exception) {
            Assumptions.assumeTrue(false, ExceptionUtils.getStackTrace(exception));
        }
    }

    @Override
    void afterCustom(final RosExecutor rosExecutor) {
        try {
            rosExecutor.stopNodeMain(this.neverCompletingActionLibServer);
        } catch (final Exception exception) {
            LOGGER.error(ExceptionUtils.getStackTrace(exception));
        }
        try {
            rosExecutor.stopNodeMain(this.futureBasedClientNode);
        } catch (final Exception exception) {
            LOGGER.error(ExceptionUtils.getStackTrace(exception));
        }
        this.futureBasedClientNode = null;
        this.neverCompletingActionLibServer = null;
    }

    private static int getListenerCount(final ActionClient<?, ?, ?> actionClient, final String fieldName) {
        try {
            final Field field = ActionClient.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return ((List<?>) field.get(actionClient)).size();
        } catch (final Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static void waitForGarbageCollection(final WeakReference<?> weakReference) throws InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (weakReference.get() != null && System.nanoTime() < deadline) {
            System.gc();
            Thread.sleep(50);
        }
    }

    private static final class NeverCompletingActionLibServer extends AbstractNodeMain implements ActionServerListener<FibonacciActionGoal> {
        private final CountDownLatch startCountDownLatch = new CountDownLatch(1);
        private ActionServer<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult> actionServer = null;

        @Override
        public GraphName getDefaultNodeName() {
            return GraphName.of(FibonacciGraphNames.SERVER_NODE_GRAPH_NAME);
        }

        @Override
        public void onStart(final ConnectedNode connectedNode) {
            this.actionServer = new ActionServer<>(connectedNode, this, FibonacciGraphNames.ACTION_GRAPH_NAME,
                    FibonacciActionGoal._TYPE, FibonacciActionFeedback._TYPE, FibonacciActionResult._TYPE);
            this.startCountDownLatch.countDown();
        }

        @Override
        public void goalReceived(final FibonacciActionGoal goal) {
        }

        @Override
        public void cancelReceived(final GoalID goalId) {
        }

        @Override
        public Optional<Boolean> acceptGoal(final FibonacciActionGoal goal) {
            this.actionServer.setAccepted(goal.getGoalId().getId());
            return Optional.empty();
        }

        public boolean waitForStart(final long timeout, final TimeUnit timeUnit) throws InterruptedException {
            return this.startCountDownLatch.await(timeout, timeUnit);
        }
    }
}