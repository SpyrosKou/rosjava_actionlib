package com.github.rosjava_actionlib;

import eu.test.utils.RosExecutor;
import eu.test.utils.TestProperties;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.ros.RosCore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

abstract class BaseTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final TestProperties testProperties = TestProperties.getFromDefaultFile();

    private static final String ROS_HOST_IP = testProperties.getRosHostIp();


    private RosCore rosCore = null;


    private RosExecutor rosExecutor = null;
    private int rosMasterUriPort = -1;

    /**
     * The last method to run in the @{@link Before} annotated method
     *
     * @param rosExecutor
     */
    abstract void beforeCustom(final RosExecutor rosExecutor, final Optional<String> rosMasterUri);

    /**
     * The first method to run in the @{@link After} annotated method
     *
     * @param rosExecutor
     */
    abstract void afterCustom(final RosExecutor rosExecutor);

    final Optional<String> getRosMasterUri() {
        if (this.rosMasterUriPort > -1) {
            final String rosMasterUri = "http://" + ROS_HOST_IP + ":" + this.rosMasterUriPort;
            return Optional.of(rosMasterUri);
        } else {
            return Optional.empty();
        }
    }

    @Before
    public void before() {
        try {
            this.rosMasterUriPort = AvailablePortFinder.getNextAvailable();
            this.rosExecutor = new RosExecutor(ROS_HOST_IP);
            this.rosCore = RosCore.newPrivate(rosMasterUriPort);
            this.rosCore.start();
            this.rosCore.awaitStart(testProperties.getRosCoreStartWaitMillis(), TimeUnit.MILLISECONDS);
            final Optional<String> rosMasterUri = this.getRosMasterUri();
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("ROS Host IP:" + ROS_HOST_IP + " Current Ros Master Port:" + this.rosMasterUriPort);
            }
            Assume.assumeTrue("Could not get rosMasterUri." + "ROS Host IP:" + ROS_HOST_IP + " Current Ros Master Port:" + this.rosMasterUriPort, rosMasterUri.isPresent());
            this.beforeCustom(this.rosExecutor, this.getRosMasterUri());
        } catch (final Exception er3) {
            Assume.assumeNoException("ROS Host IP:" + ROS_HOST_IP + " Current Ros Master Port:" + this.rosMasterUriPort + "\nException:" + ExceptionUtils.getStackTrace(er3), er3);
        }

    }


    @After
    public void after() {
        try {
            this.afterCustom(this.rosExecutor);
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
        this.rosExecutor = null;

        this.rosCore = null;
    }

}
