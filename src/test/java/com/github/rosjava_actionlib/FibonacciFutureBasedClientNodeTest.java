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
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
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
    private final static TimeUnit TIME_UNIT= TimeUnit.SECONDS;

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
                Assert.assertTrue("Was not connected. Elapsed Time:" + stopwatch.elapsed(TIME_UNIT) + " timeout:" + TIMEOUT, serverStarted);
            } catch (final Exception exception) {
                Assert.fail("Failed with an exception:"+ExceptionUtils.getStackTrace(exception));
            }
        }
        Assert.assertTrue("Takes too much time. Elapsed Time:" + stopwatch.elapsed(TIME_UNIT) + " timeout:" + TIMEOUT, stopwatch.elapsed(TIME_UNIT) < TIMEOUT);
    }

    /**
     * Also demonstrates status of Client by printing client status when it changes
     */
    @Test
    public final void testFutureBasedClient() {
        final Stopwatch stopwatch = Stopwatch.createStarted();
        try {

            final boolean serverStarted = this.futureBasedClientNode.waitForClientStartAndServerConnection(TIMEOUT, TIME_UNIT);
            Assert.assertTrue("Was not connected. Elapsed Time:" + stopwatch.elapsed(TIME_UNIT) + " timeout:" + TIMEOUT, serverStarted);
            final ActionFuture<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult> resultFuture = this.futureBasedClientNode.invoke(TestInputs.TEST_INPUT);

            final FibonacciActionResult result = resultFuture.get(TIMEOUT - stopwatch.elapsed(TIME_UNIT), TIME_UNIT);
            Assert.assertTrue("Timed out. Elapsed Time:" + stopwatch.elapsed(TIME_UNIT) + " timeout:" + TIMEOUT, stopwatch.elapsed(TIME_UNIT) <= TIMEOUT);
            Assert.assertNotNull("Null Result", result);
            Assert.assertTrue("Result was wrong", Arrays.equals(result.getResult().getSequence(), TestInputs.TEST_CORRECT_OUTPUT));


        } catch (final Exception exception) {
            Assert.fail(ExceptionUtils.getStackTrace(exception));
            LOGGER.error(ExceptionUtils.getStackTrace(exception));
        }
    }

    @Test
    public final void testRepeatedSequentialGoals() {
        final Stopwatch stopwatch = Stopwatch.createStarted();
        final int repetitions = 10;

        try {
            final boolean serverStarted = this.futureBasedClientNode.waitForClientStartAndServerConnection(TIMEOUT, TIME_UNIT);
            Assert.assertTrue("Was not connected. Elapsed Time:" + stopwatch.elapsed(TIME_UNIT) + " timeout:" + TIMEOUT, serverStarted);

            for (int i = 0; i < repetitions; i++) {
                final ActionFuture<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult> resultFuture =
                        this.futureBasedClientNode.invoke(TestInputs.TEST_INPUT);

                final FibonacciActionResult result = resultFuture.get(TIMEOUT - stopwatch.elapsed(TIME_UNIT), TIME_UNIT);
                Assert.assertNotNull("Null Result on repetition:" + i, result);
                Assert.assertTrue("Result was wrong on repetition:" + i,
                        Arrays.equals(result.getResult().getSequence(), TestInputs.TEST_CORRECT_OUTPUT));
                Assert.assertEquals("Client should be idle after repetition:" + i,
                        ClientState.DONE, resultFuture.getCurrentState());
            }

            Assert.assertTrue("Timed out. Elapsed Time:" + stopwatch.elapsed(TIME_UNIT) + " timeout:" + TIMEOUT,
                    stopwatch.elapsed(TIME_UNIT) <= TIMEOUT);
        } catch (final Exception exception) {
            Assert.fail(ExceptionUtils.getStackTrace(exception));
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
            Assert.assertTrue("Was not connected. Elapsed Time:" + stopwatch.elapsed(TIME_UNIT) + " timeout:" + TIMEOUT, serverStarted);

            final ActionFuture<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult> resultFuture =
                    this.futureBasedClientNode.invoke(TestInputs.TEST_INPUT);

            final FibonacciActionResult result = resultFuture.get(TIMEOUT - stopwatch.elapsed(TIME_UNIT), TIME_UNIT);
            Assert.assertNotNull("Null Result", result);
            Assert.assertTrue("Result was wrong", Arrays.equals(result.getResult().getSequence(), TestInputs.TEST_CORRECT_OUTPUT));

            final boolean statusReceived =
                    terminalStatusReceived.await(TIMEOUT - stopwatch.elapsed(TIME_UNIT), TIME_UNIT);
            Assert.assertTrue("Expected a terminal /status publication after the result", statusReceived);
            Assert.assertEquals("Expected SUCCEEDED terminal status", Byte.valueOf(GoalStatus.SUCCEEDED), terminalStatus.get());
        } catch (final Exception exception) {
            Assert.fail(ExceptionUtils.getStackTrace(exception));
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
            Assert.assertTrue("Result OK", resultReceivedOK);
            Assert.assertTrue("Timed out. Elapsed Time:" + stopwatch.elapsed(TIME_UNIT) + " timeout:" + TIMEOUT, stopwatch.elapsed(TIME_UNIT) <= TIMEOUT);

            final FibonacciActionResult fibonacciActionResult = resultFuture.get(TIMEOUT - stopwatch.elapsed(TIME_UNIT), TIME_UNIT);
            Assert.assertTrue("Result OK", Arrays.equals(fibonacciActionResult.getResult().getSequence(), TestInputs.TEST_CORRECT_OUTPUT));
            Assert.assertTrue("Timed out. Elapsed Time:" + stopwatch.elapsed(TIME_UNIT) + " timeout:" + TIMEOUT, stopwatch.elapsed(TIME_UNIT) <= TIMEOUT);
        } catch (final Exception exception) {
            Assert.fail(ExceptionUtils.getStackTrace(exception));
        }

    }

    @Test
    public final void testCancelForDifferentGoalDoesNotChangeCurrentGoalState() {
        final Stopwatch stopwatch = Stopwatch.createStarted();
        try {
            final boolean serverStarted = this.futureBasedClientNode.waitForClientStartAndServerConnection(TIMEOUT, TIME_UNIT);
            Assert.assertTrue("Was not connected. Elapsed Time:" + stopwatch.elapsed(TIME_UNIT) + " timeout:" + TIMEOUT, serverStarted);

            final ActionFuture<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult> resultFuture =
                    this.futureBasedClientNode.invoke(TestInputs.HUGE_INPUT);

            final boolean cancelSent = this.futureBasedClientNode.getActionClient().cancelGoal("unrelated-goal-id");

            Assert.assertTrue("Cancelling a different GoalID should still publish the cancel request", cancelSent);
            Assert.assertNotEquals("Cancelling a different GoalID should not change the current goal state",
                    ClientState.WAITING_FOR_CANCEL_ACK, resultFuture.getCurrentState());
        } catch (final Exception exception) {
            Assert.fail(ExceptionUtils.getStackTrace(exception));
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
            Assert.assertTrue("Was not connected. Elapsed Time:" + stopwatch.elapsed(TIME_UNIT) + " timeout:" + TIMEOUT, serverStarted);

            final ActionFuture<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult> cancelledGoalFuture =
                    this.futureBasedClientNode.invoke(TestInputs.HUGE_INPUT);
            final boolean trackedGoalObserved = goalTracked.await(TIMEOUT - stopwatch.elapsed(TIME_UNIT), TIME_UNIT);
            Assert.assertTrue("The goal was never observed as tracked before cancelGoal", trackedGoalObserved);

            final String goalId = trackedGoalId.get();
            Assert.assertNotNull("Tracked goal ID should be captured before cancelGoal", goalId);
            final boolean cancelSent = this.futureBasedClientNode.getActionClient().cancelGoal(goalId);
            Assert.assertTrue("Cancel-goal request was not published", cancelSent);

            final FibonacciActionResult cancelledGoalResult =
                    cancelledGoalFuture.get(TIMEOUT - stopwatch.elapsed(TIME_UNIT), TIME_UNIT);
            Assert.assertNotNull("Cancelled result should not be null", cancelledGoalResult);
            Assert.assertEquals("Cancel-goal should preempt the tracked goal",
                    GoalStatus.PREEMPTED, cancelledGoalResult.getStatus().getStatus());
            Assert.assertFalse("Cancelled result should be incomplete",
                    Arrays.equals(TestInputs.TEST_CORRECT_HUGE_INPUT_OUTPUT, cancelledGoalResult.getResult().getSequence()));
            Assert.assertEquals("Cancelled future should be terminal", ClientState.DONE, cancelledGoalFuture.getCurrentState());
        } catch (final Exception exception) {
            Assert.fail(ExceptionUtils.getStackTrace(exception));
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
            Assert.assertTrue("Was not connected. Elapsed Time:" + stopwatch.elapsed(TIME_UNIT) + " timeout:" + TIMEOUT, serverStarted);

            final ActionFuture<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult> cancelledGoalFuture =
                    this.futureBasedClientNode.invoke(TestInputs.HUGE_INPUT);
            final boolean trackedGoalObserved = goalTracked.await(TIMEOUT - stopwatch.elapsed(TIME_UNIT), TIME_UNIT);
            Assert.assertTrue("The goal was never observed as tracked before cancel-all", trackedGoalObserved);

            final boolean cancelAllSent = this.futureBasedClientNode.getActionClient().cancelAll();
            Assert.assertTrue("Cancel-all request was not published", cancelAllSent);

            final FibonacciActionResult cancelledGoalResult =
                    cancelledGoalFuture.get(TIMEOUT - stopwatch.elapsed(TIME_UNIT), TIME_UNIT);
            Assert.assertNotNull("Cancelled result should not be null", cancelledGoalResult);
            Assert.assertEquals("Cancel-all should preempt the tracked goal",
                    GoalStatus.PREEMPTED, cancelledGoalResult.getStatus().getStatus());
            Assert.assertFalse("Cancelled result should be incomplete",
                    Arrays.equals(TestInputs.TEST_CORRECT_HUGE_INPUT_OUTPUT, cancelledGoalResult.getResult().getSequence()));
            Assert.assertEquals("Cancelled future should be terminal", ClientState.DONE, cancelledGoalFuture.getCurrentState());

            final ActionFuture<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult> futureGoal =
                    this.futureBasedClientNode.invoke(TestInputs.TEST_INPUT);
            final FibonacciActionResult futureGoalResult =
                    futureGoal.get(TIMEOUT - stopwatch.elapsed(TIME_UNIT), TIME_UNIT);
            Assert.assertNotNull("Future result should not be null", futureGoalResult);
            Assert.assertTrue("Future goal should still succeed after cancel-all",
                    Arrays.equals(futureGoalResult.getResult().getSequence(), TestInputs.TEST_CORRECT_OUTPUT));
        } catch (final Exception exception) {
            Assert.fail(ExceptionUtils.getStackTrace(exception));
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
            Assert.assertTrue("Was not connected. Elapsed Time:" + stopwatch.elapsed(TIME_UNIT) + " timeout:" + TIMEOUT, serverStarted);

            final ActionFuture<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult> cancelledGoalFuture =
                    this.futureBasedClientNode.invoke(TestInputs.HUGE_INPUT);
            final boolean trackedGoalObserved = goalTracked.await(TIMEOUT - stopwatch.elapsed(TIME_UNIT), TIME_UNIT);
            Assert.assertTrue("The goal was never observed as tracked before cancel-before", trackedGoalObserved);

            final Time cancelStamp = trackedGoalStamp.get();
            Assert.assertNotNull("Tracked goal stamp should be captured before cancel-before", cancelStamp);
            final boolean cancelBeforeSent = this.futureBasedClientNode.getActionClient().cancelBefore(cancelStamp);
            Assert.assertTrue("Cancel-before request was not published", cancelBeforeSent);

            final FibonacciActionResult cancelledGoalResult =
                    cancelledGoalFuture.get(TIMEOUT - stopwatch.elapsed(TIME_UNIT), TIME_UNIT);
            Assert.assertNotNull("Cancelled result should not be null", cancelledGoalResult);
            Assert.assertEquals("Cancel-before should preempt the tracked goal",
                    GoalStatus.PREEMPTED, cancelledGoalResult.getStatus().getStatus());
            Assert.assertFalse("Cancelled result should be incomplete",
                    Arrays.equals(TestInputs.TEST_CORRECT_HUGE_INPUT_OUTPUT, cancelledGoalResult.getResult().getSequence()));
            Assert.assertEquals("Cancelled future should be terminal", ClientState.DONE, cancelledGoalFuture.getCurrentState());

            final ActionFuture<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult> futureGoal =
                    this.futureBasedClientNode.invoke(TestInputs.TEST_INPUT);
            final FibonacciActionResult futureGoalResult =
                    futureGoal.get(TIMEOUT - stopwatch.elapsed(TIME_UNIT), TIME_UNIT);
            Assert.assertNotNull("Future result should not be null", futureGoalResult);
            Assert.assertTrue("Future goal should still succeed after cancel-before",
                    Arrays.equals(futureGoalResult.getResult().getSequence(), TestInputs.TEST_CORRECT_OUTPUT));
        } catch (final Exception exception) {
            Assert.fail(ExceptionUtils.getStackTrace(exception));
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
            Assert.assertTrue("Could not cancel", cancel);
            final boolean resultReceivedOK = resultReceived.await(3 - stopwatch.elapsed(TimeUnit.SECONDS), TimeUnit.SECONDS);
            Assume.assumeTrue("Managed to receive result before canceling", resultReceivedOK);

            final ClientState currentClientState = resultFuture.getCurrentState();
            Assert.assertTrue("Not Cancelled, current state:" + currentClientState
                    , ClientState.DONE.equals(currentClientState)
                            || ClientState.NO_GOAL.equals(currentClientState)
                            || ClientState.RECALLING.equals(currentClientState)
                            || ClientState.WAITING_FOR_CANCEL_ACK.equals(currentClientState)
                            || ClientState.PREEMPTING.equals(currentClientState)
            );

        } catch (final Exception exception) {
            Assert.fail(ExceptionUtils.getStackTrace(exception));
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
            Assert.fail(ExceptionUtils.getStackTrace(exception));
        }
    }


    @Override
    final void beforeCustom(final RosExecutor rosExecutor, final Optional<String> rosMasterUri) {
        try {
        Assume.assumeNotNull(rosExecutor);
        Assume.assumeTrue(rosMasterUri.isPresent());
        final Stopwatch stopwatch = Stopwatch.createStarted();
        this.fibonacciActionLibServer = new FibonacciActionLibServer();
        this.futureBasedClientNode = new FutureBasedClientNode();

        rosExecutor.startNodeMain(this.fibonacciActionLibServer, this.fibonacciActionLibServer.getDefaultNodeName().toString(), rosMasterUri.get());
        final boolean serverStarted = this.fibonacciActionLibServer.waitForStart(TIMEOUT - stopwatch.elapsed(TIME_UNIT), TIME_UNIT);
        Assert.assertTrue("Server Could not connect", serverStarted);

        rosExecutor.startNodeMain(this.futureBasedClientNode, this.futureBasedClientNode.getDefaultNodeName().toString(), rosMasterUri.get());
        final boolean clientStarted = this.futureBasedClientNode.waitForClientStartAndServerConnection(TIMEOUT - stopwatch.elapsed(TIME_UNIT), TIME_UNIT);
        Assume.assumeTrue("Client started. " + "Elapsed Time:" + stopwatch.elapsed(TIME_UNIT) + " timeout:" + TIMEOUT+" "+TIME_UNIT, clientStarted);
        } catch (final Exception exception) {
            Assume.assumeNoException(exception);
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
