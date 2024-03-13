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
import com.google.common.base.Stopwatch;
import org.ros.internal.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @param <T_GOAL>
 * @param <T_FEEDBACK>
 * @param <T_RESULT>
 */
final class ActionClientFuture<T_GOAL extends Message, T_FEEDBACK extends Message, T_RESULT extends Message>
        implements ActionFuture<T_GOAL, T_FEEDBACK, T_RESULT>,
        ActionClientListener<T_FEEDBACK, T_RESULT> {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final GoalID goalid;
    private final ActionClient<T_GOAL, T_FEEDBACK, T_RESULT> actionClient;
    private final ClientGoalManager goalManager = new ClientGoalManager(new ActionGoal<T_GOAL>());
    private T_FEEDBACK latestFeedback = null;
    private T_RESULT result = null;


    /**
     * @param actionClient
     * @param goal
     * @param <T_GOAL>
     * @param <T_FEEDBACK>
     * @param <T_RESULT>
     * @return
     */
    static final <T_GOAL extends Message, T_FEEDBACK extends Message, T_RESULT extends Message>
    ActionFuture<T_GOAL, T_FEEDBACK, T_RESULT>
    createFromGoal(final ActionClient<T_GOAL, T_FEEDBACK, T_RESULT> actionClient,final  T_GOAL goal) {
        final GoalID goalId = actionClient.getGoalId(goal);
        final ActionClientFuture<T_GOAL, T_FEEDBACK, T_RESULT> result = new ActionClientFuture<>(actionClient, goalId);
        if (LOGGER.isWarnEnabled() && actionClient.isActive()) {
            LOGGER.warn("current goal STATE:" + actionClient.getGoalState() + "=" + actionClient.getGoalState().getValue());
        }
        result.goalManager.setGoal(goal);
        actionClient.sendGoalWire(goal);
        actionClient.addListener(result);
        return result;

    }

    /**
     * @param actionClient
     * @param goalID
     */
    private ActionClientFuture(final ActionClient<T_GOAL, T_FEEDBACK, T_RESULT> actionClient, final GoalID goalID) {
        this.actionClient = actionClient;
        this.goalid = goalID;
    }


    /**
     * @return
     */
    @Override
    public final T_FEEDBACK getLatestFeedback() {
        return this.latestFeedback;
    }

    /**
     * @return
     */
    @Override
    public final ClientState getCurrentState() {
        return this.goalManager.getStateMachine().getState();
    }

    /**
     * @param mayInterruptIfRunning is currently ignored
     *
     * @return returns true if cancel was send, false if not send
     */
    @Override
    public final boolean cancel(final boolean mayInterruptIfRunning) {

        this.actionClient.sendCancel(this.goalid);
        this.goalManager.cancelGoal();
        return true;
    }

    /**
     * @return
     */
    @Override
    public final boolean isCancelled() {
        if (this.goalManager.getStateMachine().isRunning()) {
            return this.result == null;
        } else {
            return false;
        }
    }

    /**
     * @return
     */
    @Override
    public final boolean isDone() {
        return !this.goalManager.getStateMachine().isRunning();
    }

    /**
     * @return
     * @throws InterruptedException
     * @throws ExecutionException
     */
    @Override
    public final T_RESULT get() throws InterruptedException, ExecutionException {
        while (!this.isDone()) {
            Thread.sleep(100);
        }
        this.disconnect();
        return result;
    }

    @Override
    public final T_RESULT get(final long timeout, final TimeUnit timeUnit) throws InterruptedException, ExecutionException, TimeoutException {

        final Stopwatch stopwatch = Stopwatch.createStarted();
        while (!this.isDone()) {
            if (stopwatch.elapsed(timeUnit) > timeout) {
                throw new TimeoutException();
            }
            Thread.sleep(50);
        }
        stopwatch.stop();

        disconnect();
        return this.result;
    }

    /**
     * @param t_result
     */
    @Override
    public final void resultReceived(final T_RESULT t_result) {
        final ActionResult result = new ActionResult(t_result);
        if (this.LOGGER.isDebugEnabled()) {
            this.LOGGER.debug("Received result: " + result.getGoalStatusMessage().getGoalId().getId());
        }
        if (result.getGoalStatusMessage().getGoalId().getId().equals(goalid.getId())) {
            this.goalManager.updateStatus(result.getGoalStatusMessage().getStatus());
            this.goalManager.resultReceived();

            this.result = t_result;
            disconnect();

        } else {
            if (LOGGER.isWarnEnabled()) {
                LOGGER.warn("Result with Unknown id:" + result.getGoalStatusMessage().getGoalId().getId() + ", waiting for " + goalid.getId());
            }
        }


    }

    @Override
    public final void feedbackReceived(final T_FEEDBACK t_feedback) {
        final ActionFeedback actionFeedback = new ActionFeedback(t_feedback);

        if (actionFeedback.getGoalStatusMessage().getGoalId().getId().equals(goalid.getId())) {
            goalManager.updateStatus(actionFeedback.getGoalStatusMessage().getStatus());
            this.latestFeedback = t_feedback;
        }

    }

    /**
     * @param status The status message received from the server.
     */
    @Override
    public final void statusReceived(final GoalStatusArray status) {
        final String thisGoalId = this.goalid.getId();
        status.getStatusList().stream()
                .filter(goalStatus -> thisGoalId.equals(goalStatus.getGoalId().getId()))
                .map(GoalStatus::getStatus)
                .forEach(this.goalManager::updateStatus);
    }

    /**
     *
     */
    private final void disconnect() {
        this.actionClient.removeListener(this);
    }

    /**
     * @return
     */
    @Override
    public final Future<Boolean> toBooleanFuture() {
        final ActionClientFuture<T_GOAL, T_FEEDBACK, T_RESULT> self = this;
        final Future<Boolean> resultFuture = new Future<>() {
            @Override
            public final boolean cancel(boolean bln) {
                return self.cancel(bln);
            }

            @Override
            public final boolean isCancelled() {
                return self.isCancelled();
            }

            @Override
            public final boolean isDone() {
                return self.isDone();
            }

            @Override
            public final Boolean get() throws InterruptedException, ExecutionException {
                return self.get() != null;
            }

            @Override
            public final Boolean get(long timeout, TimeUnit timeUnit) throws InterruptedException, ExecutionException, TimeoutException {
                return this.get(timeout, timeUnit) != null;
            }
        };

        return resultFuture;

    }

    /**
     * @return
     */
    @Override
    public final Future<Void> toVoidFuture() {

        final ActionClientFuture<T_GOAL, T_FEEDBACK, T_RESULT> self = this;

        return new Future<>() {
            @Override
            public final boolean cancel(boolean bln) {
                return self.cancel(bln);
            }

            @Override
            public final boolean isCancelled() {
                return self.isCancelled();
            }

            @Override
            public final boolean isDone() {
                return self.isDone();
            }

            @Override
            public final Void get() throws InterruptedException, ExecutionException {
                self.get();
                return null;
            }

            @Override
            public final Void get(final long timeout, final TimeUnit timeUnit) throws InterruptedException, ExecutionException, TimeoutException {
                self.get(timeout, timeUnit);
                return null;
            }
        };

    }

}
