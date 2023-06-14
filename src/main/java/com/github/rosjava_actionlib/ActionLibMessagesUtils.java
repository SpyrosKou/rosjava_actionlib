/**
 * Copyright 2020 Spyros Koukas
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
import java.lang.reflect.Method;
import java.util.Objects;

/**
 * Created at 2020-04-19
 *
 * @author Spyros Koukas
 */
final class ActionLibMessagesUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    public static final String UNKNOWN_GOAL_STATUS = "UNKNOWN GOAL STATUS";


    /**
     * Return true if the goalStatus corresponds to one of the 9 known states as presented in {@link GoalStatus}
     *
     * @param goalStatus
     *
     * @return
     */
    public static final boolean isKnownGoalStatus(final byte goalStatus) {
        final boolean result = goalStatus == GoalStatus.PENDING ||
                goalStatus == GoalStatus.ACTIVE ||
                goalStatus == GoalStatus.PREEMPTED ||
                goalStatus == GoalStatus.SUCCEEDED ||
                goalStatus == GoalStatus.ABORTED ||
                goalStatus == GoalStatus.REJECTED ||
                goalStatus == GoalStatus.PREEMPTING ||
                goalStatus == GoalStatus.RECALLING ||
                goalStatus == GoalStatus.RECALLED ||
                goalStatus == GoalStatus.LOST;

        return result;
    }

    /**
     * will return null if goalStatus is null otherwise will call  {@link ActionLibMessagesUtils#goalStatusToString(byte)} on  {@link GoalStatus#getStatus()}
     *
     * @param goalStatus
     *
     * @return
     */
    public static final String goalStatusToString(final GoalStatus goalStatus) {
        if (goalStatus != null) {
            final StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("{");
            stringBuilder.append(goalStatus);
            stringBuilder.append(",");
            stringBuilder.append("GoalId:");
            stringBuilder.append(goalStatus.getGoalId());
            stringBuilder.append(",");
            stringBuilder.append("Text:");
            stringBuilder.append(goalStatus.getText());
            stringBuilder.append(",");
            stringBuilder.append("Status:");
            stringBuilder.append(goalStatusToString(goalStatus.getStatus()));
            stringBuilder.append("(");
            stringBuilder.append(goalStatus.getStatus());
            stringBuilder.append(")");
            stringBuilder.append("}");
            return stringBuilder.toString();
        } else {
            return null;
        }
    }

    /**
     *
     */
    private static final String[] GOAL_STATUS_TO_STRING = createGoalStatusArray();

    /**
     *
     * @return
     */
    private static final String[] createGoalStatusArray() {
        final String[] goalStatusArray = new String[10];
        goalStatusArray[GoalStatus.PENDING] = "PENDING";
        goalStatusArray[GoalStatus.ACTIVE] = "ACTIVE";
        goalStatusArray[GoalStatus.PREEMPTED] = "PREEMPTED";
        goalStatusArray[GoalStatus.SUCCEEDED] = "SUCCEEDED";
        goalStatusArray[GoalStatus.ABORTED] = "ABORTED";
        goalStatusArray[GoalStatus.REJECTED] = "REJECTED";
        goalStatusArray[GoalStatus.PREEMPTING] = "PREEMPTING";
        goalStatusArray[GoalStatus.RECALLING] = "RECALLING";
        goalStatusArray[GoalStatus.RECALLED] = "RECALLED";
        goalStatusArray[GoalStatus.LOST] = "LOST";


        return goalStatusArray;
    }

    /**
     * Get a textual representation of {@link GoalStatus}
     *
     * @param goalStatus
     *
     * @return
     */
    public static final String goalStatusToString(final byte goalStatus) {

        final String stateName = (goalStatus >= 0 && goalStatus < GOAL_STATUS_TO_STRING.length) ? GOAL_STATUS_TO_STRING[goalStatus] : UNKNOWN_GOAL_STATUS;


        return stateName;
    }



    /**
     * Return the submessage of class R_SUB_MESSAGE from the message T_MESSAGE by using the getter with the provided name in order to workaround known bug.
     * Workaround for known bug http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6924232
     *
     * @param message
     * @param getterMethodName
     * @param <R_SUB_MESSAGE>
     * @param <T_MESSAGE>
     *
     * @return
     */
    static final <R_SUB_MESSAGE, T_MESSAGE> R_SUB_MESSAGE getSubMessageFromMessage(final T_MESSAGE message, final String getterMethodName) {
        Objects.requireNonNull(message);
        Objects.requireNonNull(getterMethodName);
        R_SUB_MESSAGE subMessage = null;

        try {
            //"getStatus"
            final Method m = message.getClass().getMethod(getterMethodName);
            m.setAccessible(true); // workaround for known bug http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6924232
            subMessage = (R_SUB_MESSAGE) m.invoke(message);
        } catch (final Exception e) {
            LOGGER.error(ExceptionUtils.getStackTrace(e));
        }
        return subMessage;
    }

    /**
     * Set the submessage of class R_SUB_MESSAGE in the message T_MESSAGE by using the setter with the provided name in order to workaround known bug.
     * Workaround for known bug http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6924232
     *
     * @param message
     * @param setterMethodName
     * @param <R_SUB_MESSAGE>
     * @param <T_MESSAGE>
     */
    static final <R_SUB_MESSAGE, T_MESSAGE> void setSubMessageFromMessage(final T_MESSAGE message, final R_SUB_MESSAGE submessage, final String setterMethodName) {
        Objects.requireNonNull(message);
        Objects.requireNonNull(setterMethodName);
        try {
            final Method m = message.getClass().getMethod(setterMethodName);
            m.setAccessible(true); // workaround for known bug http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6924232
            m.invoke(message, submessage);
        } catch (final Exception e) {
            LOGGER.error(ExceptionUtils.getStackTrace(e));
        }


    }
}




