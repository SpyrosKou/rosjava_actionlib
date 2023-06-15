package com.github.rosjava_actionlib;

import com.google.common.base.Stopwatch;
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
import java.util.concurrent.atomic.AtomicLong;

final class TopicSubscriberListener<T extends Message>  extends TopicParticipantListener implements org.ros.node.topic.SubscriberListener<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final AtomicLong knownPublishersCount = new AtomicLong(0);

    private final CountDownLatch publisherConnectionNoticed = new CountDownLatch(1);


    TopicSubscriberListener(final ConnectedNode connectedNode, final String topicName) {
        super(connectedNode,topicName);
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
                return this.publisherConnectionNoticed.await(Math.max(timeout - stopwatch.elapsed(timeUnit), 0), timeUnit);

            } catch (final InterruptedException interruptedException) {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Interrupted while:" + this.toString() + " after:" + stopwatch.elapsed(timeUnit) + " " + timeUnit.name());
                }
            }
        }
        return this.isPublisherConnected();
    }

    public final boolean isPublisherConnected() {
        final boolean connected = !this.knownPublishersCount.equals(0L);
        final boolean result;
        if (connected) {
            result = true;
        } else {
            final ConnectedNode connectedNode=this.getConnectedNode();
            final MasterStateClient masterStateClient = new MasterStateClient(connectedNode, connectedNode.getMasterUri());
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
                .filter(topicSystemState -> this.getTopicName().equals(topicSystemState.getTopicName()))
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
    public final void onShutdown(final Subscriber<T> subscriber) {
        super.onShutdown(subscriber);
    }




    @Override
    public void onMasterRegistrationSuccess(final Subscriber<T> subscriber) {
        super.onMasterRegistrationSuccess(subscriber);
    }

    @Override
    public void onMasterRegistrationFailure(final Subscriber<T> subscriber) {
        super.onMasterRegistrationFailure(subscriber);
    }

    @Override
    public void onMasterUnregistrationSuccess(final Subscriber<T> subscriber) {
        super.onMasterUnregistrationSuccess(subscriber);
    }

    @Override
    public void onMasterUnregistrationFailure(final Subscriber<T> subscriber) {
        super.onMasterUnregistrationFailure(subscriber);
    }
}
