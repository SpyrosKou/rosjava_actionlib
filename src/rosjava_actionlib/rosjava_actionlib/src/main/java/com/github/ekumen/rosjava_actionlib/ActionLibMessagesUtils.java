package com.github.ekumen.rosjava_actionlib;

import actionlib_msgs.GoalID;
import actionlib_msgs.GoalStatus;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.ros.internal.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * Created at 2019-04-19
 *
 * @author Spyros Koukas
 */
public class ActionLibMessagesUtils {
    private static final Logger logger = LoggerFactory.getLogger(ActionLibMessagesUtils.class);
    public static final String UNKNOWN_GOAL_STATUS = "UNKNOWN GOAL STATUS";

    /**
     * Return true if the goalStatus corresponds to one of the 9 known states as presented in {@link GoalStatus}
     *
     * @param goalStatus
     *
     * @return
     */
    public static final boolean isKnownGoalStatus(final byte goalStatus) {
        return goalStatus == GoalStatus.PENDING ||
                goalStatus == GoalStatus.ACTIVE ||
                goalStatus == GoalStatus.PREEMPTED ||
                goalStatus == GoalStatus.SUCCEEDED ||
                goalStatus == GoalStatus.ABORTED ||
                goalStatus == GoalStatus.REJECTED ||
                goalStatus == GoalStatus.PREEMPTING ||
                goalStatus == GoalStatus.RECALLING ||
                goalStatus == GoalStatus.RECALLED ||
                goalStatus == GoalStatus.LOST;
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
     * Get a textual representation of {@link GoalStatus}
     *
     * @param goalStatus
     *
     * @return
     */
    public static final String goalStatusToString(final byte goalStatus) {

        final String stateName;
        switch (goalStatus) {
            case GoalStatus.PENDING:
                stateName = "PENDING";
                break;

            case GoalStatus.ACTIVE:
                stateName = "ACTIVE";
                break;

            case GoalStatus.PREEMPTED:
                stateName = "PREEMPTED";
                break;
            case GoalStatus.SUCCEEDED:
                stateName = "SUCCEEDED";
                break;
            case GoalStatus.ABORTED:
                stateName = "ABORTED";
                break;

            case GoalStatus.REJECTED:
                stateName = "REJECTED";
                break;

            case GoalStatus.PREEMPTING:
                stateName = "PREEMPTING";
                break;

            case GoalStatus.RECALLING:
                stateName = "RECALLING";
                break;


            case GoalStatus.RECALLED:
                stateName = "RECALLED";
                break;


            case GoalStatus.LOST:
                stateName = "LOST";
                break;

            default:
                stateName = UNKNOWN_GOAL_STATUS;
                break;
        }
        return stateName;
    }

    /**
     * Convenience method for retrieving the goal ID of a given action goal message.
     *
     * @param goal The action goal message from where to obtain the goal ID.
     *
     * @return Goal ID object containing the ID of the action message.
     *
     * @see actionlib_msgs.GoalID
     */
    public static final GoalID getGoalId(final Message goal) {
        return getSubMessageFromMessage(goal,"getGoalId");

    }

    /**
     * Convenience method for setting the goal ID of an action goal message.
     *
     * @param goal The action goal message to set the goal ID for.
     * @param gid  The goal ID object.
     *
     * @see GoalID
     */
    public static final void setGoalId(final Message goal, GoalID gid) {
//        Objects.requireNonNull(goal);
//        try {
//            Method m = goal.getClass().getMethod("setGoalId", GoalID.class);
//            m.setAccessible(true); // workaround for known bug http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6924232
//            m.invoke(goal, gid);
//        } catch (Exception e) {
//            logger.error(ExceptionUtils.getStackTrace(e));
//        }
        setSubMessageFromMessage(goal, gid, "setGoalId");
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
            logger.error(ExceptionUtils.getStackTrace(e));
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
            logger.error(ExceptionUtils.getStackTrace(e));
        }


    }
}




