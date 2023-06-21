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
import actionlib_tutorials.FibonacciGoal;
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
 * Focus on {@link FutureBasedClient} status changes
 * Demonstrate future usage
 */
public class FibonacciFutureBasedClientTest extends BaseTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private FutureBasedClient futureBasedClient = null;
    private FibonacciActionLibServer fibonacciActionLibServer = null;

    private final long timeout = 60;
    private final TimeUnit timeUnit = TimeUnit.SECONDS;
    private final int TEST_INPUT = 5;
    private final int[] TEST_CORRECT_OUTPUT=new FibonacciCalculator().fibonacciSequence(TEST_INPUT);

    /**
     * Also demonstrates status of Client by printing client status when it changes
     */
    @Test
    public void testMultipleWaitForConnections() {
        final Stopwatch stopwatch = Stopwatch.createStarted();
        final int reps = 1000;
        for (int i = 0; i < reps; i++) {
            try {
                final boolean result = this.futureBasedClient.waitForServerConnection(this.timeout, this.timeUnit);
                Assert.assertTrue("Was not connected. Elapsed Time:" + stopwatch.elapsed(this.timeUnit) + " timeout:" + timeout, result);
            } catch (final Exception exception) {
                Assert.fail(ExceptionUtils.getStackTrace(exception));
            }
        }
        Assert.assertTrue("Takes too much time. Elapsed Time:" + stopwatch.elapsed(this.timeUnit) + " timeout:" + timeout, stopwatch.elapsed(this.timeUnit) < this.timeout);
    }

    /**
     * Also demonstrates status of Client by printing client status when it changes
     */
    @Test
    public void testFutureBasedClient() {
        final Stopwatch stopwatch = Stopwatch.createStarted();
        try {

            final boolean serverStarted = this.futureBasedClient.waitForServerConnection(this.timeout - stopwatch.elapsed(this.timeUnit), timeUnit);
            Assert.assertTrue("Server Not Started", serverStarted);
            final ActionFuture<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult> resultFuture = this.futureBasedClient.invoke(TEST_INPUT);

            final FibonacciActionResult result = resultFuture.get(this.timeout - stopwatch.elapsed(this.timeUnit), this.timeUnit);
            Assert.assertTrue("Timed out. Elapsed Time:" + stopwatch.elapsed(this.timeUnit) + " timeout:" + timeout, stopwatch.elapsed(this.timeUnit) <= this.timeout);
            Assert.assertNotNull("Null Result", result);

            Assert.assertTrue("Result was wrong", Arrays.equals(result.getResult().getSequence(), TEST_CORRECT_OUTPUT));
            ;

        } catch (final Exception exception) {
            Assert.fail(ExceptionUtils.getStackTrace(exception));
            LOGGER.error(ExceptionUtils.getStackTrace(exception));
        }
    }

    @Test
    public void testResultListener() {
        final Stopwatch stopwatch=Stopwatch.createStarted();
        final CountDownLatch resultReceived = new CountDownLatch(1);

        final ActionClientResultListener<FibonacciActionResult> resultListener = fibonacciActionResult -> resultReceived.countDown();
        this.futureBasedClient.getActionClient().addListener(resultListener);
        final ActionFuture<FibonacciActionGoal,FibonacciActionFeedback,FibonacciActionResult> resultFuture= this.futureBasedClient.invoke(TEST_INPUT);
        try {
            final boolean resultReceivedOK=resultReceived.await(this.timeout- stopwatch.elapsed(this.timeUnit),this.timeUnit);
            Assert.assertTrue("Result OK",resultReceivedOK);
            Assert.assertTrue("Timed out. Elapsed Time:" + stopwatch.elapsed(this.timeUnit) + " timeout:" + timeout, stopwatch.elapsed(this.timeUnit) <= this.timeout);

            final FibonacciActionResult fibonacciActionResult=resultFuture.get(this.timeout- stopwatch.elapsed(this.timeUnit),this.timeUnit);
            Assert.assertTrue("Result OK",Arrays.equals(fibonacciActionResult.getResult().getSequence(), TEST_CORRECT_OUTPUT));
            Assert.assertTrue("Timed out. Elapsed Time:" + stopwatch.elapsed(this.timeUnit) + " timeout:" + timeout, stopwatch.elapsed(this.timeUnit) <= this.timeout);
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
        this.futureBasedClient = new FutureBasedClient();

        rosExecutor.startNodeMain(this.fibonacciActionLibServer, this.fibonacciActionLibServer.getDefaultNodeName().toString(), rosMasterUri.get());
        this.fibonacciActionLibServer.waitForStart();
        rosExecutor.startNodeMain(this.futureBasedClient, this.futureBasedClient.getDefaultNodeName().toString(), rosMasterUri.get());
        final boolean serverStarted = this.futureBasedClient.waitForServerConnection(this.timeout - stopwatch.elapsed(this.timeUnit), this.timeUnit);
        Assume.assumeTrue("Server Not Started. " + "Elapsed Time:" + stopwatch.elapsed(this.timeUnit) + " timeout:" + timeout, serverStarted);

    }

    @Override
    final void afterCustom(final RosExecutor rosExecutor) {
        try {
            rosExecutor.stopNodeMain(fibonacciActionLibServer);
        } catch (final Exception e2) {
            LOGGER.error(ExceptionUtils.getStackTrace(e2));
        }
        try {
            rosExecutor.stopNodeMain(futureBasedClient);
        } catch (final Exception e2) {
            LOGGER.error(ExceptionUtils.getStackTrace(e2));
        }
        this.futureBasedClient = null;
        this.fibonacciActionLibServer = null;
    }

}