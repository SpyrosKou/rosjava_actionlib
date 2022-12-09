package com.github.ekumen.rosjava_actionlib;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Vector;

import static org.junit.Assert.*;


public class TestClientStateMachine {
  private ClientStateMachine subject;


  // Executes before each test.
  @Before
  public void setUp() {
    subject = new ClientStateMachine();
  }

  @Test
  public void testGetState() {
    int expectedState = ActionLibClientStates.WAITING_FOR_GOAL_ACK;
    int actualState;
    subject.setState(expectedState);
    actualState = subject.getState();
    assertEquals(expectedState, actualState);
  }

  @Test
  public void testSetState() {
    int expectedState = ActionLibClientStates.WAITING_FOR_GOAL_ACK;
    assertEquals(subject.getState(), 0);
    subject.setState(expectedState);
    assertEquals(expectedState, subject.getState());
  }

  @Test
  public void testUpdateStatusWhenStateIsNotDone() {
    int expectedStatus = 7;
    subject.setState(ActionLibClientStates.WAITING_FOR_GOAL_ACK);
    assertEquals(0, subject.getLatestGoalStatus());
    subject.updateStatus(expectedStatus);
    assertEquals(expectedStatus, subject.getLatestGoalStatus());
  }

  @Test
  public void testUpdateStatusWhenStateIsDone() {
    subject.setState(ActionLibClientStates.DONE);
    assertEquals(0, subject.getLatestGoalStatus());
    subject.updateStatus(7);
    assertEquals(0, subject.getLatestGoalStatus());
  }

  @Test
  public void testCancelOnCancellableStates() {
    checkCancelOnInitialCancellableState(ActionLibClientStates.WAITING_FOR_GOAL_ACK);
    checkCancelOnInitialCancellableState(ActionLibClientStates.PENDING);
    checkCancelOnInitialCancellableState(ActionLibClientStates.ACTIVE);
  }

  @Test
  public void testCancelOnNonCancellableStates() {
    checkCancelOnInitialNonCancellableState(ActionLibClientStates.INVALID_TRANSITION);
    checkCancelOnInitialNonCancellableState(ActionLibClientStates.NO_TRANSITION);
    checkCancelOnInitialNonCancellableState(ActionLibClientStates.WAITING_FOR_RESULT);
    checkCancelOnInitialNonCancellableState(ActionLibClientStates.WAITING_FOR_CANCEL_ACK);
    checkCancelOnInitialNonCancellableState(ActionLibClientStates.RECALLING);
    checkCancelOnInitialNonCancellableState(ActionLibClientStates.PREEMPTING);
    checkCancelOnInitialNonCancellableState(ActionLibClientStates.DONE);
    checkCancelOnInitialNonCancellableState(ActionLibClientStates.LOST);
  }

  private void checkCancelOnInitialCancellableState(int initialState) {
    subject.setState(initialState);
    assertTrue("Failed test on initial state " + initialState, subject.cancel());
    assertEquals("Failed test on initial state " + initialState, ActionLibClientStates.WAITING_FOR_CANCEL_ACK, subject.getState());
  }


  private void checkCancelOnInitialNonCancellableState(int initialState) {
    subject.setState(initialState);
    assertFalse("Failed test on initial state " + initialState, subject.cancel());
    assertEquals("Failed test on initial state " + initialState, initialState, subject.getState());
  }

  @Test
  public void testResultReceivedWhileWaitingForResult() {
    subject.setState(ActionLibClientStates.WAITING_FOR_RESULT);
    subject.resultReceived();
    assertEquals(ActionLibClientStates.DONE, subject.getState());
  }

  @Test
  public void testResultReceivedWhileNotWaitingForResult() {
    checkResultReceivedWhileNotWaitingForResult(ActionLibClientStates.INVALID_TRANSITION);
    checkResultReceivedWhileNotWaitingForResult(ActionLibClientStates.NO_TRANSITION);
    checkResultReceivedWhileNotWaitingForResult(ActionLibClientStates.WAITING_FOR_GOAL_ACK);
    checkResultReceivedWhileNotWaitingForResult(ActionLibClientStates.PENDING);
    checkResultReceivedWhileNotWaitingForResult(ActionLibClientStates.ACTIVE);
    checkResultReceivedWhileNotWaitingForResult(ActionLibClientStates.WAITING_FOR_CANCEL_ACK);
    checkResultReceivedWhileNotWaitingForResult(ActionLibClientStates.RECALLING);
    checkResultReceivedWhileNotWaitingForResult(ActionLibClientStates.PREEMPTING);
    checkResultReceivedWhileNotWaitingForResult(ActionLibClientStates.DONE);
    checkResultReceivedWhileNotWaitingForResult(ActionLibClientStates.LOST);
  }

  private void checkResultReceivedWhileNotWaitingForResult(int state) {
    subject.setState(state);
    subject.resultReceived();
    assertEquals("Failed test on initial state " + state, state, subject.getState());
  }

  @Test
  public void testGetTransition() {
    Vector<Integer> expectedResult;
    expectedResult = new Vector<>(Arrays.asList(ActionLibClientStates.PENDING));
    checkGetTransition(ActionLibClientStates.WAITING_FOR_GOAL_ACK,
      actionlib_msgs.GoalStatus.PENDING, expectedResult);

    expectedResult = new Vector<>(Arrays.asList(ActionLibClientStates.PENDING,
      ActionLibClientStates.WAITING_FOR_RESULT));
    checkGetTransition(ActionLibClientStates.WAITING_FOR_GOAL_ACK,
      actionlib_msgs.GoalStatus.REJECTED, expectedResult);
  }

  private void checkGetTransition(int initialState, int goalStatus, Vector<Integer> expected) {
    subject.setState(initialState);
    Vector<Integer> output = subject.getTransition(goalStatus);
    assertEquals(expected, output);
  }
}
