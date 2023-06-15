package com.github.rosjava_actionlib;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import org.ros.internal.message.Message;
import org.ros.internal.node.topic.PublisherIdentifier;
import org.ros.master.client.MasterStateClient;
import org.ros.master.client.TopicSystemState;
import org.ros.node.ConnectedNode;
import org.ros.node.topic.Subscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

final class TopicSubscriberListener<T extends Message> implements org.ros.node.topic.SubscriberListener<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final AtomicBoolean isRegistered = new AtomicBoolean(false);
    private final ConnectedNode connectedNode;
    private final AtomicLong knownPublishersCount = new AtomicLong(0);
    private final CountDownLatch registrationCountDownLatch = new CountDownLatch(1);
    private final CountDownLatch publisherConnectionNoticed = new CountDownLatch(1);
    private final String topicName;

    TopicSubscriberListener(final ConnectedNode connectedNode, final String topicName) {
        Preconditions.checkNotNull(connectedNode);
        Preconditions.checkArgument(!Strings.isNullOrEmpty(topicName));
        this.connectedNode = connectedNode;
        this.topicName = topicName;
    }

    public final boolean isRegistered() {
        return this.isRegistered.get();
    }

    public final boolean waitForRegistration() {
        final Stopwatch stopwatch = Stopwatch.createStarted();
        while (!this.isRegistered()) {
            try {
                this.registrationCountDownLatch.await();
                return this.isRegistered();
            } catch (final InterruptedException interruptedException) {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Interrupted while:" + this.toString() + " after:" + stopwatch.elapsed(TimeUnit.MILLISECONDS) + " " + TimeUnit.MILLISECONDS);
                }
            }
        }
        return this.isRegistered();
    }

    /**
     * @param timeout
     * @param timeUnit
     * @return
     */
    public final boolean waitForRegistration(final long timeout, final TimeUnit timeUnit) {
        final Stopwatch stopwatch = Stopwatch.createStarted();
        while (!this.isRegistered()) {
            try {
                this.registrationCountDownLatch.await(Math.max(timeout - stopwatch.elapsed(timeUnit), 0), timeUnit);
                return this.isRegistered();
            } catch (final InterruptedException interruptedException) {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Interrupted while:" + this.toString() + " after:" + stopwatch.elapsed(timeUnit) + " " + timeUnit.name());
                }
            }
        }
        return this.isRegistered();
    }

    public final boolean waitForPublisher() {
        final Stopwatch stopwatch = Stopwatch.createStarted();
        while (!this.isPublisherConnected()) {
            try {
                this.publisherConnectionNoticed.await();
                return this.isPublisherConnected();
            } catch (final InterruptedException interruptedException) {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Interrupted while:" + this.toString() + " after:" + stopwatch.elapsed(TimeUnit.MILLISECONDS) + " " + TimeUnit.MILLISECONDS);
                }
            }
        }
        return this.isRegistered();
    }

    /**
     * @param timeout
     * @param timeUnit
     * @return
     */
    public final boolean waitForPublisher(final long timeout, final TimeUnit timeUnit) {
        final Stopwatch stopwatch = Stopwatch.createStarted();
        while (!this.isPublisherConnected()) {
            try {
                this.publisherConnectionNoticed.await(Math.max(timeout - stopwatch.elapsed(timeUnit), 0), timeUnit);
                return this.isPublisherConnected();
            } catch (final InterruptedException interruptedException) {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Interrupted while:" + this.toString() + " after:" + stopwatch.elapsed(timeUnit) + " " + timeUnit.name());
                }
            }
        }
        return this.isRegistered();
    }

    public final boolean isPublisherConnected() {
        final boolean connected = !this.knownPublishersCount.equals(0L);
        final boolean result;
        if (connected) {
            result = true;
        } else {
            final MasterStateClient masterStateClient = new MasterStateClient(this.connectedNode, this.connectedNode.getMasterUri());
            final long publishers = this.countPublishers(masterStateClient);
            this.knownPublishersCount.set(publishers);
            result = publishers != 0L;
        }
        return result;
    }

    /**
     * @param masterStateClient
     * @return
     */
    private final long countPublishers(final MasterStateClient masterStateClient) {


        final long result = masterStateClient.getSystemState().getTopics().stream()
                .filter(Objects::nonNull)
                .filter(topicSystemState -> this.topicName.equals(topicSystemState.getTopicName()))
                .map(TopicSystemState::getPublishers)
                .filter(Objects::nonNull)
                .flatMap(Set::stream)
                .count();
        return result;

    }

    @Override
    final public void onNewPublisher(final Subscriber<T> subscriber, PublisherIdentifier publisherIdentifier) {

        final long publishers = this.knownPublishersCount.incrementAndGet();
        this.publisherConnectionNoticed.countDown();
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("New publisher for Topic:" + publisherIdentifier.getTopicName() + " node:" + publisherIdentifier.getNodeName() + " type:" + subscriber.getTopicMessageType() + " total publishers:" + publishers);
        }
    }

    @Override
    final public void onShutdown(final Subscriber<T> subscriber) {
        this.isRegistered.set(false);
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Shutdown subscriber for topic:" + subscriber.getTopicName() + " type:" + subscriber.getTopicMessageType());
        }
    }

    @Override
    final public void onMasterRegistrationSuccess(final Subscriber<T> subscriber) {
        this.isRegistered.set(true);
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Master Registration success for topic:" + subscriber.getTopicName() + " type:" + subscriber.getTopicMessageType());
        }
    }

    @Override
    final public void onMasterRegistrationFailure(final Subscriber<T> subscriber) {
        this.isRegistered.set(false);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Master Registration Failure for topic:" + subscriber.getTopicName() + " type:" + subscriber.getTopicMessageType());
        }
    }

    @Override
    final public void onMasterUnregistrationSuccess(final Subscriber<T> subscriber) {
        this.isRegistered.set(false);
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Master UnRegistration Success for topic:" + subscriber.getTopicName() + " type:" + subscriber.getTopicMessageType());
        }
    }

    @Override
    final public void onMasterUnregistrationFailure(final Subscriber<T> subscriber) {

        this.isRegistered.set(false);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Master UnRegistration Failure for topic:" + subscriber.getTopicName() + " type:" + subscriber.getTopicMessageType());
        }
    }

    @Override
    public final String toString() {
        return "isRegistered:" + this.isRegistered() + "isPublisherConnected:" + this.isPublisherConnected();
    }
}