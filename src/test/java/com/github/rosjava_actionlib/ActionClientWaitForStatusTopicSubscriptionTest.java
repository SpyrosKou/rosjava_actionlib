/**
 * Copyright 2026 Spyros Koukas
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
import eu.test.utils.RosExecutor;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

public final class ActionClientWaitForStatusTopicSubscriptionTest extends BaseTest {
    private static final long TIMEOUT = 30;
    private static final TimeUnit TIME_UNIT = TimeUnit.SECONDS;
    private static final long NO_STATUS_TIMEOUT_MILLIS = 200;

    private RosExecutor rosExecutor = null;
    private String rosMasterUri = null;
    private FutureBasedClientNode futureBasedClientNode = null;
    private FibonacciActionLibServer fibonacciActionLibServer = null;

    @Test
    public final void waitForStatusTopicSubscriptionWaitsForFirstStatusHeartbeat() {
        try {
            final ActionClient<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult> actionClient =
                    this.futureBasedClientNode.getActionClient();

            final boolean statusReceivedBeforeServerStart =
                    actionClient.waitForStatusTopicSubscription(NO_STATUS_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            Assertions.assertFalse(statusReceivedBeforeServerStart,
                    "The client should not observe /status traffic before the server starts publishing it");

            this.fibonacciActionLibServer = new FibonacciActionLibServer();
            this.rosExecutor.startNodeMain(this.fibonacciActionLibServer,
                    this.fibonacciActionLibServer.getDefaultNodeName().toString(), this.rosMasterUri);
            final boolean serverStarted = this.fibonacciActionLibServer.waitForStart(TIMEOUT, TIME_UNIT);
            Assertions.assertTrue(serverStarted, "Server could not connect");

            final boolean statusReceivedAfterServerStart =
                    actionClient.waitForStatusTopicSubscription(TIMEOUT, TIME_UNIT);
            Assertions.assertTrue(statusReceivedAfterServerStart,
                    "Expected the client to observe the first /status heartbeat after the server starts");

            final boolean statusStillObserved = actionClient.waitForStatusTopicSubscription(0, TimeUnit.MILLISECONDS);
            Assertions.assertTrue(statusStillObserved,
                    "Status reception should remain observable after the first /status heartbeat");
        } catch (final Exception exception) {
            Assertions.fail(ExceptionUtils.getStackTrace(exception));
        }
    }

    @Override
    void beforeCustom(final RosExecutor rosExecutor, final Optional<String> rosMasterUri) {
        try {
            Assumptions.assumeTrue(rosExecutor != null);
            Assumptions.assumeTrue(rosMasterUri.isPresent());

            this.rosExecutor = rosExecutor;
            this.rosMasterUri = rosMasterUri.get();
            this.futureBasedClientNode = new FutureBasedClientNode();

            rosExecutor.startNodeMain(this.futureBasedClientNode,
                    this.futureBasedClientNode.getDefaultNodeName().toString(), this.rosMasterUri);
            final boolean clientStarted = this.futureBasedClientNode.waitForStart(TIMEOUT, TIME_UNIT);
            Assumptions.assumeTrue(clientStarted, "Client could not start");
        } catch (final Exception exception) {
            Assumptions.assumeTrue(false, ExceptionUtils.getStackTrace(exception));
        }
    }

    @Override
    void afterCustom(final RosExecutor rosExecutor) {
        try {
            if (this.fibonacciActionLibServer != null) {
                rosExecutor.stopNodeMain(this.fibonacciActionLibServer);
            }
        } catch (final Exception ignored) {
        }
        try {
            if (this.futureBasedClientNode != null) {
                rosExecutor.stopNodeMain(this.futureBasedClientNode);
            }
        } catch (final Exception ignored) {
        }
        this.rosExecutor = null;
        this.rosMasterUri = null;
        this.futureBasedClientNode = null;
        this.fibonacciActionLibServer = null;
    }
}
