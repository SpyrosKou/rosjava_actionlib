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

import actionlib_msgs.GoalStatus;
import actionlib_tutorials.FibonacciActionFeedback;
import actionlib_tutorials.FibonacciActionGoal;
import actionlib_tutorials.FibonacciActionResult;
import com.google.common.base.Stopwatch;
import eu.test.utils.RosExecutor;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class ActionServerResultStatusCompatibilityTest extends BaseTest {
    private static final long TIMEOUT = 30;
    private static final TimeUnit TIME_UNIT = TimeUnit.SECONDS;

    private FutureBasedClientNode futureBasedClientNode = null;
    private FibonacciActionLibServer fibonacciActionLibServer = null;

    @Test
    public final void resultUsesTrackedTerminalStatusWhenServerDoesNotPopulateItExplicitly() {
        final Stopwatch stopwatch = Stopwatch.createStarted();
        final CountDownLatch terminalStatusReceived = new CountDownLatch(1);
        final AtomicReference<Byte> terminalStatus = new AtomicReference<>();

        this.futureBasedClientNode.getActionClient().addActionClientStatusListener(statusArray -> statusArray.getStatusList().stream()
                .filter(goalStatus -> goalStatus != null)
                .map(goalStatus -> goalStatus.getStatus())
                .filter(ActionServer::isTerminalStatus)
                .findFirst()
                .ifPresent(status -> {
                    terminalStatus.compareAndSet(null, status);
                    terminalStatusReceived.countDown();
                }));

        try {
            final boolean serverStarted = this.futureBasedClientNode.waitForClientStartAndServerConnection(TIMEOUT, TIME_UNIT);
            Assertions.assertTrue(serverStarted, "Was not connected. Elapsed Time:" + stopwatch.elapsed(TIME_UNIT) + " timeout:" + TIMEOUT);

            final ActionFuture<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult> resultFuture =
                    this.futureBasedClientNode.invoke(TestInputs.TEST_INPUT);

            final FibonacciActionResult result = resultFuture.get(TIMEOUT - stopwatch.elapsed(TIME_UNIT), TIME_UNIT);
            Assertions.assertNotNull(result, "Null Result");
            Assertions.assertTrue(Arrays.equals(result.getResult().getSequence(), TestInputs.TEST_CORRECT_OUTPUT), "Result was wrong");
            Assertions.assertEquals(GoalStatus.SUCCEEDED, result.getStatus().getStatus(), "Result should inherit tracked SUCCEEDED status");

            final boolean statusReceived = terminalStatusReceived.await(TIMEOUT - stopwatch.elapsed(TIME_UNIT), TIME_UNIT);
            Assertions.assertTrue(statusReceived, "Expected a terminal /status publication after the result");
            Assertions.assertEquals(Byte.valueOf(GoalStatus.SUCCEEDED), terminalStatus.get(), "Expected SUCCEEDED terminal status");
        } catch (final Exception exception) {
            Assertions.fail(ExceptionUtils.getStackTrace(exception));
        }
    }

    @Override
    final void beforeCustom(final RosExecutor rosExecutor, final Optional<String> rosMasterUri) {
        try {
            Assumptions.assumeTrue(rosExecutor != null);
            Assumptions.assumeTrue(rosMasterUri.isPresent());
            final Stopwatch stopwatch = Stopwatch.createStarted();
            this.fibonacciActionLibServer = new FibonacciActionLibServer(false);
            this.futureBasedClientNode = new FutureBasedClientNode();

            rosExecutor.startNodeMain(this.fibonacciActionLibServer, this.fibonacciActionLibServer.getDefaultNodeName().toString(), rosMasterUri.get());
            final boolean serverStarted = this.fibonacciActionLibServer.waitForStart(TIMEOUT - stopwatch.elapsed(TIME_UNIT), TIME_UNIT);
            Assertions.assertTrue(serverStarted, "Server Could not connect");

            rosExecutor.startNodeMain(this.futureBasedClientNode, this.futureBasedClientNode.getDefaultNodeName().toString(), rosMasterUri.get());
            final boolean clientStarted = this.futureBasedClientNode.waitForClientStartAndServerConnection(TIMEOUT - stopwatch.elapsed(TIME_UNIT), TIME_UNIT);
            Assumptions.assumeTrue(clientStarted, () -> "Client started. " + "Elapsed Time:" + stopwatch.elapsed(TIME_UNIT) + " timeout:" + TIMEOUT + " " + TIME_UNIT);
        } catch (final Exception exception) {
            Assumptions.assumeTrue(false, ExceptionUtils.getStackTrace(exception));
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
}