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
import eu.test.utils.TestProperties;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.*;
import org.ros.RosCore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Test wait methods with {@link FibonacciActionLibServer}
 */
public class WaitMethodsClientServerTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());


    private static final TestProperties testProperties = TestProperties.getFromDefaultFile();

    private static final String ROS_HOST_IP = testProperties.getRosHostIp();
    private static final int ROS_MASTER_URI_PORT = testProperties.getRosMasterUriPort();
    private static final String ROS_MASTER_URI = testProperties.getRosMasterUri();
    private RosCore rosCore = null;

    private FutureBasedClientNode futureBasedClientNode = null;

    private FibonacciActionLibServer fibonacciActionLibServer = null;
    private final RosExecutor rosExecutor = new RosExecutor(ROS_HOST_IP);

    @Before
    public void before() {
        try {
            this.rosCore = RosCore.newPublic(ROS_MASTER_URI_PORT);
            this.rosCore.start();
            final boolean rosCoreStarted = this.rosCore.awaitStart(testProperties.getRosCoreStartWaitMillis(), TimeUnit.MILLISECONDS);
            Assert.assertTrue("Ros core not connected", rosCoreStarted);
            this.fibonacciActionLibServer = new FibonacciActionLibServer();
            this.rosExecutor.startNodeMain(this.fibonacciActionLibServer, this.fibonacciActionLibServer.getDefaultNodeName().toString(), ROS_MASTER_URI);
            final boolean serverConnected = this.fibonacciActionLibServer.waitForStart(5, TimeUnit.SECONDS);
            Assert.assertTrue("Server Could not connect", serverConnected);
            this.futureBasedClientNode = new FutureBasedClientNode();
            this.rosExecutor.startNodeMain(this.futureBasedClientNode, this.futureBasedClientNode.getDefaultNodeName().toString(), ROS_MASTER_URI);
            final boolean clientStarted = this.futureBasedClientNode.waitForClientStartAndServerConnection(5, TimeUnit.SECONDS);

            Assert.assertTrue("ClientNotStarted", clientStarted);


        } catch (final Exception er3) {
            LOGGER.error(ExceptionUtils.getStackTrace(er3));
            Assert.fail(ExceptionUtils.getStackTrace(er3));

        }

    }


    /**
     *
     */
    @Test
    public void testCancel() {
        try {
            try {
                final ActionFuture<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult> resultFuture = this.futureBasedClientNode.invoke(TestInputs.HUGE_INPUT);
                final boolean cancel = resultFuture.cancel(true);
                Assert.assertTrue("Could not cancel", cancel);

                try {
                    final FibonacciActionResult fibonacciActionResult = resultFuture.get(10, TimeUnit.SECONDS);
                    Assert.assertNotNull("result should not be null", fibonacciActionResult);
                    final Set<ClientState> expectedValues = EnumSet.of(ClientState.PENDING, ClientState.NO_GOAL, ClientState.RECALLING, ClientState.WAITING_FOR_CANCEL_ACK);
                    final ClientState clientState = resultFuture.getCurrentState();
                    Assert.assertTrue("Checking state, failed. Managed to receive result before canceling. Is resultOk:" + TestInputs.TEST_CORRECT_HUGE_INPUT_OUTPUT.equals(fibonacciActionResult.getResult().getSequence()) + " expeced size:" + TestInputs.TEST_CORRECT_HUGE_INPUT_OUTPUT.length + " current size:" + fibonacciActionResult.getResult().getSequence().length, expectedValues.contains(clientState));
                    Assert.assertFalse("Results should normally NOT be correct", Arrays.equals(TestInputs.TEST_CORRECT_HUGE_INPUT_OUTPUT, fibonacciActionResult.getResult().getSequence()));
                } catch (final Exception exception) {
                    LOGGER.error(ExceptionUtils.getStackTrace(exception));
                    Assert.fail(ExceptionUtils.getStackTrace(exception));
                }

                final ClientState currentClientState = resultFuture.getCurrentState();
                Assert.assertEquals("Not Cancelled, current state:" + currentClientState, ClientState.NO_GOAL, currentClientState);
                LOGGER.trace("ClientState:" + currentClientState);

            } catch (final Exception exception) {
                Assert.fail(ExceptionUtils.getStackTrace(exception));
            }


        } catch (final Exception e) {
            LOGGER.error(ExceptionUtils.getStackTrace(e));
        }
    }

    /**
     *
     */
    @Test
    public void testGoal() {


        try {
            final ActionFuture<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult> resultFuture = this.futureBasedClientNode.invoke(TestInputs.TEST_INPUT);


            final FibonacciActionResult fibonacciActionResult = resultFuture.get(10, TimeUnit.SECONDS);
            Assert.assertNotNull("result should not be null", fibonacciActionResult);

            Assert.assertTrue("Results Error", Arrays.equals(TestInputs.TEST_CORRECT_HUGE_INPUT_OUTPUT, fibonacciActionResult.getResult().getSequence()));


            final ClientState currentClientState = resultFuture.getCurrentState();
            Assert.assertEquals("Not Cancelled, current state:" + currentClientState, ClientState.NO_GOAL, currentClientState);
            LOGGER.trace("ClientState:" + currentClientState);

        } catch (final Exception exception) {
            Assert.fail(ExceptionUtils.getStackTrace(exception));
        }


    }

    @After
    public void after() {
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


        try {
            if (this.rosExecutor != null) {
                this.rosExecutor.stopAllNodesAndClose();
            }
        } catch (final Exception e) {
            LOGGER.error(ExceptionUtils.getStackTrace(e));
        }

        try {
            if (this.rosCore != null) {
                this.rosCore.shutdown();

            }
        } catch (final Exception e) {
            LOGGER.error(ExceptionUtils.getStackTrace(e));
        }

        this.futureBasedClientNode = null;
        this.fibonacciActionLibServer = null;
        this.rosCore = null;
    }

}