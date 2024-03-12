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
import com.google.common.base.Stopwatch;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.ros.internal.message.Message;
import org.ros.master.client.MasterStateClient;
import org.ros.master.client.TopicSystemState;
import org.ros.message.Duration;
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
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Client implementation for actionlib.
 * This class encapsulates the communication with an actionlib server.
 * Can accept more than one Action Listeners
 *
 * @author Ernesto Corbellini ecorbellini@ekumenlabs.com
 * @author Spyros Koukas
 */
public final class ActionClient<T_ACTION_GOAL extends Message,
        T_ACTION_FEEDBACK extends Message,
        T_ACTION_RESULT extends Message> {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final ClientGoalManager<T_ACTION_GOAL> goalManager;
    private final String actionGoalType;
    private final String actionResultType;
    private final String actionFeedbackType;
    private Publisher<T_ACTION_GOAL> goalPublisher = null;
    private Publisher<GoalID> cancelPublisher = null;
    private Subscriber<T_ACTION_RESULT> serverResultSubscriber = null;
    private Subscriber<T_ACTION_FEEDBACK> serverFeedbackSubscriber = null;
    private Subscriber<GoalStatusArray> serverStatusSubscriber = null;
    private String actionName;
    private static final boolean LATCH_MODE = false;
    //    private final List<ActionClientListener<T_ACTION_FEEDBACK,T_ACTION_RESULT>> callbackTargets = new CopyOnWriteArrayList<>();
    private final List<ActionClientResultListener<T_ACTION_RESULT>> callbackResultTargets = new CopyOnWriteArrayList<>();
    private final List<ActionClientFeedbackListener<T_ACTION_FEEDBACK>> callbackFeedbackTargets = new CopyOnWriteArrayList<>();
    private final List<ActionClientStatusListener> callbackStatusTargets = new CopyOnWriteArrayList<>();

    private final GoalIDGenerator goalIdGenerator;
    private volatile boolean statusSubscriberFlag = false;
    private final MasterStateClient masterStateClient;

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
        this.masterStateClient = new MasterStateClient(connectedNode, connectedNode.getMasterUri());

        this.actionName = actionName;
        this.actionGoalType = actionGoalType;
        this.actionFeedbackType = actionFeedbackType;
        this.actionResultType = actionResultType;
        this.goalIdGenerator = new GoalIDGenerator(connectedNode);
        this.connect(connectedNode);
        this.goalManager = new ClientGoalManager(new ActionGoal<T_ACTION_GOAL>());
    }

    /**
     * @param target the listener to add
     */
    public final void addListener(final ActionClientListener<T_ACTION_FEEDBACK, T_ACTION_RESULT> target) {
        if (target != null) {
            this.callbackStatusTargets.add(target);
            this.callbackFeedbackTargets.add(target);
            this.callbackResultTargets.add(target);
        }
    }

    /**
     * @param targets the listeners to add
     */
    public final void addListeners(final Collection<ActionClientListener<T_ACTION_FEEDBACK, T_ACTION_RESULT>> targets) {
        if (targets != null) {
            for (final ActionClientListener target : targets) {
                this.addListener(target);
            }
        }
    }

    /**
     * @param target the status listener
     */
    public final void addListener(final ActionClientStatusListener target) {
        if (target != null) {
            this.callbackStatusTargets.add(target);
        }
    }

    /**
     * @param target the feedback listener
     */
    public final void addListener(final ActionClientFeedbackListener target) {
        if (target != null) {
            this.callbackFeedbackTargets.add(target);
        }
    }

    /**
     * @param target the result listener
     */
    public final void addListener(final ActionClientResultListener target) {
        if (target != null) {
            this.callbackResultTargets.add(target);
        }
    }


    /**
     * @param target the client listener for status, result and feedback
     */
    public final void removeListener(final ActionClientListener target) {

        callbackStatusTargets.remove(target);
        callbackFeedbackTargets.remove(target);
        callbackResultTargets.remove(target);
    }

    /**
     * Publish an action goal to the server. The type of the action goal message
     * is dependent on the application.
     *
     * @param agMessage The action goal message.
     * @param id        A string containing the ID for the goal. The ID should represent
     *                  this goal in a unique fashion in the server and the client.
     *                  If the Id is {@link StringUtils#isBlank(CharSequence)} e.g. null or empty it will be autogenerated.
     * @return
     */
    public final ActionFuture<T_ACTION_GOAL, T_ACTION_FEEDBACK, T_ACTION_RESULT> sendGoal(final T_ACTION_GOAL agMessage, final String id) {
        final GoalID gid = getGoalId(agMessage);
        if (StringUtils.isBlank(id)) {
            this.goalIdGenerator.generateID(gid);
        } else {
            gid.setId(id);
        }

        return ActionClientFuture.createFromGoal(this, agMessage);
    }

    /**
     * @param agMessage
     */
    final void sendGoalWire(final T_ACTION_GOAL agMessage) {
        this.goalManager.setGoal(agMessage);
        this.goalPublisher.publish(agMessage);
    }

    /**
     * Publish an action goal to the server. The type of the action goal message
     * is dependent on the application. A goal ID will be automatically generated.
     *
     * @param agMessage The action goal message.
     */
    public final ActionFuture<T_ACTION_GOAL, T_ACTION_FEEDBACK, T_ACTION_RESULT> sendGoal(final T_ACTION_GOAL agMessage) {
        return sendGoal(agMessage, null);
    }

    /**
     * Convenience method for retrieving the goal ID of a given action goal message.
     *
     * @param goal The action goal message from where to obtain the goal ID.
     * @return Goal ID object containing the ID of the action message.
     * @see actionlib_msgs.GoalID
     */
    public final GoalID getGoalId(final T_ACTION_GOAL goal) {

        final GoalID gid = ActionLibMessagesUtils.getSubMessageFromMessage(goal, "getGoalId");
        return gid;
    }

    /**
     * Convenience method for setting the goal ID of an action goal message.
     *
     * @param goal The action goal message to set the goal ID for.
     * @param gid  The goal ID object.
     * @see actionlib_msgs.GoalID
     */
    public final void setGoalId(final T_ACTION_GOAL goal, final GoalID gid) {
        ActionLibMessagesUtils.setSubMessageFromMessage(goal, gid, "getGoalId");
    }

    /**
     * Publish a cancel message. This instructs the action server to cancel the
     * specified goal.
     *
     * @param id The GoalID message identifying the goal to cancel.
     * @see actionlib_msgs.GoalID
     */
    public final void sendCancel(final GoalID id) {
        this.goalManager.cancelGoal();
        this.cancelPublisher.publish(id);
    }

    /**
     * Start publishing on the client topics: /goal and /cancel.
     *
     * @param connectedNode The node object that is connected to the ROS master.
     */
    private final void publishClient(final ConnectedNode connectedNode) {
        Objects.requireNonNull(connectedNode);
        this.goalPublisher = connectedNode.newPublisher(actionName + "/goal", actionGoalType);
        this.goalPublisher.setLatchMode(LATCH_MODE);
        this.cancelPublisher = connectedNode.newPublisher(actionName + "/cancel", GoalID._TYPE);
    }

    /**
     * Stop publishing our client topics.
     */
    private final void unpublishClient() {
        if (this.goalPublisher != null) {
            this.goalPublisher.shutdown(5, TimeUnit.SECONDS);
            this.goalPublisher = null;
        }
        if (this.cancelPublisher != null) {
            this.cancelPublisher.shutdown(5, TimeUnit.SECONDS);
            this.cancelPublisher = null;
        }
    }

    public T_ACTION_GOAL newGoalMessage() {
        return goalPublisher.newMessage();
    }

    /**
     * Subscribe to the server topics.
     *
     * @param node The node object that is connected to the ROS master.
     */
    private final void subscribeToServer(final ConnectedNode node) {
        this.serverResultSubscriber = node.newSubscriber(actionName + "/result", actionResultType);
        this.serverFeedbackSubscriber = node.newSubscriber(actionName + "/feedback", actionFeedbackType);
        this.serverStatusSubscriber = node.newSubscriber(actionName + "/status", GoalStatusArray._TYPE);

        this.serverResultSubscriber.addMessageListener(message -> gotResult(message));
        this.serverFeedbackSubscriber.addMessageListener(message -> gotFeedback(message));
        this.serverStatusSubscriber.addMessageListener(message -> gotStatus(message));
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
     * Called whenever we get a message in the result topic.
     *
     * @param message The result message received. The type of this message
     *                depends on the application.
     */
    private final void gotResult(T_ACTION_RESULT message) {
        ActionResult<T_ACTION_RESULT> ar = new ActionResult(message);
        if (ar.getGoalStatusMessage().getGoalId().getId().equals(goalManager.getActionGoal().getGoalId())) {
            goalManager.updateStatus(ar.getGoalStatusMessage().getStatus());
        }
        goalManager.resultReceived();
        // Propagate the callback
        for (final ActionClientResultListener<T_ACTION_RESULT> actionClientListener : this.callbackResultTargets) {
            if (actionClientListener != null) {
                actionClientListener.resultReceived(message);
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
        ActionFeedback<T_ACTION_FEEDBACK> af = new ActionFeedback(message);
        if (af.getGoalStatusMessage().getGoalId().getId().equals(goalManager.getActionGoal().getGoalId())) {
            goalManager.updateStatus(af.getGoalStatusMessage().getStatus());
        }
        // Propagate the callback
        for (final ActionClientFeedbackListener<T_ACTION_FEEDBACK> actionClientListener : this.callbackFeedbackTargets) {
            if (actionClientListener != null) {
                actionClientListener.feedbackReceived(message);
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
        this.statusSubscriberFlag = true;

        // Find the status for our current goal
        final GoalStatus goalStatus = this.findStatus(message);
        if (goalStatus != null) {
            // update the goal status tracking
            this.goalManager.updateStatus(goalStatus.getStatus());
        } else {
            if (LOGGER.isDebugEnabled()) {
                if (message.getStatusList() != null && !message.getStatusList().isEmpty()) {
                    LOGGER.debug("Status update is not for current goal! Action:[" + this.actionName + "]");
                }
            }
        }
        // Propagate the callback

        for (final ActionClientStatusListener actionClientListener : this.callbackStatusTargets) {
            if (actionClientListener != null) {
                actionClientListener.statusReceived(message);
            }
        }

    }

    /**
     * Walk through the status array and find the status for the action goal that
     * we are interested in.
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

            if (this.goalManager.getActionGoal() != null && statusMessage.getStatusList() != null && !statusMessage.getStatusList().isEmpty()) {
                final String idToFind = this.goalManager.getActionGoal().getGoalId();

                if (idToFind != null) {
                    final List<GoalStatus> goalStatuses = statusList.stream().filter(goalStatusParam -> goalStatusParam.getGoalId().getId().equals(idToFind)).collect(Collectors.toList());
                    final int goalStatusesSize = goalStatuses.size();
                    if (LOGGER.isInfoEnabled() && (goalStatusesSize >= 0) && !statusList.isEmpty()) {
                        LOGGER.info("Found [" + goalStatuses.size() + "] statuses for goal ID: " + idToFind + " action:[" + actionName + "]");

                    }
                    if (goalStatusesSize > 0) {
                        goalStatus = goalStatuses.stream().findAny().orElse(null);
                        try {
                            if (goalStatus != null) {
                                for (final GoalStatus status : goalStatuses) {
                                    goalStatus = goalStatus.getGoalId().getStamp().compareTo(status.getGoalId().getStamp()) >= 0 ? goalStatus : status;
                                    if (LOGGER.isTraceEnabled()) {
                                        LOGGER.trace("Latest status: [" + goalStatus.getStatus() + "," + goalStatus.getText() + "] for goal with ID: " + idToFind + " action:[" + actionName + "]");
                                    }

                                }
                            }
                        } catch (final Exception e) {
                            if (LOGGER.isErrorEnabled()) {
                                LOGGER.error(ExceptionUtils.getStackTrace(e));
                            }
                        }
                    }

                }
            }
        }
        return goalStatus;
    }

    /**
     * Publishes the client's topics and suscribes to the server's topics.
     *
     * @param node The node object that is connected to the ROS master.
     */
    private final void connect(final ConnectedNode node) {
        publishClient(node);
        subscribeToServer(node);
    }


    /**
     * Checks if the server is started at the time of the method call.
     * @return
     */
    public final boolean isActionServerStarted() {
        final boolean result= this.statusSubscriberFlag
                && this.goalPublisher.hasSubscribers()
                && this.cancelPublisher.hasSubscribers()
                && this.isTopicPublished(this.serverFeedbackSubscriber.getTopicName().toString())
                && this.isTopicPublished(this.serverFeedbackSubscriber.getTopicName().toString());
                  this.isTopicPublished(this.serverResultSubscriber.getTopicName().toString());

        return result;
    }

    /**
     * Wait for an actionlib server to connect.
     *
     * @param timeout The maximum amount of time to wait for an action server. If
     *                this value is less than or equal to zero, it will wait forever until a
     *                server is detected.
     * @return True if the action server was detected before the timeout and
     * false otherwise.
     */
    public final boolean waitForActionServerToStart(final Duration timeout) {
        final Stopwatch stopwatch = Stopwatch.createStarted();
        final long durationInNanos = timeout.totalNsecs();
        boolean result = false;
        long tests = 0;
        boolean goalHasSubscribers = false;
        boolean cancelHasSubscribers = false;
        boolean feedbackSubscriberFlag = false;
        boolean resultSubscriberFlag = false;
        while (!result && (durationInNanos <= 0 || stopwatch.elapsed(TimeUnit.NANOSECONDS) <= durationInNanos)) {
            tests++;
            if (!goalHasSubscribers) {
                goalHasSubscribers = this.goalPublisher.hasSubscribers();
            }
            if (!cancelHasSubscribers) {
                cancelHasSubscribers = this.cancelPublisher.hasSubscribers();
            }
            if (!feedbackSubscriberFlag) {
                feedbackSubscriberFlag = this.isTopicPublished(this.serverFeedbackSubscriber.getTopicName().toString());
            }
            if (!resultSubscriberFlag) {
                resultSubscriberFlag = this.isTopicPublished(this.serverResultSubscriber.getTopicName().toString());
            }
            result = goalHasSubscribers
                    && cancelHasSubscribers
                    && this.statusSubscriberFlag
                    && resultSubscriberFlag
                    && feedbackSubscriberFlag;

            try {
                Thread.sleep(50);

            } catch (final Exception e) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug(ExceptionUtils.getStackTrace(e));
                }
            }

        }

        if (!result && LOGGER.isErrorEnabled()) {

            LOGGER.error("[Could not connect to Server] tests:" + tests + "] GoalTopic:[" + this.actionName + "/goal] CancelTopic[" + actionName + "/cancel] timeout:[" + timeout + "] \n"
                    + " [goalHasSubscribers:" + goalHasSubscribers
                    + "] [cancelHasSubscribers:" + cancelHasSubscribers
                    + "] [feedbackSubscriberFlag:" + feedbackSubscriberFlag
                    + "] [resultSubscriberFlag:" + resultSubscriberFlag
                    + "] [statusSubscriberFlag:" + statusSubscriberFlag + "]"
            );

        }
        if (!result && LOGGER.isDebugEnabled()) {
            LOGGER.debug(" [Server Started:" + result + "] [tests:" + tests + "] Goal Topic:[" + this.actionName + "/goal] CancelTopic[" + actionName + "/cancel] timeout:[" + timeout + "]");
        }
        return result;
    }

    /**
     * TODO this probably should be removed. It is not a good practice to override finalize.
     */
    protected final void finalize() {
        this.disconnect();
    }

    /**
     * Wait indefinately until an actionlib server is connected.
     */
    public final void waitForActionServerToStart() {
        waitForActionServerToStart(new Duration(0));
    }


    /**
     * @param topicName
     * @return
     */
    private final boolean isTopicPublished(final String topicName) {
        boolean result = false;
        if (topicName != null) {
            for (final TopicSystemState topicSystemState : this.masterStateClient.getSystemState().getTopics()) {
                if (topicSystemState != null
                        && topicName.equals(topicSystemState.getTopicName())
                        && topicSystemState.getPublishers() != null
                        && !topicSystemState.getPublishers().isEmpty()) {
                    result = true;
                    break;
                }

            }
        }
        return result;
    }


    /**
     * @return
     */
    final ClientState getGoalState() {
        return goalManager.getGoalState();
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
        unpublishClient();
        unsubscribeToServer();
    }


    @Override
    public String toString() {
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
                .add("statusSubscriberFlag=" + statusSubscriberFlag)
                .add("masterStateClient=" + masterStateClient)
                .toString();
    }
}
