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

import actionlib_tutorials.FibonacciActionFeedback;
import actionlib_tutorials.FibonacciActionGoal;
import actionlib_tutorials.FibonacciActionResult;
import eu.test.utils.RosExecutor;
import eu.test.utils.TestProperties;
import org.apache.commons.lang3.exception.ExceptionUtils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

    @BeforeEach
    public void beforeEach() {
        try {
            if (!testProperties.useExternalRosMaster()) {
                this.rosCore = RosCore.newPublic(ROS_MASTER_URI_PORT);
                this.rosCore.start();
                final boolean rosCoreStarted = this.rosCore.awaitStart(testProperties.getRosCoreStartWaitMillis(), TimeUnit.MILLISECONDS);
                Assertions.assertTrue(rosCoreStarted, "Ros core not connected");

            }
            this.fibonacciActionLibServer = new FibonacciActionLibServer();


            this.rosExecutor.startNodeMain(this.fibonacciActionLibServer, this.fibonacciActionLibServer.getDefaultNodeName().toString(), ROS_MASTER_URI);
            final boolean serverConnected = this.fibonacciActionLibServer.waitForStart(5, TimeUnit.SECONDS);
            Assertions.assertTrue(serverConnected, "Server Could not connect");
            this.futureBasedClientNode = new FutureBasedClientNode();
            this.rosExecutor.startNodeMain(this.futureBasedClientNode, this.futureBasedClientNode.getDefaultNodeName().toString(), ROS_MASTER_URI);
            final boolean clientStarted = this.futureBasedClientNode.waitForClientStartAndServerConnection(5, TimeUnit.SECONDS);

            Assertions.assertTrue(clientStarted, "ClientNotStarted");


        } catch (final Exception er3) {
            LOGGER.error(ExceptionUtils.getStackTrace(er3));
            Assertions.fail(ExceptionUtils.getStackTrace(er3));

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
                Assertions.assertTrue(cancel, "Could not cancel");

                try {
                    final FibonacciActionResult fibonacciActionResult = resultFuture.get(10, TimeUnit.SECONDS);
                    Assertions.assertNotNull(fibonacciActionResult, "result should not be null");
                    final Set<ClientState> expectedValues = EnumSet.of(ClientState.PENDING, ClientState.RECALLING, ClientState.WAITING_FOR_CANCEL_ACK, ClientState.DONE);
                    final ClientState clientState = resultFuture.getCurrentState();
                    Assertions.assertTrue(expectedValues.contains(clientState), "Checking state, failed. Managed to receive result before canceling. Is resultOk:" + TestInputs.TEST_CORRECT_HUGE_INPUT_OUTPUT.equals(fibonacciActionResult.getResult().getSequence()) + " expeced size:" + TestInputs.TEST_CORRECT_HUGE_INPUT_OUTPUT.length + " current size:" + fibonacciActionResult.getResult().getSequence().length);
                    Assertions.assertFalse(Arrays.equals(TestInputs.TEST_CORRECT_HUGE_INPUT_OUTPUT, fibonacciActionResult.getResult().getSequence()), "Results should normally NOT be correct");
                } catch (final Exception exception) {
                    LOGGER.error(ExceptionUtils.getStackTrace(exception));
                    Assertions.fail(ExceptionUtils.getStackTrace(exception));
                }

                final ClientState currentClientState = resultFuture.getCurrentState();
                Assertions.assertEquals(ClientState.DONE, currentClientState, "Not Cancelled, current state:" + currentClientState);
                LOGGER.trace("ClientState:{}", currentClientState);

            } catch (final Exception exception) {
                Assertions.fail(ExceptionUtils.getStackTrace(exception));
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
            Assertions.assertNotNull(fibonacciActionResult, "result should not be null");

            Assertions.assertTrue(Arrays.equals(TestInputs.TEST_CORRECT_OUTPUT, fibonacciActionResult.getResult().getSequence()), "Results Error");


            final ClientState currentClientState = resultFuture.getCurrentState();
            Assertions.assertEquals(ClientState.DONE, currentClientState, "Not Cancelled, current state:" + currentClientState);
            LOGGER.trace("ClientState:" + currentClientState);

        } catch (final Exception exception) {
            Assertions.fail(ExceptionUtils.getStackTrace(exception));
        }


    }

    @AfterEach
    public void afterEach() {
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