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
package eu.test.utils;

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.ros.node.DefaultNodeMainExecutor;
import org.ros.node.NodeConfiguration;
import org.ros.node.NodeMain;
import org.ros.node.NodeMainExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.util.Objects;

/**
 * Utilities to Run clients and Servers directly via java
 * <p>
 * Created at 2019-04-02
 * Updated at 2020-03-18
 *
 * @author Spyros Koukas
 */
public final class RosExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private NodeMainExecutor nodeMainExecutor = null;
    private final String rosHostIp;

    /**
     * The Ip that the started node will report to ROS Master.
     * This IP should be reachable by the ROS Master and other ROS hardware in the network
     *
     * @param rosHostIp
     */
    public RosExecutor(final String rosHostIp) {
        Preconditions.checkArgument(StringUtils.isNotBlank(rosHostIp));
        this.rosHostIp = rosHostIp;
    }

    /**
     * @param aNodeMain
     *
     * @author Spyros Koukas
     */

    public final void stopNodeMain(final NodeMain aNodeMain) {

        LOGGER.trace("Stopping Node. aNodeMain=" + aNodeMain + " ros host ip=" + this.rosHostIp);
        if (aNodeMain != null) {
            try {
                this.getOrCreateNodeMainExecutor().shutdownNodeMain(aNodeMain);
                LOGGER.trace("Stopped Node.  aNodeMain=" + aNodeMain + " ros host ip=" + this.rosHostIp);
            } catch (final Exception e) {
                LOGGER.error("Error while stopping node: "+ ExceptionUtils.getStackTrace(e));
            }
        }

    }




    /**
     * @param nodeMain
     * @param nodeName
     * @param masterUri
     *
     * @return
     */
    public final NodeMain startNodeMain(final NodeMain nodeMain, final String nodeName, final String masterUri) {

        try {
            final URI uri = new URI(masterUri);
            startNodeMain(this.getOrCreateNodeMainExecutor(),nodeMain, nodeName, this.rosHostIp, uri);
            return nodeMain;
        } catch (final Exception e) {
            LOGGER.error(ExceptionUtils.getStackTrace(e));
            throw new IllegalArgumentException(e);
        }


    }




    /**
     * @return
     */
    private final NodeMainExecutor getOrCreateNodeMainExecutor() {
        if (this.nodeMainExecutor == null) {
            this.nodeMainExecutor = DefaultNodeMainExecutor.newDefault();
        }
        return nodeMainExecutor;
    }

    /**
     * Shutdown node main executor
     */
    public final void stopAllNodesAndClose() {
        if (this.nodeMainExecutor != null) {
            this.nodeMainExecutor.shutdown();
        }
        this.nodeMainExecutor = null;
    }



    /**
     * The core method
     *
     * @param executor
     * @param nodeMain
     * @param nodeName
     * @param thisHostIp
     * @param masterUri
     */
    private static void startNodeMain(final NodeMainExecutor executor, final NodeMain nodeMain, final String nodeName, final String thisHostIp, final URI masterUri) {


        Objects.requireNonNull(nodeMain);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(masterUri);
        Preconditions.checkArgument(StringUtils.isNotBlank(nodeName), "nodeName should not be null or empty.");
        Preconditions.checkArgument(StringUtils.isNotBlank(thisHostIp), "ip should not be null or empty.");

        // Load the Class
        try {

            final NodeConfiguration nodeConfiguration = NodeConfiguration.newPublic(thisHostIp);
            nodeConfiguration.setNodeName(nodeName);
            nodeConfiguration.setMasterUri(masterUri);

            executor.execute(nodeMain, nodeConfiguration);

        } catch (final Exception e) {
            final RuntimeException rte = new RuntimeException("Error while trying to start node: " + nodeMain, e);
            LOGGER.error("Throwing:" + ExceptionUtils.getStackTrace(rte));
            throw rte;
        }

    }


}
