package com.github.ekumen.rosjava_actionlib;

/**
 * Created at 2019-04-23
 * Client States
 * @author Spyros
 */
public class ActionLibClientStates {
    public final static int INVALID_TRANSITION = -2;
    public final static int NO_TRANSITION = -1;
    public final static int WAITING_FOR_GOAL_ACK = 0;
    public final static int PENDING = 1;
    public final static int ACTIVE = 2;
    public final static int WAITING_FOR_RESULT = 3;
    public final static int WAITING_FOR_CANCEL_ACK = 4;
    public final static int RECALLING = 5;
    public final static int PREEMPTING = 6;
    public final static int DONE = 7;
    public final static int LOST = 8;

    /**
     * @param state
     *
     * @return
     */
    public static String translateState(int state) {
        String stateName;
        switch (state) {
            case INVALID_TRANSITION:
                stateName = "INVALID_TRANSITION";
                break;
            case NO_TRANSITION:
                stateName = "NO_TRANSITION";
                break;
            case WAITING_FOR_GOAL_ACK:
                stateName = "WAITING_FOR_GOAL_ACK";
                break;
            case PENDING:
                stateName = "PENDING";
                break;
            case ACTIVE:
                stateName = "ACTIVE";
                break;
            case WAITING_FOR_RESULT:
                stateName = "WAITING_FOR_RESULT";
                break;
            case WAITING_FOR_CANCEL_ACK:
                stateName = "WAITING_FOR_CANCEL_ACK";
                break;
            case RECALLING:
                stateName = "RECALLING";
                break;
            case PREEMPTING:
                stateName = "PREEMPTING";
                break;
            case DONE:
                stateName = "DONE";
                break;
            case LOST:
                stateName = "LOST";
                break;
            default:
                stateName = "UNKNOWN_STATE";
                break;
        }
        return stateName;
    }
}
