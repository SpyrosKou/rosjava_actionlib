package com.github.ekumen.rosjava_actionlib;

import eu.test.utils.RosExecutor;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestClientServer {
    private static Logger logger = LoggerFactory.getLogger(TestClientServer.class);
    private static final String ROS_MASTER_IP = "127.0.0.1";
    private static final String ROS_MASTER_IP_PORT = "http://" + ROS_MASTER_IP + ":11311";
    private TestClient testClient = null;
    private TestServer testServer = null;
    private final RosExecutor rosExecutor = new RosExecutor();

    @Before
    public void before() {
        try {

            testServer = new TestServer();

            testClient = new TestClient();

            rosExecutor.startNodeMain(testServer, testServer.getDefaultNodeName().toString(), ROS_MASTER_IP, ROS_MASTER_IP_PORT);
            rosExecutor.startNodeMain(testClient, testClient.getDefaultNodeName().toString(), ROS_MASTER_IP, ROS_MASTER_IP_PORT);
            testServer.waitForStart();
            testClient.waitForStart();
        } catch (final Exception er3) {
            logger.error(ExceptionUtils.getStackTrace(er3));
            throw er3;
        }

    }



    @Test
    public void testClientServer2() {


            try {



                logger.trace("Starting Tasks");

                testClient.startTasks();
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
            rosExecutor.stopNodeMain(testServer);
        } catch (final Exception e2) {
            logger.error(ExceptionUtils.getStackTrace(e2));
        }
        try {
            rosExecutor.stopNodeMain(testClient);
        } catch (final Exception e2) {
            logger.error(ExceptionUtils.getStackTrace(e2));
        }

        this.testClient = null;
        this.testServer = null;
    }

}