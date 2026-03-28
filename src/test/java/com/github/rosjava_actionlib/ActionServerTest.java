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
package com.github.rosjava_actionlib;

import actionlib_msgs.GoalID;
import actionlib_msgs.GoalStatus;
import actionlib_msgs.GoalStatusArray;
import actionlib_tutorials.FibonacciActionFeedback;
import actionlib_tutorials.FibonacciActionGoal;
import actionlib_tutorials.FibonacciActionResult;
import org.junit.Assert;
import org.junit.Test;
import org.ros.internal.message.DefaultMessageFactory;
import org.ros.internal.message.Message;
import org.ros.internal.message.definition.MessageDefinitionReflectionProvider;
import org.ros.message.MessageFactory;
import org.ros.message.Time;
import org.ros.namespace.GraphName;
import org.ros.node.ConnectedNode;
import org.ros.node.topic.Publisher;
import org.ros.node.topic.Subscriber;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class ActionServerTest {
    private static final String ACTION_NAME = "/action_server_test";

    @Test
    public final void terminalStatusesAreMarkedForEviction() {
        Assert.assertTrue(ActionServer.isTerminalStatus(GoalStatus.REJECTED));
        Assert.assertTrue(ActionServer.isTerminalStatus(GoalStatus.RECALLED));
        Assert.assertTrue(ActionServer.isTerminalStatus(GoalStatus.PREEMPTED));
        Assert.assertTrue(ActionServer.isTerminalStatus(GoalStatus.SUCCEEDED));
        Assert.assertTrue(ActionServer.isTerminalStatus(GoalStatus.ABORTED));
    }

    @Test
    public final void nonTerminalStatusesAreNotMarkedForEviction() {
        Assert.assertFalse(ActionServer.isTerminalStatus(GoalStatus.PENDING));
        Assert.assertFalse(ActionServer.isTerminalStatus(GoalStatus.ACTIVE));
        Assert.assertFalse(ActionServer.isTerminalStatus(GoalStatus.RECALLING));
        Assert.assertFalse(ActionServer.isTerminalStatus(GoalStatus.PREEMPTING));
        Assert.assertFalse(ActionServer.isTerminalStatus(GoalStatus.LOST));
    }

    @Test
    public final void acceptedGoalBecomesActiveAndInvokesUserCallbacks() {
        try (ActionServerHarness harness = new ActionServerHarness(Optional.of(Boolean.TRUE))) {
            final FibonacciActionGoal goal = harness.newGoal("accepted-goal", new Time(20, 0), 5);

            harness.actionServer.gotGoal(goal);

            Assert.assertEquals(1, harness.listener.goalReceivedCount.get());
            Assert.assertEquals(1, harness.listener.acceptGoalCount.get());
            Assert.assertEquals(GoalStatus.ACTIVE, harness.actionServer.getGoalStatus(goal.getGoalId().getId()));
            Assert.assertTrue(harness.resultPublisher.publishedMessages.isEmpty());
        }
    }

    @Test
    public final void cancelAfterAcceptanceTransitionsGoalToPreempting() {
        try (ActionServerHarness harness = new ActionServerHarness(Optional.of(Boolean.TRUE))) {
            final FibonacciActionGoal goal = harness.newGoal("preempt-goal", new Time(30, 0), 5);

            harness.actionServer.gotGoal(goal);
            harness.actionServer.gotCancel(harness.newGoalId(goal.getGoalId().getId(), goal.getGoalId().getStamp()));

            Assert.assertEquals(1, harness.listener.goalReceivedCount.get());
            Assert.assertEquals(1, harness.listener.acceptGoalCount.get());
            Assert.assertEquals(1, harness.listener.cancelReceivedCount.get());
            Assert.assertEquals(GoalStatus.PREEMPTING, harness.actionServer.getGoalStatus(goal.getGoalId().getId()));
            Assert.assertTrue(harness.resultPublisher.publishedMessages.isEmpty());
        }
    }

    @Test
    public final void exactIdCancelBeforeGoalRecallsWithoutUserCallbacks() {
        try (ActionServerHarness harness = new ActionServerHarness(Optional.of(Boolean.TRUE))) {
            final String goalId = "recalled-goal";
            final FibonacciActionGoal goal = harness.newGoal(goalId, new Time(40, 0), 5);

            harness.actionServer.gotCancel(harness.newGoalId(goalId, new Time()));
            harness.actionServer.gotGoal(goal);

            Assert.assertEquals(0, harness.listener.goalReceivedCount.get());
            Assert.assertEquals(0, harness.listener.acceptGoalCount.get());
            Assert.assertEquals(1, harness.listener.cancelReceivedCount.get());
            Assert.assertEquals(GoalStatus.RECALLED, harness.actionServer.getGoalStatus(goalId));

            final FibonacciActionResult result = harness.resultPublisher.getLastPublishedMessage();
            Assert.assertNotNull(result);
            Assert.assertEquals(GoalStatus.RECALLED, result.getStatus().getStatus());
            Assert.assertEquals(goalId, result.getStatus().getGoalId().getId());
            Assert.assertEquals(goal.getGoalId().getStamp(), result.getStatus().getGoalId().getStamp());
        }
    }

    @Test
    public final void timestampCancelBeforeGoalRecallsMatchingGoalWithoutUserCallbacks() {
        try (ActionServerHarness harness = new ActionServerHarness(Optional.of(Boolean.TRUE))) {
            final Time cancelStamp = new Time(50, 0);
            final FibonacciActionGoal goal = harness.newGoal("timestamp-goal", new Time(49, 0), 5);

            harness.actionServer.gotCancel(harness.newGoalId("", cancelStamp));
            harness.actionServer.gotGoal(goal);

            Assert.assertEquals(0, harness.listener.goalReceivedCount.get());
            Assert.assertEquals(0, harness.listener.acceptGoalCount.get());
            Assert.assertEquals(GoalStatus.RECALLED, harness.actionServer.getGoalStatus(goal.getGoalId().getId()));

            final FibonacciActionResult result = harness.resultPublisher.getLastPublishedMessage();
            Assert.assertNotNull(result);
            Assert.assertEquals(GoalStatus.RECALLED, result.getStatus().getStatus());
        }
    }

    @Test
    public final void timestampCancelDoesNotRecallNewerGoal() {
        try (ActionServerHarness harness = new ActionServerHarness(Optional.of(Boolean.TRUE))) {
            harness.actionServer.gotCancel(harness.newGoalId("", new Time(60, 0)));
            final FibonacciActionGoal goal = harness.newGoal("newer-goal", new Time(61, 0), 5);

            harness.actionServer.gotGoal(goal);

            Assert.assertEquals(1, harness.listener.goalReceivedCount.get());
            Assert.assertEquals(1, harness.listener.acceptGoalCount.get());
            Assert.assertEquals(GoalStatus.ACTIVE, harness.actionServer.getGoalStatus(goal.getGoalId().getId()));
            Assert.assertTrue(harness.resultPublisher.publishedMessages.isEmpty());
        }
    }

    @Test
    public final void cancelAllAffectsTrackedGoalsButNotFutureGoals() {
        try (ActionServerHarness harness = new ActionServerHarness(Optional.of(Boolean.TRUE))) {
            final FibonacciActionGoal firstGoal = harness.newGoal("tracked-goal-1", new Time(80, 0), 5);
            final FibonacciActionGoal secondGoal = harness.newGoal("tracked-goal-2", new Time(81, 0), 5);

            harness.actionServer.gotGoal(firstGoal);
            harness.actionServer.gotGoal(secondGoal);
            harness.actionServer.gotCancel(harness.newGoalId("", new Time()));

            Assert.assertEquals(GoalStatus.PREEMPTING, harness.actionServer.getGoalStatus(firstGoal.getGoalId().getId()));
            Assert.assertEquals(GoalStatus.PREEMPTING, harness.actionServer.getGoalStatus(secondGoal.getGoalId().getId()));

            final FibonacciActionGoal futureGoal = harness.newGoal("future-goal", new Time(82, 0), 5);
            harness.actionServer.gotGoal(futureGoal);

            Assert.assertEquals(GoalStatus.ACTIVE, harness.actionServer.getGoalStatus(futureGoal.getGoalId().getId()));
            Assert.assertEquals(3, harness.listener.goalReceivedCount.get());
            Assert.assertEquals(3, harness.listener.acceptGoalCount.get());
            Assert.assertEquals(1, harness.listener.cancelReceivedCount.get());
            Assert.assertTrue(harness.resultPublisher.publishedMessages.isEmpty());
        }
    }

    @Test
    public final void unmatchedExactCancelPlaceholderExpiresWhenGoalNeverArrives() throws Exception {
        try (ActionServerHarness harness = new ActionServerHarness(Optional.of(Boolean.TRUE), 5, TimeUnit.MILLISECONDS)) {
            final String goalId = "missing-goal";

            harness.actionServer.gotCancel(harness.newGoalId(goalId, new Time()));
            Assert.assertEquals(GoalStatus.RECALLING, harness.actionServer.getGoalStatus(goalId));

            harness.actionServer.sendStatusTick();
            Thread.sleep(20);
            harness.actionServer.sendStatusTick();

            Assert.assertEquals(-100, harness.actionServer.getGoalStatus(goalId));
        }
    }

    @Test
    public final void goalIsHandledNormallyAfterExpiredPlaceholderIsRemoved() throws Exception {
        try (ActionServerHarness harness = new ActionServerHarness(Optional.of(Boolean.TRUE), 5, TimeUnit.MILLISECONDS)) {
            final String goalId = "late-goal";
            final FibonacciActionGoal goal = harness.newGoal(goalId, new Time(70, 0), 5);

            harness.actionServer.gotCancel(harness.newGoalId(goalId, new Time()));
            harness.actionServer.sendStatusTick();
            Thread.sleep(20);
            harness.actionServer.sendStatusTick();

            harness.actionServer.gotGoal(goal);

            Assert.assertEquals(1, harness.listener.goalReceivedCount.get());
            Assert.assertEquals(1, harness.listener.acceptGoalCount.get());
            Assert.assertEquals(GoalStatus.ACTIVE, harness.actionServer.getGoalStatus(goalId));
            Assert.assertTrue(harness.resultPublisher.publishedMessages.isEmpty());
        }
    }

    @Test
    public final void duplicateGoalIdIsIgnoredWithoutMutatingTrackedGoalMetadata() {
        try (ActionServerHarness harness = new ActionServerHarness(Optional.of(Boolean.TRUE))) {
            final String goalId = "duplicate-goal";
            final Time originalStamp = new Time(90, 0);
            final Time duplicateStamp = new Time(91, 0);
            final FibonacciActionGoal firstGoal = harness.newGoal(goalId, originalStamp, 5);
            final FibonacciActionGoal duplicateGoal = harness.newGoal(goalId, duplicateStamp, 8);

            harness.actionServer.gotGoal(firstGoal);
            harness.actionServer.sendStatusTick();
            harness.actionServer.gotGoal(duplicateGoal);
            harness.actionServer.sendStatusTick();

            Assert.assertEquals(1, harness.listener.goalReceivedCount.get());
            Assert.assertEquals(1, harness.listener.acceptGoalCount.get());
            Assert.assertEquals(GoalStatus.ACTIVE, harness.actionServer.getGoalStatus(goalId));
            Assert.assertTrue(harness.resultPublisher.publishedMessages.isEmpty());

            final GoalStatusArray latestStatus = harness.statusPublisher.getLastPublishedMessage();
            Assert.assertNotNull(latestStatus);
            Assert.assertEquals(1, latestStatus.getStatusList().size());
            final GoalStatus trackedStatus = latestStatus.getStatusList().get(0);
            Assert.assertEquals(goalId, trackedStatus.getGoalId().getId());
            Assert.assertEquals(originalStamp, trackedStatus.getGoalId().getStamp());
        }
    }

    private static final class ActionServerHarness implements AutoCloseable {
        private static final String ACTION_RESULT_TOPIC = ACTION_NAME + "/result";
        private static final String ACTION_FEEDBACK_TOPIC = ACTION_NAME + "/feedback";
        private static final String ACTION_STATUS_TOPIC = ACTION_NAME + "/status";
        private static final String ACTION_GOAL_TOPIC = ACTION_NAME + "/goal";
        private static final String ACTION_CANCEL_TOPIC = ACTION_NAME + "/cancel";

        private final MessageFactory messageFactory =
                new DefaultMessageFactory(new MessageDefinitionReflectionProvider());
        private final CapturingPublisher<FibonacciActionResult> resultPublisher =
                new CapturingPublisher<>(this.messageFactory, FibonacciActionResult._TYPE);
        private final CapturingPublisher<FibonacciActionFeedback> feedbackPublisher =
                new CapturingPublisher<>(this.messageFactory, FibonacciActionFeedback._TYPE);
        private final CapturingPublisher<GoalStatusArray> statusPublisher =
                new CapturingPublisher<>(this.messageFactory, GoalStatusArray._TYPE);
        private final CapturingSubscriber<FibonacciActionGoal> goalSubscriber = new CapturingSubscriber<>();
        private final CapturingSubscriber<GoalID> cancelSubscriber = new CapturingSubscriber<>();
        private final RecordingListener listener;
        private final ActionServer<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult> actionServer;

        private ActionServerHarness(final Optional<Boolean> acceptGoalResponse) {
            this(acceptGoalResponse, 5000, TimeUnit.MILLISECONDS);
        }

        private ActionServerHarness(final Optional<Boolean> acceptGoalResponse,
                                    final long terminalStatusRetention,
                                    final TimeUnit terminalStatusRetentionTimeUnit) {
            this.listener = new RecordingListener(acceptGoalResponse);
            this.actionServer = new ActionServer<>(this.createConnectedNode(), this.listener, ACTION_NAME,
                    FibonacciActionGoal._TYPE, FibonacciActionFeedback._TYPE, FibonacciActionResult._TYPE,
                    terminalStatusRetention, terminalStatusRetentionTimeUnit);
        }

        private final FibonacciActionGoal newGoal(final String goalId, final Time stamp, final int order) {
            final FibonacciActionGoal goal = this.messageFactory.newFromType(FibonacciActionGoal._TYPE);
            goal.getGoalId().setId(goalId);
            goal.getGoalId().setStamp(new Time(stamp));
            goal.getGoal().setOrder(order);
            return goal;
        }

        private final GoalID newGoalId(final String goalId, final Time stamp) {
            final GoalID result = this.messageFactory.newFromType(GoalID._TYPE);
            result.setId(goalId);
            result.setStamp(new Time(stamp));
            return result;
        }

        private final ConnectedNode createConnectedNode() {
            return (ConnectedNode) Proxy.newProxyInstance(
                    ConnectedNode.class.getClassLoader(),
                    new Class[]{ConnectedNode.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getDefaultMessageFactory" -> this.messageFactory;
                        case "newPublisher" -> this.getPublisher((String) args[0]);
                        case "newSubscriber" -> this.getSubscriber((String) args[0]);
                        case "getName" -> GraphName.of("/action_server_test_node");
                        case "resolveName" -> args[0] instanceof GraphName ? args[0] : GraphName.of((String) args[0]);
                        case "shutdown", "removeListeners" -> null;
                        default -> defaultValue(method.getReturnType());
                    });
        }

        private final Object getPublisher(final String topicName) {
            if (ACTION_RESULT_TOPIC.equals(topicName)) {
                return this.resultPublisher.proxy;
            }
            if (ACTION_FEEDBACK_TOPIC.equals(topicName)) {
                return this.feedbackPublisher.proxy;
            }
            if (ACTION_STATUS_TOPIC.equals(topicName)) {
                return this.statusPublisher.proxy;
            }
            throw new IllegalArgumentException("Unexpected publisher topic:" + topicName);
        }

        private final Object getSubscriber(final String topicName) {
            if (ACTION_GOAL_TOPIC.equals(topicName)) {
                return this.goalSubscriber.proxy;
            }
            if (ACTION_CANCEL_TOPIC.equals(topicName)) {
                return this.cancelSubscriber.proxy;
            }
            throw new IllegalArgumentException("Unexpected subscriber topic:" + topicName);
        }

        @Override
        public final void close() {
            this.actionServer.finish();
        }
    }

    private static final class RecordingListener implements ActionServerListener<FibonacciActionGoal> {
        private final AtomicInteger goalReceivedCount = new AtomicInteger();
        private final AtomicInteger acceptGoalCount = new AtomicInteger();
        private final AtomicInteger cancelReceivedCount = new AtomicInteger();
        private final Optional<Boolean> acceptGoalResponse;

        private RecordingListener(final Optional<Boolean> acceptGoalResponse) {
            this.acceptGoalResponse = acceptGoalResponse;
        }

        @Override
        public final void goalReceived(final FibonacciActionGoal goal) {
            this.goalReceivedCount.incrementAndGet();
        }

        @Override
        public final Optional<Boolean> acceptGoal(final FibonacciActionGoal goal) {
            this.acceptGoalCount.incrementAndGet();
            return this.acceptGoalResponse;
        }

        @Override
        public final void cancelReceived(final GoalID id) {
            this.cancelReceivedCount.incrementAndGet();
        }
    }

    private static final class CapturingPublisher<T extends Message> {
        private final List<T> publishedMessages = new CopyOnWriteArrayList<>();
        private final Publisher<T> proxy;

        @SuppressWarnings("unchecked")
        private CapturingPublisher(final MessageFactory messageFactory, final String messageType) {
            this.proxy = (Publisher<T>) Proxy.newProxyInstance(
                    Publisher.class.getClassLoader(),
                    new Class[]{Publisher.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "newMessage" -> messageFactory.newFromType(messageType);
                        case "publish" -> {
                            this.publishedMessages.add((T) args[0]);
                            yield null;
                        }
                        case "setLatchMode", "shutdown", "addListener" -> null;
                        case "getLatchMode", "hasSubscribers" -> Boolean.FALSE;
                        case "getNumberOfSubscribers" -> 0;
                        default -> defaultValue(method.getReturnType());
                    });
        }

        private final T getLastPublishedMessage() {
            if (this.publishedMessages.isEmpty()) {
                return null;
            }
            return this.publishedMessages.get(this.publishedMessages.size() - 1);
        }
    }

    private static final class CapturingSubscriber<T extends Message> {
        private final Subscriber<T> proxy;

        @SuppressWarnings("unchecked")
        private CapturingSubscriber() {
            this.proxy = (Subscriber<T>) Proxy.newProxyInstance(
                    Subscriber.class.getClassLoader(),
                    new Class[]{Subscriber.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "addMessageListener", "addSubscriberListener", "removeAllMessageListeners", "shutdown" -> null;
                        case "removeMessageListener", "getLatchMode" -> Boolean.FALSE;
                        default -> defaultValue(method.getReturnType());
                    });
        }
    }

    private static final Object defaultValue(final Class<?> returnType) {
        if (returnType == null || Void.TYPE.equals(returnType)) {
            return null;
        }
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (Boolean.TYPE.equals(returnType)) {
            return Boolean.FALSE;
        }
        if (Byte.TYPE.equals(returnType)) {
            return (byte) 0;
        }
        if (Short.TYPE.equals(returnType)) {
            return (short) 0;
        }
        if (Integer.TYPE.equals(returnType)) {
            return 0;
        }
        if (Long.TYPE.equals(returnType)) {
            return 0L;
        }
        if (Float.TYPE.equals(returnType)) {
            return 0F;
        }
        if (Double.TYPE.equals(returnType)) {
            return 0D;
        }
        if (Character.TYPE.equals(returnType)) {
            return '\0';
        }
        throw new IllegalArgumentException("Unsupported primitive type:" + returnType);
    }
}
