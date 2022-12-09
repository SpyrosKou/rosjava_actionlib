package eu.test.actionlib.turtlesim;

import actionlib_msgs.GoalID;
import actionlib_msgs.GoalStatus;
import actionlib_msgs.GoalStatusArray;
import com.github.ekumen.rosjava_actionlib.*;
import org.apache.commons.logging.Log;
import org.ros.message.Duration;
import org.ros.namespace.GraphName;
import org.ros.node.AbstractNodeMain;
import org.ros.node.ConnectedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import turtle_actionlib.*;

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
    private static final Logger logger = LoggerFactory.getLogger(TurtleSimActionLibClient.class);
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

        boolean serverStarted;


        // Attach listener for the callbacks

        logger.trace("\nWaiting for action server to start...");
        serverStarted = shapeActionClient.waitForActionServerToStart(new Duration(new Duration(serverStartTimeoutSecs)));
        if (serverStarted) {
            logger.trace("Action server started.\n");
        } else {
            logger.trace("No actionlib server found after waiting for " + serverStartTimeoutSecs + " seconds!");
        }

        // Create Fibonacci goal message
        final ShapeActionGoal goalMessage = (ShapeActionGoal) shapeActionClient.newGoalMessage();
        goalMessage.setGoal(shapeGoal);
        logger.trace("Sending goal...");
        shapeActionClient.sendGoal(goalMessage);
        final GoalID gid1 = ActionLibMessagesUtils.getGoalId(goalMessage);
        logger.trace("Sent goal with ID: " + gid1.getId());
        logger.trace("Waiting for goal to complete...");
        int oldState = ActionLibClientStates.NO_TRANSITION;
        int newState = oldState;
        while ((newState = shapeActionClient.getGoalState()) != ActionLibClientStates.DONE) {
            if (oldState != newState) {
                logger.trace("State:" + ActionLibClientStates.translateState(oldState) + " --> " + ActionLibClientStates.translateState(newState));
                oldState = newState;
            }
            sleep(100);
        }
        logger.trace("Goal completed!\n");
//
//        logger.trace("Sending a new goal...");
//        shapeActionClient.sendGoal(goalMessage);
//        final GoalID gid2 = shapeActionClient.getGoalId(goalMessage);
//        logger.trace("Sent goal with ID: " + gid2.getId());
//        logger.trace("Cancelling this goal...");
//        shapeActionClient.sendCancel(gid2);
//        while (shapeActionClient.getGoalState() != ClientStateMachine.ClientStates.DONE) {
//            sleep(1);
//        }
//        logger.trace("Goal cancelled succesfully.\n");
//        logger.trace("Bye!");

    }

    @Override
    public void onStart(ConnectedNode node) {
        logger.trace("Starting");
        this.shapeActionClient = new ActionClient<>(node, "/turtle_shape", ShapeActionGoal._TYPE, ShapeActionFeedback._TYPE, ShapeActionResult._TYPE);
        final ShapeHandler shapeHandler = new ShapeHandler();
        this.shapeActionClient.addListener(shapeHandler);
        log = node.getLog();
        //TODO synchronous start method implement.
        this.isStarted = true;
        logger.trace("Started");

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
    public final class ShapeHandler implements ActionClientListener<turtle_actionlib.ShapeActionFeedback, turtle_actionlib.ShapeActionResult> {

        /**
         * Called when a result message is received from the server.
         *
         * @param shapeActionResult Result message from the server. The type of this message
         *                          depends on the application.
         */
        @Override
        public void resultReceived(final ShapeActionResult shapeActionResult) {
            logger.trace("shapeActionResult:" + shapeActionResult);
            final ShapeResult result = shapeActionResult.getResult();

            final float apothem = result.getApothem();
            final float interiorAngle = result.getInteriorAngle();
            logger.trace("Result: {apothem:" + apothem + " interiorAngle:" + interiorAngle + " }");
        }

        /**
         * Called when a feedback message is received from the server.
         *
         * @param shapeActionFeedback The feedback message received from the server. The type of
         *                            this message depends on the application.
         */
        @Override
        public void feedbackReceived(final ShapeActionFeedback shapeActionFeedback) {
            logger.trace("shapeActionFeedback:" + shapeActionFeedback);
            final ShapeFeedback feedback = shapeActionFeedback.getFeedback();
            logger.trace("feedback:" + feedback);

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
            logger.trace("status:" + status);
            if (status != null) {
                final List<GoalStatus> statusList = status.getStatusList();
                if (statusList != null) {
                    logger.trace("StatusList size:" + statusList.size());
                }
                for (final GoalStatus gs : statusList) {
                    logger.trace("GoalID: " + gs.getGoalId().getId() + " -- GoalStatus: " + ActionLibClientStates.translateState(gs.getStatus()) + "(" + +gs.getStatus() + ") -- " + gs.getText());
                }

            }
        }
    }
}

