package com.github.ekumen.rosjava_actionlib;

/**
 * Created at 2019-04-23
 *This is a static utility class.
 * Utility functions to get the ActionLib related topic consistently.
 * Does not perform any checks.
 * @author Spyros Koukas
 */
public final class ActionLibTopics {
    /**
     * This is a static utility class.
     */
    private ActionLibTopics(){
        throw new AssertionError("This class is not expected to be instantiated");
    }

    private static final String GOAL_TOPIC_NAME="goal";
    private static final String CANCEL_TOPIC_NAME="cancel";
    private  static final String FEEDBACK_TOPIC_NAME="feedback";
    private  static final String RESULT_TOPIC_NAME="result";
    private static final String STATUS_TOPIC_NAME="status";

    /**
     * Returns the path of the actionName and the topicName
     * @param actionName
     * @param topicName
     * @return
     */
    private static final String getTopicNameForActionName(final String actionName, final String topicName){
        return actionName+"/"+topicName;
    }

    /**
     * Get the Goal topic name for a given action name
     * @param actionName
     * @return
     */
    public static final String getGoalTopicNameForActionName(final String actionName){
        return getTopicNameForActionName(actionName, GOAL_TOPIC_NAME);
    }

    /**
     *Get the Cancel topic name for a given action name
     * @param actionName
     * @return
     */
    public static final String getCancelTopicNameForActionName(final String actionName){
        return getTopicNameForActionName(actionName, CANCEL_TOPIC_NAME);
    }

    /**
     *Get the Feedback topic name for a given action name
     * @param actionName
     * @return
     */
    public static final String getFeedbackTopicNameForActionName(final String actionName){
        return getTopicNameForActionName(actionName, FEEDBACK_TOPIC_NAME);
    }

    /**
     *Get the Result topic name for a given action name
     * @param actionName
     * @return
     */
    public static final String getResultTopicNameForActionName(final String actionName){
        return getTopicNameForActionName(actionName, RESULT_TOPIC_NAME);
    }

    /**
     *Get the Status topic name for a given action name
     * @param actionName
     * @return
     */
    public static final String getGoalStatusTopicNameForActionName(final String actionName){
        return getTopicNameForActionName(actionName, STATUS_TOPIC_NAME);
    }
}
