package eu.test.utils;

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.ros.node.DefaultNodeMainExecutor;
import org.ros.node.NodeConfiguration;
import org.ros.node.NodeMain;
import org.ros.node.NodeMainExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Objects;

/**
 * Created at 2019-04-02
 *
 * @author Spyros Koukas
 */
public final class RosExecutor {

    private static final Logger logger = LoggerFactory.getLogger(RosExecutor.class);
    private final NodeMainExecutor nodeMainExecutor = DefaultNodeMainExecutor.newDefault();


    /**
     * @param aNodeMain
     *
     * @author Spyros Koukas
     */
    public final void stopNodeMain(final NodeMain aNodeMain) {
        logger.trace(" aNodeMain=" + aNodeMain + " nodeMainExecutor=" + nodeMainExecutor + " Shutting down node");

        try {
            if (aNodeMain != null) {
                nodeMainExecutor.shutdownNodeMain(aNodeMain);
            }

            logger.trace(" aNodeMain=" + aNodeMain + " nodeMainExecutor=" + nodeMainExecutor + " Node Shutdown call completed");
        } catch (final Exception e) {
            logger.error(ExceptionUtils.getStackTrace(e));
        }

    }

    /**
     *
     * @param nodeMain
     * @param nodeName
     * @param thisHostIp
     * @param masterUri
     * @return
     */
    public final NodeMain startNodeMain(final NodeMain nodeMain, final String nodeName, final String thisHostIp, final String masterUri) {
        logger.trace("Starting nodeMain:"+nodeMain+" nodeName:"+nodeName+" thisHostIp:"+thisHostIp+" masterUri:"+masterUri);
        try {
            final URI rosMasterUri = new URI(masterUri);
            return startNodeMain(nodeMain, nodeName, thisHostIp, rosMasterUri);
        } catch (final Exception e) {
            logger.error(ExceptionUtils.getStackTrace(e));
            throw new RuntimeException(e);
        }
    }

    /**
     * @param nodeMain
     * @param nodeName
     * @param thisHostIp
     * @param masterUri
     *
     * @return
     */

    public final NodeMain startNodeMain(final NodeMain nodeMain, final String nodeName, final String thisHostIp, final URI masterUri) {


        Objects.requireNonNull(nodeMain);
        Objects.requireNonNull(masterUri);

        if (nodeName == null || nodeName.isEmpty()) {
            IllegalStateException rte = new IllegalStateException("nodeName should not be null or empty.");
            throw rte;
        }
        if (thisHostIp == null || thisHostIp.isEmpty()) {
            IllegalStateException rte = new IllegalStateException("ip should not be null or empty.");
            throw rte;
        }
        // Load the Class
        try {

            final NodeConfiguration nodeConfiguration = NodeConfiguration.newPublic(thisHostIp);
            nodeConfiguration.setNodeName(nodeName);
            nodeConfiguration.setMasterUri(masterUri);

            Preconditions.checkState(nodeMain != null);
            nodeMainExecutor.execute(nodeMain, nodeConfiguration);
            return nodeMain;
        } catch (final Exception e) {
            RuntimeException rte = new RuntimeException("Error while trying to start node: " + nodeMain, e);
            logger.error("Throwing:" + ExceptionUtils.getStackTrace(rte));
            throw rte;
        }

    }
}
