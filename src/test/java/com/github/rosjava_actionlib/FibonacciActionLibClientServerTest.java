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

import eu.test.utils.RosExecutor;
import eu.test.utils.TestProperties;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.*;
import org.ros.RosCore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Demonstrate and test {@link FibonacciActionLibServer} with {@link FibonacciActionLibClient}
 */
public class FibonacciActionLibClientServerTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());


    private static final TestProperties testProperties = TestProperties.getFromDefaultFile();

    private static final String ROS_HOST_IP = testProperties.getRosHostIp();


    private RosCore rosCore = null;

    private FibonacciActionLibClient fibonacciActionLibClient = null;

    private FibonacciActionLibServer fibonacciActionLibServer = null;
    private final RosExecutor rosExecutor = new RosExecutor(ROS_HOST_IP);

    @Before
    public void before() {
        try {
            this.rosCore = RosCore.newPublic(ROS_HOST_IP, testProperties.getRosMasterUriPort());
            this.rosCore.start();
            this.rosCore.awaitStart(testProperties.getRosCoreStartWaitMillis(), TimeUnit.MILLISECONDS);
            this.fibonacciActionLibServer = new FibonacciActionLibServer();

            this.fibonacciActionLibClient = new FibonacciActionLibClient();

            this.rosExecutor.startNodeMain(this.fibonacciActionLibServer, this.fibonacciActionLibServer.getDefaultNodeName().toString(), this.rosCore.getMasterServer().getUri().toString());
            this.fibonacciActionLibServer.waitForStart();
            this.rosExecutor.startNodeMain(this.fibonacciActionLibClient, this.fibonacciActionLibClient.getDefaultNodeName().toString(), this.rosCore.getMasterServer().getUri().toString());

            final boolean connectedToServer = this.fibonacciActionLibClient.waitForServerConnection(20, TimeUnit.SECONDS);
            Assume.assumeTrue("Not Connected to server", connectedToServer);
        } catch (final Exception er3) {
            LOGGER.error(ExceptionUtils.getStackTrace(er3));
            Assume.assumeNoException(er3);

        }

    }


    @Test
    public void testCallNormal() {
        try {
            final CountDownLatch countDownLatch = this.fibonacciActionLibClient.callNormal();
            Thread.currentThread().setName("ActionCallResults" + Thread.currentThread().getName());
            boolean finished = false;
            final long totalMiliseconds=20000;
            final long stepsNUmber=100;
            for (int i = 1; i <= stepsNUmber; i++) {
                finished = countDownLatch.await(totalMiliseconds/stepsNUmber, TimeUnit.MILLISECONDS);
                if (finished) {
                    break;
                }
            }
            LOGGER.debug("Finished call. Result:"+finished);
            Assert.assertTrue("Tasks took too long", finished);

        } catch (final Exception e) {
            LOGGER.debug("Finished call.");
            Assert.fail(ExceptionUtils.getStackTrace(e));
        }
    }

    @Test
    public void testCallCancelled() {
        try {
            final CountDownLatch countDownLatch = this.fibonacciActionLibClient.callCancelled();

            final boolean withinTime = countDownLatch.await(2, TimeUnit.MINUTES);
            LOGGER.debug("Finished call.");
            Assert.assertTrue("Cancel took too long", withinTime);
            final ClientState currentClientState=this.fibonacciActionLibClient.getActionClient().getGoalState();
            Assert.assertTrue("Not Cancelled", ClientState.RECALLING.equals(currentClientState)||ClientState.RECALLING.equals(currentClientState));

        } catch (final Exception e) {
            LOGGER.debug("Finished call.");
            Assert.fail(ExceptionUtils.getStackTrace(e));
        }
    }

    @After
    public void after() {

        try {
            this.rosExecutor.stopNodeMain(fibonacciActionLibClient);
        } catch (final Exception e2) {
            LOGGER.error(ExceptionUtils.getStackTrace(e2));
        }
        try {
            this.rosExecutor.stopNodeMain(fibonacciActionLibServer);
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

        this.fibonacciActionLibClient = null;
        this.fibonacciActionLibServer = null;
        this.rosCore = null;
    }

}