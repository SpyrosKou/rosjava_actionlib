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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.stream.Collectors;


/**
 * State machine for the action client.
 *
 * @author Ernesto Corbellini ecorbellini@ekumenlabs.com
 * @author Spyros Koukas
 */
final class ClientStateMachine {
    private static final Set<ClientState> CANCELABLE_STATES = Set.of(ClientState.WAITING_FOR_GOAL_ACK, ClientState.PENDING, ClientState.ACTIVE);

    private ClientState latestGoalStatus = null;
    private ClientState state = ClientState.UNKNOWN_STATE;
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    /**
     * A ClientStateMachine should always have an existing starting state.
     *
     * @param initialState
     */
    ClientStateMachine(final ClientState initialState) {
        this.resetToState(initialState);

    }

    /**
     * Completely resets this {@link ClientStateMachine} to the specified starting state.
     *
     * @param initialState
     */
    final synchronized void resetToState(final ClientState initialState) {
        Objects.requireNonNull(initialState);
        this.state = initialState;
        this.latestGoalStatus = null;
    }


    /**
     * @param state
     */
    final synchronized void setState(final ClientState state) {
        Objects.requireNonNull(state);
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("ClientStateMachine - State changed from " + this.state + " to " + state);
        }
        this.state = state;
    }

    final synchronized ClientState getState() {
        return this.state;
    }

    /**
     * Update the state of the client based on the current state and the goal state.
     *
     * @param status
     */
    final synchronized void updateStatus(final ClientState status) {
        if (this.state != ClientState.DONE) {
            this.latestGoalStatus = status;
        }
    }

    /**
     * Update the state of the client upon the received status of the goal.
     *
     * @param goalStatus Status of the goal.
     */
    final synchronized void transition(final int goalStatus) {

        // transition to next states
        final List<ClientState> nextStates = this.getTransition(goalStatus);

        if (!nextStates.isEmpty()) {
            if (nextStates.size() == 1 && nextStates.contains(this.state)) {
                if (this.LOGGER.isTraceEnabled()) {
                    this.LOGGER.trace("Maintaining ClientState:" + this.state + " for GoalStatus:" + goalStatus);
                }
            } else {
                for (int i = 0; i < nextStates.size(); i++) {
                    final ClientState state = nextStates.get(i);
                    if (this.LOGGER.isTraceEnabled()) {
                        this.LOGGER.trace("Transition" + (i + 1) + " of " + nextStates.size() + " from ClientState:" + this.state + " to ClientState: " + state + " on GoalStatus:" + goalStatus);
                    }

                    this.state = state;
                }
            }
        }
    }

    /**
     * Get the next state transition depending on the current client state and the
     * goal state.
     *
     * @param goalStatus The current status of the tracked goal.
     * @return A list with the list of next states. The states should be
     * transitioned in order. This is necessary because if we loose a state update
     * we might still be able to infer the actual transition history that took us
     * to the final goal state.
     */
    final List<ClientState> getTransitionInteger(int goalStatus) {
        return this.getTransition(goalStatus).stream().collect(Collectors.toList());
    }

    /**
     * @param goalStatus
     * @return
     */
    private final List<ClientState> getTransition(int goalStatus) {
        final List<ClientState> stateList = new ArrayList<>();

        switch (this.state) {
            case WAITING_FOR_GOAL_ACK:
                switch (goalStatus) {
                    case actionlib_msgs.GoalStatus.PENDING:
                        stateList.add(ClientState.PENDING);
                        break;
                    case actionlib_msgs.GoalStatus.ACTIVE:
                        stateList.add(ClientState.ACTIVE);
                        break;
                    case actionlib_msgs.GoalStatus.REJECTED:
                        stateList.add(ClientState.PENDING);
                        stateList.add(ClientState.WAITING_FOR_RESULT);
                        break;
                    case actionlib_msgs.GoalStatus.RECALLING:
                        stateList.add(ClientState.PENDING);
                        stateList.add(ClientState.RECALLING);
                        break;
                    case actionlib_msgs.GoalStatus.RECALLED:
                        stateList.add(ClientState.PENDING);
                        stateList.add(ClientState.WAITING_FOR_RESULT);
                        break;
                    case actionlib_msgs.GoalStatus.PREEMPTED:
                        stateList.add(ClientState.ACTIVE);
                        stateList.add(ClientState.PREEMPTING);
                        stateList.add(ClientState.WAITING_FOR_RESULT);
                        break;
                    case actionlib_msgs.GoalStatus.SUCCEEDED:
                        stateList.add(ClientState.ACTIVE);
                        stateList.add(ClientState.WAITING_FOR_RESULT);
                        break;
                    case actionlib_msgs.GoalStatus.ABORTED:
                        stateList.add(ClientState.ACTIVE);
                        stateList.add(ClientState.WAITING_FOR_RESULT);
                        break;
                    case actionlib_msgs.GoalStatus.PREEMPTING:
                        stateList.add(ClientState.ACTIVE);
                        stateList.add(ClientState.PREEMPTING);
                        break;
                }
                break;
            case PENDING:
                switch (goalStatus) {
                    case actionlib_msgs.GoalStatus.PENDING:
                        // no transition
                        break;
                    case actionlib_msgs.GoalStatus.ACTIVE:
                        stateList.add(ClientState.ACTIVE);
                        break;
                    case actionlib_msgs.GoalStatus.REJECTED:
                        stateList.add(ClientState.WAITING_FOR_RESULT);
                        break;
                    case actionlib_msgs.GoalStatus.RECALLING:
                        stateList.add(ClientState.RECALLING);
                        break;
                    case actionlib_msgs.GoalStatus.RECALLED:
                        stateList.add(ClientState.RECALLING);
                        stateList.add(ClientState.WAITING_FOR_RESULT);
                        break;
                    case actionlib_msgs.GoalStatus.PREEMPTED:
                        stateList.add(ClientState.ACTIVE);
                        stateList.add(ClientState.PREEMPTING);
                        stateList.add(ClientState.WAITING_FOR_RESULT);
                        break;
                    case actionlib_msgs.GoalStatus.SUCCEEDED:
                        stateList.add(ClientState.ACTIVE);
                        stateList.add(ClientState.WAITING_FOR_RESULT);
                        break;
                    case actionlib_msgs.GoalStatus.ABORTED:
                        stateList.add(ClientState.ACTIVE);
                        stateList.add(ClientState.WAITING_FOR_RESULT);
                        break;
                    case actionlib_msgs.GoalStatus.PREEMPTING:
                        stateList.add(ClientState.ACTIVE);
                        stateList.add(ClientState.PREEMPTING);
                        break;
                }
                break;
            case ACTIVE:
                switch (goalStatus) {
                    case actionlib_msgs.GoalStatus.PENDING:
                        stateList.add(ClientState.INVALID_TRANSITION);
                        break;
                    case actionlib_msgs.GoalStatus.ACTIVE:
                        // no transition
                        break;
                    case actionlib_msgs.GoalStatus.REJECTED:
                        stateList.add(ClientState.INVALID_TRANSITION);
                        break;
                    case actionlib_msgs.GoalStatus.RECALLING:
                        stateList.add(ClientState.INVALID_TRANSITION);
                        break;
                    case actionlib_msgs.GoalStatus.RECALLED:
                        stateList.add(ClientState.INVALID_TRANSITION);
                        break;
                    case actionlib_msgs.GoalStatus.PREEMPTED:
                        stateList.add(ClientState.PREEMPTING);
                        stateList.add(ClientState.WAITING_FOR_RESULT);
                        break;
                    case actionlib_msgs.GoalStatus.SUCCEEDED:
                        stateList.add(ClientState.WAITING_FOR_RESULT);
                        break;
                    case actionlib_msgs.GoalStatus.ABORTED:
                        stateList.add(ClientState.WAITING_FOR_RESULT);
                        break;
                    case actionlib_msgs.GoalStatus.PREEMPTING:
                        stateList.add(ClientState.PREEMPTING);
                        break;
                }
                break;
            case WAITING_FOR_RESULT:
                switch (goalStatus) {
                    case actionlib_msgs.GoalStatus.PENDING:
                        stateList.add(ClientState.INVALID_TRANSITION);
                        break;
                    case actionlib_msgs.GoalStatus.ACTIVE:
                        // no transition
                        break;
                    case actionlib_msgs.GoalStatus.REJECTED:
                        // no transition
                        break;
                    case actionlib_msgs.GoalStatus.RECALLING:
                        stateList.add(ClientState.INVALID_TRANSITION);
                        break;
                    case actionlib_msgs.GoalStatus.RECALLED:
                        // no transition
                        break;
                    case actionlib_msgs.GoalStatus.PREEMPTED:
                        // no transition
                        break;
                    case actionlib_msgs.GoalStatus.SUCCEEDED:
                        // no transition
                        break;
                    case actionlib_msgs.GoalStatus.ABORTED:
                        // no transition
                        break;
                    case actionlib_msgs.GoalStatus.PREEMPTING:
                        stateList.add(ClientState.INVALID_TRANSITION);
                        break;
                }
                break;
            case WAITING_FOR_CANCEL_ACK:
                switch (goalStatus) {
                    case actionlib_msgs.GoalStatus.PENDING:
                        // no transition
                        break;
                    case actionlib_msgs.GoalStatus.ACTIVE:
                        // no transition
                        break;
                    case actionlib_msgs.GoalStatus.REJECTED:
                        stateList.add(ClientState.WAITING_FOR_RESULT);
                        break;
                    case actionlib_msgs.GoalStatus.RECALLING:
                        stateList.add(ClientState.RECALLING);
                        break;
                    case actionlib_msgs.GoalStatus.RECALLED:
                        stateList.add(ClientState.RECALLING);
                        stateList.add(ClientState.WAITING_FOR_RESULT);
                        break;
                    case actionlib_msgs.GoalStatus.PREEMPTED:
                        stateList.add(ClientState.PREEMPTING);
                        stateList.add(ClientState.WAITING_FOR_RESULT);
                        break;
                    case actionlib_msgs.GoalStatus.SUCCEEDED:
                        stateList.add(ClientState.PREEMPTING);
                        stateList.add(ClientState.WAITING_FOR_RESULT);
                        break;
                    case actionlib_msgs.GoalStatus.ABORTED:
                        stateList.add(ClientState.PREEMPTING);
                        stateList.add(ClientState.WAITING_FOR_RESULT);
                        break;
                    case actionlib_msgs.GoalStatus.PREEMPTING:
                        stateList.add(ClientState.PREEMPTING);
                        break;
                }
                break;
            case RECALLING:
                switch (goalStatus) {
                    case actionlib_msgs.GoalStatus.PENDING:
                        // no transition
                        break;
                    case actionlib_msgs.GoalStatus.ACTIVE:
                        // no transition
                        break;
                    case actionlib_msgs.GoalStatus.REJECTED:
                        stateList.add(ClientState.WAITING_FOR_RESULT);
                        break;
                    case actionlib_msgs.GoalStatus.RECALLING:
                        stateList.add(ClientState.RECALLING);
                        break;
                    case actionlib_msgs.GoalStatus.RECALLED:
                        stateList.add(ClientState.RECALLING);
                        stateList.add(ClientState.WAITING_FOR_RESULT);
                        break;
                    case actionlib_msgs.GoalStatus.PREEMPTED:
                        stateList.add(ClientState.PREEMPTING);
                        stateList.add(ClientState.WAITING_FOR_RESULT);
                        break;
                    case actionlib_msgs.GoalStatus.SUCCEEDED:
                        stateList.add(ClientState.PREEMPTING);
                        stateList.add(ClientState.WAITING_FOR_RESULT);
                        break;
                    case actionlib_msgs.GoalStatus.ABORTED:
                        stateList.add(ClientState.PREEMPTING);
                        stateList.add(ClientState.WAITING_FOR_RESULT);
                        break;
                    case actionlib_msgs.GoalStatus.PREEMPTING:
                        stateList.add(ClientState.PREEMPTING);
                        break;
                }
                break;
            case PREEMPTING:
                switch (goalStatus) {
                    case actionlib_msgs.GoalStatus.PENDING:
                        stateList.add(ClientState.INVALID_TRANSITION);
                        break;
                    case actionlib_msgs.GoalStatus.ACTIVE:
                        stateList.add(ClientState.INVALID_TRANSITION);
                        break;
                    case actionlib_msgs.GoalStatus.REJECTED:
                        stateList.add(ClientState.INVALID_TRANSITION);
                        break;
                    case actionlib_msgs.GoalStatus.RECALLING:
                        stateList.add(ClientState.INVALID_TRANSITION);
                        break;
                    case actionlib_msgs.GoalStatus.RECALLED:
                        stateList.add(ClientState.INVALID_TRANSITION);
                        break;
                    case actionlib_msgs.GoalStatus.PREEMPTED:
                        stateList.add(ClientState.WAITING_FOR_RESULT);
                        break;
                    case actionlib_msgs.GoalStatus.SUCCEEDED:
                        stateList.add(ClientState.WAITING_FOR_RESULT);
                        break;
                    case actionlib_msgs.GoalStatus.ABORTED:
                        stateList.add(ClientState.WAITING_FOR_RESULT);
                        break;
                    case actionlib_msgs.GoalStatus.PREEMPTING:
                        // no transition
                        break;
                }
                break;
            case DONE:
                switch (goalStatus) {
                    case actionlib_msgs.GoalStatus.PENDING:
                        stateList.add(ClientState.INVALID_TRANSITION);
                        break;
                    case actionlib_msgs.GoalStatus.ACTIVE:
                        stateList.add(ClientState.INVALID_TRANSITION);
                        break;
                    case actionlib_msgs.GoalStatus.REJECTED:
                        // no transition
                        break;
                    case actionlib_msgs.GoalStatus.RECALLING:
                        stateList.add(ClientState.INVALID_TRANSITION);
                        break;
                    case actionlib_msgs.GoalStatus.RECALLED:
                        // no transition
                        break;
                    case actionlib_msgs.GoalStatus.PREEMPTED:
                        // no transition
                        break;
                    case actionlib_msgs.GoalStatus.SUCCEEDED:
                        // no transition
                        break;
                    case actionlib_msgs.GoalStatus.ABORTED:
                        // no transition
                        break;
                    case actionlib_msgs.GoalStatus.PREEMPTING:
                        stateList.add(ClientState.INVALID_TRANSITION);
                        break;
                }
                break;
        }
        return stateList;
    }

    /**
     * Cancel action goal. The goal can only be cancelled if its in certain
     * states. If it can be cancelled the state will be changed to
     * WAITING_FOR_CANCEL_ACK.
     *
     * @return True if the goal can be cancelled, false otherwise.
     */
    final boolean cancel() {
        final boolean shouldCancel = CANCELABLE_STATES.contains(state);

        if (shouldCancel) {
            this.state = ClientState.WAITING_FOR_CANCEL_ACK;
        }
        return shouldCancel;
    }

    /**
     * Signal that the result has been received.
     * <p>
     * If this {@link ClientStateMachine} is in a {@link ClientState#WAITING_FOR_RESULT}
     * then its next state will be a{@link ClientState#DONE} state.
     * <p>
     * If this {@link ClientStateMachine} is not in a {@link ClientState#WAITING_FOR_RESULT}
     * then its next state will be a {@link ClientState#NO_GOAL} state.
     */
    final void resultReceived() {
        if (this.state == ClientState.WAITING_FOR_RESULT) {
            this.state = ClientState.DONE;
        } else {
            this.state = ClientState.NO_GOAL;
        }
    }


    /**
     * @return the
     */
    final boolean isRunning() {
        return this.state.isRunning();
    }

    /**
     * @return
     */
    final ClientState getLatestGoalStatus() {
        return this.latestGoalStatus;
    }
}
