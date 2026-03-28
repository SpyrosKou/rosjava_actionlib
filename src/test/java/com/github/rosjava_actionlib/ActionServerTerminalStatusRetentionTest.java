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

import actionlib_tutorials.FibonacciActionFeedback;
import actionlib_tutorials.FibonacciActionGoal;
import actionlib_tutorials.FibonacciActionResult;
import com.google.common.base.Stopwatch;
import eu.test.utils.RosExecutor;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public final class ActionServerTerminalStatusRetentionTest extends BaseTest {
    private static final long TIMEOUT = 30;
    private static final TimeUnit TIME_UNIT = TimeUnit.SECONDS;
    private static final long TERMINAL_STATUS_RETENTION_MILLIS = 1000;

    private FutureBasedClientNode futureBasedClientNode = null;
    private FibonacciActionLibServer fibonacciActionLibServer = null;

    @Test
    public final void terminalGoalsRemainTrackedUntilRetentionTimeoutExpires() {
        final Stopwatch stopwatch = Stopwatch.createStarted();
        try {
            final boolean serverStarted = this.futureBasedClientNode.waitForClientStartAndServerConnection(TIMEOUT, TIME_UNIT);
            Assert.assertTrue("Was not connected. Elapsed Time:" + stopwatch.elapsed(TIME_UNIT) + " timeout:" + TIMEOUT, serverStarted);

            final ActionFuture<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult> resultFuture =
                    this.futureBasedClientNode.invoke(TestInputs.TEST_INPUT);

            final FibonacciActionResult result = resultFuture.get(TIMEOUT - stopwatch.elapsed(TIME_UNIT), TIME_UNIT);
            Assert.assertNotNull("Null Result", result);
            final String goalId = result.getStatus().getGoalId().getId();
            final Map<?, ?> trackedGoals = this.getTrackedGoalsMap();

            Assert.assertTrue("Terminal goal should remain tracked for additional status heartbeats", trackedGoals.containsKey(goalId));
            this.waitUntil(() -> !trackedGoals.containsKey(goalId), TERMINAL_STATUS_RETENTION_MILLIS + 2000);
            Assert.assertFalse("Terminal goal should be evicted after the retention timeout", trackedGoals.containsKey(goalId));
        } catch (final Exception exception) {
            Assert.fail(ExceptionUtils.getStackTrace(exception));
        }
    }

    @Override
    final void beforeCustom(final RosExecutor rosExecutor, final Optional<String> rosMasterUri) {
        try {
            Assume.assumeNotNull(rosExecutor);
            Assume.assumeTrue(rosMasterUri.isPresent());
            final Stopwatch stopwatch = Stopwatch.createStarted();
            this.fibonacciActionLibServer = new FibonacciActionLibServer(true, TERMINAL_STATUS_RETENTION_MILLIS, TimeUnit.MILLISECONDS);
            this.futureBasedClientNode = new FutureBasedClientNode();

            rosExecutor.startNodeMain(this.fibonacciActionLibServer, this.fibonacciActionLibServer.getDefaultNodeName().toString(), rosMasterUri.get());
            final boolean serverStarted = this.fibonacciActionLibServer.waitForStart(TIMEOUT - stopwatch.elapsed(TIME_UNIT), TIME_UNIT);
            Assert.assertTrue("Server Could not connect", serverStarted);

            rosExecutor.startNodeMain(this.futureBasedClientNode, this.futureBasedClientNode.getDefaultNodeName().toString(), rosMasterUri.get());
            final boolean clientStarted = this.futureBasedClientNode.waitForClientStartAndServerConnection(TIMEOUT - stopwatch.elapsed(TIME_UNIT), TIME_UNIT);
            Assume.assumeTrue("Client started. " + "Elapsed Time:" + stopwatch.elapsed(TIME_UNIT) + " timeout:" + TIMEOUT + " " + TIME_UNIT, clientStarted);
        } catch (final Exception exception) {
            Assume.assumeNoException(exception);
        }
    }

    @Override
    final void afterCustom(final RosExecutor rosExecutor) {
        try {
            rosExecutor.stopNodeMain(this.fibonacciActionLibServer);
        } catch (final Exception ignored) {
        }
        try {
            rosExecutor.stopNodeMain(this.futureBasedClientNode);
        } catch (final Exception ignored) {
        }
        this.futureBasedClientNode = null;
        this.fibonacciActionLibServer = null;
    }

    private Map<?, ?> getTrackedGoalsMap() {
        try {
            final Field actionServerField = FibonacciActionLibServer.class.getDeclaredField("actionServer");
            actionServerField.setAccessible(true);
            final Object actionServer = actionServerField.get(this.fibonacciActionLibServer);

            final Field trackedGoalsField = ActionServer.class.getDeclaredField("goalIdToGoalStatusMap");
            trackedGoalsField.setAccessible(true);
            return (Map<?, ?>) trackedGoalsField.get(actionServer);
        } catch (final Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private void waitUntil(final CheckedCondition condition, final long timeoutMillis) throws Exception {
        final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (!condition.evaluate() && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }
    }

    @FunctionalInterface
    private interface CheckedCondition {
        boolean evaluate() throws Exception;
    }
}
