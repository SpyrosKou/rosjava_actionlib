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

import actionlib_msgs.GoalID;
import actionlib_msgs.GoalStatus;
import actionlib_msgs.GoalStatusArray;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.ros.message.Duration;
import org.ros.namespace.GraphName;
import org.ros.node.AbstractNodeMain;
import org.ros.node.ConnectedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import turtle_actionlib.*;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Objects;

/**
 * Created at 2019-04-19
 * <p>
 * A simple Client for tests that work with for the turtle_actionlib server.
 * See https://wiki.ros.org/turtle_actionlib
 *
 * @author Spyros Koukas
 */
final class TurtleSimActionLibClient extends AbstractNodeMain {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final String clientName;
    private static final String DEFAULT_NAME = "turtleSimDefaultClient";

    public final ActionClient<ShapeActionGoal, ShapeActionFeedback, ShapeActionResult> getShapeActionClient(){
        return this.shapeActionClient;
    }

    public TurtleSimActionLibClient() {
        this(DEFAULT_NAME);
    }

    public TurtleSimActionLibClient(final String clientName) {
        Objects.requireNonNull(clientName);
        this.clientName = clientName;
    }


    private ActionClient<ShapeActionGoal, ShapeActionFeedback, ShapeActionResult> shapeActionClient = null;

    private volatile boolean isStarted = false;

    private Log log;

    @Override
    public GraphName getDefaultNodeName() {
        return GraphName.of(this.clientName);
    }

    /**
     * Getter for isStarted
     *
     * @return isStarted
     **/
    public void waitForStart() {
        while (!this.isStarted) {
            if(this.getShapeActionClient()!=null){
                this.getShapeActionClient().waitForActionServerToStart();
            }
            sleep(5);
        }
    }

    public final ShapeGoal createShapeGoal() {

        return this.shapeActionClient.newGoalMessage().getGoal();
    }


    /**
     * Waits for the goal to complete, or timeout, or fail
     *
     * @param shapeGoal
     * @param serverStartTimeoutSecs
     */
    @Deprecated
    public void synchronousCompleteGoal(final ShapeGoal shapeGoal, final double serverStartTimeoutSecs) {
        Objects.requireNonNull(shapeGoal);

        // Attach listener for the callbacks

        LOGGER.trace("\nWaiting for action server to start...");
        final boolean serverStarted = shapeActionClient.waitForActionServerToStart(new Duration(new Duration(serverStartTimeoutSecs)));
        if (serverStarted) {
            LOGGER.trace("Action server started.\n");
        } else {
            LOGGER.trace("No actionlib server found after waiting for " + serverStartTimeoutSecs + " seconds!");
        }

        // Create Fibonacci goal message
        final ShapeActionGoal goalMessage = (ShapeActionGoal) shapeActionClient.newGoalMessage();
        goalMessage.setGoal(shapeGoal);
        LOGGER.trace("Sending goal...");
        shapeActionClient.sendGoal(goalMessage);
        final GoalID gid1 = goalMessage.getGoalId();
        LOGGER.trace("Sent goal with ID: " + gid1.getId());
        LOGGER.trace("Waiting for goal to complete...");
        ClientState oldState = ClientState.NO_TRANSITION;
        ClientState newState = oldState;
        while ((newState = shapeActionClient.getGoalState()) != ClientState.DONE) {
            if (oldState != newState) {
                LOGGER.trace("State:" + oldState + " --> " + newState);
                oldState = newState;
            }
            sleep(100);
        }
        LOGGER.trace("Goal completed!\n");


    }

    @Override
    public void onStart(ConnectedNode node) {
        LOGGER.trace("Starting");
        this.shapeActionClient = new ActionClient<>(node, "/turtle_shape", ShapeActionGoal._TYPE, ShapeActionFeedback._TYPE, ShapeActionResult._TYPE);
        final ShapeHandler shapeHandler = new ShapeHandler();
        this.shapeActionClient.addListener(shapeHandler);
        log = node.getLog();
        //TODO synchronous start method implement.
        this.isStarted = true;
        LOGGER.trace("Started");

    }


    void sleep(long msec) {
        try {
            Thread.sleep(msec);
        } catch (InterruptedException ex) {
        }
    }

    /**
     *
     */
    public final class ShapeHandler implements ActionClientListener<ShapeActionFeedback, ShapeActionResult> {

        /**
         * Called when a result message is received from the server.
         *
         * @param shapeActionResult Result message from the server. The type of this message
         *                          depends on the application.
         */
        @Override
        public void resultReceived(final ShapeActionResult shapeActionResult) {
            LOGGER.trace("shapeActionResult:" + shapeActionResult);
            final ShapeResult result = shapeActionResult.getResult();

            final float apothem = result.getApothem();
            final float interiorAngle = result.getInteriorAngle();
            LOGGER.trace("Result: {apothem:" + apothem + " interiorAngle:" + interiorAngle + " }");
        }

        /**
         * Called when a feedback message is received from the server.
         *
         * @param shapeActionFeedback The feedback message received from the server. The type of
         *                            this message depends on the application.
         */
        @Override
        public void feedbackReceived(final ShapeActionFeedback shapeActionFeedback) {
            LOGGER.trace("shapeActionFeedback:" + shapeActionFeedback);
            final ShapeFeedback feedback = shapeActionFeedback.getFeedback();
            LOGGER.trace("feedback:" + feedback);

        }

        /**
         * Called when a status message is received from the server.
         *
         * @param status The status message received from the server.
         *
         * @see GoalStatusArray
         */
        @Override
        public void statusReceived(final GoalStatusArray status) {
            LOGGER.trace("status:" + status);
            if (status != null) {
                final List<GoalStatus> statusList = status.getStatusList();
                if (statusList != null) {
                    LOGGER.trace("StatusList size:" + statusList.size());
                }
                for (final GoalStatus gs : statusList) {
                    LOGGER.trace("GoalID: " + gs.getGoalId().getId() + " - GoalStatus: " + gs.getStatus() + "(" + +gs.getStatus() + ") -- " + gs.getText());
                }

            }
        }
    }
}

