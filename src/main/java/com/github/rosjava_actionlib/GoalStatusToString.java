package com.github.rosjava_actionlib;

import actionlib_msgs.GoalStatus;

import java.util.HashMap;
import java.util.Map;

public final class GoalStatusToString {

    private final Map<Byte, String> valueToStringMap;

    public GoalStatusToString() {
        final Map<Byte, String> valueToStringMapTemp = new HashMap<>();
        valueToStringMapTemp.put(GoalStatus.PENDING, "PENDING");
        valueToStringMapTemp.put(GoalStatus.ACTIVE, "ACTIVE");
        valueToStringMapTemp.put(GoalStatus.PREEMPTED, "PREEMPTED");
        valueToStringMapTemp.put(GoalStatus.SUCCEEDED, "SUCCEEDED");
        valueToStringMapTemp.put(GoalStatus.ABORTED, "ABORTED");
        valueToStringMapTemp.put(GoalStatus.REJECTED, "REJECTED");
        valueToStringMapTemp.put(GoalStatus.PREEMPTING, "PREEMPTING");
        valueToStringMapTemp.put(GoalStatus.RECALLING, "RECALLING");
        valueToStringMapTemp.put(GoalStatus.RECALLED, "RECALLED");
        valueToStringMapTemp.put(GoalStatus.LOST, "LOST");
        this.valueToStringMap = Map.copyOf(valueToStringMapTemp);
    }

    public final String getStatus(final byte value) {
        return this.valueToStringMap.get(value);
    }


}
