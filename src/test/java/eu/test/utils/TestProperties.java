package eu.test.utils;

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.util.Objects;
import java.util.Properties;
import java.util.StringJoiner;

/**
 * Some properties used in tests
 * Created at 2019-06-13
 *
 * @author Spyros Koukas
 */
public class TestProperties {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final static String ROS_HOST_IP_PARAMETER_NAME = "ROS_IP";
    private final static String ROS_HOSTNAME_PARAMETER_NAME = "ROS_HOSTNAME";
    private final static String ROS_MASTER_URI_PARAMETER_NAME = "ROS_MASTER_URI";
    private final static String ROS_MASTER_URI_PORT_PARAMETER_NAME = "ROS_MASTER_URI_PORT";
    private final static String USE_EXTERNAL_ROS_MASTER_PARAMETER_NAME = "USE_EXTERNAL_ROS_MASTER";
    private final static String ROS_CORE_START_WAIT_MILLIS_PARAMETER_NAME_ = "ROS_CORE_START_WAIT_MILLIS";
    public final static String TEST_PROPERTIES_FILE_NAME = "test_configurations.properties";


    /**
     *
     */
    private static final long serialVersionUID = 4190990652080438515L;
    private final String rosHostIp;
    private final String rosHostName;
    private final String rosMasterUri;
    private final int rosMasterUriPort;
    private final long rosCoreStartWaitMillis;
    private final boolean useExternalRosMaster;

    /**
     * @param rosHostIp
     * @param rosHostName
     * @param rosMasterUri
     * @param rosMasterUriPort
     * @param rosCoreStartWaitMillis
     */
    public TestProperties(final String rosHostIp, final String rosHostName, final String rosMasterUri, final int rosMasterUriPort, final long rosCoreStartWaitMillis, final boolean useExternalRosMaster) {
        Preconditions.checkArgument(StringUtils.isNotBlank(rosHostIp), "rosHostIp should not be blank.");
        Preconditions.checkArgument(StringUtils.isNotBlank(rosHostIp), "rosHostName should not be blank.");
        Preconditions.checkArgument(StringUtils.isNotBlank(rosHostIp), "rosMasterUri should not be blank.");

        this.rosHostIp = rosHostIp;
        this.rosHostName = rosHostName;
        this.rosMasterUri = rosMasterUri;
        this.rosMasterUriPort = rosMasterUriPort;
        this.rosCoreStartWaitMillis = rosCoreStartWaitMillis;
        this.useExternalRosMaster = useExternalRosMaster;
    }

    /**
     * Creates properties from a property file
     *
     * @param propertyFileName
     * @return
     */
    public static final TestProperties getFromFile(final String propertyFileName) {
        Objects.requireNonNull(propertyFileName);
        return new TestProperties(propertyFileName);
    }

    /**
     * Creates properties from the default file
     *
     * @return
     */
    public static final TestProperties getFromDefaultFile() {
        return getFromFile(TEST_PROPERTIES_FILE_NAME);
    }

    /**
     * Getter for rosCoreStartWaitMillis
     *
     * @return rosCoreStartWaitMillis
     **/
    public final long getRosCoreStartWaitMillis() {
        return rosCoreStartWaitMillis;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof TestProperties)) return false;
        TestProperties that = (TestProperties) o;
        return getRosMasterUriPort() == that.getRosMasterUriPort() &&
                this.getRosCoreStartWaitMillis() == that.getRosCoreStartWaitMillis() &&
                Objects.equals(this.getRosHostIp(), that.getRosHostIp()) &&
                Objects.equals(this.getRosHostName(), that.getRosHostName()) &&
                Objects.equals(this.getRosMasterUri(), that.getRosMasterUri()) &&
                this.useExternalRosMaster() == that.useExternalRosMaster();
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                this.getRosHostIp()
                , this.getRosHostName()
                , this.getRosMasterUri()
                , this.getRosMasterUriPort()
                , this.getRosCoreStartWaitMillis()
                , this.useExternalRosMaster()
        );
    }

    /**
     * Gets properties from a property file
     *
     * @param propertyFileName
     */
    TestProperties(final String propertyFileName) {
        super();
        Preconditions.checkArgument(StringUtils.isNotBlank(propertyFileName), "propertyFileName should not be blank.");
        final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try (final InputStream input = classLoader.getResourceAsStream(propertyFileName);
             final BufferedInputStream bufinput = new BufferedInputStream(input);) {
            final Properties properties = new Properties();
            properties.load(bufinput);
            this.rosHostIp = properties.getProperty(ROS_HOST_IP_PARAMETER_NAME);
            this.rosHostName = properties.getProperty(ROS_HOSTNAME_PARAMETER_NAME);
            this.rosMasterUri = properties.getProperty(ROS_MASTER_URI_PARAMETER_NAME);
            this.rosMasterUriPort = PropertiesUtilities.getInt(ROS_MASTER_URI_PORT_PARAMETER_NAME, properties);
            this.rosCoreStartWaitMillis = PropertiesUtilities.getLong(ROS_CORE_START_WAIT_MILLIS_PARAMETER_NAME_, properties);
            this.useExternalRosMaster = PropertiesUtilities.getBoolean(USE_EXTERNAL_ROS_MASTER_PARAMETER_NAME, properties);

            LOGGER.trace("Loaded from:" + propertyFileName + " " + this.toString());

            Preconditions.checkArgument(StringUtils.isNotBlank(rosHostIp), "rosHostIp should not be blank.");
            Preconditions.checkArgument(StringUtils.isNotBlank(rosHostName), "rosHostName should not be blank.");
            Preconditions.checkArgument(StringUtils.isNotBlank(rosMasterUri), "rosMasterUri should not be blank.");
        } catch (final IOException e) {
            LOGGER.error(ExceptionUtils.getStackTrace(e));
            throw new RuntimeException(e);
        }
    }

    /**
     * Getter for rosHostIp
     *
     * @return rosHostIp
     **/
    public final String getRosHostIp() {
        return rosHostIp;
    }

    /**
     * Getter for rosHostName
     *
     * @return rosHostName
     **/
    public final String getRosHostName() {
        return rosHostName;
    }

    /**
     * Getter for rosMasterUri
     *
     * @return rosMasterUri
     **/
    public final String getRosMasterUri() {
        return rosMasterUri;
    }

    /**
     * @return
     */
    public int getRosMasterUriPort() {
        return rosMasterUriPort;
    }

    public boolean useExternalRosMaster() {
        return this.useExternalRosMaster;
    }


    @Override
    public String toString() {
        return new StringJoiner(", ", TestProperties.class.getSimpleName() + "[", "]")
                .add("rosHostIp='" + rosHostIp + "'")
                .add("rosHostName='" + rosHostName + "'")
                .add("rosMasterUri='" + rosMasterUri + "'")
                .add("rosMasterUriPort=" + rosMasterUriPort)
                .add("rosCoreStartWaitMillis=" + rosCoreStartWaitMillis)
                .add("useExternalRosMaster=" + useExternalRosMaster)
                .toString();
    }
}