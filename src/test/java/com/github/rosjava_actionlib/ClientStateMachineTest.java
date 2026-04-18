/**
 * Copyright 2015 Ekumen www.ekumenlabs.com
 * Copyright 2023 Spyros Koukas
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.*;
import static org.junit.jupiter.api.Assertions.assertNotEquals;


/**
 * Tests state machine
 */
public class ClientStateMachineTest {
    private ClientStateMachine clientStateMachine;
    private static final ClientState INITIAL_CLIENT_STATE=ClientState.NO_GOAL;
    // Executes before each test.
    @BeforeEach
    public void beforeEach() {
        clientStateMachine = new ClientStateMachine(ClientState.NO_GOAL);
    }

    @Test
    public void testGetState() {
        ClientState expectedState = ClientState.WAITING_FOR_GOAL_ACK;
        ClientState actualState;
        clientStateMachine.setState(expectedState);
        actualState = clientStateMachine.getState();
        assertEquals(expectedState, actualState);
    }

    @Test
    public void testSetState() {
        assertEquals(clientStateMachine.getState(), INITIAL_CLIENT_STATE);
        ClientState expectedState = ClientState.WAITING_FOR_GOAL_ACK;
        assertNotEquals(INITIAL_CLIENT_STATE, ClientState.WAITING_FOR_GOAL_ACK);
        clientStateMachine.setState(expectedState);
        assertEquals(expectedState, clientStateMachine.getState());
    }

    @Test
    public void testUpdateStatusWhenStateIsNotDoneAndStatusIsWaitingForGoalAck() {
        clientStateMachine.setState(ClientState.WAITING_FOR_GOAL_ACK);
        clientStateMachine.updateStatus(ClientState.WAITING_FOR_GOAL_ACK);
        assertEquals(ClientState.WAITING_FOR_GOAL_ACK, clientStateMachine.getLatestGoalStatus());
        clientStateMachine.updateStatus(ClientState.DONE);
        assertEquals(ClientState.DONE, clientStateMachine.getLatestGoalStatus());
    }


    @Test
    public void testUpdateStatusWhenStateIsDoneAndStatusIsWaitingForGoalAck() {
        clientStateMachine.updateStatus(ClientState.WAITING_FOR_GOAL_ACK);
        clientStateMachine.setState(ClientState.DONE);
        assertEquals(ClientState.WAITING_FOR_GOAL_ACK, clientStateMachine.getLatestGoalStatus());
        clientStateMachine.updateStatus(ClientState.DONE);
        assertEquals(ClientState.WAITING_FOR_GOAL_ACK, clientStateMachine.getLatestGoalStatus());
    }

    @Test
    public void testCancelOnCancellableStates() {
        checkCancelOnInitialCancellableState(ClientState.WAITING_FOR_GOAL_ACK);
        checkCancelOnInitialCancellableState(ClientState.PENDING);
        checkCancelOnInitialCancellableState(ClientState.ACTIVE);
    }

    @Test
    public void testCancelOnNonCancellableStates() {
        checkCancelOnInitialNonCancellableState(ClientState.INVALID_TRANSITION);
        checkCancelOnInitialNonCancellableState(ClientState.NO_TRANSITION);
        checkCancelOnInitialNonCancellableState(ClientState.WAITING_FOR_RESULT);
        checkCancelOnInitialNonCancellableState(ClientState.WAITING_FOR_CANCEL_ACK);
        checkCancelOnInitialNonCancellableState(ClientState.RECALLING);
        checkCancelOnInitialNonCancellableState(ClientState.PREEMPTING);
        checkCancelOnInitialNonCancellableState(ClientState.DONE);
        checkCancelOnInitialNonCancellableState(ClientState.LOST);
    }

    private void checkCancelOnInitialCancellableState(ClientState initialState) {
        clientStateMachine.setState(initialState);
        assertTrue("Failed test on initial state " + initialState, clientStateMachine.cancel());
        assertEquals("Failed test on initial state " + initialState, ClientState.WAITING_FOR_CANCEL_ACK, clientStateMachine.getState());
    }


    private void checkCancelOnInitialNonCancellableState(ClientState initialState) {
        clientStateMachine.setState(initialState);
        assertFalse("Failed test on initial state " + initialState, clientStateMachine.cancel());
        assertEquals("Failed test on initial state " + initialState, initialState, clientStateMachine.getState());
    }

    @Test
    public void testResultReceivedWhileWaitingForResult() {
        clientStateMachine.setState(ClientState.WAITING_FOR_RESULT);
        clientStateMachine.resultReceived();
        assertEquals(ClientState.DONE, clientStateMachine.getState());
    }

    @Test
    public void testResultReceivedWhileNotWaitingForResult() {
        checkResultReceivedWhileNotWaitingForResult(ClientState.INVALID_TRANSITION);
        checkResultReceivedWhileNotWaitingForResult(ClientState.NO_TRANSITION);
        checkResultReceivedWhileNotWaitingForResult(ClientState.WAITING_FOR_GOAL_ACK);
        checkResultReceivedWhileNotWaitingForResult(ClientState.PENDING);
        checkResultReceivedWhileNotWaitingForResult(ClientState.ACTIVE);
        checkResultReceivedWhileNotWaitingForResult(ClientState.WAITING_FOR_CANCEL_ACK);
        checkResultReceivedWhileNotWaitingForResult(ClientState.RECALLING);
        checkResultReceivedWhileNotWaitingForResult(ClientState.PREEMPTING);
        checkResultReceivedWhileNotWaitingForResult(ClientState.DONE);
        checkResultReceivedWhileNotWaitingForResult(ClientState.LOST);
    }

    private void checkResultReceivedWhileNotWaitingForResult(ClientState state) {
        clientStateMachine.setState(state);
        clientStateMachine.resultReceived();
        assertEquals("Failed test on initial state " + state, ClientState.NO_GOAL, clientStateMachine.getState());
    }

    @Test
    public void testGetTrasition() {

        LinkedList<ClientState>  expected = new LinkedList<>(Arrays.asList(ClientState.PENDING));
        checkGetTransition(ClientState.WAITING_FOR_GOAL_ACK,actionlib_msgs.GoalStatus.PENDING, expected);

        expected = new LinkedList<>(Arrays.asList(ClientState.PENDING,ClientState.WAITING_FOR_RESULT));
        checkGetTransition(ClientState.WAITING_FOR_GOAL_ACK,actionlib_msgs.GoalStatus.REJECTED, expected);
    }

    private final void checkGetTransition(ClientState initialState, int goalStatus, List<ClientState> expected) {
        clientStateMachine.setState(initialState);
        List<ClientState> output = clientStateMachine.getTransitionInteger(goalStatus);
        assertEquals(expected, output);
    }
}
