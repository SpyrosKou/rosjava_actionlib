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
import eu.test.utils.RosExecutor;
import eu.test.utils.TestProperties;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.ros.RosCore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.TimeUnit;

/**
 * Focus on {@link FutureBasedClient} status changes
 * Demonstrate future usage
 */
public class FutureBasedClientTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final TestProperties testProperties = TestProperties.getFromDefaultFile();

    private static final String ROS_HOST_IP = testProperties.getRosHostIp();
    private static final int ROS_MASTER_URI_PORT = testProperties.getRosMasterUriPort();
    private static final String ROS_MASTER_URI = testProperties.getRosMasterUri();
    private RosCore rosCore = null;

    private FutureBasedClient futureBasedClient = null;
    private SimpleServer simpleServer = null;
    private final RosExecutor rosExecutor = new RosExecutor(ROS_HOST_IP);

    @Before
    public void before() {
        try {
            this.rosCore = RosCore.newPublic(ROS_MASTER_URI_PORT);
            this.rosCore.start();
            this.rosCore.awaitStart(testProperties.getRosCoreStartWaitMillis(), TimeUnit.MILLISECONDS);

            this.simpleServer = new SimpleServer();
            this.futureBasedClient = new FutureBasedClient();

            this.rosExecutor.startNodeMain(this.simpleServer, this.simpleServer.getDefaultNodeName().toString(), ROS_MASTER_URI);
            this.simpleServer.waitForStart();
            this.rosExecutor.startNodeMain(this.futureBasedClient, this.futureBasedClient.getDefaultNodeName().toString(), ROS_MASTER_URI);
            final boolean serverStarted = this.futureBasedClient.waitForServerConnection(30, TimeUnit.SECONDS);
            Assume.assumeTrue("Server Not Started", serverStarted);
        } catch (final Exception er3) {
            LOGGER.error(ExceptionUtils.getStackTrace(er3));
            Assume.assumeNoException(er3);

        }

    }

    /**
     * Also demonstrates status of Client by printing client status when it changes
     */
    @Test
    public void testFutureBasedClientWithStatuses() {
        try {
            LOGGER.trace("Starting Tasks");
            this.futureBasedClient.waitForServerConnection(20, TimeUnit.SECONDS);
            LOGGER.trace("Server Started");
            final ActionFuture<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult> resultFuture = this.futureBasedClient.invoke(3);
            String lastClientState = null;
            while (!resultFuture.isDone()) {
                final String clientState = resultFuture.getCurrentState().name();
                if (lastClientState != clientState) {
                    LOGGER.trace("Client State:" + clientState);
                    lastClientState = clientState;
                }
                try {
                    Thread.sleep(1);
                } catch (final Exception er3) {
                    LOGGER.error(ExceptionUtils.getStackTrace(er3));
                }
            }
            final String clientState = resultFuture.getCurrentState().name();
            LOGGER.trace("Client State:" + clientState);

            //This is blocking
            final FibonacciActionResult result = resultFuture.get();
            LOGGER.trace("Result:" + result.getResult().getSequence());
        } catch (final Exception e) {
            LOGGER.error(ExceptionUtils.getStackTrace(e));
        }
    }

    /**
     * Demonstrate future based client usage
     */
    @Test
    public void testFutureBasedClient() {
        try {
            LOGGER.trace("Starting Tasks");
            this.futureBasedClient.waitForServerConnection(20, TimeUnit.SECONDS);
            LOGGER.trace("Server Started");
            final ActionFuture<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult> resultFuture = this.futureBasedClient.invoke(3);

            //This is blocking anyway
            final FibonacciActionResult result = resultFuture.get();
            LOGGER.trace("Result:" + result.getResult().getSequence());
        } catch (final Exception e) {
            LOGGER.error(ExceptionUtils.getStackTrace(e));
        }
    }

    @After
    public void after() {
        try {
            rosExecutor.stopNodeMain(simpleServer);
        } catch (final Exception e2) {
            LOGGER.error(ExceptionUtils.getStackTrace(e2));
        }
        try {
            rosExecutor.stopNodeMain(futureBasedClient);
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

        this.futureBasedClient = null;
        this.simpleServer = null;
        this.rosCore = null;
    }

}