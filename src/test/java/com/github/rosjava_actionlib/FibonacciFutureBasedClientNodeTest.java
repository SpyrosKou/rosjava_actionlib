/**
 * Copyright 2020 Spyros Koukas
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

import actionlib_tutorials.FibonacciActionFeedback;
import actionlib_tutorials.FibonacciActionGoal;
import actionlib_tutorials.FibonacciActionResult;
import com.google.common.base.Stopwatch;
import eu.test.utils.RosExecutor;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Focus on {@link FutureBasedClientNode} status changes
 * Demonstrate future usage
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
    public void testMultipleWaitForConnections() {
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
    public void testFutureBasedClient() {
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
    public void testResultListener() {
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
    public void testCancel() {
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
                    , ClientState.RECALLING.equals(currentClientState)
                            || ClientState.WAITING_FOR_CANCEL_ACK.equals(currentClientState)
                            || ClientState.PREEMPTING.equals(currentClientState)
//                            || ClientState.DONE.equals(currentClientState)
            );

        } catch (final Exception exception) {
            Assert.fail(ExceptionUtils.getStackTrace(exception));
        }

    }




    @Override
    final void beforeCustom(final RosExecutor rosExecutor, final Optional<String> rosMasterUri) {
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

}