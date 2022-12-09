package eu.test.actionlib.clientServer;

import eu.test.utils.RosExecutor;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestClientServerFeedback {
    private static Logger logger = LoggerFactory.getLogger(TestClientServerFeedback.class);
    private static final String ROS_MASTER_IP = "127.0.0.1";
    private static final String ROS_MASTER_IP_PORT = "http://" + ROS_MASTER_IP + ":11311";
    private ActionLibClientFeedback actionLibClientFeedback = null;
    private ActionLibServerFeedback actionLibServerFeedback = null;
    private final RosExecutor rosExecutor = new RosExecutor();

    @Before
    public void before() {
        try {

            actionLibServerFeedback = new ActionLibServerFeedback();

            actionLibClientFeedback = new ActionLibClientFeedback();

            rosExecutor.startNodeMain(actionLibServerFeedback, actionLibServerFeedback.getDefaultNodeName().toString(), ROS_MASTER_IP, ROS_MASTER_IP_PORT);
            rosExecutor.startNodeMain(actionLibClientFeedback, actionLibClientFeedback.getDefaultNodeName().toString(), ROS_MASTER_IP, ROS_MASTER_IP_PORT);
            actionLibServerFeedback.waitForStart();
            actionLibClientFeedback.waitForStart();
        } catch (final Exception er3) {
            logger.error(ExceptionUtils.getStackTrace(er3));
            throw er3;
        }

    }



    @Test
    public void testClientServer2() {


            try {



                logger.trace("Starting Tasks");

                actionLibClientFeedback.startTasks();
                logger.trace("Falling asleep");

                try {
                    Thread.sleep(10_000);
                } catch (final Exception er3) {
                    logger.error(ExceptionUtils.getStackTrace(er3));
                }
                logger.trace("Awaken");

                logger.trace("Stopping");


        } catch (final Exception e) {
            logger.error(ExceptionUtils.getStackTrace(e));
        }
    }

    @After
    public void after() {
        try {
            rosExecutor.stopNodeMain(actionLibServerFeedback);
        } catch (final Exception e2) {
            logger.error(ExceptionUtils.getStackTrace(e2));
        }
        try {
            rosExecutor.stopNodeMain(actionLibClientFeedback);
        } catch (final Exception e2) {
            logger.error(ExceptionUtils.getStackTrace(e2));
        }

        this.actionLibClientFeedback = null;
        this.actionLibServerFeedback = null;
    }

}