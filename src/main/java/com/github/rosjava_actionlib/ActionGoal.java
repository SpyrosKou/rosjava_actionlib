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
import org.ros.message.Time;
import std_msgs.Header;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * Class to encapsulate the action goal object.
 *
 * @author Ernesto Corbellini ecorbellini@ekumenlabs.com
 */
public final class ActionGoal<T_ACTION_GOAL extends Message> {
    private T_ACTION_GOAL goalMessage = null;

    public ActionGoal(T_ACTION_GOAL ag) {
        goalMessage = ag;
    }

    public ActionGoal() {
    }

    /**
     * Return the sequence number of the action goal message's header.
     *
     * @return The sequence number of the std_msgs.Header or -1 if there is an error.
     *
     * @see std_msgs.Header
     */
    public int getHeaderSequence() {

        final Header header = getHeaderMessage();
        final int headerSequence = header == null ? -1 :
                ActionLibMessagesUtils.getSubMessageFromMessage(header, "getSeq");

        return headerSequence;
    }

    /**
     * Set the sequence number of the action goal message's header.
     *
     * @param seq The sequence number for the std_msgs.Header.
     *
     * @see std_msgs.Header
     */
    public void setHeaderSequence(int seq) {
        final Header header = getHeaderMessage();
        if (header != null) {
            ActionLibMessagesUtils.setSubMessageFromMessage(header,new Integer(seq),"setSeq");

        }
    }

    /**
     * Return the time stamp of the action goal message's header.
     *
     * @return The time stamp (org.ros.message.Time) of the std_msgs.Header or null otherwise.
     *
     * @see org.ros.message.Time
     */
    public Time getHeaderTimestamp() {
        Time t = null;
        Header h = getHeaderMessage();
        if (h != null) {
            try {
                Method m = h.getClass().getMethod("getStamp");
                m.setAccessible(true); // workaround for known bug http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6924232
                t = (Time) m.invoke(h);
            } catch (Exception e) {
                e.printStackTrace(System.out);
            }
        }
        return t;
    }

    /**
     * Sets the time stamp for the action goal message's header.
     *
     * @param t The time stamp (org.ros.message.Time) of the std_msgs.Header.
     *
     * @see org.ros.message.Time
     */
    public void setHeaderTimestamp(Time t) {
        Header h = getHeaderMessage();
        if (h != null) {
            try {
                Method m = h.getClass().getMethod("setStamp", Time.class);
                m.setAccessible(true); // workaround for known bug http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6924232
                m.invoke(h, t);
            } catch (Exception e) {
                e.printStackTrace(System.out);
            }
        }
    }

    public String getHeaderFrameId() {
        return null;
    }

    public void setHeaderFrameId(String id) {

    }

    /**
     * Return the standard actionlib header message for this action goal.
     *
     * @return The std_msgs.Header object or null otherwise.
     *
     * @see std_msgs.Header
     */
    public Header getHeaderMessage() {
        Header h = null;
        if (goalMessage != null) {
            try {
                Method m = goalMessage.getClass().getMethod("getHeader");
                m.setAccessible(true); // workaround for known bug http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6924232
                h = (Header) m.invoke(goalMessage);
            } catch (Exception e) {
                e.printStackTrace(System.out);
            }
        }
        return h;
    }

    public void setHeaderMessage(Header h) {

    }

    /**
     *
     * @return
     */
    public final String getGoalId() {
        String id = null;
        GoalID gid = getGoalIdMessage();
        if (gid != null) {
            try {
                Method m = gid.getClass().getMethod("getId");
                m.setAccessible(true); // workaround for known bug http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6924232
                id = (String) m.invoke(gid);
            } catch (Exception e) {
                e.printStackTrace(System.out);
            }
        }
        return id;
    }

    /**
     * Set the action goal's goal ID string and timestamp.
     *
     * @param id Identification string for this goal.
     * @param t  Time stamp (org.ros.message.Time).
     */
    public void setGoalId(String id, Time t) {
        GoalID gid = getGoalIdMessage();
        if (gid != null) {
            try {
                Method m = gid.getClass().getMethod("setId", String.class);
                m.setAccessible(true); // workaround for known bug http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6924232
                m.invoke(gid, id);
                m = gid.getClass().getMethod("setStamp", Time.class);
                m.setAccessible(true); // workaround for known bug http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6924232
                m.invoke(gid, t);
            } catch (Exception e) {
                e.printStackTrace(System.out);
            }
        }
    }

    public Time getGoalIdTimestamp() {
        return null;
    }

    public void setGoalIdTimestamp(Time t) {

    }

    /**
     * Return the actionlib GoalID message for this action goal.
     *
     * @return The actionlib_msgs.GoalID object or null otherwise.
     *
     * @see actionlib_msgs.GoalID
     */
    public GoalID getGoalIdMessage() {
        GoalID gid = null;
        if (goalMessage != null) {
            try {
                Method m = goalMessage.getClass().getMethod("getGoalId");
                m.setAccessible(true); // workaround for known bug http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6924232
                gid = (GoalID) m.invoke(goalMessage);
            } catch (Exception e) {
                e.printStackTrace(System.out);
            }
        }
        return gid;
    }

    public void setGoalIdMessage(GoalID gid) {

    }

    /**
     * @return
     */
    public T_ACTION_GOAL getActionGoalMessage() {
        return goalMessage;
    }

    /**
     * @return
     */
    public void setActionGoalMessage(final T_ACTION_GOAL agm) {
        goalMessage = agm;
    }

    /**
     * @return
     */
    public Message getGoalMessage() {
        Message g = null;
        if (goalMessage != null) {
            try {
                Method m = goalMessage.getClass().getMethod("getGoal");
                m.setAccessible(true); // workaround for known bug http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6924232
                g = (Message) m.invoke(goalMessage);
            } catch (Exception e) {
                e.printStackTrace(System.out);
            }
        }
        return g;
    }

    /**
     * @param goalMessage
     */
    public void setGoalMessage(final Message goalMessage) {
        if (goalMessage != null) {
            ActionLibMessagesUtils.setSubMessageFromMessage(this.goalMessage,goalMessage,"setGoal");

        }
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) return true;
        if (!(object instanceof ActionGoal)) return false;
        final ActionGoal<?> that = (ActionGoal<?>) object;
        return getGoalMessage().equals(that.getGoalMessage());
    }

    @Override
    public  int hashCode() {
        return Objects.hash(getGoalMessage());
    }


}
