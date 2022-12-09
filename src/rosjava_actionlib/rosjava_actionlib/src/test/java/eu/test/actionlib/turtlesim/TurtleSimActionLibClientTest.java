package eu.test.actionlib.turtlesim;

import com.github.ekumen.rosjava_actionlib.ActionLibClientStates;
import eu.test.utils.RosExecutor;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import turtle_actionlib.ShapeActionGoal;
import turtle_actionlib.ShapeGoal;

/**
 * Test to if {@link TurtleSimActionLibClient} can drive turtle sim action lib server
 * The Turtle Sim Action Lib server should be running.
 * @author Spyros Koukas
 */
public class TurtleSimActionLibClientTest {
    private static Logger logger = LoggerFactory.getLogger(TurtleSimActionLibClientTest.class);
    private static final String ROS_MASTER_IP = "127.0.0.1";
    private static final String ROS_MASTER_IP_PORT = "http://" + ROS_MASTER_IP + ":11311";
    private TurtleSimActionLibClient testClient = null;

    private final RosExecutor rosExecutor = new RosExecutor();

    @Before
    public void before() {
        try {


            testClient = new TurtleSimActionLibClient();

            rosExecutor.startNodeMain(testClient, testClient.getDefaultNodeName().toString(), ROS_MASTER_IP, ROS_MASTER_IP_PORT);
            testClient.waitForStart();
        } catch (final Exception er3) {
            logger.error(ExceptionUtils.getStackTrace(er3));
            throw er3;
        }

    }


    @Test
    public void testTurtle() {


        try {


            logger.trace("Starting Tasks");
            final ShapeGoal goal = testClient.createShapeGoal();
            goal.setEdges(4);
            goal.setRadius(1f);
            testClient.synchronousCompleteGoal(goal, 20.0f);
            logger.trace("Falling asleep");

            try {
                Thread.sleep(10_000);
            } catch (final Exception er3) {
                logger.error(ExceptionUtils.getStackTrace(er3));
            }


            logger.trace("Stopping");


        } catch (final Exception e) {
            logger.error(ExceptionUtils.getStackTrace(e));
        }
    }


    @Test
    public void testTurtleAsync() {


        try {


            logger.trace("Starting Tasks");
            testClient.getShapeActionClient().waitForActionServerToStart();
            final ShapeActionGoal goalAction = testClient.getShapeActionClient().newGoalMessage();
            goalAction.getGoal().setEdges(3);
            goalAction.getGoal().setRadius(1f);

            testClient.getShapeActionClient().sendGoal(goalAction);
            int oldState = ActionLibClientStates.NO_TRANSITION;
            int newState = oldState;
            while ((newState = testClient.getShapeActionClient().getGoalState()) != ActionLibClientStates.DONE) {
                if (oldState != newState) {
                    logger.trace("State:" + ActionLibClientStates.translateState(oldState) + " --> " + ActionLibClientStates.translateState(newState));
                    oldState = newState;
                }
                try {
                    Thread.sleep(100);
                } catch (final Exception e) {
                }
            }

            logger.trace("Falling asleep");


            logger.trace("Stopping");


        } catch (final Exception e) {
            logger.error(ExceptionUtils.getStackTrace(e));
        }
    }

    @After
    public void after() {

        try {
            rosExecutor.stopNodeMain(testClient);
        } catch (final Exception e2) {
            logger.error(ExceptionUtils.getStackTrace(e2));
        }

        this.testClient = null;
    }

}