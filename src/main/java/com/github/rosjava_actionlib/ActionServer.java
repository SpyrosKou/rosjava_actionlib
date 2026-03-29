/**
 * Copyright 2020 Spyros Koukas
 * Copyright 2015 Ekumen www.ekumenlabs.com
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
import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.ros.internal.message.Message;
import org.ros.message.MessageFactory;
import org.ros.message.Time;
import org.ros.node.ConnectedNode;
import org.ros.node.topic.Publisher;
import org.ros.node.topic.Subscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Class to encapsulate the actionlib server's communication and goal management.
 *
 * @author Spyros Koukas
 * @author Ernesto Corbellini ecorbellini@ekumenlabs.com
 */
public final class ActionServer<T_ACTION_GOAL extends Message, T_ACTION_FEEDBACK extends Message, T_ACTION_RESULT extends Message> {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    //default status_frequency is 5Hz for python and cpp
    private static final long DEFAULT_STATUS_TICK_PERIOD_MILLIS = 200;
    private static final long DEFAULT_STATUS_TICK_DELAY_MILLIS = 200;
    private static final long DEFAULT_TERMINAL_STATUS_RETENTION_MILLIS = 5000;
    private static final long TERMINAL_STATUS_RETENTION_NOT_SCHEDULED_NANOS = Long.MIN_VALUE;

    /**
     * Keeps the status of each goal
     *
     * @param <T_ACTION_GOAL_TYPE> the T_ACTION_GOAL type
     */
    private static final class ServerGoal<T_ACTION_GOAL_TYPE extends Message> {
        private final T_ACTION_GOAL_TYPE goal;
        private final GoalID goalId;
        private final ServerStateMachine stateMachine = new ServerStateMachine();
        private volatile long terminalStatusRetentionDeadlineNanos = TERMINAL_STATUS_RETENTION_NOT_SCHEDULED_NANOS;

        private ServerGoal(final T_ACTION_GOAL_TYPE goal, final GoalID goalId) {
            this.goal = goal;
            this.goalId = goalId;
        }
    }

    //Final
    private final String actionGoalType;
    private final String actionResultType;
    private final String actionFeedbackType;
    private final String actionName;
    private final ActionLibTopics actionTopics;
    private final ActionServerListener<T_ACTION_GOAL> actionServerListener;
    private final MessageFactory messageFactory;
    private final long terminalStatusRetentionNanos;
    private final Timer statusTick = new Timer();
    private final ConcurrentHashMap<String, ServerGoal<T_ACTION_GOAL>> goalIdToGoalStatusMap = new ConcurrentHashMap<>();
    private volatile Time lastCancelStamp = new Time();

    //Non Final
    private Subscriber<T_ACTION_GOAL> goalSubscriber = null;
    private Subscriber<GoalID> cancelSubscriber = null;
    private Publisher<T_ACTION_RESULT> resultPublisher = null;
    private Publisher<T_ACTION_FEEDBACK> feedbackPublisher = null;
    private Publisher<GoalStatusArray> statusPublisher = null;


    /**
     * Constructor.
     *
     * @param connectedNode        Object representing a node connected to a ROS master.
     * @param actionServerListener the Listener of the T_ACTION_GOAL, actionServerListener is used to consume incoming goals
     * @param actionName           String that identifies the name of this action. This name
     *                             is used for naming the ROS topics.
     * @param actionGoalType       String holding the type for the action goal message.
     * @param actionFeedbackType   String holding the type for the action feedback
     *                             message.
     * @param actionResultType     String holding the type for the action result
     *                             message.
     */
    public ActionServer(final ConnectedNode connectedNode
            , final ActionServerListener<T_ACTION_GOAL> actionServerListener
            , final String actionName
            , final String actionGoalType
            , final String actionFeedbackType
            , final String actionResultType) {
        this(connectedNode, actionServerListener, actionName, actionGoalType, actionFeedbackType, actionResultType,
                DEFAULT_TERMINAL_STATUS_RETENTION_MILLIS, TimeUnit.MILLISECONDS);
    }

    ActionServer(final ConnectedNode connectedNode
            , final ActionServerListener<T_ACTION_GOAL> actionServerListener
            , final String actionName
            , final String actionGoalType
            , final String actionFeedbackType
            , final String actionResultType
            , final long terminalStatusRetention
            , final TimeUnit terminalStatusRetentionTimeUnit) {
        Objects.requireNonNull(connectedNode);
        Objects.requireNonNull(actionServerListener);
        Objects.requireNonNull(terminalStatusRetentionTimeUnit);
        Preconditions.checkArgument(StringUtils.isNotBlank(actionName));
        Preconditions.checkArgument(StringUtils.isNotBlank(actionGoalType));
        Preconditions.checkArgument(StringUtils.isNotBlank(actionFeedbackType));
        Preconditions.checkArgument(StringUtils.isNotBlank(actionResultType));
        Preconditions.checkArgument(terminalStatusRetention >= 0);
        this.actionServerListener = actionServerListener;

        this.actionName = actionName;
        this.actionGoalType = actionGoalType;
        this.actionFeedbackType = actionFeedbackType;
        this.actionResultType = actionResultType;
        this.actionTopics = new ActionLibTopics(actionName);
        this.messageFactory = connectedNode.getDefaultMessageFactory();
        this.terminalStatusRetentionNanos = terminalStatusRetentionTimeUnit.toNanos(terminalStatusRetention);
        this.connect(connectedNode);
    }

    /**
     * Returns the preferred immutable API for reading this server's actionlib topic names.
     *
     * @return topic bundle for this action server
     */
    public final ActionLibTopics getActionTopics() {
        return this.actionTopics;
    }


    /**
     * Publish the current status information for the tracked goals on the /status topic.
     *
     * @param status GoalStatusArray message containing the status to send.
     * @see actionlib_msgs.GoalStatusArray
     */
    public final void sendStatus(final GoalStatusArray status) {

        this.statusPublisher.publish(status);
    }

    /**
     * Publish a feedback message on the /feedback topic.
     *
     * @param feedback An action feedback message to send.
     */
    public final void sendFeedback(final T_ACTION_FEEDBACK feedback) {
        this.feedbackPublisher.publish(feedback);
    }

    /**
     * Publish a result message on the /result topic.
     *
     * @param result The action result message to send.
     */
    public final void sendResult(final T_ACTION_RESULT result) {
        final GoalStatus goalStatus = this.synchronizeResultGoalStatus(this.getResultGoalStatus(result));
        if (LOGGER.isTraceEnabled()) {
            final String goalId = goalStatus != null && goalStatus.getGoalId() != null ? goalStatus.getGoalId().getId() : null;
            LOGGER.trace("Publishing result on action:[{}] with goalId:[{}]", this.actionName, goalId);
        }
        this.resultPublisher.publish(result);
        if (goalStatus != null && isTerminalStatus(goalStatus.getStatus())) {
            this.sendStatusTick();
        }
    }

    private final void evictGoal(final String goalId) {
        if (goalId == null) {
            return;
        }
        this.goalIdToGoalStatusMap.remove(goalId);
    }

    private final GoalStatus getResultGoalStatus(final T_ACTION_RESULT result) {
        return ActionLibMessagesUtils.getSubMessageFromMessage(result, "getStatus");
    }

    private final GoalStatus synchronizeResultGoalStatus(final GoalStatus goalStatus) {
        if (goalStatus == null || goalStatus.getGoalId() == null) {
            return goalStatus;
        }
        final String goalId = goalStatus.getGoalId().getId();
        final ServerGoal<T_ACTION_GOAL> serverGoal = this.goalIdToGoalStatusMap.get(goalId);
        if (serverGoal == null) {
            return goalStatus;
        }
        goalStatus.getGoalId().setId(serverGoal.goalId.getId());
        goalStatus.getGoalId().setStamp(serverGoal.goalId.getStamp());
        final byte trackedStatus = serverGoal.stateMachine.getState();
        goalStatus.setStatus(trackedStatus);
        return goalStatus;
    }

    static final boolean isTerminalStatus(final byte status) {
        return status == GoalStatus.REJECTED
                || status == GoalStatus.RECALLED
                || status == GoalStatus.PREEMPTED
                || status == GoalStatus.SUCCEEDED
                || status == GoalStatus.ABORTED;
    }

    /**
     * Publish the action server topics: /status, /feedback, /result
     *
     * @param connectedNode The object representing a connectedNode connected to a ROS master.
     */
    private final void publishServer(final ConnectedNode connectedNode) {
        this.statusPublisher = connectedNode.newPublisher(this.actionTopics.getGoalStatusTopicName(), GoalStatusArray._TYPE);
        this.feedbackPublisher = connectedNode.newPublisher(this.actionTopics.getFeedbackTopicName(), actionFeedbackType);
        this.resultPublisher = connectedNode.newPublisher(this.actionTopics.getResultTopicName(), actionResultType);
        this.statusTick.scheduleAtFixedRate(new TimerTask() {
            @Override
            public final void run() {
                ActionServer.this.sendStatusTick();
            }
        }, DEFAULT_STATUS_TICK_DELAY_MILLIS, DEFAULT_STATUS_TICK_PERIOD_MILLIS);
    }

    /**
     * @return published topics in the order result, feedback, status
     * @deprecated Use {@link #getActionTopics()} and read the required topic names directly.
     */
    @Deprecated
    public final String[] getPublishedTopics() {
        final String[] publishedTopics = new String[3];
        publishedTopics[0] = this.actionTopics.getResultTopicName();
        publishedTopics[1] = this.actionTopics.getFeedbackTopicName();
        publishedTopics[2] = this.actionTopics.getGoalStatusTopicName();
        return publishedTopics;
    }

    /**
     * @return subscribed topics in the order goal, cancel
     * @deprecated Use {@link #getActionTopics()} and read the required topic names directly.
     */
    @Deprecated
    public final String[] getSubscribedTopics() {
        final String[] subscribedTopics = new String[2];
        subscribedTopics[0] = this.actionTopics.getGoalTopicName();
        subscribedTopics[1] = this.actionTopics.getCancelTopicName();

        return subscribedTopics;
    }

    /**
     * Subscribed topic
     *
     * @return goal topic name
     * @deprecated Use {@link #getActionTopics()} and then {@link ActionLibTopics#getGoalTopicName()}.
     */
    @Deprecated
    public final String getActionGoalTopic() {
        return this.actionTopics.getGoalTopicName();
    }

    /**
     * Subscribed topic
     *
     * @return cancel topic name
     * @deprecated Use {@link #getActionTopics()} and then {@link ActionLibTopics#getCancelTopicName()}.
     */
    @Deprecated
    public final String getActionCancelTopic() {
        return this.actionTopics.getCancelTopicName();
    }

    /**
     * Published topic
     *
     * @return status topic name
     * @deprecated Use {@link #getActionTopics()} and then {@link ActionLibTopics#getGoalStatusTopicName()}.
     */
    @Deprecated
    public final String getActionStatusTopic() {
        return this.actionTopics.getGoalStatusTopicName();
    }

    /**
     * Published topic
     *
     * @return feedback topic name
     * @deprecated Use {@link #getActionTopics()} and then {@link ActionLibTopics#getFeedbackTopicName()}.
     */
    @Deprecated
    public final String getActionFeedbackTopic() {
        return this.actionTopics.getFeedbackTopicName();
    }

    /**
     * Published topic
     *
     * @return result topic name
     * @deprecated Use {@link #getActionTopics()} and then {@link ActionLibTopics#getResultTopicName()}.
     */
    @Deprecated
    public final String getActionResultTopic() {
        return this.actionTopics.getResultTopicName();
    }


    /**
     * Stop publishing the action server topics.
     */
    private final void unpublishServer() {

        try {
            this.statusTick.purge();
            this.statusTick.cancel();


        } catch (final Exception exception) {
            LOGGER.error(ExceptionUtils.getStackTrace(exception));
        }


        if (this.statusPublisher != null) {
            try {
                this.statusPublisher.shutdown(5, TimeUnit.SECONDS);
                this.statusPublisher = null;
            } catch (final Exception exception) {
                LOGGER.error(ExceptionUtils.getStackTrace(exception));
            }
        }
        if (this.feedbackPublisher != null) {
            try {
                this.feedbackPublisher.shutdown(5, TimeUnit.SECONDS);
                this.feedbackPublisher = null;
            } catch (final Exception exception) {
                LOGGER.error(ExceptionUtils.getStackTrace(exception));
            }
        }
        if (this.resultPublisher != null) {
            try {
                this.resultPublisher.shutdown(5, TimeUnit.SECONDS);
                this.resultPublisher = null;
            } catch (final Exception exception) {
                LOGGER.error(ExceptionUtils.getStackTrace(exception));
            }
        }

    }

    /**
     * Subscribe to the action client's topics: goal and cancel.
     *
     * @param node The ROS node connected to the master.
     */
    private final void subscribeToClient(final ConnectedNode node) {
        this.goalSubscriber = node.newSubscriber(this.actionTopics.getGoalTopicName(), actionGoalType);
        this.cancelSubscriber = node.newSubscriber(this.actionTopics.getCancelTopicName(), GoalID._TYPE);
        this.goalSubscriber.addMessageListener(this::gotGoal);
        this.cancelSubscriber.addMessageListener(this::gotCancel);
    }

    /**
     * Unsubscribe from the client's topics.
     */
    private final void unsubscribeToClient() {
        if (this.goalSubscriber != null) {
            this.goalSubscriber.shutdown(5, TimeUnit.SECONDS);
            this.goalSubscriber = null;
        }
        if (this.cancelSubscriber != null) {
            this.cancelSubscriber.shutdown(5, TimeUnit.SECONDS);
            this.cancelSubscriber = null;
        }
    }

    /**
     * Called when a message is received from the subscribed goal topic.
     *
     * @param goal
     */
    public final void gotGoal(final T_ACTION_GOAL goal) {
        if (goal != null) {
            final GoalID goalId = getGoalId(goal);
            final String goalIdString = goalId.getId();
            final AtomicBoolean goalAlreadyTracked = new AtomicBoolean(false);
            final ServerGoal<T_ACTION_GOAL> trackedGoal = this.goalIdToGoalStatusMap.compute(goalIdString, (id, existingGoal) -> {
                if (existingGoal != null) {
                    goalAlreadyTracked.set(true);
                    if (existingGoal.goal == null) {
                        copyGoalId(goalId, existingGoal.goalId);
                    }
                    return existingGoal;
                }
                return new ServerGoal<>(goal, ActionServer.this.copyGoalId(goalId));
            });
            final byte trackedState = trackedGoal.stateMachine.getState();
            final boolean matchedPendingCancelPlaceholder =
                    goalAlreadyTracked.get() && trackedGoal.goal == null && trackedState == GoalStatus.PENDING;

            if (trackedState == GoalStatus.RECALLING || matchedPendingCancelPlaceholder) {
                trackedGoal.terminalStatusRetentionDeadlineNanos = TERMINAL_STATUS_RETENTION_NOT_SCHEDULED_NANOS;
                this.recallTrackedGoal(goalIdString);
                return;
            }

            if (goalAlreadyTracked.get()) {
                return;
            }

            if (this.isGoalCancelledByTimestamp(goalId)) {
                this.recallTrackedGoal(goalIdString);
                return;
            }

            //this#actionServerListener is guaranteed to never be null, this call is for information purposes only
            this.actionServerListener.goalReceived(goal);

            // ask if the user accepts the goal
            final Optional<Boolean> acceptedOptional = this.actionServerListener.acceptGoal(goal);
            Objects.requireNonNull(acceptedOptional);
            if (acceptedOptional.isPresent()) {
                final boolean accepted = acceptedOptional.get();
                if (accepted) {
                    this.setAccepted(goalIdString);

                } else {
                    this.setRejected(goalIdString);
                }
            } else {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Goal :{} should be handled by user", goalIdString);
                }
            }

        } else {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Got null goal:{}",this.toString());
            }
        }

    }

    /**
     * Called when we get a message on the subscribed cancel topic.
     *
     * @param goalID
     */
    public final void gotCancel(final GoalID goalID) {
        if (goalID != null) {
            if (goalID.getStamp() != null && goalID.getStamp().compareTo(this.lastCancelStamp) > 0) {
                this.lastCancelStamp = new Time(goalID.getStamp());
            }
            boolean exactGoalIdMatched = false;
            for (final Map.Entry<String, ServerGoal<T_ACTION_GOAL>> entry : this.goalIdToGoalStatusMap.entrySet()) {
                final String trackedGoalId = entry.getKey();
                final ServerGoal<T_ACTION_GOAL> trackedGoal = entry.getValue();
                if (this.matchesCancelRequest(goalID, trackedGoal.goalId)) {
                    exactGoalIdMatched = exactGoalIdMatched || trackedGoalId.equals(goalID.getId());
                    this.requestCancel(trackedGoalId);
                }
            }
            if (StringUtils.isNotBlank(goalID.getId()) && !exactGoalIdMatched) {
                final ServerGoal<T_ACTION_GOAL> pendingCancelledGoal = this.goalIdToGoalStatusMap.compute(goalID.getId(),
                        (id, existingGoal) -> existingGoal == null ? new ServerGoal<>(null, ActionServer.this.copyGoalId(goalID)) : existingGoal);
                if (pendingCancelledGoal != null) {
                    copyGoalId(goalID, pendingCancelledGoal.goalId);
                    this.requestCancel(goalID.getId());
                }
            }
            this.actionServerListener.cancelReceived(goalID);
        }
    }


    /**
     * Publishes the current status on the server's status topic.
     * This is used like a heartbeat to update the status of every tracked goal.
     */
    public final synchronized void sendStatusTick() {
        try {
            final long nowNanos = System.nanoTime();
            final GoalStatusArray goalStatusArray = this.messageFactory.newFromType(GoalStatusArray._TYPE);
            final List<GoalStatus> goalStatusList = new ArrayList<>(this.goalIdToGoalStatusMap.size());
            final List<String> goalIdsToEvict = new ArrayList<>();
            goalStatusArray.setStatusList(goalStatusList);

            for (final Map.Entry<String, ServerGoal<T_ACTION_GOAL>> entry : this.goalIdToGoalStatusMap.entrySet()) {
                final String goalId = entry.getKey();
                final ServerGoal<T_ACTION_GOAL> serverGoal = entry.getValue();
                final byte goalState = serverGoal.stateMachine.getState();
                if (shouldExpireStatusEntry(serverGoal, goalState)) {
                    final long retentionDeadlineNanos = serverGoal.terminalStatusRetentionDeadlineNanos;
                    if (retentionDeadlineNanos == TERMINAL_STATUS_RETENTION_NOT_SCHEDULED_NANOS) {
                        serverGoal.terminalStatusRetentionDeadlineNanos = nowNanos + this.terminalStatusRetentionNanos;
                    } else if (retentionDeadlineNanos <= nowNanos) {
                        goalIdsToEvict.add(goalId);
                        continue;
                    }
                } else {
                    serverGoal.terminalStatusRetentionDeadlineNanos = TERMINAL_STATUS_RETENTION_NOT_SCHEDULED_NANOS;
                }
                final GoalStatus goalStatus = this.messageFactory.newFromType(GoalStatus._TYPE);
                goalStatus.setGoalId(serverGoal.goalId);
                goalStatus.setStatus(goalState);
                goalStatusList.add(goalStatus);
            }


            this.sendStatus(goalStatusArray);
            goalIdsToEvict.forEach(this::evictGoal);

        } catch (final Exception exception) {
            LOGGER.error(ExceptionUtils.getStackTrace(exception));
        }

    }

    /**
     * @return a new T_ACTION_RESULT result message
     */
    public final T_ACTION_RESULT newResultMessage() {
        return this.resultPublisher.newMessage();
    }

    /**
     * @return a new T_ACTION_FEEDBACK Message
     */
    public final T_ACTION_FEEDBACK newFeedbackMessage() {
        return this.feedbackPublisher.newMessage();
    }

    /**
     * Returns the goal ID object related to a given action goal.
     *
     * @param goal An action goal message.
     * @return The goal ID object.
     */
    public final GoalID getGoalId(final T_ACTION_GOAL goal) {

        final GoalID gid = ActionLibMessagesUtils.getSubMessageFromMessage(goal, "getGoalId");
        return gid;
    }

    /**
     * Get the current state of the referenced goal.
     *
     * @param goalId String representing the ID of the goal.
     * @return The current state of the goal or -100 if the goal ID is not tracked.
     * @see actionlib_msgs.GoalStatus
     */
    public final byte getGoalStatus(final String goalId) {
        final ServerGoal<T_ACTION_GOAL> trackedGoal = this.goalIdToGoalStatusMap.get(goalId);
        return trackedGoal == null ? -100 : trackedGoal.stateMachine.getState();
    }

    /**
     * Express a succeed event for this goal. The state of the goal will be updated.
     *
     * @param goalIdString
     */
    public final void setSucceed(final String goalIdString) {
        this.recordTransitionEvent(goalIdString, ServerStateMachine.Events.SUCCEED);

    }

    /**
     * Express a preempted event for this goal. The state of the goal will be updated.
     *
     * @param goalIdString
     */
    public final void setPreempt(final String goalIdString) {
        this.recordTransitionEvent(goalIdString, ServerStateMachine.Events.CANCEL_REQUEST);
        this.recordTransitionEvent(goalIdString, ServerStateMachine.Events.CANCEL);
    }

    /**
     * The user accepted the goal
     * Status is also sent to client
     */
    public final void setAccepted(final String goalIdString) {
        // the user accepted the goal

        this.recordTransitionEvent(goalIdString, ServerStateMachine.Events.ACCEPT);
        this.sendStatusTick();
    }

    /**
     * The user requested to cancel the goal
     */
    public final void setCancelRequested(final String goalIdString) {
        this.recordTransitionEvent(goalIdString, ServerStateMachine.Events.CANCEL_REQUEST);
    }

    /**
     * The server cancelled the goal
     */
    public final void setCancel(final String goalIdString) {
        this.recordTransitionEvent(goalIdString, ServerStateMachine.Events.CANCEL);
    }


    /**
     * the user rejected the goal
     * Status is also sent to client
     *
     * @param goalIdString
     */
    public final void setRejected(final String goalIdString) {
        // the user rejected the goal
        this.recordTransitionEvent(goalIdString, ServerStateMachine.Events.REJECT);
        this.sendStatusTick();

    }

    private final void recordTransitionEvent(final String goalIdString, final int event) {
        this.goalIdToGoalStatusMap.computeIfPresent(goalIdString, (id, value) -> {
            value.stateMachine.transition(event);
            return value;
        });
    }

    static final boolean isCancelRequestedStatus(final byte status) {
        return status == GoalStatus.PREEMPTING || status == GoalStatus.RECALLING;
    }

    static final boolean isCancelledStatus(final byte status) {
        return status == GoalStatus.PREEMPTED || status == GoalStatus.RECALLED;
    }

    private static final boolean shouldExpireStatusEntry(final ServerGoal<?> serverGoal, final byte status) {
        return isTerminalStatus(status) || isUnmatchedCancelPlaceholder(serverGoal, status);
    }

    private static final boolean isUnmatchedCancelPlaceholder(final ServerGoal<?> serverGoal, final byte status) {
        return serverGoal != null && serverGoal.goal == null && status == GoalStatus.RECALLING;
    }

    private final GoalID copyGoalId(final GoalID source) {
        final GoalID copy = this.messageFactory.newFromType(GoalID._TYPE);
        copyGoalId(source, copy);
        return copy;
    }

    private static final void copyGoalId(final GoalID source, final GoalID target) {
        if (source == null || target == null) {
            return;
        }
        target.setId(source.getId());
        target.setStamp(source.getStamp() == null ? new Time() : new Time(source.getStamp()));
    }

    private final boolean isGoalCancelledByTimestamp(final GoalID goalId) {
        return goalId != null
                && goalId.getStamp() != null
                && !goalId.getStamp().isZero()
                && goalId.getStamp().compareTo(this.lastCancelStamp) <= 0;
    }

    private static final boolean isCancelAllRequest(final GoalID cancelGoalId) {
        return cancelGoalId != null
                && StringUtils.isBlank(cancelGoalId.getId())
                && cancelGoalId.getStamp() != null
                && cancelGoalId.getStamp().isZero();
    }

    private final boolean matchesCancelRequest(final GoalID cancelGoalId, final GoalID trackedGoalId) {
        if (cancelGoalId == null || trackedGoalId == null) {
            return false;
        }
        if (isCancelAllRequest(cancelGoalId)) {
            return true;
        }
        if (StringUtils.isNotBlank(cancelGoalId.getId()) && cancelGoalId.getId().equals(trackedGoalId.getId())) {
            return true;
        }
        return cancelGoalId.getStamp() != null
                && !cancelGoalId.getStamp().isZero()
                && trackedGoalId.getStamp() != null
                && trackedGoalId.getStamp().compareTo(cancelGoalId.getStamp()) <= 0;
    }

    private final void requestCancel(final String goalIdString) {
        final ServerGoal<T_ACTION_GOAL> trackedGoal = this.goalIdToGoalStatusMap.get(goalIdString);
        if (trackedGoal == null) {
            return;
        }
        final byte currentState = trackedGoal.stateMachine.getState();
        if (currentState == GoalStatus.PENDING || currentState == GoalStatus.ACTIVE) {
            this.setCancelRequested(goalIdString);
        }
    }

    private final void recallTrackedGoal(final String goalIdString) {
        this.requestCancel(goalIdString);
        this.setCancel(goalIdString);
        this.publishTrackedResult(goalIdString);
    }

    private final void publishTrackedResult(final String goalIdString) {
        final ServerGoal<T_ACTION_GOAL> trackedGoal = this.goalIdToGoalStatusMap.get(goalIdString);
        if (trackedGoal == null) {
            return;
        }
        final T_ACTION_RESULT resultMessage = this.newResultMessage();
        final GoalStatus resultGoalStatus = this.getResultGoalStatus(resultMessage);
        if (resultGoalStatus != null && resultGoalStatus.getGoalId() != null) {
            copyGoalId(trackedGoal.goalId, resultGoalStatus.getGoalId());
            resultGoalStatus.setStatus(trackedGoal.stateMachine.getState());
        }
        this.sendResult(resultMessage);
    }

    /**
     * Express an aborted event for this goal. The state of the goal will be updated.
     */
    public final void setAbort(final String goalIdString) {
        this.recordTransitionEvent(goalIdString, ServerStateMachine.Events.ABORT);
    }


    /**
     * Publishes the server's topics and subscribes to the client's topics.
     */
    private final void connect(final ConnectedNode node) {
        this.publishServer(node);
        this.subscribeToClient(node);
    }

    /**
     * Finish the action server.
     * Unregister publishers and listeners.
     * The programmer need to ensure that this method is called when the program has finished using this {@link ActionServer}
     */
    public final void finish() {
        this.unpublishServer();
        this.unsubscribeToClient();

    }

}
