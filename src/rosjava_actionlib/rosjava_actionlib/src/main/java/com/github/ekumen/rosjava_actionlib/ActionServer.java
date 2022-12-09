/**
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

package com.github.ekumen.rosjava_actionlib;

import actionlib_msgs.GoalID;
import actionlib_msgs.GoalStatus;
import actionlib_msgs.GoalStatusArray;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.ros.internal.message.Message;
import org.ros.message.MessageListener;
import org.ros.node.ConnectedNode;
import org.ros.node.topic.Publisher;
import org.ros.node.topic.Subscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import java.util.concurrent.TimeUnit;

import static com.github.ekumen.rosjava_actionlib.ActionLibTopics.*;

/**
 * Class to encapsulate the actiolib server's communication and goal management.
 *
 * @author Ernesto Corbellini ecorbellini@ekumenlabs.com
 */
public final class ActionServer<T_ACTION_GOAL extends Message, T_ACTION_FEEDBACK extends Message, T_ACTION_RESULT extends Message> implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(ActionServer.class);
    /**
     * Default delay in millis used in publishing status.
     */
    private static final long DEFAULT_STATUS_TICK_DELAY_MILLIS = 2000;
    /**
     * Default period in millis used in publishing status.
     */
    private static final long DEFAULT_STATUS_TICK_PERIOD_MILLIS = 1000;

    private static final long DEFAULT_SHUTDOWN_WAIT_SECONDS = 5;

    private static final int GOAL_NOT_TRACKED_STATE = -100;


    /**
     * Keeps the status of each goal
     *
     * @param <T_ACTION_GOAL_TYPE> the T_ACTION_GOAL type
     */
    private static final class ServerGoal<T_ACTION_GOAL_TYPE extends Message> {
        private final T_ACTION_GOAL_TYPE goal;
        private final ServerStateMachine stateMachine = new ServerStateMachine();

        private ServerGoal(final T_ACTION_GOAL_TYPE goal) {
            this.goal = goal;
        }
    }

    //  private T_ACTION_GOAL actionGoal;
    private final String actionGoalType;
    private final String actionResultType;
    private final String actionFeedbackType;
    private Subscriber<T_ACTION_GOAL> goalSuscriber = null;
    private Subscriber<GoalID> cancelSuscriber = null;

    private Publisher<T_ACTION_RESULT> resultPublisher = null;
    private Publisher<T_ACTION_FEEDBACK> feedbackPublisher = null;
    private Publisher<GoalStatusArray> statusPublisher = null;
    private ConnectedNode node = null;
    private final String actionName;
    private ActionServerListener callbackTarget = null;
    private Timer statusTick = new Timer();
    private HashMap<String, ServerGoal> goalTracker = new HashMap<String, ServerGoal>(1);

    /**
     * Constructor.
     *
     * @param node               Object representing a node connected to a ROS master.
     * @param actionName         String that identifies the name of this action. This name
     *                           is used for naming the ROS topics.
     * @param actionGoalType     String holding the type for the action goal message.
     * @param actionFeedbackType String holding the type for the action feedback
     *                           message.
     * @param actionResultType   String holding the type for the action result
     *                           message.
     */
    public ActionServer(final ConnectedNode connectedNode
            , final ActionServerListener<T_ACTION_GOAL> actionServerListener
            , final String actionName
            , final String actionGoalType
            , final String actionFeedbackType
            , final String actionResultType) {
        Objects.requireNonNull(connectedNode);
        Objects.requireNonNull(actionServerListener);
        Preconditions.checkArgument(StringUtils.isNotBlank(actionName));
        Preconditions.checkArgument(StringUtils.isNotBlank(actionGoalType));
        Preconditions.checkArgument(StringUtils.isNotBlank(actionFeedbackType));
        Preconditions.checkArgument(StringUtils.isNotBlank(actionResultType));
        this.actionServerListener = actionServerListener;

        this.actionName = actionName;
        this.actionGoalType = actionGoalType;
        this.actionFeedbackType = actionFeedbackType;
        this.actionResultType = actionResultType;
        this.messageFactory = connectedNode.getTopicMessageFactory();
        this.connect(connectedNode);
    }


    /**
     * Attach a listener to this actionlib server. The listener must implement the
     * ActionServerListener interface which provides callback methods for each
     * incoming message and event.
     *
     * @param target An object that implements the ActionServerListener interface.
     *               This object will receive the callbacks with the events.
     */
    public void attachListener(ActionServerListener target) {
        callbackTarget = target;
    }

    /**
     * Publish the current status information for the tracked goals on the /status topic.
     *
     * @param status GoalStatusArray message containing the status to send.
     *
     * @see actionlib_msgs.GoalStatusArray
     */
    public void sendStatus(final GoalStatusArray status) {
        statusPublisher.publish(status);
    }

    /**
     * Publish a feedback message on the /feedback topic.
     *
     * @param feedback An action feedback message to send.
     */
    public void sendFeedback(final T_ACTION_FEEDBACK feedback) {
        feedbackPublisher.publish(feedback);
    }

    /**
     * Publish result message on the /result topic.
     *
     * @param result The action result message to send.
     */
    public void sendResult(final T_ACTION_RESULT result) {
        resultPublisher.publish(result);
    }

    /**
     * Publish the action server topics: /status, /feedback, /result
     *
     * @param node The object representing a node connected to a ROS master.
     */
    private void publishServer(final ConnectedNode node) {
        statusPublisher = node.newPublisher(getGoalStatusTopicNameForActionName(actionName), GoalStatusArray._TYPE);
        feedbackPublisher = node.newPublisher(getFeedbackTopicNameForActionName(actionName), actionFeedbackType);
        resultPublisher = node.newPublisher(getResultTopicNameForActionName(actionName), actionResultType);
        statusTick.scheduleAtFixedRate(new TimerTask() {
            @Override
            public final void run() {
                sendStatusTick();
            }
        }, DEFAULT_STATUS_TICK_DELAY_MILLIS, DEFAULT_STATUS_TICK_PERIOD_MILLIS);
    }

    /**
     * Stop publishing the action server topics.
     */
    private final void unpublishServer() {
        if (statusPublisher != null) {
            statusPublisher.shutdown(DEFAULT_SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS);
            statusPublisher = null;
        }
        if (feedbackPublisher != null) {
            feedbackPublisher.shutdown(DEFAULT_SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS);
            feedbackPublisher = null;
        }
        if (resultPublisher != null) {
            resultPublisher.shutdown(DEFAULT_SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS);
            resultPublisher = null;
        }
    }

    /**
     * Subscribe to the action client's topics: goal and cancel.
     *
     * @param node The ROS node connected to the master.
     */
    private final void subscribeToClient(ConnectedNode node) {
        goalSuscriber = node.newSubscriber(actionName + "/goal", actionGoalType);
        cancelSuscriber = node.newSubscriber(actionName + "/cancel", GoalID._TYPE);

        goalSuscriber.addMessageListener(new MessageListener<T_ACTION_GOAL>() {
            @Override
            public void onNewMessage(T_ACTION_GOAL message) {
                gotGoal(message);
            }
        });

        cancelSuscriber.addMessageListener(new MessageListener<GoalID>() {
            @Override
            public void onNewMessage(GoalID message) {
                gotCancel(message);
            }
        });
    }

    /**
     * Unsubscribe from the client's topics.
     */
    private final void unsubscribeToClient() {
        if (goalSuscriber != null) {
            goalSuscriber.shutdown(DEFAULT_SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS);
            goalSuscriber = null;
        }
        if (cancelSuscriber != null) {
            cancelSuscriber.shutdown(DEFAULT_SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS);
            cancelSuscriber = null;
        }
    }

    /**
     * Called when we get a message on the subscribed goal topic.
     */
    public final void gotGoal(final T_ACTION_GOAL goal) {
        boolean accepted = false;
        String goalIdString = getGoalId(goal).getId();

        // start tracking this newly received goal
        goalTracker.put(goalIdString, new ServerGoal(goal));
        // Propagate the callback
        if (callbackTarget != null) {
            // inform the user of a received message
            callbackTarget.goalReceived(goal);
            // ask the user to accept the goal
            accepted = callbackTarget.acceptGoal(goal);
            if (accepted) {
                // the user accepted the goal
                try {
                    goalTracker.get(goalIdString).state.transition(ServerStateMachine.Events.ACCEPT);
                } catch (final Exception e) {
                    logger.error(ExceptionUtils.getStackTrace(e));
                }
            } else {
                // the user rejected the goal
                try {
                    goalTracker.get(goalIdString).state.transition(ServerStateMachine.Events.REJECT);
                } catch (final Exception e) {
                    logger.error(ExceptionUtils.getStackTrace(e));
                }
            }
        }
    }

    /**
     * Called when we get a message on the subscribed cancel topic.
     */
    public final void gotCancel(final GoalID gid) {
        // Propagate the callback
        if (callbackTarget != null) {
            callbackTarget.cancelReceived(gid);
        }
    }

    /**
     * Publishes the current status on the server's status topic.
     * This is used like a heartbeat to update the status of every tracked goal.
     * It uses the {@link ActionServer#DEFAULT_STATUS_TICK_DELAY_MILLIS} and {@link ActionServer#DEFAULT_STATUS_TICK_PERIOD_MILLIS}
     */
    public final void sendStatusTick() {
        final GoalStatusArray status = statusPublisher.newMessage();
        GoalStatus goalStatus;
        final Vector<GoalStatus> goalStatusList = new Vector<GoalStatus>();

        for (final ServerGoal<T_ACTION_GOAL> sg : goalTracker.values()) {
            goalStatus = node.getTopicMessageFactory().newFromType(GoalStatus._TYPE);
            goalStatus.setGoalId(getGoalId(sg.goal));
            goalStatus.setStatus((byte) sg.state.getState());
            goalStatusList.add(goalStatus);
        }
        status.setStatusList(goalStatusList);
        sendStatus(status);
    }

    /**
     * Create a new Result Message
     *
     * @return
     */
    public final T_ACTION_RESULT newResultMessage() {
        return resultPublisher.newMessage();
    }

    /**
     * Create a new Feedback Message
     *
     * @return
     */
    public final T_ACTION_FEEDBACK newFeedbackMessage() {
        return feedbackPublisher.newMessage();
    }

    /**
     * Returns the goal ID object related to a given action goal.
     *
     * @param goal An action goal message.
     *
     * @return The goal ID object.
     */
    public final GoalID getGoalId(final T_ACTION_GOAL goal) {
        GoalID gid = null;
        try {
            final Method m = goal.getClass().getMethod("getGoalId");
            m.setAccessible(true); // workaround for known bug http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6924232
            gid = (GoalID) m.invoke(goal);
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
        return gid;
    }

    /**
     * Get the current state of the referenced goal.
     *
     * @param goalId String representing the ID of the goal.
     *
     * @return The current state of the goal or {@link ActionServer#GOAL_NOT_TRACKED_STATE} if the goal ID is not tracked.
     *
     * @see actionlib_msgs.GoalStatus
     */
    public final int getGoalState(final String goalId) {
        int returnState = 0;

        if (goalTracker.containsKey(goalId)) {
            returnState = goalTracker.get(goalId).state.getState();
        } else {
            returnState = GOAL_NOT_TRACKED_STATE;
        }
        return returnState;
    }

    /**
     * Express a succeed event for this goal. The state of the goal will be updated.
     */
    public final void setSucceed(final String goalIdString) {
        try {
            this.goalTracker.get(goalIdString).state.transition(ServerStateMachine.Events.SUCCEED);
        } catch (final Exception e) {
            logger.error(ExceptionUtils.getStackTrace(e));
        }
    }

    /**
     * Set goal ID and state information to the goal status message.
     *
     * @param gstat     GoalStatus message.
     * @param gidString String identifying the goal.
     *
     * @see actionlib_msgs.GoalStatus
     */
    public void setGoalStatus(final GoalStatus gstat, final String gidString) {
        try {
            final ServerGoal<T_ACTION_GOAL> serverGoal = goalTracker.get(gidString);
            gstat.setGoalId(getGoalId(serverGoal.goal));
            gstat.setStatus((byte) serverGoal.state.getState());
        } catch (final Exception e) {
            logger.error(ExceptionUtils.getStackTrace(e));
        }
    }

    /**
     * Publishes the server's topics and suscribes to the client's topics.
     */
    private final void connect(final ConnectedNode node) {
        publishServer(node);
        subscribeToClient(node);
    }

    /**
     * Finish the action server. Unregister publishers and listeners.
     */
    public final void finish() {
        callbackTarget = null;
        unpublishServer();
        unsubscribeToClient();
    }


    /**
     *
     * Will not throw any exception in any case. However it will log exceptions.
     *
     * Closes this resource, relinquishing any underlying resources.
     * This method is invoked automatically on objects managed by the
     * {@code try}-with-resources statement.
     *
     * <p>While this interface method is declared to throw {@code
     * Exception}, implementers are <em>strongly</em> encouraged to
     * declare concrete implementations of the {@code close} method to
     * throw more specific exceptions, or to throw no exception at all
     * if the close operation cannot fail.
     *
     * <p> Cases where the close operation may fail require careful
     * attention by implementers. It is strongly advised to relinquish
     * the underlying resources and to internally <em>mark</em> the
     * resource as closed, prior to throwing the exception. The {@code
     * close} method is unlikely to be invoked more than once and so
     * this ensures that the resources are released in a timely manner.
     * Furthermore it reduces problems that could arise when the resource
     * wraps, or is wrapped, by another resource.
     *
     * <p><em>Implementers of this interface are also strongly advised
     * to not have the {@code close} method throw {@link
     * InterruptedException}.</em>
     * <p>
     * This exception interacts with a thread's interrupted status,
     * and runtime misbehavior is likely to occur if an {@code
     * InterruptedException} is {@linkplain Throwable#addSuppressed
     * suppressed}.
     * <p>
     * More generally, if it would cause problems for an
     * exception to be suppressed, the {@code AutoCloseable.close}
     * method should not throw it.
     *
     * <p>Note that unlike the {@link Closeable#close close}
     * method of {@link Closeable}, this {@code close} method
     * is <em>not</em> required to be idempotent.  In other words,
     * calling this {@code close} method more than once may have some
     * visible side effect, unlike {@code Closeable.close} which is
     * required to have no effect if called more than once.
     * <p>
     * However, implementers of this interface are strongly encouraged
     * to make their {@code close} methods idempotent.
     *
     * @throws Exception if this resource cannot be closed
     */
    @Override
    public void close() throws Exception {
        try {
            finish();
        } catch (final Exception e) {
            logger.error(ExceptionUtils.getStackTrace(e));
        }
    }
}
