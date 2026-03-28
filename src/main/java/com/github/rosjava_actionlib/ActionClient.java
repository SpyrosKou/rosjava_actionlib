/**
 * Derivative Work: Copyright 2020 Spyros Koukas
 * Original File: Copyright 2015 Ekumen www.ekumenlabs.com
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
import com.google.common.base.Stopwatch;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.util.concurrent.Runnables;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.ros.internal.message.Message;
import org.ros.message.Time;
import org.ros.master.client.MasterStateClient;
import org.ros.master.client.TopicSystemState;
import org.ros.node.ConnectedNode;
import org.ros.node.topic.Publisher;
import org.ros.node.topic.Subscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Client implementation for actionlib.
 * This class encapsulates the communication with an actionlib server.
 * Can accept more than one Action Listeners
 * *
 *
 * @author Ernesto Corbellini ecorbellini@ekumenlabs.com
 * @author Spyros Koukas
 */
public final class ActionClient<T_ACTION_GOAL extends Message,
        T_ACTION_FEEDBACK extends Message,
        T_ACTION_RESULT extends Message> {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final long PUBLISHER_SHUTDOWN_TIMEOUT_MILLIS = 5000;
    private static final boolean LATCH_MODE = false;
    private final GoalStatusToString goalStatusToString = new GoalStatusToString();

    private final ClientGoalManager<T_ACTION_GOAL> goalManager = new ClientGoalManager<>(new ActionGoal<>());

    private final String actionGoalType;
    private final String actionResultType;
    private final String actionFeedbackType;
    private Publisher<T_ACTION_GOAL> goalPublisher = null;
    private Publisher<GoalID> cancelPublisher = null;
    private Subscriber<T_ACTION_RESULT> serverResultSubscriber = null;
    private Subscriber<T_ACTION_FEEDBACK> serverFeedbackSubscriber = null;
    private Subscriber<GoalStatusArray> serverStatusSubscriber = null;
    private String actionName;


    private final List<ActionClientResultListener<T_ACTION_RESULT>> callbackResultTargets = new CopyOnWriteArrayList<>();
    private final List<ActionClientFeedbackListener<T_ACTION_FEEDBACK>> callbackFeedbackTargets = new CopyOnWriteArrayList<>();
    private final List<ActionClientStatusListener> callbackStatusTargets = new CopyOnWriteArrayList<>();

    /**
     * This is set to `true` when all topics are connected for the first time and should never be reset.
     * It can also be used to determine if the client is running and (seems to be) connected to a server.
     */
    private final AtomicBoolean hasOnConnectionBeenCalled = new AtomicBoolean(false);
    private final Runnable onConnection;

    private final GoalIDGenerator goalIdGenerator;
    private volatile boolean statusSubscriberFlagReception = false;

    private final TopicSubscriberListener<GoalStatusArray> statusArrayTopicSubscriberListener;
    private final TopicSubscriberListener<T_ACTION_RESULT> resultArrayTopicSubscriberListener;
    private final TopicSubscriberListener<T_ACTION_FEEDBACK> feedbackArrayTopicSubscriberListener;

    private final TopicPublisherListener<T_ACTION_GOAL> goalTopicPublisherListener;
    private final TopicPublisherListener<GoalID> cancelTopicPublisherListener;
    private final CopyOnWriteArraySet<String> topicsToBeConnectedSet;

    private static final CopyOnWriteArraySet<String> createTopicConnectionSet(final String actionName) {
        final CopyOnWriteArraySet<String> result = new CopyOnWriteArraySet<>();
        result.add(ActionClient.getCancelTopicName(actionName));
        result.add(ActionClient.getResultTopicName(actionName));
        result.add(ActionClient.getStatusTopicName(actionName));
        result.add(ActionClient.getFeedbackTopicName(actionName));
        result.add(ActionClient.getGoalTopicName(actionName));
        assert (result.size() == 5);
        return result;
    }

    private final ConnectedNode connectedNode;

    /**
     * Constructor for an ActionClient object.
     *
     * @param connectedNode      The node object that is connected to the ROS master.
     * @param actionName         A string representing the name of this action. This name
     *                           is used to publish the actionlib topics and should be agreed between server
     *                           and the client.
     * @param actionGoalType     A string with the type information for the action
     *                           goal message.
     * @param actionFeedbackType A string with the type information for the
     *                           feedback message.
     * @param actionResultType   A string with the type information for the result
     *                           message.
     */
    public ActionClient(final ConnectedNode connectedNode
            , final String actionName
            , final String actionGoalType
            , final String actionFeedbackType
            , final String actionResultType) {
        this(connectedNode, actionName, actionGoalType, actionFeedbackType, actionResultType, Runnables::doNothing);
    }

    /**
     * Constructor for an ActionClient object.
     *
     * @param connectedNode      The node object that is connected to the ROS master.
     * @param actionName         A string representing the name of this action. This name
     *                           is used to publish the actionlib topics and should be agreed between server
     *                           and the client.
     * @param actionGoalType     A string with the type information for the action
     *                           goal message.
     * @param actionFeedbackType A string with the type information for the
     *                           feedback message.
     * @param actionResultType   A string with the type information for the result
     *                           message.
     * @param onConnection       A {@link Runnable} that will be called only once, when and if this client has detected a complete connection to the RosActionLib protocol
     */
    public ActionClient(
            final ConnectedNode connectedNode
            , final String actionName
            , final String actionGoalType
            , final String actionFeedbackType
            , final String actionResultType
            , final Runnable onConnection) {

        Preconditions.checkArgument(StringUtils.isNotBlank(actionName));
        Preconditions.checkArgument(StringUtils.isNotBlank(actionGoalType));
        Preconditions.checkArgument(StringUtils.isNotBlank(actionFeedbackType));
        Preconditions.checkArgument(StringUtils.isNotBlank(actionResultType));
        Preconditions.checkNotNull(onConnection);
        this.actionName = actionName;
        this.actionGoalType = actionGoalType;
        this.actionFeedbackType = actionFeedbackType;
        this.actionResultType = actionResultType;
        this.goalIdGenerator = new GoalIDGenerator(connectedNode);
        this.connectedNode = connectedNode;
        this.topicsToBeConnectedSet = ActionClient.createTopicConnectionSet(actionName);
        this.onConnection = onConnection;
        this.statusArrayTopicSubscriberListener = new TopicSubscriberListener<>(connectedNode, ActionClient.getStatusTopicName(actionName), this::processOnConnection);
        this.resultArrayTopicSubscriberListener = new TopicSubscriberListener<>(connectedNode, ActionClient.getResultTopicName(actionName), this::processOnConnection);
        this.feedbackArrayTopicSubscriberListener = new TopicSubscriberListener<>(connectedNode, ActionClient.getFeedbackTopicName(actionName), this::processOnConnection);
        this.goalTopicPublisherListener = new TopicPublisherListener<>(connectedNode, ActionClient.getGoalTopicName(actionName), this::processOnConnection);
        this.cancelTopicPublisherListener = new TopicPublisherListener<>(connectedNode, ActionClient.getCancelTopicName(actionName), this::processOnConnection);
        this.connect(connectedNode);
    }

    /**
     * Internal method that
     *
     * @param topicName
     */
    private final void processOnConnection(final String topicName) {
        //If the connection Runnable has been called ignore any calls to this function.
        if (!this.hasOnConnectionBeenCalled.get()) {
            Preconditions.checkArgument(StringUtils.isNotBlank(topicName));

            final boolean existed = this.topicsToBeConnectedSet.remove(topicName);
            if (existed && this.topicsToBeConnectedSet.isEmpty()) {
                //On the last topic connection call the method, if it has not been called
                if (this.hasOnConnectionBeenCalled.compareAndSet(false, true)) {
                    this.onConnection.run();
                }
            }

        } else {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Internal method for:{} Called multiple times. Node:{}", topicName, this.connectedNode == null ? null : this.connectedNode.getName());
            }
        }
    }


    /**
     * @param actionClientListener the listener to add
     */
    public final void addActionClientListener(final ActionClientListener<T_ACTION_FEEDBACK, T_ACTION_RESULT> actionClientListener) {
        if (actionClientListener != null) {
            this.callbackStatusTargets.add(actionClientListener);
            this.callbackFeedbackTargets.add(actionClientListener);
            this.callbackResultTargets.add(actionClientListener);
        }
    }

    /**
     * @param targets the listeners to add
     */
    public final void addActionClientListeners(final Collection<ActionClientListener<T_ACTION_FEEDBACK, T_ACTION_RESULT>> targets) {
        if (targets != null) {
            for (final ActionClientListener<T_ACTION_FEEDBACK, T_ACTION_RESULT> target : targets) {
                this.addActionClientListener(target);
            }
        }
    }

    /**
     * @param target the status listener
     */
    public final void addActionClientStatusListener(final ActionClientStatusListener target) {
        if (target != null) {
            this.callbackStatusTargets.add(target);
        }
    }

    /**
     * @param target the feedback listener
     */
    public final void addActionClientFeedbackListener(final ActionClientFeedbackListener<T_ACTION_FEEDBACK> target) {
        if (target != null) {
            this.callbackFeedbackTargets.add(target);
        }
    }

    /**
     * @param target the result listener
     */
    public final void addActionClientResultListener(final ActionClientResultListener<T_ACTION_RESULT> target) {
        if (target != null) {
            this.callbackResultTargets.add(target);
        }
    }


    /**
     * @param target the client listener for status, result and feedback
     */
    public final void removeActionClientListenerListener(final ActionClientListener<T_ACTION_FEEDBACK, T_ACTION_RESULT> target) {
        if (target != null) {
            this.callbackStatusTargets.remove(target);
            this.callbackFeedbackTargets.remove(target);
            this.callbackResultTargets.remove(target);
        }
    }

    /**
     * @param actionFeedbackListener
     */
    public final void removeActionClientFeedbackListener(final ActionClientFeedbackListener<T_ACTION_FEEDBACK> actionFeedbackListener) {
        if (actionFeedbackListener != null) {
            this.callbackFeedbackTargets.remove(actionFeedbackListener);
        }
    }

    /**
     * @param actionResultListener
     */
    public final void removeActionClientResultListener(final ActionClientResultListener<T_ACTION_RESULT> actionResultListener) {
        if (actionResultListener != null) {
            this.callbackResultTargets.remove(actionResultListener);
        }
    }

    /**
     * @param actionClientStatusListener
     */
    public final void removeActionClientStatusListener(final ActionClientStatusListener actionClientStatusListener) {
        if (actionClientStatusListener != null) {
            this.callbackStatusTargets.remove(actionClientStatusListener);
        }
    }

    /**
     * Publish an action goal to the server. The type of the action goal message
     * is dependent on the application.
     *
     * @param actionGoalMessage The action goal message.
     * @param id                A string containing the ID for the goal. The ID should represent
     *                          this goal in a unique fashion in the server and the client.
     *                          If the Id is {@link StringUtils#isBlank(CharSequence)} e.g. null or empty it will be autogenerated.
     * @return
     */
    public final ActionFuture<T_ACTION_GOAL, T_ACTION_FEEDBACK, T_ACTION_RESULT> sendGoal(final T_ACTION_GOAL actionGoalMessage, final String id) {
        final GoalID gid = this.getGoalId(actionGoalMessage);
        if (StringUtils.isBlank(id)) {
            this.goalIdGenerator.generateID(gid);
        } else {
            gid.setId(id);
        }
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Prepared goal for action:[{}] with goalId:[{}]", this.actionName, gid.getId());
        }

        final ActionFuture<T_ACTION_GOAL, T_ACTION_FEEDBACK, T_ACTION_RESULT> actionClientFuture = ActionClientFuture.createFromGoal(this, actionGoalMessage);
        this.sendGoalWire(actionGoalMessage);
        return actionClientFuture;

    }

    /**
     * @param actionGoalMessage
     */
    final void sendGoalWire(final T_ACTION_GOAL actionGoalMessage) {
        this.goalManager.setGoal(actionGoalMessage);
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Publishing goal on action:[{}] with goalId:[{}]", this.actionName, this.goalManager.getActionGoal().getGoalId());
        }
        this.goalPublisher.publish(actionGoalMessage);
    }

    /**
     * Publish an action goal to the server. The type of the action goal message
     * is dependent on the application. A goal ID will be automatically generated.
     *
     * @param actionGoalMessage The action goal message.
     */
    public final ActionFuture<T_ACTION_GOAL, T_ACTION_FEEDBACK, T_ACTION_RESULT> sendGoal(final T_ACTION_GOAL actionGoalMessage) {
        return sendGoal(actionGoalMessage, null);
    }


    /**
     * Convenience method for retrieving the actionGoalMessage ID of a given action actionGoalMessage message.
     *
     * @param actionGoalMessage The action actionGoalMessage message from where to obtain the actionGoalMessage ID.
     * @return Goal ID object containing the ID of the action message.
     * @see actionlib_msgs.GoalID
     */
    public final GoalID getGoalId(final T_ACTION_GOAL actionGoalMessage) {

        final GoalID gid = ActionLibMessagesUtils.getSubMessageFromMessage(actionGoalMessage, "getGoalId");
        return gid;
    }

    /**
     * Convenience method for setting the actionGoalMessage ID of an action actionGoalMessage message.
     *
     * @param actionGoalMessage The action actionGoalMessage message to set the actionGoalMessage ID for.
     * @param gid               The actionGoalMessage ID object.
     * @see actionlib_msgs.GoalID
     */
    public final void setGoalId(final T_ACTION_GOAL actionGoalMessage, final GoalID gid) {
        ActionLibMessagesUtils.setSubMessageFromMessage(actionGoalMessage, gid, "getGoalId");
    }

    /**
     * Publish a cancel message. This instructs the action server to cancel the
     * specified goal.
     *
     * @param goalIDd The GoalID message identifying the goal to cancel.
     * @see actionlib_msgs.GoalID
     */
    public final void sendCancel(final GoalID goalIDd) {
        this.sendCancelInternal(goalIDd);
    }

    /**
     * Publish a cancel request for a single goal ID.
     *
     * @param goalId the ID of the goal to cancel
     * @return {@code true} if the cancel request was published, {@code false} otherwise
     */
    public final boolean cancelGoal(final String goalId) {
        Preconditions.checkArgument(StringUtils.isNotBlank(goalId));
        return this.sendCancelInternal(this.newCancelGoalId(goalId, new Time()));
    }

    /**
     * Publish a cancel-all request. This instructs the action server to cancel
     * all currently tracked goals.
     *
     * @return {@code true} if the cancel request was published, {@code false} otherwise
     */
    public final boolean cancelAll() {
        return this.sendCancelInternal(this.newCancelGoalId("", new Time()));
    }

    /**
     * Publish a timestamp-based cancel request. This instructs the action server
     * to cancel tracked goals whose timestamps are less than or equal to the supplied stamp.
     *
     * @param stamp the cutoff time for cancelling tracked goals
     * @return {@code true} if the cancel request was published, {@code false} otherwise
     */
    public final boolean cancelBefore(final Time stamp) {
        Objects.requireNonNull(stamp);
        return this.sendCancelInternal(this.newCancelGoalId("", stamp));
    }

    final boolean sendCancelInternal(final GoalID goalIDd) {
        Objects.requireNonNull(goalIDd);
        if (this.cancelPublisher == null) {
            return false;
        }
        final String currentGoalId = this.goalManager.getActionGoal() == null ? null : this.goalManager.getActionGoal().getGoalId();
        final String cancelledGoalId = goalIDd.getId();
        this.cancelPublisher.publish(goalIDd);
        if (StringUtils.isNotBlank(cancelledGoalId) && cancelledGoalId.equals(currentGoalId)) {
            this.goalManager.cancelGoal();
        }
        return true;
    }

    private final GoalID newCancelGoalId(final String id, final Time stamp) {
        final GoalID goalId = this.connectedNode.getDefaultMessageFactory().newFromType(GoalID._TYPE);
        goalId.setId(id);
        goalId.setStamp(new Time(stamp));
        return goalId;
    }

    /**
     * Start publishing on the client topics: /goal and /cancel.
     *
     * @param connectedNode The node object that is connected to the ROS master.
     */
    private final void publishClient(final ConnectedNode connectedNode) {
        Objects.requireNonNull(connectedNode);
        this.goalPublisher = connectedNode.newPublisher(this.getGoalTopicName(), actionGoalType);
        this.goalPublisher.setLatchMode(LATCH_MODE);
        this.goalPublisher.addListener(this.goalTopicPublisherListener);
        this.cancelPublisher = connectedNode.newPublisher(this.getCancelTopicName(), GoalID._TYPE);
        this.cancelPublisher.addListener(this.cancelTopicPublisherListener);
    }

    /**
     * Stop publishing our client topics.
     */
    private final void unpublishClients() {
        if (this.goalPublisher != null) {
            this.goalPublisher.shutdown(PUBLISHER_SHUTDOWN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            this.goalPublisher = null;
        }
        if (this.cancelPublisher != null) {
            this.cancelPublisher.shutdown(PUBLISHER_SHUTDOWN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            this.cancelPublisher = null;
        }
    }

    public final T_ACTION_GOAL newGoalMessage() {
        return goalPublisher.newMessage();
    }

    /**
     * Subscribe to the server topics.
     *
     * @param node The node object that is connected to the ROS master.
     */
    private final void subscribeToServer(final ConnectedNode node) {
        this.serverResultSubscriber = node.newSubscriber(this.getResultTopicName(), actionResultType);
        this.serverFeedbackSubscriber = node.newSubscriber(this.getFeedbackTopicName(), actionFeedbackType);
        this.serverStatusSubscriber = node.newSubscriber(this.getStatusTopicName(), GoalStatusArray._TYPE);

        Preconditions.checkState(this.resultArrayTopicSubscriberListener != null);
        Preconditions.checkState(this.feedbackArrayTopicSubscriberListener != null);
        Preconditions.checkState(this.statusArrayTopicSubscriberListener != null);

        this.serverResultSubscriber.addSubscriberListener(this.resultArrayTopicSubscriberListener);
        this.serverFeedbackSubscriber.addSubscriberListener(this.feedbackArrayTopicSubscriberListener);
        this.serverStatusSubscriber.addSubscriberListener(this.statusArrayTopicSubscriberListener);

        this.serverResultSubscriber.addMessageListener(this::gotResult);
        this.serverFeedbackSubscriber.addMessageListener(this::gotFeedback);
        this.serverStatusSubscriber.addMessageListener(this::gotStatus);


    }

    final String getResultTopicName() {
        return ActionClient.getResultTopicName(actionName);
    }

    final String getFeedbackTopicName() {
        return ActionClient.getFeedbackTopicName(actionName);
    }

    final String getStatusTopicName() {
        return ActionClient.getStatusTopicName(actionName);
    }

    final String getGoalTopicName() {
        return ActionClient.getGoalTopicName(actionName);
    }

    final String getCancelTopicName() {
        return ActionClient.getCancelTopicName(actionName);
    }


    static final String getResultTopicName(final String actionName) {
        return actionName + "/result";
    }

    private static final String getFeedbackTopicName(final String actionName) {
        return actionName + "/feedback";
    }

    private static final String getStatusTopicName(final String actionName) {
        return actionName + "/status";
    }

    private static final String getGoalTopicName(final String actionName) {
        return actionName + "/goal";
    }

    private static final String getCancelTopicName(final String actionName) {
        return actionName + "/cancel";
    }

    /**
     * Unsubscribe from the server topics.
     */
    private final void unsubscribeToServer() {
        if (this.serverFeedbackSubscriber != null) {
            this.serverFeedbackSubscriber.removeAllMessageListeners();
            this.serverFeedbackSubscriber.shutdown(5, TimeUnit.SECONDS);
            this.serverFeedbackSubscriber = null;
        }
        if (this.serverResultSubscriber != null) {
            this.serverResultSubscriber.removeAllMessageListeners();
            this.serverResultSubscriber.shutdown(5, TimeUnit.SECONDS);
            this.serverResultSubscriber = null;
        }
        if (this.serverStatusSubscriber != null) {
            this.serverStatusSubscriber.removeAllMessageListeners();
            this.serverStatusSubscriber.shutdown(5, TimeUnit.SECONDS);
            this.serverStatusSubscriber = null;
        }
    }

    /**
     * Called whenever we get a resultMessage in the result topic.
     * Only addresses
     *
     * @param resultMessage The result resultMessage received. The type of this resultMessage
     *                      depends on the application.
     */
    private final void gotResult(final T_ACTION_RESULT resultMessage) {
        final ActionResult<T_ACTION_RESULT> actionResultMessage = new ActionResult<>(resultMessage);
        final GoalStatus resultGoalStatus = actionResultMessage.getGoalStatusMessage();
        final GoalID goalId = resultGoalStatus == null ? null : resultGoalStatus.getGoalId();
        final String currentGoalId = this.goalManager.getActionGoal() == null ? null : this.goalManager.getActionGoal().getGoalId();
        if (LOGGER.isTraceEnabled()) {
            final String goalId_id=goalId == null ? null : goalId.getId();
            LOGGER.trace("Received result for action:[{}] with result goalId:[{}] while current goalId:[{}]", this.actionName,goalId_id , currentGoalId);
        }
        if (goalId != null && StringUtils.isNotBlank(currentGoalId) && currentGoalId.equals(goalId.getId())) {
            this.goalManager.updateStatus(resultGoalStatus.getStatus());

            this.goalManager.resultReceived();
            // Propagate the callback
            for (final ActionClientResultListener<T_ACTION_RESULT> actionClientListener : this.callbackResultTargets) {
                if (actionClientListener != null) {
                    actionClientListener.resultReceived(resultMessage);
                }
            }
            try {
                this.sendCancel(goalId);
            } catch (final Exception exception) {
                LOGGER.error("Error while cancelling goal of received result{}", ExceptionUtils.getStackTrace(exception));
            }
        } else {
            if (LOGGER.isTraceEnabled()) {
                final String goalId_id=goalId == null ? null : goalId.getId();
                LOGGER.trace("Received and ignored GoalId:{} because current client goal id=={}",goalId_id, currentGoalId);
            }
        }
    }

    /**
     * Called whenever we get a message in the feedback topic.
     *
     * @param message The feedback message received. The type of this message
     *                depends on the application.
     */
    private final void gotFeedback(final T_ACTION_FEEDBACK message) {
        final ActionFeedback<T_ACTION_FEEDBACK> actionFeedback = new ActionFeedback<>(message);
        if (actionFeedback.getGoalStatusMessage().getGoalId().getId().equals(this.goalManager.getActionGoal().getGoalId())) {
            this.goalManager.updateStatus(actionFeedback.getGoalStatusMessage().getStatus());

            // Propagate the callback
            for (final ActionClientFeedbackListener<T_ACTION_FEEDBACK> actionClientListener : this.callbackFeedbackTargets) {
                if (actionClientListener != null) {
                    actionClientListener.feedbackReceived(message);
                }
            }
        }
    }

    /**
     * Called whenever we get a message in the status topic.
     *
     * @param message The GoalStatusArray message received.
     * @see actionlib_msgs.GoalStatusArray
     */
    private final void gotStatus(final GoalStatusArray message) {
        this.statusSubscriberFlagReception = true;

        // Find the status for our current goal
        final GoalStatus goalStatus = this.findStatus(message);
        if (goalStatus != null) {
            // update the goal status tracking
            this.goalManager.updateStatus(goalStatus.getStatus());
            // Propagate the callback

            for (final ActionClientStatusListener actionClientListener : this.callbackStatusTargets) {
                if (actionClientListener != null) {
                    actionClientListener.statusReceived(message);
                }
            }
        } else {
            if (LOGGER.isDebugEnabled()) {
                if (message.getStatusList() != null && !message.getStatusList().isEmpty()) {
                    LOGGER.debug("Status update is not for current goal! Action:[{}]", this.actionName);
                }
            }
        }

    }

    /**
     * Walk through the status array and find the status for the action goal for this client
     *
     * @param statusMessage The message with the goal status array
     *                      (actionlib_msgs.GoalStatusArray)
     * @return The goal status message for the goal we want or null if we didn't
     * find it.
     */
    public final GoalStatus findStatus(final GoalStatusArray statusMessage) {
        GoalStatus goalStatus = null;
        if (statusMessage != null) {
            final List<GoalStatus> statusList = statusMessage.getStatusList();

            if (this.goalManager.getActionGoal() != null && statusList != null && !statusList.isEmpty()) {
                final String idToFind = this.goalManager.getActionGoal().getGoalId();

                if (idToFind != null) {
                    try {
                        for (final GoalStatus status : statusList) {
                            if (status != null
                                    && status.getGoalId() != null
                                    && idToFind.equals(status.getGoalId().getId())
                                    && (goalStatus == null || goalStatus.getGoalId().getStamp().compareTo(status.getGoalId().getStamp()) < 0)) {
                                goalStatus = status;
                            }
                        }
                        if (goalStatus != null && LOGGER.isTraceEnabled()) {
                            LOGGER.trace("Latest status: [{}({}),{}] for goal with ID: {} action:[{}]", goalStatus.getStatus(), this.goalStatusToString.getStatus(goalStatus.getStatus()), goalStatus.getText(), idToFind, actionName);
                        }
                    } catch (final Exception e) {
                        if (LOGGER.isErrorEnabled()) {
                            LOGGER.error(ExceptionUtils.getStackTrace(e));
                        }
                    }

                }
            }
        }
        return goalStatus;
    }

    /**
     * Publishes the client's topics and subscribes to the server's topics.
     *
     * @param node The node object that is connected to the ROS master.
     */
    private final void connect(final ConnectedNode node) {
        this.subscribeToServer(node);

        this.publishClient(node);
    }

    /**
     * Waits for a given timeout period until the subscribers of the client are registered with the master.
     *
     * @param timeout
     * @param timeUnit
     * @return true if client subscribers to the result, feedback and status topics have been registered before the timeout elapses, else false
     */
    public final boolean waitForRegistration(final long timeout, final TimeUnit timeUnit) throws InterruptedException {
        final Stopwatch stopwatch = Stopwatch.createStarted();
        boolean result = this.resultArrayTopicSubscriberListener.waitForRegistration(Math.max(timeout - stopwatch.elapsed(timeUnit), 0), timeUnit);
        result = result && this.feedbackArrayTopicSubscriberListener.waitForRegistration(Math.max(timeout - stopwatch.elapsed(timeUnit), 0), timeUnit);
        result = result && this.statusArrayTopicSubscriberListener.waitForRegistration(Math.max(timeout - stopwatch.elapsed(timeUnit), 0), timeUnit);
        return result;
    }

    /**
     * Waits for a given timeout period until the  publishers are detected in the server topics.
     * Normally these should be the publishers of the server.
     * This is a heuristic method
     *
     * @param timeout
     * @param timeUnit
     * @return true if publishers to the result, feedback and status topics have been detected before the timeout elapses, else false
     */
    public final boolean waitForServerPublishers(final long timeout, final TimeUnit timeUnit) throws InterruptedException {
        final Stopwatch stopwatch = Stopwatch.createStarted();
        boolean result = this.resultArrayTopicSubscriberListener.waitForPublisher(Math.max(timeout - stopwatch.elapsed(timeUnit), 0), timeUnit);
        result = result && this.feedbackArrayTopicSubscriberListener.waitForPublisher(Math.max(timeout - stopwatch.elapsed(timeUnit), 0), timeUnit);
        result = result && this.statusArrayTopicSubscriberListener.waitForPublisher(Math.max(timeout - stopwatch.elapsed(timeUnit), 0), timeUnit);
        return result;
    }

    /**
     * Waits for a given timeout period until the  publishers are detected in the server topics.
     * Normally these should be the publishers of the server.
     * This is a heuristic method
     *
     * @param timeout
     * @param timeUnit
     * @return true if publishers to the result, feedback and status topics have been detected before the timeout elapses, else false
     */
    public final boolean waitForClientSubscribers(final long timeout, final TimeUnit timeUnit) throws InterruptedException {
        final Stopwatch stopwatch = Stopwatch.createStarted();
        boolean debugShown = false;
        boolean result = this.goalTopicPublisherListener.waitForSubscriber(Math.max(timeout - stopwatch.elapsed(timeUnit), 0), timeUnit);
        if (!result && !debugShown && LOGGER.isDebugEnabled()) {
            debugShown = true;
            LOGGER.debug("waitForSubscriber goal did not connect after:{} {} while timeout={} {}", stopwatch.elapsed(timeUnit), timeUnit.name(), timeout, timeUnit.name());
        }
        result = result && this.cancelTopicPublisherListener.waitForSubscriber(Math.max(timeout - stopwatch.elapsed(timeUnit), 0), timeUnit);
        if (!result && !debugShown && LOGGER.isDebugEnabled()) {
            debugShown = true;
            LOGGER.debug("waitForSubscriber cancel did not connect after:{} {} while timeout={} {}", stopwatch.elapsed(timeUnit), timeUnit.name(), timeout, timeUnit.name());
        }
        return result;
    }

    /**
     * Waits for a given timeout period until publishers and subscribers are detected in the server topics.
     * Normally these should be the publishers and clients of the server
     * This is a heuristic method
     *
     * @param timeout
     * @param timeUnit
     * @return true if all publishers and clients required for the client/server connection are connected, else false
     */
    public final boolean waitForServerConnection(final long timeout, final TimeUnit timeUnit) throws InterruptedException {
        final Stopwatch stopwatch = Stopwatch.createStarted();
        final boolean registered = this.waitForRegistration(Math.max(timeout - stopwatch.elapsed(timeUnit), 0), timeUnit);
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Action:{} client registered:[{}]", this.actionName, registered);
        }
        if (registered) {
            final boolean publishersConnected = this.waitForServerPublishers(Math.max(timeout - stopwatch.elapsed(timeUnit), 0), timeUnit);
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Action:{} publishers connected:[{}]", this.actionName, publishersConnected);
            }
            if (publishersConnected) {
                final boolean subscribersConnected = this.waitForClientSubscribers(Math.max(timeout - stopwatch.elapsed(timeUnit), 0), timeUnit);
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Action:{} subscribers connected:[{}]", this.actionName, subscribersConnected);
                }
                //if registered and publishers are  connected in time, the results depends on subscribersConnection
                return subscribersConnected;
            } else {
                //if registered but publishers not connected in time
                return false;
            }

        } else {
            //if not registered in time
            return false;
        }

    }


    /**
     * Checks if the server is started at the time of the method call.
     * This method involves network interaction
     *
     * @return
     */
    public final boolean isActionServerStarted() {
        final Supplier<MasterStateClient> masterStateClientSupplier = Suppliers.memoize(() -> new MasterStateClient(this.connectedNode, this.connectedNode.getMasterUri()));
        final boolean result = this.statusSubscriberFlagReception
                && this.goalPublisher.hasSubscribers()
                && this.cancelPublisher.hasSubscribers()
                && this.isTopicPublished(this.getFeedbackTopicName(), masterStateClientSupplier.get())
//                && this.isTopicPublished(this.serverFeedbackSubscriber.getTopicName().toString());
                && this.isTopicPublished(this.getResultTopicName(), masterStateClientSupplier.get());

        return result;
    }

    /**
     * @param timeout The maximum amount of time to wait for an action server. If
     *                this value is less than or equal to zero, it will wait forever until a
     *                server is detected.
     * @return True if the action server was detected before the timeout and
     * false otherwise.
     * @deprecated use {@link ActionClient#waitForServerConnection}
     * Wait for an actionlib server to connect.
     */
    @Deprecated
    public final boolean waitForActionServerToStart(final long timeout, final TimeUnit timeUnit) {
        if (this.hasOnConnectionBeenCalled.get()) {
            return true;
        }
        try {
            if (timeout <= 0L) {
                while (true) {
                    // Preserve the legacy "wait forever" contract without a polling sleep loop.
                    if (this.waitForServerConnection(1, TimeUnit.DAYS)) {
                        return true;
                    }
                }
            }
            return this.waitForServerConnection(timeout, timeUnit);
        } catch (final InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Interrupted while waiting for action server:[{}] connection", this.actionName, interruptedException);
            }
            return false;
        }
    }

    /**
     * @param topicName
     * @return
     */
    private final boolean isTopicPublished(final String topicName, final MasterStateClient masterStateClient) {
        for (final TopicSystemState topicSystemState : masterStateClient.getSystemState().getTopics()) {
            if (topicSystemState != null
                    && topicName.equals(topicSystemState.getTopicName())
                    && topicSystemState.getPublishers() != null
                    && !topicSystemState.getPublishers().isEmpty()) {
                return true;

            }

        }
        return false;
    }


    /**
     * @return
     */
    final ClientState getGoalState() {
        return this.goalManager.getGoalState();
    }


    /**
     * @return
     */
    public final boolean isActive() {
        return this.goalManager.getStateMachine().isRunning();
    }

    /**
     * Disconnect the action client. Unregister publishers and listeners.
     */
    public final void disconnect() {
        this.callbackResultTargets.clear();
        this.callbackFeedbackTargets.clear();
        this.callbackStatusTargets.clear();
        this.unpublishClients();
        this.unsubscribeToServer();
    }


    @Override
    public final String toString() {
        return new StringJoiner(", ", ActionClient.class.getSimpleName() + "[", "]")
                .add("goalManager=" + goalManager)
                .add("actionGoalType='" + actionGoalType + "'")
                .add("actionResultType='" + actionResultType + "'")
                .add("actionFeedbackType='" + actionFeedbackType + "'")
                .add("goalPublisher=" + goalPublisher)
                .add("cancelPublisher=" + cancelPublisher)
                .add("serverResultSubscriber=" + serverResultSubscriber)
                .add("serverFeedbackSubscriber=" + serverFeedbackSubscriber)
                .add("serverStatusSubscriber=" + serverStatusSubscriber)
                .add("actionName='" + actionName + "'")
                .add("callbackResultTargets=" + callbackResultTargets)
                .add("callbackFeedbackTargets=" + callbackFeedbackTargets)
                .add("callbackStatusTargets=" + callbackStatusTargets)
                .add("goalIdGenerator=" + goalIdGenerator)
                .add("statusSubscriberFlag=" + statusSubscriberFlagReception)
                .add("connectedNode=" + connectedNode)
                .toString();
    }
}
