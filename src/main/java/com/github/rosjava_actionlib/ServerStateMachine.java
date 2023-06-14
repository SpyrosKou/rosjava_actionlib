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

package com.github.rosjava_actionlib;

import actionlib_msgs.GoalStatus;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

/*
 * Class to manage the server state machine transitions.
 *
 * @author Ernesto Corbellini ecorbellini@ekumenlabs.com
 * @author Spyros Koukas
 */
final class ServerStateMachine {
    private static final boolean THROW_EXCEPTION_ON_TRANSITION_ERROR = false;

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    /**
     * Events that trigger transitions
     */
    public static final class Events {
        public final static int CANCEL_REQUEST = 1;
        public final static int CANCEL = 2;
        public final static int REJECT = 3;
        public final static int ACCEPT = 4;
        public final static int SUCCEED = 5;
        public final static int ABORT = 6;

        /**
         * used for logging
         *
         * @param event
         *
         * @return
         */
        public final static String eventToString(final int event) {
            switch (event) {
                case 1:
                    return "CANCEL_REQUEST(1)";
                case 2:
                    return "CANCEL(2)";
                case 3:
                    return "REJECT(3)";
                case 4:
                    return "ACCEPT(4)";
                case 5:
                    return "SUCCEED(5)";
                case 6:
                    return "ABORT(6)";
                default:
                    return "UNKNOWN_STATE(" + event + ")";
            }
        }
    }

    private byte state;

    ServerStateMachine() {
        // Initial state
        this.state = GoalStatus.PENDING;
    }

    /**
     * @return
     */
    public final synchronized byte getState() {
        return this.state;
    }

    public final synchronized void setState(final byte newState) {
        this.state = newState;
    }

    /**
     * @return
     */
    private static final void handleTransitionException(final int event, final byte initialState, final byte nextState) {
        final IllegalStateException illegalStateException = new IllegalStateException(ServerStateMachine.class.getSimpleName()
                + "Invalid transition from state:[" + ActionLibMessagesUtils.goalStatusToString(initialState) + "(" + initialState + ")] to state:[" + ActionLibMessagesUtils.goalStatusToString(nextState) + "(" + nextState + ")] on event:[" + Events.eventToString(event) + "]!");
        LOGGER.error(ExceptionUtils.getStackTrace(illegalStateException));
        if (THROW_EXCEPTION_ON_TRANSITION_ERROR) {
            throw illegalStateException;
        }
    }

    /**
     * @param event
     *
     * @return
     */
    public final synchronized int transition(final int event) {
        {
            byte nextState = this.state;
            switch (this.state) {
                case GoalStatus.PENDING:
                    switch (event) {
                        case Events.REJECT:
                            nextState = GoalStatus.REJECTED;
                            break;
                        case Events.CANCEL_REQUEST:
                            nextState = GoalStatus.RECALLING;
                            break;
                        case Events.ACCEPT:
                            nextState = GoalStatus.ACTIVE;
                            break;
                        default:
                            handleTransitionException(event, this.state, nextState);

                    }
                    break;
                case GoalStatus.RECALLING:
                    switch (event) {
                        case Events.REJECT:
                            nextState = GoalStatus.REJECTED;
                            break;
                        case Events.CANCEL:
                            nextState = GoalStatus.RECALLED;
                            break;
                        case Events.ACCEPT:
                            nextState = GoalStatus.PREEMPTING;
                            break;
                        default:
                            handleTransitionException(event, this.state, nextState);
                    }
                    break;
                case GoalStatus.ACTIVE:
                    switch (event) {
                        case Events.SUCCEED:
                            nextState = GoalStatus.SUCCEEDED;
                            break;
                        case Events.CANCEL_REQUEST:
                            nextState = GoalStatus.PREEMPTING;
                            break;
                        case Events.ABORT:
                            nextState = GoalStatus.ABORTED;
                            break;
                        default:
                            handleTransitionException(event, this.state, nextState);
                    }
                    break;
                case GoalStatus.PREEMPTING:
                    switch (event) {
                        case Events.SUCCEED:
                            nextState = GoalStatus.SUCCEEDED;
                            break;
                        case Events.CANCEL:
                            nextState = GoalStatus.PREEMPTED;
                            break;
                        case Events.ABORT:
                            nextState = GoalStatus.ABORTED;
                            break;
                        default:
                            handleTransitionException(event, this.state, nextState);
                    }
                    break;
                case GoalStatus.REJECTED:
                    break;
                case GoalStatus.RECALLED:
                    break;
                case GoalStatus.PREEMPTED:
                    break;
                case GoalStatus.SUCCEEDED:
                    break;
                case GoalStatus.ABORTED:
                    break;
                default:
                    throw new IllegalStateException(ServerStateMachine.class.getSimpleName() + " Error: Invalid internal " + GoalStatus.class.getSimpleName() + " state=[" + this.state + "]!");
            }
            // transition to the next state
            this.state = nextState;
        }
        return this.state;
    }
}
