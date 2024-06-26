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

import actionlib_msgs.GoalID;
import org.ros.internal.message.Message;

import java.util.Optional;


/**
 * Listener interface to receive the incoming messages from the ActionLib client.
 * A server should implement this interface if it wants to receive the callbacks
 * with information from the client.
 *
 * @author Ernesto Corbellini ecorbellini@ekumenlabs.com
 */
public interface ActionServerListener<T_ACTION_GOAL extends Message> {
    /**
     * This callback is called when a message is received on the goal topic.
     * Note: this method is called right after the server starts tracking this
     * goal and is intended for informative purposes. This method is called for every goal received,
     * regardless if the goal is accepted or not.
     *
     * @param goal the action goal received.
     */
    void goalReceived(T_ACTION_GOAL goal);

    /**
     * This callback is called when a message is received on the cancel topic.
     *
     * @param id Goal ID object that was received.
     */
    void cancelReceived(GoalID id);

    /**
     * Callback method to accept a recently received action goal.
     * This method can be also used to trigger that depend on accepting or rejecting the goal.
     *
     * @param goal The action goal received.
     * @return The implementer must return true if he accepts the goal or false. If it is empty, it means that the implementer will manually handle the acceptance or rejection.
     * otherwise.
     */
    Optional<Boolean> acceptGoal(T_ACTION_GOAL goal);
}
