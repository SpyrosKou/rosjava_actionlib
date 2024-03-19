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

    public Header getHeaderMessage() {
        Header header = null;
        if (this.actionResultMessage != null) {
            header = ActionLibMessagesUtils.getSubMessageFromMessage(this.actionResultMessage, "getHeader");
        }
        return header;
    }

    public GoalStatus getGoalStatusMessage() {
        GoalStatus goalStatus = null;
        if (this.actionResultMessage != null) {
            goalStatus = ActionLibMessagesUtils.getSubMessageFromMessage(this.actionResultMessage, "getStatus");
        }
        return goalStatus;
    }

    public Message getResultMessage() {
        Message resultMessage= null;
        if (this.actionResultMessage != null) {
            resultMessage = ActionLibMessagesUtils.getSubMessageFromMessage(this.actionResultMessage, "getResult");
        }
        return resultMessage;
    }
}
