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
import com.google.common.base.Stopwatch;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.ros.internal.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.concurrent.*;

/**
 * Only responsible to track a single goal.
 *
 * @param <T_GOAL>
 * @param <T_FEEDBACK>
 * @param <T_RESULT>
 */
final class ActionClientFuture<T_GOAL extends Message, T_FEEDBACK extends Message, T_RESULT extends Message>
        implements ActionFuture<T_GOAL, T_FEEDBACK, T_RESULT> {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final GoalID goalid;
    private final CountDownLatch countDownLatch;
    private final ActionClient<T_GOAL, T_FEEDBACK, T_RESULT> actionClient;
    private T_FEEDBACK latestFeedback = null;
    private T_RESULT result = null;
    private final ActionClientFutureListener actionClientFutureListener = new ActionClientFutureListener();

    private final class ActionClientFutureListener implements ActionClientResultListener<T_RESULT>, ActionClientFeedbackListener<T_FEEDBACK> {

        public final void feedbackReceived(final T_FEEDBACK t_feedback) {
            final ActionFeedback<T_FEEDBACK> actionFeedback = new ActionFeedback(t_feedback);
            if (ActionClientFuture.this.LOGGER.isTraceEnabled()) {
                ActionClientFuture.this.LOGGER.trace("Received feedback for goalId: " + actionFeedback.getGoalStatusMessage().getGoalId().getId());
            }
            if (actionFeedback.getGoalStatusMessage().getGoalId().getId().equals(ActionClientFuture.this.goalid.getId())) {
                ActionClientFuture.this.latestFeedback = t_feedback;
            }

        }

        /**
         * @param t_result
         */
        @Override
        public final void resultReceived(final T_RESULT t_result) {
            final ActionResult resultWrapper = new ActionResult(t_result);
            final String goalId=resultWrapper.getGoalStatusMessage().getGoalId().getId();
            if (ActionClientFuture.this.LOGGER.isDebugEnabled()) {
                ActionClientFuture.this.LOGGER.debug("Received result for goalId: " + goalId);
            }
            if (goalId.equals(ActionClientFuture.this.goalid.getId())) {
                ActionClientFuture.this.result = t_result;
                ActionClientFuture.this.disconnect();
                ActionClientFuture.this.countDownLatch.countDown();

            } else {
                if (LOGGER.isErrorEnabled()) {
                    LOGGER.error("Result with Unknown id:" + goalId + ", waiting for goalId:" + ActionClientFuture.this.goalid.getId());
                }
            }


        }

    }

    /**
     * @param actionClient
     * @param goal
     * @param <T_GOAL>
     * @param <T_FEEDBACK>
     * @param <T_RESULT>
     * @return
     */
    static final <T_GOAL extends Message, T_FEEDBACK extends Message, T_RESULT extends Message>
    ActionFuture<T_GOAL, T_FEEDBACK, T_RESULT> createFromGoal(final ActionClient<T_GOAL, T_FEEDBACK, T_RESULT> actionClient, final T_GOAL goal) {
        final GoalID goalId = actionClient.getGoalId(goal);
        final ActionClientFuture<T_GOAL, T_FEEDBACK, T_RESULT> actionClientFuture = new ActionClientFuture<>(actionClient, goalId);
        if (LOGGER.isWarnEnabled() && actionClient.isActive()) {
            LOGGER.warn("current goal STATE:" + actionClient.getGoalState() + "=" + actionClient.getGoalState().getValue());
        }
        actionClient.addListener((ActionClientFeedbackListener<T_FEEDBACK>) actionClientFuture.actionClientFutureListener);
        actionClient.addListener((ActionClientResultListener<T_RESULT>) actionClientFuture.actionClientFutureListener);
        return actionClientFuture;
    }

    /**
     * @param actionClient
     * @param goalID
     */
    private ActionClientFuture(final ActionClient<T_GOAL, T_FEEDBACK, T_RESULT> actionClient, final GoalID goalID) {
        Preconditions.checkNotNull(actionClient);
        Preconditions.checkNotNull(goalID);
        Preconditions.checkArgument(StringUtils.isNotBlank(goalID.getId()));
        this.actionClient = actionClient;
        this.goalid = goalID;
        this.countDownLatch = new CountDownLatch(1);
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
        return this.actionClient.getGoalState();
    }

    /**
     * @param mayInterruptIfRunning is currently ignored
     * @return returns true if cancel request was sent, false if not sent
     */
    @Override
    public final boolean cancel(final boolean mayInterruptIfRunning) {
        this.actionClient.sendCancel(this.goalid);
        return true;
    }

    /**
     * @return
     */
    @Override
    public final boolean isCancelled() {
        if (this.actionClient.getGoalState().isRunning()) {
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
        return !this.actionClient.getGoalState().isRunning();
    }

    /**
     * @return
     * @throws InterruptedException
     * @throws ExecutionException
     */
    @Override
    public final T_RESULT get() throws InterruptedException, ExecutionException {
        while (!this.isDone()) {
            this.countDownLatch.await();
        }
        return result;
    }

    @Override
    public final T_RESULT get(final long timeout, final TimeUnit timeUnit) throws InterruptedException, ExecutionException, TimeoutException {

        final Stopwatch stopwatch = Stopwatch.createStarted();

        while (!this.isDone() && stopwatch.elapsed(timeUnit) < timeout) {

            this.countDownLatch.await(timeout - stopwatch.elapsed(timeUnit), timeUnit);

        }
        stopwatch.stop();
        return this.result;
    }


    /**
     *
     */
    private final void disconnect() {
        this.actionClient.removeActionClientFeedbackListener(this.actionClientFutureListener);
        this.actionClient.removeActionClientResultListener(this.actionClientFutureListener);
    }

    /**
     * @return
     */
    @Override
    public final Future<Boolean> toBooleanFuture() {
        final Future<Boolean> resultFuture = new Future<>() {
            @Override
            public final boolean cancel(boolean mayInterruptIfRunning) {
                return ActionClientFuture.this.cancel(mayInterruptIfRunning);
            }

            @Override
            public final boolean isCancelled() {
                return ActionClientFuture.this.isCancelled();
            }

            @Override
            public final boolean isDone() {
                return ActionClientFuture.this.isDone();
            }

            @Override
            public final Boolean get() throws ExecutionException, InterruptedException {
                return ActionClientFuture.this.get() != null;
            }

            @Override
            public final Boolean get(long timeout, TimeUnit timeUnit) throws ExecutionException, InterruptedException, TimeoutException {
                return ActionClientFuture.this.get(timeout, timeUnit) != null;
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
