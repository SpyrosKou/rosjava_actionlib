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
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.ros.internal.message.Message;
import org.ros.internal.node.topic.PublisherIdentifier;
import org.ros.message.Duration;
import org.ros.message.MessageListener;
import org.ros.message.Time;
import org.ros.node.ConnectedNode;
import org.ros.node.topic.DefaultSubscriberListener;
import org.ros.node.topic.Publisher;
import org.ros.node.topic.Subscriber;

import java.util.Collection;
import java.util.List;
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
        T_ACTION_RESULT extends Message> extends DefaultSubscriberListener {

    private static final Log LOGGER = LogFactory.getLog(ActionClient.class);
    private ClientGoalManager<T_ACTION_GOAL> goalManager;
    private String actionGoalType;
    private String actionResultType;
    private String actionFeedbackType;
    private Publisher<T_ACTION_GOAL> goalPublisher = null;
    private Publisher<GoalID> cancelPublisher = null;
    private Subscriber<T_ACTION_RESULT> serverResultSubscriber = null;
    private Subscriber<T_ACTION_FEEDBACK> serverFeedbackSubscriber = null;
    private Subscriber<GoalStatusArray> serverStatusSubscriber = null;
    private ConnectedNode node = null;
    private String actionName;
    private static final boolean LATCH_MODE=true;
    //    private final List<ActionClientListener<T_ACTION_FEEDBACK,T_ACTION_RESULT>> callbackTargets = new CopyOnWriteArrayList<>();
    private final List<ActionClientResultListener<T_ACTION_RESULT>> callbackResultTargets = new CopyOnWriteArrayList<>();
    private final List<ActionClientFeedbackListener<T_ACTION_FEEDBACK>> callbackFeedbackTargets = new CopyOnWriteArrayList<>();
    private final List<ActionClientStatusListener> callbackStatusTargets = new CopyOnWriteArrayList<>();

    private final GoalIDGenerator goalIdGenerator;
    private volatile boolean statusReceivedFlag = false;
    private volatile boolean feedbackPublisherFlag = false;
    private volatile boolean resultPublisherFlag = false;


    /**
     * Constructor for an ActionClient object.
     *
     * @param node               The node object that is connected to the ROS master.
     * @param actionName         A string representing the name of this action. This name
     *                           is used to publish the actinlib topics and should be agreed between server
     *                           and the client.
     * @param actionGoalType     A string with the type information for the action
     *                           goal message.
     * @param actionFeedbackType A string with the type information for the
     *                           feedback message.
     * @param actionResultType   A string with the type information for the result
     *                           message.
     */
    public ActionClient(final ConnectedNode node, final String actionName, final String actionGoalType,
                        final String actionFeedbackType, final String actionResultType) {
        this.node = node;
        this.actionName = actionName;
        this.actionGoalType = actionGoalType;
        this.actionFeedbackType = actionFeedbackType;
        this.actionResultType = actionResultType;
        this.goalIdGenerator = new GoalIDGenerator(node);
        this.connect(node);
        this.goalManager = new ClientGoalManager(new ActionGoal<T_ACTION_GOAL>());
    }

    /**
     * @param target
     */
    public final void addListener(final ActionClientListener<T_ACTION_FEEDBACK, T_ACTION_RESULT> target) {
//        callbackTargets.add(target);
        callbackStatusTargets.add(target);
        callbackFeedbackTargets.add(target);
        callbackResultTargets.add(target);
    }

    /**
     * @param targets
     */
    public final void addListeners(final Collection<ActionClientListener<T_ACTION_FEEDBACK, T_ACTION_RESULT>> targets) {
        if (targets != null) {
            for (final ActionClientListener target : targets) {
//                callbackTargets.add(target);
                callbackStatusTargets.add(target);
                callbackFeedbackTargets.add(target);
                callbackResultTargets.add(target);
            }
        }
    }

    /**
     * @param target
     */
    public final void addListener(final ActionClientStatusListener target) {

        this.callbackStatusTargets.add(target);
    }

    /**
     * @param target
     */
    public final void addListener(final ActionClientFeedbackListener target) {
        this.callbackFeedbackTargets.add(target);
    }

    /**
     * @param target
     */
    public final void addListener(final ActionClientResultListener target) {
        this.callbackResultTargets.add(target);
    }

    /**
     * @param target
     *
     * @deprecated better use {@link ActionClient#addListener(ActionClientListener)} that clearly shows seperaton between different listeners type
     * listeners can be added.
     */
    @Deprecated
    public final void attachListener(final ActionClientListener target) {
        this.addListener(target);
    }

    /**
     * @param target
     */
    public void detachListener(final ActionClientListener target) {

//        callbackTargets.remove(target);
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
    void sendGoalWire(final T_ACTION_GOAL agMessage) {
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
     *
     * @return Goal ID object containing the ID of the action message.
     *
     * @see actionlib_msgs.GoalID
     */
    public GoalID getGoalId(final T_ACTION_GOAL goal) {

        final GoalID gid = ActionLibMessagesUtils.getSubMessageFromMessage(goal, "getGoalId");
        return gid;
    }

    /**
     * Convenience method for setting the goal ID of an action goal message.
     *
     * @param goal The action goal message to set the goal ID for.
     * @param gid  The goal ID object.
     *
     * @see actionlib_msgs.GoalID
     */
    public void setGoalId(final T_ACTION_GOAL goal, final GoalID gid) {
        ActionLibMessagesUtils.setSubMessageFromMessage(goal, gid, "getGoalId");
    }

    /**
     * Publish a cancel message. This instructs the action server to cancel the
     * specified goal.
     *
     * @param id The GoalID message identifying the goal to cancel.
     *
     * @see actionlib_msgs.GoalID
     */
    public void sendCancel(final GoalID id) {
        this.goalManager.cancelGoal();
        this.cancelPublisher.publish(id);
    }

    /**
     * Start publishing on the client topics: /goal and /cancel.
     *
     * @param node The node object that is connected to the ROS master.
     */
    private final void publishClient(final ConnectedNode node) {
        this.goalPublisher = this.node.newPublisher(actionName + "/goal", actionGoalType);
        this.goalPublisher.setLatchMode(LATCH_MODE);
        this.cancelPublisher = this.node.newPublisher(actionName + "/cancel", GoalID._TYPE);
    }

    /**
     * Stop publishing our client topics.
     */
    private void unpublishClient() {
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

        this.serverFeedbackSubscriber.addSubscriberListener(this);
        this.serverResultSubscriber.addSubscriberListener(this);

        this.serverResultSubscriber.addMessageListener(message -> gotResult(message));
        this.serverFeedbackSubscriber.addMessageListener(message -> gotFeedback(message));
        this.serverStatusSubscriber.addMessageListener(message -> gotStatus(message));
    }

    /**
     * Unsubscribe from the server topics.
     */
    private void unsubscribeToServer() {
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
    public void gotResult(T_ACTION_RESULT message) {
        ActionResult<T_ACTION_RESULT> ar = new ActionResult(message);
        if (ar.getGoalStatusMessage().getGoalId().getId().equals(goalManager.getActionGoal().getGoalId())) {
            goalManager.updateStatus(ar.getGoalStatusMessage().getStatus());
        }
        goalManager.resultReceived();
        // Propagate the callback
        for (final ActionClientResultListener<T_ACTION_RESULT> actionClientListener : callbackResultTargets) {
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
    public void gotFeedback(final T_ACTION_FEEDBACK message) {
        ActionFeedback<T_ACTION_FEEDBACK> af = new ActionFeedback(message);
        if (af.getGoalStatusMessage().getGoalId().getId().equals(goalManager.getActionGoal().getGoalId())) {
            goalManager.updateStatus(af.getGoalStatusMessage().getStatus());
        }
        // Propagate the callback
        for (final ActionClientFeedbackListener<T_ACTION_FEEDBACK> actionClientListener : callbackFeedbackTargets) {
            if (actionClientListener != null) {
                actionClientListener.feedbackReceived(message);
            }
        }
    }

    /**
     * Called whenever we get a message in the status topic.
     *
     * @param message The GoalStatusArray message received.
     *
     * @see actionlib_msgs.GoalStatusArray
     */
    public void gotStatus(final GoalStatusArray message) {
        //can only be set
        if (!this.statusReceivedFlag) {
            this.statusReceivedFlag = true;
        }
        // Find the status for our current goal
        final GoalStatus goalStatus = this.findStatus(message);
        if (goalStatus != null) {
            // update the goal status tracking
            this.goalManager.updateStatus(goalStatus.getStatus());
        } else {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Status update is not for current goal!");
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
     *
     * @return The goal status message for the goal we want or null if we didn't
     * find it.
     */
    public final GoalStatus findStatus(final GoalStatusArray statusMessage) {

        final List<GoalStatus> statusList = statusMessage.getStatusList();
        final String idToFind = this.goalManager.getActionGoal().getGoalId();
        GoalStatus goalStatus = null;
        if (idToFind != null) {
            final List<GoalStatus> goalStatuses = statusList.stream().filter(goalStatusParam -> goalStatusParam.getGoalId().getId().equals(idToFind)).collect(Collectors.toList());
            final int goalStatusesSize = goalStatuses.size();
            if (LOGGER.isDebugEnabled() && (goalStatusesSize > 1 || goalStatusesSize == 0)) {
                LOGGER.debug("Found [" + goalStatuses.size() + "] goals with ID: " + idToFind);

            }
            goalStatus = goalStatuses.stream().findAny().orElse(null);
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
     * Wait for an actionlib server to connect.
     *
     * @param timeout The maximum amount of time to wait for an action server. If
     *                this value is less than or equal to zero, it will wait forever until a
     *                server is detected.
     *
     * @return True if the action server was detected before the timeout and
     * false otherwise.
     */
    public boolean waitForActionServerToStart(final Duration timeout) {
        final Stopwatch stopwatch = Stopwatch.createStarted();
        final long durationInNanos = Math.max(0, timeout.totalNsecs());
        boolean result = false;
        long tests = 0;
        while (!result && stopwatch.elapsed(TimeUnit.NANOSECONDS) <= durationInNanos) {
            tests++;
            result =
                    this.goalPublisher.hasSubscribers()
                            && this.cancelPublisher.hasSubscribers()
                            && this.feedbackPublisherFlag
                            && this.resultPublisherFlag
                            && this.statusReceivedFlag;

            try {
                Thread.sleep(5);

            } catch (final Exception e) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug(ExceptionUtils.getStackTrace(e));
                }
            }

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

    @Override
    public final void onNewPublisher(final Subscriber subscriber, final PublisherIdentifier publisherIdentifier) {
        //public void onNewFeedbackPublisher(Subscriber<T_ACTION_FEEDBACK> subscriber, PublisherIdentifier publisherIdentifier) {
        if (subscriber.equals(this.serverFeedbackSubscriber)) {
            this.feedbackPublisherFlag = true;
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("Found server publishing on the " + this.actionName + "/feedback topic.");
            }
        } else {
            if (subscriber.equals(this.serverResultSubscriber)) {
                this.resultPublisherFlag = true;
                if (LOGGER.isInfoEnabled()) {
                    LOGGER.info("Found server publishing on the " + this.actionName + "/result topic.");
                }
            }
        }
    }

    /**
     * @return
     */
    public ClientState getGoalState() {
        return goalManager.getGoalState();
    }


    public boolean isActive() {
        return goalManager.getStateMachine().isRunning();
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
                .add("actionGoalType='" + actionGoalType + "'")
                .add("actionResultType='" + actionResultType + "'")
                .add("actionFeedbackType='" + actionFeedbackType + "'")
                .add("node=" + NodePrinter.translateConnectedNodeToString(node))
                .add("actionName='" + actionName + "'")
                .add("statusReceivedFlag=" + statusReceivedFlag)
                .add("feedbackPublisherFlag=" + feedbackPublisherFlag)
                .add("resultPublisherFlag=" + resultPublisherFlag)
                .toString();
    }
}
