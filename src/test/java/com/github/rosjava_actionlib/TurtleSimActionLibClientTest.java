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


import eu.test.utils.RosExecutor;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import turtle_actionlib.ShapeActionGoal;
import turtle_actionlib.ShapeGoal;

import java.lang.invoke.MethodHandles;

/**
 * Test to if {@link TurtleSimActionLibClient} can drive turtle sim action lib server
 * The Turtle Sim Action Lib server should be running.
 *
 * @author Spyros Koukas
 */
@Ignore //uncomment to use this test is ignored
public class TurtleSimActionLibClientTest {
private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final String ROS_HOST_IP = "127.0.0.1";
    private static final String ROS_MASTER_IP_PORT = "http://127.0.0.1:11311";
    private TurtleSimActionLibClient testClient = null;

    private final RosExecutor rosExecutor = new RosExecutor(ROS_HOST_IP);

    @Before
    public void before() {
        try {


            testClient = new TurtleSimActionLibClient();

            rosExecutor.startNodeMain(testClient, testClient.getDefaultNodeName().toString(), ROS_MASTER_IP_PORT);
            testClient.waitForStart();


        } catch (final Exception er3) {
            LOGGER.error(ExceptionUtils.getStackTrace(er3));

            Assume.assumeNoException(er3);
        }

    }


    @Test
    public void testTurtle() {


        try {


            LOGGER.trace("Starting Tasks");
            final ShapeGoal goal = testClient.createShapeGoal();
            goal.setEdges(4);
            goal.setRadius(1f);
            testClient.synchronousCompleteGoal(goal, 20.0f);
            LOGGER.trace("Falling asleep");

            try {
                Thread.sleep(10_000);
            } catch (final Exception er3) {
                LOGGER.error(ExceptionUtils.getStackTrace(er3));

            }


            LOGGER.trace("Stopping");


        } catch (final Exception e) {
            LOGGER.error(ExceptionUtils.getStackTrace(e));
            Assert.fail(ExceptionUtils.getStackTrace(e));
        }
    }


    @Test
    public void testTurtleAsync() {


        try {


            LOGGER.trace("Starting Tasks");
            testClient.getShapeActionClient().waitForActionServerToStart();
            final ShapeActionGoal goalAction = testClient.getShapeActionClient().newGoalMessage();
            goalAction.getGoal().setEdges(3);
            goalAction.getGoal().setRadius(1f);

            testClient.getShapeActionClient().sendGoal(goalAction);
            ClientState oldState = ClientState.NO_TRANSITION;
            ClientState newState = oldState;
            while (!ClientState.DONE.equals((newState = testClient.getShapeActionClient().getGoalState()))) {
                if (oldState != newState) {
                    LOGGER.trace("State:" + oldState + " --> " + newState);
                    oldState = newState;
                }
                try {
                    Thread.sleep(100);
                } catch (final Exception e) {
                }
            }

            LOGGER.trace("Falling asleep");


            LOGGER.trace("Stopping");


        } catch (final Exception e) {
            LOGGER.error(ExceptionUtils.getStackTrace(e));
            Assert.fail(ExceptionUtils.getStackTrace(e));
        }
    }

    @After
    public void after() {

        try {
            this.rosExecutor.stopNodeMain(testClient);
        } catch (final Exception e2) {
            LOGGER.error(ExceptionUtils.getStackTrace(e2));
        }

        this.testClient = null;
    }

}