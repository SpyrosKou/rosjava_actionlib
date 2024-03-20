/**
 * Copyright 2019 Spyros Koukas
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
import org.junit.*;
import org.ros.RosCore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ClientServerFeedbackTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final TestProperties testProperties = TestProperties.getFromDefaultFile();
    private static final String ROS_HOST_IP = testProperties.getRosHostIp();
    private static final int ROS_MASTER_URI_PORT = testProperties.getRosMasterUriPort();
    private static final String ROS_MASTER_URI = testProperties.getRosMasterUri();
    private static final boolean USE_EXTERNAL_ROS_MASTER = testProperties.useExternalRosMaster();
    private RosCore rosCore = null;

    private ActionLibClientFeedbackListenerNode actionLibClientFeedbackListenerNode = null;
    private AsyncGoalRunnerActionLibServer asyncGoalRunnerActionLibServer = null;
    private final RosExecutor rosExecutor = new RosExecutor(ROS_HOST_IP);

    @Before
    public void before() {
        try {
            final boolean coreStartedOk ;
            if (!USE_EXTERNAL_ROS_MASTER) {
                this.rosCore = RosCore.newPublic(ROS_MASTER_URI_PORT);
                this.rosCore.start();
                coreStartedOk = this.rosCore.awaitStart(testProperties.getRosCoreStartWaitMillis(), TimeUnit.MILLISECONDS);
                Assert.assertTrue("Core not started", coreStartedOk);
            }else{
                coreStartedOk = false;
            }
            Assert.assertTrue(USE_EXTERNAL_ROS_MASTER || coreStartedOk);
            this.asyncGoalRunnerActionLibServer = new AsyncGoalRunnerActionLibServer(false);

            this.actionLibClientFeedbackListenerNode = new ActionLibClientFeedbackListenerNode();

            this.rosExecutor.startNodeMain(asyncGoalRunnerActionLibServer, asyncGoalRunnerActionLibServer.getDefaultNodeName().toString(), ROS_MASTER_URI);
            this.rosExecutor.startNodeMain(actionLibClientFeedbackListenerNode, actionLibClientFeedbackListenerNode.getDefaultNodeName().toString(), ROS_MASTER_URI);
            final boolean serverStarted = this.asyncGoalRunnerActionLibServer.waitForStart(10000, TimeUnit.SECONDS);
            Assume.assumeTrue("Could not connect", serverStarted);
            final boolean clientStarted = this.actionLibClientFeedbackListenerNode.waitForStartAndConnection(10000, TimeUnit.SECONDS);
            Assume.assumeTrue("Could not connect", clientStarted);
        } catch (final Exception er3) {
            LOGGER.error(ExceptionUtils.getStackTrace(er3));
            Assume.assumeNoException(er3);
        }

    }


    @Test
    public void testNormal() {


        try {

            LOGGER.trace("Starting Tasks");

            final ActionFuture<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult> resultFuture = actionLibClientFeedbackListenerNode.getFibonnaciFuture(TestInputs.TEST_INPUT);
            Assert.assertNotNull("Result was null", resultFuture);
            final FibonacciActionResult result = resultFuture.get(5, TimeUnit.SECONDS);

            LOGGER.trace("Goal completed!");

            Assert.assertNotNull("Result should not be null", result);

            Assert.assertTrue("Result was wrong", Arrays.equals(result.getResult().getSequence(), TestInputs.TEST_CORRECT_OUTPUT));
            LOGGER.trace("Stopping");


        } catch (final Exception e) {

            Assert.fail(ExceptionUtils.getStackTrace(e));
        }
    }

    @Test
    public void testCountDownLatch() {


        try {

            LOGGER.trace("Starting Tasks");
            Assert.assertTrue(actionLibClientFeedbackListenerNode.getFibonacciActionResultOptional().isEmpty());
            final CountDownLatch countDownLatch = actionLibClientFeedbackListenerNode.submitRequestSilent(TestInputs.TEST_INPUT);

            final boolean gotResult = countDownLatch.await(6, TimeUnit.SECONDS);
            Assert.assertTrue("Timeout", gotResult);
            Assert.assertTrue(actionLibClientFeedbackListenerNode.getFibonacciActionResultOptional().isPresent());
            final FibonacciActionResult result = actionLibClientFeedbackListenerNode.getFibonacciActionResultOptional().get();


            LOGGER.trace("Goal completed!");

            Assert.assertNotNull("Result should not be null", result);

            Assert.assertTrue("Result was wrong", Arrays.equals(result.getResult().getSequence(), TestInputs.TEST_CORRECT_OUTPUT));
            LOGGER.trace("Stopping");


        } catch (final Exception e) {

            Assert.fail(ExceptionUtils.getStackTrace(e));
        }
    }

    @Test
    public void testCancel() {
        try {

            LOGGER.trace("Starting Tasks");

            final var resulFutureCancelled = this.actionLibClientFeedbackListenerNode.getFibonnaciCanceledFuture(TestInputs.HUGE_INPUT);
            final FibonacciActionResult result = resulFutureCancelled.get(5, TimeUnit.SECONDS);
            LOGGER.trace("Finished");
            Assert.assertNotNull("Result should not be null", result);
            Assert.assertTrue("Result should be incomplete", Arrays.equals(TestInputs.TEST_CORRECT_HUGE_INPUT_OUTPUT, result.getResult().getSequence()));

        } catch (final Exception e) {
            LOGGER.error(ExceptionUtils.getStackTrace(e));
            Assert.fail(ExceptionUtils.getStackTrace(e));
        }
    }

    @After
    public void after() {
        try {
            rosExecutor.stopNodeMain(this.asyncGoalRunnerActionLibServer);
        } catch (final Exception e2) {
            LOGGER.error(ExceptionUtils.getStackTrace(e2));
        }
        try {
            rosExecutor.stopNodeMain(this.actionLibClientFeedbackListenerNode);
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
        this.actionLibClientFeedbackListenerNode = null;
        this.asyncGoalRunnerActionLibServer = null;
        this.rosCore = null;
    }

}