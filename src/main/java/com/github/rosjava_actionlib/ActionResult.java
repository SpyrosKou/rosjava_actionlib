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

import actionlib_msgs.GoalStatus;
import org.ros.internal.message.Message;
import std_msgs.Header;

/**
 * Class to encapsulate the action feedback object.
 *
 * @author Ernesto Corbellini ecorbellini@ekumenlabs.com
 */
public final class ActionResult<T_ACTION_RESULT extends Message> {
    private T_ACTION_RESULT actionResultMessage = null;

    public ActionResult(final T_ACTION_RESULT msg) {
        this.actionResultMessage = msg;
    }

    public final Header getHeaderMessage() {
        final Header header;
        if (this.actionResultMessage != null) {
            header = ActionLibMessagesUtils.getSubMessageFromMessage(this.actionResultMessage, "getHeader");
        } else {
            header = null;
        }
        return header;
    }

    public final GoalStatus getGoalStatusMessage() {
        final GoalStatus goalStatus;
        if (this.actionResultMessage != null) {
            goalStatus = ActionLibMessagesUtils.getSubMessageFromMessage(this.actionResultMessage, "getStatus");
        } else {
            goalStatus = null;
        }
        return goalStatus;
    }

    public final Message getResultMessage() {
        final Message resultMessage;
        if (this.actionResultMessage != null) {
            resultMessage = ActionLibMessagesUtils.getSubMessageFromMessage(this.actionResultMessage, "getResult");
        } else {
            resultMessage = null;
        }
        return resultMessage;
    }
}
