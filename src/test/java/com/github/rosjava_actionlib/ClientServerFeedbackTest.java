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
 */package com.github.rosjava_actionlib;

import eu.test.utils.RosExecutor;
import eu.test.utils.TestProperties;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.ros.RosCore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.TimeUnit;

public class ClientServerFeedbackTest {
    static {
        // comment this line if you want logs activated
        System.setProperty("org.apache.commons.logging.Log","org.apache.commons.logging.impl.NoOpLog");
    }
private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final TestProperties testProperties=TestProperties.getFromDefaultFile();
    private static final String ROS_HOST_IP = testProperties.getRosHostIp();
    private static final int ROS_MASTER_URI_PORT = testProperties.getRosMasterUriPort();
    private static final String ROS_MASTER_URI = testProperties.getRosMasterUri();
    private RosCore rosCore = null;

    private ActionLibClientFeedback actionLibClientFeedback = null;
    private ActionLibServerFeedback actionLibServerFeedback = null;
    private final RosExecutor rosExecutor = new RosExecutor(ROS_HOST_IP);

    @Before
    public void before() {
        try {
            this.rosCore = RosCore.newPublic(ROS_MASTER_URI_PORT);
            this.rosCore.start();
            this.rosCore.awaitStart(testProperties.getRosCoreStartWaitMillis(), TimeUnit.MILLISECONDS);
            this.actionLibServerFeedback = new ActionLibServerFeedback();

            this.actionLibClientFeedback = new ActionLibClientFeedback();

            this.rosExecutor.startNodeMain(actionLibServerFeedback, actionLibServerFeedback.getDefaultNodeName().toString(),  ROS_MASTER_URI);
            this.rosExecutor.startNodeMain(actionLibClientFeedback, actionLibClientFeedback.getDefaultNodeName().toString(),  ROS_MASTER_URI);
            this.actionLibClientFeedback.waitForStart();
        } catch (final Exception er3) {
            LOGGER.error(ExceptionUtils.getStackTrace(er3));
            Assume.assumeNoException(er3);
        }

    }



    @Test
    public void testNormal() {


            try {

                LOGGER.trace("Starting Tasks");

                actionLibClientFeedback.getFibonnaciBlocking(10);
                LOGGER.trace("Falling asleep");

                try {
                    Thread.sleep(10_000);
                } catch (final Exception er3) {
                    LOGGER.error(ExceptionUtils.getStackTrace(er3));
                }
                LOGGER.trace("Awaken");

                LOGGER.trace("Stopping");


        } catch (final Exception e) {
            LOGGER.error(ExceptionUtils.getStackTrace(e));
        }
    }

    @Test
    public void testCancel() {


        try {

            LOGGER.trace("Starting Tasks");

            actionLibClientFeedback.getFibonnaciBlockingWithCancelation(10);
            LOGGER.trace("Falling asleep");

            try {
                Thread.sleep(10_000);
            } catch (final Exception er3) {
                LOGGER.error(ExceptionUtils.getStackTrace(er3));
            }
            LOGGER.trace("Awaken");

            LOGGER.trace("Stopping");


        } catch (final Exception e) {
            LOGGER.error(ExceptionUtils.getStackTrace(e));
        }
    }

    @After
    public void after() {
        try {
            rosExecutor.stopNodeMain(actionLibServerFeedback);
        } catch (final Exception e2) {
            LOGGER.error(ExceptionUtils.getStackTrace(e2));
        }
        try {
            rosExecutor.stopNodeMain(actionLibClientFeedback);
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
        this.actionLibClientFeedback = null;
        this.actionLibServerFeedback = null;
        this.rosCore = null;
    }

}