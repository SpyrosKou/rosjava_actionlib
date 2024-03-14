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

import com.google.common.base.Stopwatch;
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

//    private FibonacciActionLibClient fibonacciActionLibClient = null;
    private FutureBasedClient futureBasedClient = null;

    private FibonacciActionLibServer fibonacciActionLibServer = null;
    private final RosExecutor rosExecutor = new RosExecutor(ROS_HOST_IP);

    @Before
    public void before() {
        try {
            this.rosCore = RosCore.newPublic(ROS_HOST_IP, testProperties.getRosMasterUriPort());
            this.rosCore.start();
            this.rosCore.awaitStart(testProperties.getRosCoreStartWaitMillis(), TimeUnit.MILLISECONDS);
            this.fibonacciActionLibServer = new FibonacciActionLibServer();

//            this.fibonacciActionLibClient = new FibonacciActionLibClient();

            this.futureBasedClient=new FutureBasedClient();
            this.rosExecutor.startNodeMain(this.fibonacciActionLibServer, this.fibonacciActionLibServer.getDefaultNodeName().toString(), this.rosCore.getMasterServer().getUri().toString());
            this.fibonacciActionLibServer.waitForStart();
//            this.rosExecutor.startNodeMain(this.fibonacciActionLibClient, this.fibonacciActionLibClient.getDefaultNodeName().toString(), this.rosCore.getMasterServer().getUri().toString());
            this.rosExecutor.startNodeMain(this.futureBasedClient, this.futureBasedClient.getDefaultNodeName().toString(), this.rosCore.getMasterServer().getUri().toString());

//            final boolean connectedToServer = this.fibonacciActionLibClient.waitForServerConnection(20, TimeUnit.SECONDS);
            final boolean connectedToServer = this.futureBasedClient.waitForServerConnection(20, TimeUnit.SECONDS);
            Assume.assumeTrue("Not Connected to server", connectedToServer);
        } catch (final Exception er3) {
            LOGGER.error(ExceptionUtils.getStackTrace(er3));
            Assume.assumeNoException(er3);

        }

    }


    @Test
    public void testCallCancelled() {
        try {
//            final CountDownLatch countDownLatch = this.fibonacciActionLibClient.callCancelled();
//            final CountDownLatch countDownLatch = this.futureBasedClient.callCancelled();
        final CountDownLatch countDownLatch=new CountDownLatch(1);
            final boolean withinTime = countDownLatch.await(2, TimeUnit.MINUTES);
            LOGGER.debug("Finished call.");
            Assert.assertTrue("Cancel took too long", withinTime);
//            final ClientState currentClientState = this.fibonacciActionLibClient.getActionClient().getGoalState();
            final ClientState currentClientState = this.futureBasedClient.getActionClient().getGoalState();
            Assert.assertTrue("Not Cancelled, current state:" + currentClientState
                    , ClientState.RECALLING.equals(currentClientState)
                            || ClientState.WAITING_FOR_CANCEL_ACK.equals(currentClientState)
                            || ClientState.PREEMPTING.equals(currentClientState)
//                            || ClientState.DONE.equals(currentClientState)
            );
            LOGGER.debug("First client state:" + currentClientState);
            final Stopwatch stopwatch = Stopwatch.createStarted();
//            ClientState updatedClientState= this.fibonacciActionLibClient.getActionClient().getGoalState();
//            while(stopwatch.isRunning() && stopwatch.elapsed(TimeUnit.SECONDS)<10&&currentClientState.equals(updatedClientState)){
//                updatedClientState= this.fibonacciActionLibClient.getActionClient().getGoalState();
//            }
//            LOGGER.debug("Later client state:"+updatedClientState+" changed:"+!currentClientState.equals(updatedClientState));
        } catch (final Exception e) {
            LOGGER.debug("Finished call.");
            Assert.fail(ExceptionUtils.getStackTrace(e));
        }
    }

    @After
    public void after() {

        try {
//            this.rosExecutor.stopNodeMain(fibonacciActionLibClient);
            this.rosExecutor.stopNodeMain(this.futureBasedClient);
        } catch (final Exception e2) {
            LOGGER.error(ExceptionUtils.getStackTrace(e2));
        }
        try {
            this.rosExecutor.stopNodeMain(this.fibonacciActionLibServer);
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

//        this.fibonacciActionLibClient = null;
        this.fibonacciActionLibServer = null;
        this.futureBasedClient=null;
        this.rosCore = null;
    }

}