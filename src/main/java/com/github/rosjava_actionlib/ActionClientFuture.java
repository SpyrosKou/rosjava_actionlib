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
import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;
import org.ros.internal.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.lang.ref.Cleaner;
import java.lang.ref.WeakReference;
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
    private static final Cleaner CLEANER = Cleaner.create();
    private final GoalID goalid;
    private final CountDownLatch countDownLatch;
    private final ActionClient<T_GOAL, T_FEEDBACK, T_RESULT> actionClient;
    private final ActionClientFutureListener<T_GOAL, T_FEEDBACK, T_RESULT> actionClientFutureListener;
    private final Cleaner.Cleanable cleanable;
    private volatile boolean cancelRequested = false;
    private volatile ClientState terminalState = null;
    private T_FEEDBACK latestFeedback = null;
    private T_RESULT result = null;

    private static final class ListenerCleanup<T_GOAL extends Message, T_FEEDBACK extends Message, T_RESULT extends Message> implements Runnable {
        private final ActionClient<T_GOAL, T_FEEDBACK, T_RESULT> actionClient;
        private final ActionClientFutureListener<T_GOAL, T_FEEDBACK, T_RESULT> listener;

        private ListenerCleanup(final ActionClient<T_GOAL, T_FEEDBACK, T_RESULT> actionClient,
                                final ActionClientFutureListener<T_GOAL, T_FEEDBACK, T_RESULT> listener) {
            this.actionClient = actionClient;
            this.listener = listener;
        }

        @Override
        public final void run() {
            this.actionClient.removeActionClientFeedbackListener(this.listener);
            this.actionClient.removeActionClientResultListener(this.listener);
        }
    }

    private static final class ActionClientFutureListener<T_GOAL extends Message, T_FEEDBACK extends Message, T_RESULT extends Message>
            implements ActionClientResultListener<T_RESULT>, ActionClientFeedbackListener<T_FEEDBACK> {
        private final WeakReference<ActionClientFuture<T_GOAL, T_FEEDBACK, T_RESULT>> ownerReference;

        private ActionClientFutureListener(final ActionClientFuture<T_GOAL, T_FEEDBACK, T_RESULT> owner) {
            this.ownerReference = new WeakReference<>(owner);
        }

        @Override
        public final void feedbackReceived(final T_FEEDBACK t_feedback) {
            final ActionClientFuture<T_GOAL, T_FEEDBACK, T_RESULT> owner = this.ownerReference.get();
            if (owner == null) {
                return;
            }
            final ActionFeedback<T_FEEDBACK> actionFeedback = new ActionFeedback<>(t_feedback);
            if (ActionClientFuture.LOGGER.isTraceEnabled()) {
                ActionClientFuture.LOGGER.trace("Received feedback for goalId: {}", actionFeedback.getGoalStatusMessage().getGoalId().getId());
            }
            if (actionFeedback.getGoalStatusMessage().getGoalId().getId().equals(owner.goalid.getId())) {
                owner.latestFeedback = t_feedback;
            }
        }

        @Override
        public final void resultReceived(final T_RESULT t_result) {
            final ActionClientFuture<T_GOAL, T_FEEDBACK, T_RESULT> owner = this.ownerReference.get();
            if (owner == null) {
                return;
            }
            final ActionResult<T_RESULT> resultWrapper = new ActionResult<>(t_result);
            final String goalId = resultWrapper.getGoalStatusMessage().getGoalId().getId();
            if (ActionClientFuture.LOGGER.isDebugEnabled()) {
                ActionClientFuture.LOGGER.debug("Received result for goalId: {}", goalId);
            }
            if (goalId.equals(owner.goalid.getId())) {
                owner.completeWithResult(t_result);
            } else {
                if (ActionClientFuture.LOGGER.isErrorEnabled()) {
                    ActionClientFuture.LOGGER.error("Result with Unknown id:{}, waiting for goalId:{}", goalId, owner.goalid.getId());
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
            LOGGER.warn("current goal STATE:{}={}", actionClient.getGoalState(), actionClient.getGoalState().getValue());
        }
        actionClient.addActionClientFeedbackListener((ActionClientFeedbackListener<T_FEEDBACK>) actionClientFuture.actionClientFutureListener);
        actionClient.addActionClientResultListener((ActionClientResultListener<T_RESULT>) actionClientFuture.actionClientFutureListener);
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
        this.actionClientFutureListener = new ActionClientFutureListener<>(this);
        this.cleanable = CLEANER.register(this, new ListenerCleanup<>(actionClient, this.actionClientFutureListener));
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
        if (this.terminalState != null) {
            return this.terminalState;
        }
        return this.actionClient.getGoalState();
    }

    /**
     * @param mayInterruptIfRunning is currently ignored
     * @return returns true if cancel request was sent, false if not sent
     */
    @Override
    public final boolean cancel(final boolean mayInterruptIfRunning) {
        this.cancelRequested = this.actionClient.sendCancelInternal(this.goalid);
        return this.cancelRequested;
    }

    /**
     * @return
     */
    @Override
    public final boolean isCancelled() {
        return this.cancelRequested && this.result == null && this.terminalState == null;
    }

    /**
     * @return
     */
    @Override
    public final boolean isDone() {
        return this.countDownLatch.getCount() == 0L;
    }

    /**
     * @return
     * @throws InterruptedException
     * @throws ExecutionException
     */
    @Override
    public final T_RESULT get() throws InterruptedException, ExecutionException {
        this.countDownLatch.await();
        return result;
    }

    @Override
    public final T_RESULT get(final long timeout, final TimeUnit timeUnit) throws InterruptedException, ExecutionException, TimeoutException {
        final boolean completed = this.countDownLatch.await(timeout, timeUnit);
        if (!completed) {
            throw new TimeoutException("Timed out waiting for result for goalId: " + this.goalid.getId());
        }
        return this.result;
    }


    /**
     *
     */
    private final void disconnect() {
        this.cleanable.clean();
    }

    private void completeWithResult(final T_RESULT t_result) {
        this.result = t_result;
        this.terminalState = ClientState.DONE;
        this.disconnect();
        this.countDownLatch.countDown();
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
