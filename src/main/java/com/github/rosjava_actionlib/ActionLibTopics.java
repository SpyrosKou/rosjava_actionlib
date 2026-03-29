/**
 * Copyright 2026 Spyros Koukas
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

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;

/**
 * Immutable actionlib topic bundle for a single action name.
 * The topic-grouping idea was adapted from the historical helper introduced on the old {@code pickingChanges} branch.
 */
public record ActionLibTopics(String goalTopicName,
                              String cancelTopicName,
                              String feedbackTopicName,
                              String resultTopicName,
                              String goalStatusTopicName) {
    private static final String GOAL_TOPIC_NAME = "goal";
    private static final String CANCEL_TOPIC_NAME = "cancel";
    private static final String FEEDBACK_TOPIC_NAME = "feedback";
    private static final String RESULT_TOPIC_NAME = "result";
    private static final String STATUS_TOPIC_NAME = "status";

    public ActionLibTopics {
        Preconditions.checkArgument(StringUtils.isNotBlank(goalTopicName));
        Preconditions.checkArgument(StringUtils.isNotBlank(cancelTopicName));
        Preconditions.checkArgument(StringUtils.isNotBlank(feedbackTopicName));
        Preconditions.checkArgument(StringUtils.isNotBlank(resultTopicName));
        Preconditions.checkArgument(StringUtils.isNotBlank(goalStatusTopicName));
    }

    public ActionLibTopics(final String actionName) {
        this(getGoalTopicNameForActionName(actionName),
                getCancelTopicNameForActionName(actionName),
                getFeedbackTopicNameForActionName(actionName),
                getResultTopicNameForActionName(actionName),
                getGoalStatusTopicNameForActionName(actionName));
    }

    private static final String getTopicNameForActionName(final String actionName, final String topicName) {
        Preconditions.checkArgument(StringUtils.isNotBlank(actionName));
        Preconditions.checkArgument(StringUtils.isNotBlank(topicName));
        return actionName + "/" + topicName;
    }

    public static final String getGoalTopicNameForActionName(final String actionName) {
        return getTopicNameForActionName(actionName, GOAL_TOPIC_NAME);
    }

    public static final String getCancelTopicNameForActionName(final String actionName) {
        return getTopicNameForActionName(actionName, CANCEL_TOPIC_NAME);
    }

    public static final String getFeedbackTopicNameForActionName(final String actionName) {
        return getTopicNameForActionName(actionName, FEEDBACK_TOPIC_NAME);
    }

    public static final String getResultTopicNameForActionName(final String actionName) {
        return getTopicNameForActionName(actionName, RESULT_TOPIC_NAME);
    }

    public static final String getGoalStatusTopicNameForActionName(final String actionName) {
        return getTopicNameForActionName(actionName, STATUS_TOPIC_NAME);
    }

    public final String getGoalTopicName() {
        return this.goalTopicName;
    }

    public final String getCancelTopicName() {
        return this.cancelTopicName;
    }

    public final String getFeedbackTopicName() {
        return this.feedbackTopicName;
    }

    public final String getResultTopicName() {
        return this.resultTopicName;
    }

    public final String getGoalStatusTopicName() {
        return this.goalStatusTopicName;
    }
}
