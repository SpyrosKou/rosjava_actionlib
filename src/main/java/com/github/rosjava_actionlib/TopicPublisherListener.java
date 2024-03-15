package com.github.rosjava_actionlib;

import com.google.common.base.Stopwatch;
import org.ros.internal.message.Message;
import org.ros.internal.node.topic.SubscriberIdentifier;
import org.ros.internal.node.topic.TopicIdentifier;
import org.ros.master.client.MasterStateClient;
import org.ros.master.client.TopicSystemState;
import org.ros.node.ConnectedNode;
import org.ros.node.topic.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

final class TopicPublisherListener<T extends Message> extends TopicParticipantListener implements org.ros.node.topic.PublisherListener<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final AtomicLong knownSubscribersCount = new AtomicLong(0);

    private final CountDownLatch subscriberConnectionNoticed = new CountDownLatch(1);

    /**
     * @param connectedNode
     * @param topicName
     * @param callOnceOnConnectionConsumer
     */
    TopicPublisherListener(final ConnectedNode connectedNode, final String topicName, final Consumer<String> callOnceOnConnectionConsumer) {
        super(connectedNode, topicName, callOnceOnConnectionConsumer);
        if (this.isSubscriberConnected()) {
            this.callOnceOnConnection();
        }
    }


    /**
     * @param timeout
     * @param timeUnit
     * @return
     */
    public final boolean waitForSubscriber(final long timeout, final TimeUnit timeUnit) {
        final Stopwatch stopwatch = Stopwatch.createStarted();
        while (!this.isSubscriberConnected()) {
            try {
                final boolean ok = this.subscriberConnectionNoticed.await(Math.max(timeout - stopwatch.elapsed(timeUnit), 0), timeUnit);
                break;
            } catch (final InterruptedException interruptedException) {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Interrupted while:" + this.toString() + " after:" + stopwatch.elapsed(timeUnit) + " " + timeUnit.name());
                }
            }
        }
        final boolean result = this.isSubscriberConnected();
        if (result) {
            this.callOnceOnConnection();
        }
        return result;
    }

    public final boolean isSubscriberConnected() {
        final boolean connected = this.knownSubscribersCount.get() > 0L;
        final boolean result;
        if (connected) {
            result = true;
        } else {
            final ConnectedNode connectedNode = this.getConnectedNode();
            final MasterStateClient masterStateClient = new MasterStateClient(connectedNode, connectedNode.getMasterUri());
            final long publishers = this.countSubscribers(masterStateClient);
            this.knownSubscribersCount.set(publishers);
            result = publishers != 0L;
        }
        return result;
    }

    /**
     * @param masterStateClient
     * @return
     */
    private final long countSubscribers(final MasterStateClient masterStateClient) {
        final long result = masterStateClient.getSystemState().getTopics().stream()
                .filter(Objects::nonNull)
                .filter(topicSystemState -> this.getTopicName().equals(topicSystemState.getTopicName()))
                .map(TopicSystemState::getSubscribers)
                .filter(Objects::nonNull)
                .flatMap(Set::stream)
                .count();
        return result;

    }

    @Override
    public final void onNewSubscriber(final Publisher<T> publisher, final SubscriberIdentifier subscriberIdentifier) {

        this.knownSubscribersCount.set(publisher.getNumberOfSubscribers());
        final long subscribers = this.knownSubscribersCount.get();
        this.subscriberConnectionNoticed.countDown();
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("New publisher for Topic:" + publisher.getTopicName() + " type:" + publisher.getTopicMessageType() + " total subscribers:" + subscribers);
        }
        this.callOnceOnConnection();

    }


    @Override
    public final void onMasterRegistrationSuccess(final Publisher<T> publisher) {
        super.onMasterRegistrationSuccess(publisher);
    }

    @Override
    public final void onMasterRegistrationFailure(final Publisher<T> publisher) {
        super.onMasterRegistrationFailure(publisher);
    }

    @Override
    public final void onMasterUnregistrationSuccess(final Publisher<T> publisher) {
        super.onMasterUnregistrationSuccess(publisher);
    }

    @Override
    public final void onMasterUnregistrationFailure(final Publisher<T> publisher) {
        super.onMasterUnregistrationFailure(publisher);
    }

    @Override
    public final void onShutdown(final Publisher<T> publisher) {
        super.onShutdown(publisher);
    }


}
