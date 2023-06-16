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
import org.checkerframework.checker.units.qual.C;
import org.junit.*;
import org.ros.RosCore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test wait methods with {@link SimpleServer} with {@link SimpleClient}
 */
public class ConnectionNotificationMethodClientTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());


    private static final TestProperties testProperties = TestProperties.getFromDefaultFile();

    private static final String ROS_HOST_IP = testProperties.getRosHostIp();

    private RosCore rosCore = null;

    private SimpleClient simpleClient = null;

    private SimpleServer simpleServer = null;
    private final RosExecutor rosExecutor = new RosExecutor(ROS_HOST_IP);

    @Before
    public void before() {
        try {
            this.rosCore = RosCore.newPrivate();
            this.rosCore.start();
            this.rosCore.awaitStart(testProperties.getRosCoreStartWaitMillis(), TimeUnit.MILLISECONDS);
            this.simpleServer = new SimpleServer();

            this.simpleClient = new SimpleClient();

            this.rosExecutor.startNodeMain(this.simpleServer, this.simpleServer.getDefaultNodeName().toString(), this.rosCore.getMasterServer().getUri().toString());
            this.simpleServer.waitForStart();


        } catch (final Exception er3) {
            LOGGER.error(ExceptionUtils.getStackTrace(er3));
            Assume.assumeNoException(er3);

        }

    }


    /**
     * TODO remove Sleep
     */
    @Test
    @Deprecated
    public void testClientServer() {
        try {
            final long timeoutMillis = 30_000;

            final CountDownLatch countDownLatch = new CountDownLatch(1);
            this.simpleClient.setOnConnection(() -> countDownLatch.countDown());
            final Stopwatch stopWatchClient = Stopwatch.createStarted();
            this.rosExecutor.startNodeMain(this.simpleClient, this.simpleClient.getDefaultNodeName().toString(), this.rosCore.getMasterServer().getUri().toString());

            final boolean clientStarted = countDownLatch.await(timeoutMillis, TimeUnit.MILLISECONDS);
            LOGGER.trace("Connected:" + clientStarted + " after:" + stopWatchClient.elapsed(TimeUnit.MILLISECONDS) + " millis");
            Assert.assertTrue("Client Not Started", clientStarted);
            final long clientConnectionsMillis = stopWatchClient.elapsed(TimeUnit.MILLISECONDS);
            Assert.assertTrue(timeoutMillis >= clientConnectionsMillis);


        } catch (final Exception e) {
            LOGGER.error(ExceptionUtils.getStackTrace(e));
        }
    }

    @After
    public void after() {
        try {
            rosExecutor.stopNodeMain(simpleServer);
        } catch (final Exception e2) {
            LOGGER.error(ExceptionUtils.getStackTrace(e2));
        }
        try {
            rosExecutor.stopNodeMain(simpleClient);
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

        this.simpleClient = null;
        this.simpleServer = null;
        this.rosCore = null;
    }

}