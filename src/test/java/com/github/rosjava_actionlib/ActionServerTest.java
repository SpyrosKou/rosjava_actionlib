/**
 * Copyright 2019 Spyros Koukas
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
