package eu.test.actionlib.clientServer;

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


import actionlib_msgs.GoalID;
import actionlib_msgs.GoalStatus;
import actionlib_msgs.GoalStatusArray;
import actionlib_tutorials.*;
import com.github.ekumen.rosjava_actionlib.*;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.logging.Log;
import org.ros.message.Duration;
import org.ros.namespace.GraphName;
import org.ros.node.AbstractNodeMain;
import org.ros.node.ConnectedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;


/**
 * Class to test the actionlib client.
 *
 * @author Ernesto Corbellini ecorbellini@ekumenlabs.com
 * @author Spryos Koukas
 */
class ActionLibClientFeedback extends AbstractNodeMain implements ActionClientListener<FibonacciActionFeedback, FibonacciActionResult> {
    private static final Logger logger= LoggerFactory.getLogger(ActionLibClientFeedback.class);
    static {
        // comment this line if you want logs activated
        System.setProperty("org.apache.commons.logging.Log",
                "org.apache.commons.logging.impl.NoOpLog");
    }



    private ActionClient actionClient = null;
    private volatile boolean resultReceived = false;
    private volatile boolean isStarted=false;

    private Log log;

    @Override
    public GraphName getDefaultNodeName() {
        return GraphName.of("fibonacci_test_client");
    }

    /**
     * Getter for isStarted
     *
     * @return isStarted
     **/
    public void waitForStart() {
        while(!this.isStarted){
            try{
                Thread.sleep(5);
            }catch (final InterruptedException ie){
                logger.error(ExceptionUtils.getStackTrace(ie));
            }catch (final Exception e){
                logger.error(ExceptionUtils.getStackTrace(e));
            }
        }
    }

    public void startTasks(){
        Duration serverTimeout = new Duration(20);
        boolean serverStarted;


        // Attach listener for the callbacks
        actionClient.addListener(this);
        logger.trace("\nWaiting for action server to start...");
        serverStarted = actionClient.waitForActionServerToStart(new Duration(200));
        if (serverStarted) {
            logger.trace("Action server started.\n");
        } else {
            logger.trace("No actionlib server found after waiting for " + serverTimeout.totalNsecs() / 1e9 + " seconds!");
            System.exit(1);
        }

        // Create Fibonacci goal message
        final FibonacciActionGoal goalMessage = (FibonacciActionGoal) actionClient.newGoalMessage();
        final FibonacciGoal fibonacciGoal = goalMessage.getGoal();
        // set Fibonacci parameter
        fibonacciGoal.setOrder(3);
        logger.trace("Sending goal...");
        actionClient.sendGoal(goalMessage);
        final GoalID gid1 = ActionLibMessagesUtils.getGoalId(goalMessage);
        logger.trace("Sent goal with ID: " + gid1.getId());
        logger.trace("Waiting for goal to complete...");
        while (actionClient.getGoalState() != ActionLibClientStates.DONE) {
            sleep(1);
        }
        logger.trace("Goal completed!\n");

        logger.trace("Sending a new goal...");
        actionClient.sendGoal(goalMessage);
        final GoalID gid2 = ActionLibMessagesUtils.getGoalId(goalMessage);
        logger.trace("Sent goal with ID: " + gid2.getId());
        logger.trace("Cancelling this goal...");
        actionClient.sendCancel(gid2);
        while (actionClient.getGoalState() != ActionLibClientStates.DONE) {
            sleep(1);
        }
        logger.trace("Goal cancelled succesfully.\n");
        logger.trace("Bye!");

    }

    @Override
    public void onStart(ConnectedNode node) {
        actionClient = new ActionClient<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult>(node, ActionLibServerFeedback.DEFAULT_ACTION_NAME, FibonacciActionGoal._TYPE, FibonacciActionFeedback._TYPE, FibonacciActionResult._TYPE);
        log = node.getLog();
        this.isStarted=true;

//        System.exit(0);
    }

    @Override
    public void resultReceived(FibonacciActionResult message) {
        FibonacciResult result = message.getResult();
        int[] sequence = result.getSequence();
        int i;

        resultReceived = true;
        System.out.print("Got Fibonacci result sequence: ");
        for (i = 0; i < sequence.length; i++)
            System.out.print(Integer.toString(sequence[i]) + " ");
        logger.trace("");
    }

    @Override
    public void feedbackReceived(FibonacciActionFeedback message) {
        FibonacciFeedback result = message.getFeedback();
        int[] sequence = result.getSequence();
        int i;

        System.out.print("Feedback from Fibonacci server: ");
        for (i = 0; i < sequence.length; i++)
            System.out.print(Integer.toString(sequence[i]) + " ");
        System.out.print("\n");
    }

    @Override
    public void statusReceived(GoalStatusArray status) {
        List<GoalStatus> statusList = status.getStatusList();
        for (GoalStatus gs : statusList) {
            log.info("GoalID: " + gs.getGoalId().getId() + " -- GoalStatus: " + gs.getStatus() + " -- " + gs.getText());
        }
        log.info("Current state of our goal: " + ActionLibClientStates.translateState(actionClient.getGoalState()));
    }

    void sleep(long msec) {
        try {
            Thread.sleep(msec);
        } catch (InterruptedException ex) {
        }
    }
}
