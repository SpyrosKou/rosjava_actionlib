package com.github.rosjava_actionlib;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import org.ros.internal.message.Message;
import org.ros.internal.node.topic.PublisherIdentifier;
import org.ros.internal.node.topic.SubscriberIdentifier;
import org.ros.internal.node.topic.TopicIdentifier;
import org.ros.internal.node.topic.TopicParticipant;
import org.ros.master.client.MasterStateClient;
import org.ros.master.client.TopicSystemState;
import org.ros.node.ConnectedNode;
import org.ros.node.topic.Publisher;
import org.ros.node.topic.Subscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

abstract class TopicParticipantListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final AtomicBoolean isRegistered = new AtomicBoolean(false);
    private final AtomicBoolean hasCallOnceBeenCalled = new AtomicBoolean(false);
    private final ConnectedNode connectedNode;

    protected final ConnectedNode getConnectedNode() {
        return this.connectedNode;
    }

    private final CountDownLatch registrationCountDownLatch = new CountDownLatch(1);
    private final String topicName;

    protected final String getTopicName() {
        return this.topicName;
    }


    /**
     * This method will be called only once on connection.
     */
    private final Consumer<String> callOnceOnConnection;

    final void callOnceOnConnection() {
        if (this.hasCallOnceBeenCalled.compareAndSet(false, true)) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Calling once on connection for 1st time.{}", this.toString());
            }
            this.callOnceOnConnection.accept(this.topicName);
        } else {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Do nothing. Already called.{}", this.toString());
            }
        }
    }

    /**
     * @param connectedNode
     * @param topicName
     * @param callOnceOnConnection
     */
    TopicParticipantListener(final ConnectedNode connectedNode, final String topicName, final Consumer<String> callOnceOnConnection) {
        Preconditions.checkNotNull(connectedNode);
        Preconditions.checkArgument(!Strings.isNullOrEmpty(topicName));
        Preconditions.checkNotNull(callOnceOnConnection);
        this.connectedNode = connectedNode;
        this.topicName = topicName;
        this.callOnceOnConnection = callOnceOnConnection;
    }

    public final boolean isRegistered() {
        if (this.isRegistered.get()) {
            return true;
        }
        return this.refreshRegistrationFromMaster();
    }

    private final boolean refreshRegistrationFromMaster() {
        try {
            final MasterStateClient masterStateClient = new MasterStateClient(this.connectedNode, this.connectedNode.getMasterUri());
            if (this.isRegisteredWithMaster(masterStateClient)) {
                this.isRegistered.set(true);
                this.registrationCountDownLatch.countDown();
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Observed registration via master state for topic:{} node:{}", this.topicName, this.connectedNode.getName());
                }
                return true;
            }
        } catch (final Exception exception) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Could not refresh registration from master for topic:{}", this.topicName, exception);
            }
        }
        return false;
    }

    protected final boolean isRegisteredWithMaster(final MasterStateClient masterStateClient) {
        final String nodeName = this.getConnectedNode().getName().toString();
        final String topicName=this.getTopicName();
        return masterStateClient.isRegisteredWithMaster(nodeName,topicName);
    }

    public final boolean waitForRegistration() throws InterruptedException {
        final Stopwatch stopwatch = Stopwatch.createStarted();
        while (!this.isRegistered()) {
            try {
                this.registrationCountDownLatch.await();
                return this.isRegistered();
            } catch (final InterruptedException interruptedException) {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Interrupted while:" + this.toString() + " after:" + stopwatch.elapsed(TimeUnit.MILLISECONDS) + " " + TimeUnit.MILLISECONDS);
                }
                throw interruptedException;
            }
        }
        final boolean result = this.isRegistered();
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Duration:{} result:[{}]", stopwatch.elapsed(TimeUnit.MILLISECONDS), result);
        }
        return result;
    }

    /**
     * @param timeout
     * @param timeUnit
     * @return
     */
    public final boolean waitForRegistration(final long timeout, final TimeUnit timeUnit)  throws InterruptedException{
        final Stopwatch stopwatch = Stopwatch.createStarted();
        while (!this.isRegistered() && stopwatch.elapsed(timeUnit) <= timeout) {
            try {
                final long effectiveTimeout = Math.max(timeout - stopwatch.elapsed(timeUnit), 0);
                if (effectiveTimeout > 0) {
                    this.registrationCountDownLatch.await(effectiveTimeout, timeUnit);
                }
                final boolean result = this.isRegistered();
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Registering timed out. Duration:{} result:[{}]", stopwatch.elapsed(TimeUnit.MILLISECONDS), result);
                }
                return result;
            } catch (final InterruptedException interruptedException) {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Interrupted while:" + this.toString() + " after:" + stopwatch.elapsed(timeUnit) + " " + timeUnit.name());
                }
                throw interruptedException;
            }
        }
        final boolean result = this.isRegistered();
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Registered in time. Duration:{} result:[{}]", stopwatch.elapsed(TimeUnit.MILLISECONDS), result);
        }
        return result;
    }


    final public void onShutdown(final TopicParticipant topicParticipant) {
        this.isRegistered.set(false);
        this.registrationCountDownLatch.countDown();
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Shutdown topicParticipant for topic:{} type:{}", topicParticipant.getTopicName(), topicParticipant.getTopicMessageType());
        }
    }


    final public void onMasterRegistrationSuccess(final TopicParticipant topicParticipant) {
        this.isRegistered.set(true);
        this.registrationCountDownLatch.countDown();
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Master Registration success for topic:{} type:{}", topicParticipant.getTopicName(), topicParticipant.getTopicMessageType());
        }
    }

    final public void onMasterRegistrationFailure(final TopicParticipant topicParticipant) {
        this.isRegistered.set(false);
        this.registrationCountDownLatch.countDown();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Master Registration Failure for topic:{} type:{}", topicParticipant.getTopicName(), topicParticipant.getTopicMessageType());
        }
    }

    final public void onMasterUnregistrationSuccess(final TopicParticipant topicParticipant) {
        this.isRegistered.set(false);
        this.registrationCountDownLatch.countDown();
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Master UnRegistration Success for topic:{} type:{}", topicParticipant.getTopicName(), topicParticipant.getTopicMessageType());
        }
    }

    final public void onMasterUnregistrationFailure(final TopicParticipant topicParticipant) {
        this.isRegistered.set(false);
        this.registrationCountDownLatch.countDown();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Master UnRegistration Failure for topic:{} type:{}", topicParticipant.getTopicName(), topicParticipant.getTopicMessageType());
        }
    }


    @Override
    public String toString() {
        return "TopicParticipantListener{" +
                "isRegistered=" + isRegistered +
                ", hasCallOnceBeenCalled=" + hasCallOnceBeenCalled +
                ", connectedNode=" + connectedNode +
                ", registrationCountDownLatch=" + registrationCountDownLatch +
                ", topicName='" + topicName + '\'' +
                ", callOnceOnConnection=" + callOnceOnConnection +
                '}';
    }
}
