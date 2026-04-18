/**
 * Copyright 2023 Spyros Koukas
 *
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
import org.ros.internal.message.Message;
import org.ros.message.Time;
import org.ros.node.ConnectedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Focus on {@link FutureBasedClientNode} status changes
 * Demonstrate future usage
 * @author Spyros Koukas
 */
public class FibonacciFutureBasedClientNodeTest extends BaseTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private FutureBasedClientNode futureBasedClientNode = null;
    private FibonacciActionLibServer fibonacciActionLibServer = null;

    private final static long TIMEOUT = 60;
    private final static TimeUnit TIME_UNIT = TimeUnit.SECONDS;

    /**
     * Also demonstrates status of Client by printing client status when it changes
     */
    @Test
    public final void testMultipleWaitForConnections() {
        final Stopwatch stopwatch = Stopwatch.createStarted();
        final int reps = 1000;
        for (int i = 0; i < reps; i++) {
            try {
                final boolean serverStarted = this.futureBasedClientNode.waitForClientStartAndServerConnection(TIMEOUT, TIME_UNIT);
                Assertions.assertTrue(serverStarted, "Was not connected. Elapsed Time:" + stopwatch.elapsed(TIME_UNIT) + " timeout:" + TIMEOUT);
            } catch (final Exception exception) {
                Assertions.fail("Failed with an exception:" + ExceptionUtils.getStackTrace(exception));
            }
        }
        Assertions.assertTrue(stopwatch.elapsed(TIME_UNIT) < TIMEOUT, "Takes too much time. Elapsed Time:" + stopwatch.elapsed(TIME_UNIT) + " timeout:" + TIMEOUT);
    }

    /**
     * Also demonstrates status of Client by printing client status when it changes
     */
    @Test
    public final void testFutureBasedClient() {
        final Stopwatch stopwatch = Stopwatch.createStarted();
        try {

            final boolean serverStarted = this.futureBasedClientNode.waitForClientStartAndServerConnection(TIMEOUT, TIME_UNIT);
            Assertions.assertTrue(serverStarted, "Was not connected. Elapsed Time:" + stopwatch.elapsed(TIME_UNIT) + " timeout:" + TIMEOUT);
            final ActionFuture<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult> resultFuture = this.futureBasedClientNode.invoke(TestInputs.TEST_INPUT);

            final FibonacciActionResult result = resultFuture.get(TIMEOUT - stopwatch.elapsed(TIME_UNIT), TIME_UNIT);
            Assertions.assertTrue(stopwatch.elapsed(TIME_UNIT) <= TIMEOUT, "Timed out. Elapsed Time:" + stopwatch.elapsed(TIME_UNIT) + " timeout:" + TIMEOUT);
            Assertions.assertNotNull(result, "Null Result");
            Assertions.assertTrue(Arrays.equals(result.getResult().getSequence(), TestInputs.TEST_CORRECT_OUTPUT), "Result was wrong");


        } catch (final Exception exception) {
            Assertions.fail(ExceptionUtils.getStackTrace(exception));
            LOGGER.error(ExceptionUtils.getStackTrace(exception));
        }
    }

    @Test
    public final void testRepeatedSequentialGoals() {
        final Stopwatch stopwatch = Stopwatch.createStarted();
        final int repetitions = 10;

        try {
            final boolean serverStarted = this.futureBasedClientNode.waitForClientStartAndServerConnection(TIMEOUT, TIME_UNIT);
            Assertions.assertTrue(serverStarted, "Was not connected. Elapsed Time:" + stopwatch.elapsed(TIME_UNIT) + " timeout:" + TIMEOUT);

            for (int i = 0; i < repetitions; i++) {
                final ActionFuture<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult> resultFuture =
                        this.futureBasedClientNode.invoke(TestInputs.TEST_INPUT);

                final FibonacciActionResult result = resultFuture.get(TIMEOUT - stopwatch.elapsed(TIME_UNIT), TIME_UNIT);
                Assertions.assertNotNull(result, "Null Result on repetition:" + i);
                Assertions.assertTrue(
                        Arrays.equals(result.getResult().getSequence(), TestInputs.TEST_CORRECT_OUTPUT),
                        "Result was wrong on repetition:" + i);
                Assertions.assertEquals(
                        ClientState.DONE, resultFuture.getCurrentState(),
                        "Client should be idle after repetition:" + i);
            }

            Assertions.assertTrue(
                    stopwatch.elapsed(TIME_UNIT) <= TIMEOUT,
                    "Timed out. Elapsed Time:" + stopwatch.elapsed(TIME_UNIT) + " timeout:" + TIMEOUT);
        } catch (final Exception exception) {
            Assertions.fail(ExceptionUtils.getStackTrace(exception));
        }
    }

    @Test
    public final void testTerminalStatusIsPublishedAfterResult() {
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

            final boolean statusReceived =
                    terminalStatusReceived.await(TIMEOUT - stopwatch.elapsed(TIME_UNIT), TIME_UNIT);
            Assertions.assertTrue(statusReceived, "Expected a terminal /status publication after the result");
            Assertions.assertEquals(Byte.valueOf(GoalStatus.SUCCEEDED), terminalStatus.get(), "Expected SUCCEEDED terminal status");
        } catch (final Exception exception) {
            Assertions.fail(ExceptionUtils.getStackTrace(exception));
        }
    }

    @Test
    public final void testResultListener() {
        final Stopwatch stopwatch = Stopwatch.createStarted();
        final CountDownLatch resultReceived = new CountDownLatch(1);

        final ActionClientResultListener<FibonacciActionResult> resultListener = fibonacciActionResult -> resultReceived.countDown();
        this.futureBasedClientNode.getActionClient().addActionClientResultListener(resultListener);
        final ActionFuture<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult> resultFuture = this.futureBasedClientNode.invoke(TestInputs.TEST_INPUT);
        try {
            final boolean resultReceivedOK = resultReceived.await(TIMEOUT - stopwatch.elapsed(TIME_UNIT), TIME_UNIT);
            Assertions.assertTrue(resultReceivedOK, "Result OK");
            Assertions.assertTrue(stopwatch.elapsed(TIME_UNIT) <= TIMEOUT, "Timed out. Elapsed Time:" + stopwatch.elapsed(TIME_UNIT) + " timeout:" + TIMEOUT);

            final FibonacciActionResult fibonacciActionResult = resultFuture.get(TIMEOUT - stopwatch.elapsed(TIME_UNIT), TIME_UNIT);
            Assertions.assertTrue(Arrays.equals(fibonacciActionResult.getResult().getSequence(), TestInputs.TEST_CORRECT_OUTPUT), "Result OK");
            Assertions.assertTrue(stopwatch.elapsed(TIME_UNIT) <= TIMEOUT, "Timed out. Elapsed Time:" + stopwatch.elapsed(TIME_UNIT) + " timeout:" + TIMEOUT);
        } catch (final Exception exception) {
            Assertions.fail(ExceptionUtils.getStackTrace(exception));
        }

    }

    @Test
    public final void testCancelForDifferentGoalDoesNotChangeCurrentGoalState() {
        final Stopwatch stopwatch = Stopwatch.createStarted();
        try {
            final boolean serverStarted = this.futureBasedClientNode.waitForClientStartAndServerConnection(TIMEOUT, TIME_UNIT);
            Assertions.assertTrue(serverStarted, "Was not connected. Elapsed Time:" + stopwatch.elapsed(TIME_UNIT) + " timeout:" + TIMEOUT);

            final ActionFuture<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult> resultFuture =
                    this.futureBasedClientNode.invoke(TestInputs.HUGE_INPUT);

            final boolean cancelSent = this.futureBasedClientNode.getActionClient().cancelGoal("unrelated-goal-id");

            Assertions.assertTrue(cancelSent, "Cancelling a different GoalID should still publish the cancel request");
            Assertions.assertNotEquals(
                    ClientState.WAITING_FOR_CANCEL_ACK, resultFuture.getCurrentState(),
                    "Cancelling a different GoalID should not change the current goal state");
        } catch (final Exception exception) {
            Assertions.fail(ExceptionUtils.getStackTrace(exception));
        }
    }

    @Test
    public final void testCancelGoalCancelsTrackedGoalById() {
        final Stopwatch stopwatch = Stopwatch.createStarted();
        final CountDownLatch goalTracked = new CountDownLatch(1);
        final AtomicReference<String> trackedGoalId = new AtomicReference<>();

        this.futureBasedClientNode.getActionClient().addActionClientStatusListener(statusArray -> statusArray.getStatusList().stream()
                .filter(goalStatus -> goalStatus != null)
                .filter(goalStatus -> goalStatus.getGoalId() != null)
                .filter(goalStatus -> goalStatus.getStatus() == GoalStatus.PENDING
                        || goalStatus.getStatus() == GoalStatus.ACTIVE
                        || goalStatus.getStatus() == GoalStatus.PREEMPTING
                        || goalStatus.getStatus() == GoalStatus.RECALLING)
                .map(goalStatus -> goalStatus.getGoalId().getId())
                .filter(id -> id != null && !id.isBlank())
                .findFirst()
                .ifPresent(id -> {
                    if (trackedGoalId.compareAndSet(null, id)) {
                        goalTracked.countDown();
                    }
                }));

        try {
            final boolean serverStarted = this.futureBasedClientNode.waitForClientStartAndServerConnection(TIMEOUT, TIME_UNIT);
            Assertions.assertTrue(serverStarted, "Was not connected. Elapsed Time:" + stopwatch.elapsed(TIME_UNIT) + " timeout:" + TIMEOUT);

            final ActionFuture<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult> cancelledGoalFuture =
                    this.futureBasedClientNode.invoke(TestInputs.HUGE_INPUT);
            final boolean trackedGoalObserved = goalTracked.await(TIMEOUT - stopwatch.elapsed(TIME_UNIT), TIME_UNIT);
            Assertions.assertTrue(trackedGoalObserved, "The goal was never observed as tracked before cancelGoal");

            final String goalId = trackedGoalId.get();
            Assertions.assertNotNull(goalId, "Tracked goal ID should be captured before cancelGoal");
            final boolean cancelSent = this.futureBasedClientNode.getActionClient().cancelGoal(goalId);
            Assertions.assertTrue(cancelSent, "Cancel-goal request was not published");

            final FibonacciActionResult cancelledGoalResult =
                    cancelledGoalFuture.get(TIMEOUT - stopwatch.elapsed(TIME_UNIT), TIME_UNIT);
            Assertions.assertNotNull(cancelledGoalResult, "Cancelled result should not be null");
            Assertions.assertEquals(
                    GoalStatus.PREEMPTED, cancelledGoalResult.getStatus().getStatus(),
                    "Cancel-goal should preempt the tracked goal");
            Assertions.assertFalse(
                    Arrays.equals(TestInputs.TEST_CORRECT_HUGE_INPUT_OUTPUT, cancelledGoalResult.getResult().getSequence()),
                    "Cancelled result should be incomplete");
            Assertions.assertEquals(ClientState.DONE, cancelledGoalFuture.getCurrentState(), "Cancelled future should be terminal");
        } catch (final Exception exception) {
            Assertions.fail(ExceptionUtils.getStackTrace(exception));
        }
    }

    @Test
    public final void testCancelAllCancelsCurrentGoalButNotFutureGoal() {
        final Stopwatch stopwatch = Stopwatch.createStarted();
        final CountDownLatch goalTracked = new CountDownLatch(1);

        this.futureBasedClientNode.getActionClient().addActionClientStatusListener(statusArray -> statusArray.getStatusList().stream()
                .filter(goalStatus -> goalStatus != null)
                .map(goalStatus -> goalStatus.getStatus())
                .filter(status -> status == GoalStatus.PENDING
                        || status == GoalStatus.ACTIVE
                        || status == GoalStatus.PREEMPTING
                        || status == GoalStatus.RECALLING)
                .findFirst()
                .ifPresent(status -> goalTracked.countDown()));

        try {
            final boolean serverStarted = this.futureBasedClientNode.waitForClientStartAndServerConnection(TIMEOUT, TIME_UNIT);
            Assertions.assertTrue(serverStarted, "Was not connected. Elapsed Time:" + stopwatch.elapsed(TIME_UNIT) + " timeout:" + TIMEOUT);

            final ActionFuture<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult> cancelledGoalFuture =
                    this.futureBasedClientNode.invoke(TestInputs.HUGE_INPUT);
            final boolean trackedGoalObserved = goalTracked.await(TIMEOUT - stopwatch.elapsed(TIME_UNIT), TIME_UNIT);
            Assertions.assertTrue(trackedGoalObserved, "The goal was never observed as tracked before cancel-all");

            final boolean cancelAllSent = this.futureBasedClientNode.getActionClient().cancelAll();
            Assertions.assertTrue(cancelAllSent, "Cancel-all request was not published");

            final FibonacciActionResult cancelledGoalResult =
                    cancelledGoalFuture.get(TIMEOUT - stopwatch.elapsed(TIME_UNIT), TIME_UNIT);
            Assertions.assertNotNull(cancelledGoalResult, "Cancelled result should not be null");
            Assertions.assertEquals(
                    GoalStatus.PREEMPTED, cancelledGoalResult.getStatus().getStatus(),
                    "Cancel-all should preempt the tracked goal");
            Assertions.assertFalse(
                    Arrays.equals(TestInputs.TEST_CORRECT_HUGE_INPUT_OUTPUT, cancelledGoalResult.getResult().getSequence()),
                    "Cancelled result should be incomplete");
            Assertions.assertEquals(ClientState.DONE, cancelledGoalFuture.getCurrentState(), "Cancelled future should be terminal");

            final ActionFuture<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult> futureGoal =
                    this.futureBasedClientNode.invoke(TestInputs.TEST_INPUT);
            final FibonacciActionResult futureGoalResult =
                    futureGoal.get(TIMEOUT - stopwatch.elapsed(TIME_UNIT), TIME_UNIT);
            Assertions.assertNotNull(futureGoalResult, "Future result should not be null");
            Assertions.assertTrue(
                    Arrays.equals(futureGoalResult.getResult().getSequence(), TestInputs.TEST_CORRECT_OUTPUT),
                    "Future goal should still succeed after cancel-all");
        } catch (final Exception exception) {
            Assertions.fail(ExceptionUtils.getStackTrace(exception));
        }
    }

    @Test
    public final void testCancelBeforeCancelsOlderTrackedGoalButNotFutureGoal() {
        final Stopwatch stopwatch = Stopwatch.createStarted();
        final CountDownLatch goalTracked = new CountDownLatch(1);
        final AtomicReference<Time> trackedGoalStamp = new AtomicReference<>();

        this.futureBasedClientNode.getActionClient().addActionClientStatusListener(statusArray -> statusArray.getStatusList().stream()
                .filter(goalStatus -> goalStatus != null)
                .filter(goalStatus -> goalStatus.getGoalId() != null)
                .filter(goalStatus -> goalStatus.getGoalId().getStamp() != null)
                .filter(goalStatus -> goalStatus.getStatus() == GoalStatus.PENDING
                        || goalStatus.getStatus() == GoalStatus.ACTIVE
                        || goalStatus.getStatus() == GoalStatus.PREEMPTING
                        || goalStatus.getStatus() == GoalStatus.RECALLING)
                .findFirst()
                .ifPresent(goalStatus -> {
                    if (trackedGoalStamp.compareAndSet(null, new Time(goalStatus.getGoalId().getStamp()))) {
                        goalTracked.countDown();
                    }
                }));

        try {
            final boolean serverStarted = this.futureBasedClientNode.waitForClientStartAndServerConnection(TIMEOUT, TIME_UNIT);
            Assertions.assertTrue(serverStarted, "Was not connected. Elapsed Time:" + stopwatch.elapsed(TIME_UNIT) + " timeout:" + TIMEOUT);

            final ActionFuture<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult> cancelledGoalFuture =
                    this.futureBasedClientNode.invoke(TestInputs.HUGE_INPUT);
            final boolean trackedGoalObserved = goalTracked.await(TIMEOUT - stopwatch.elapsed(TIME_UNIT), TIME_UNIT);
            Assertions.assertTrue(trackedGoalObserved, "The goal was never observed as tracked before cancel-before");

            final Time cancelStamp = trackedGoalStamp.get();
            Assertions.assertNotNull(cancelStamp, "Tracked goal stamp should be captured before cancel-before");
            final boolean cancelBeforeSent = this.futureBasedClientNode.getActionClient().cancelBefore(cancelStamp);
            Assertions.assertTrue(cancelBeforeSent, "Cancel-before request was not published");

            final FibonacciActionResult cancelledGoalResult =
                    cancelledGoalFuture.get(TIMEOUT - stopwatch.elapsed(TIME_UNIT), TIME_UNIT);
            Assertions.assertNotNull(cancelledGoalResult, "Cancelled result should not be null");
            Assertions.assertEquals(
                    GoalStatus.PREEMPTED, cancelledGoalResult.getStatus().getStatus(),
                    "Cancel-before should preempt the tracked goal");
            Assertions.assertFalse(
                    Arrays.equals(TestInputs.TEST_CORRECT_HUGE_INPUT_OUTPUT, cancelledGoalResult.getResult().getSequence()),
                    "Cancelled result should be incomplete");
            Assertions.assertEquals(ClientState.DONE, cancelledGoalFuture.getCurrentState(), "Cancelled future should be terminal");

            final ActionFuture<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult> futureGoal =
                    this.futureBasedClientNode.invoke(TestInputs.TEST_INPUT);
            final FibonacciActionResult futureGoalResult =
                    futureGoal.get(TIMEOUT - stopwatch.elapsed(TIME_UNIT), TIME_UNIT);
            Assertions.assertNotNull(futureGoalResult, "Future result should not be null");
            Assertions.assertTrue(
                    Arrays.equals(futureGoalResult.getResult().getSequence(), TestInputs.TEST_CORRECT_OUTPUT),
                    "Future goal should still succeed after cancel-before");
        } catch (final Exception exception) {
            Assertions.fail(ExceptionUtils.getStackTrace(exception));
        }
    }

    @Test
    public final void testCancel() {
        final Stopwatch stopwatch = Stopwatch.createStarted();
        final CountDownLatch resultReceived = new CountDownLatch(1);

        final ActionClientResultListener<FibonacciActionResult> resultListener = fibonacciActionResult -> resultReceived.countDown();
        this.futureBasedClientNode.getActionClient().addActionClientResultListener(resultListener);
        final ActionFuture<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult> resultFuture = this.futureBasedClientNode.invoke(TestInputs.HUGE_INPUT);
        try {
            final boolean cancel = resultFuture.cancel(true);
            Assertions.assertTrue(cancel, "Could not cancel");
            final boolean resultReceivedOK = resultReceived.await(3 - stopwatch.elapsed(TimeUnit.SECONDS), TimeUnit.SECONDS);
            Assumptions.assumeTrue(resultReceivedOK, "Managed to receive result before canceling");

            final ClientState currentClientState = resultFuture.getCurrentState();
            Assertions.assertTrue(
                    ClientState.DONE.equals(currentClientState)
                            || ClientState.NO_GOAL.equals(currentClientState)
                            || ClientState.RECALLING.equals(currentClientState)
                            || ClientState.WAITING_FOR_CANCEL_ACK.equals(currentClientState)
                            || ClientState.PREEMPTING.equals(currentClientState),
                    "Not Cancelled, current state:" + currentClientState
            );

        } catch (final Exception exception) {
            Assertions.fail(ExceptionUtils.getStackTrace(exception));
        }

    }

    @Test
    public final void testResultForDifferentGoalIsIgnoredBeforeAnyGoalIsSent() {
        try {
            final ActionClient<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult> actionClient =
                    this.futureBasedClientNode.getActionClient();
            final ConnectedNode connectedNode = (ConnectedNode) getDeclaredFieldValue(ActionClient.class, actionClient, "connectedNode");
            final FibonacciActionResult resultMessage = connectedNode.getDefaultMessageFactory().newFromType(FibonacciActionResult._TYPE);
            resultMessage.getStatus().getGoalId().setId("unrelated-goal-id");

            final Method gotResult = ActionClient.class.getDeclaredMethod("gotResult", Message.class);
            gotResult.setAccessible(true);
            gotResult.invoke(actionClient, resultMessage);
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
            this.fibonacciActionLibServer = new FibonacciActionLibServer();
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
            rosExecutor.stopNodeMain(fibonacciActionLibServer);
        } catch (final Exception e2) {
            LOGGER.error(ExceptionUtils.getStackTrace(e2));
        }
        try {
            rosExecutor.stopNodeMain(futureBasedClientNode);
        } catch (final Exception e2) {
            LOGGER.error(ExceptionUtils.getStackTrace(e2));
        }
        this.futureBasedClientNode = null;
        this.fibonacciActionLibServer = null;
    }

    private static final Object getDeclaredFieldValue(final Class<?> ownerClass, final Object target, final String fieldName) {
        try {
            final Field field = ownerClass.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (final Exception exception) {
            throw new AssertionError(exception);
        }
    }

}