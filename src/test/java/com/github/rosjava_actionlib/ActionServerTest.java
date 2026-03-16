package com.github.rosjava_actionlib;

import actionlib_msgs.GoalStatus;
import org.junit.Assert;
import org.junit.Test;

public class ActionServerTest {

    @Test
    public final void terminalStatusesAreMarkedForEviction() {
        Assert.assertTrue(ActionServer.isTerminalStatus(GoalStatus.REJECTED));
        Assert.assertTrue(ActionServer.isTerminalStatus(GoalStatus.RECALLED));
        Assert.assertTrue(ActionServer.isTerminalStatus(GoalStatus.PREEMPTED));
        Assert.assertTrue(ActionServer.isTerminalStatus(GoalStatus.SUCCEEDED));
        Assert.assertTrue(ActionServer.isTerminalStatus(GoalStatus.ABORTED));
    }

    @Test
    public final void nonTerminalStatusesAreNotMarkedForEviction() {
        Assert.assertFalse(ActionServer.isTerminalStatus(GoalStatus.PENDING));
        Assert.assertFalse(ActionServer.isTerminalStatus(GoalStatus.ACTIVE));
        Assert.assertFalse(ActionServer.isTerminalStatus(GoalStatus.RECALLING));
        Assert.assertFalse(ActionServer.isTerminalStatus(GoalStatus.PREEMPTING));
        Assert.assertFalse(ActionServer.isTerminalStatus(GoalStatus.LOST));
    }
}
