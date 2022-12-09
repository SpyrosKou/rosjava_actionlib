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

package eu.test.actionlib.clientServer;

import actionlib_msgs.GoalID;
import actionlib_tutorials.FibonacciActionFeedback;
import actionlib_tutorials.FibonacciActionGoal;
import actionlib_tutorials.FibonacciActionResult;
import com.github.ekumen.rosjava_actionlib.ActionServer;
import com.github.ekumen.rosjava_actionlib.ActionServerListener;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.ros.namespace.GraphName;
import org.ros.node.AbstractNodeMain;
import org.ros.node.ConnectedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Testing the Action Lib Server
 * @author Spyros Koukas
 */
class ActionLibServerFeedback extends AbstractNodeMain implements ActionServerListener<FibonacciActionGoal> {
    public static final String DEFAULT_ACTION_NAME="/fibonacci";
    private static final Logger logger = LoggerFactory.getLogger(ActionLibServerFeedback.class);
    private ActionServer<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult> actionServer = null;
    private volatile FibonacciActionGoal currentGoal = null;

    private volatile boolean isStarted = false;

    /**
     * Getter for isStarted
     *
     * @return isStarted
     **/
    public void waitForStart() {
        while (!this.isStarted) {
            try {
                Thread.sleep(5);
            } catch (final InterruptedException ie) {
                logger.error(ExceptionUtils.getStackTrace(ie));
            } catch (final Exception e) {
                logger.error(org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace(e));
            }
        }
    }

    @Override
    public GraphName getDefaultNodeName() {
        return GraphName.of("fibonacci_test_server");
    }

    @Override
    public void onStart(ConnectedNode node) {
        FibonacciActionResult result;
        String id;

        this.actionServer = new ActionServer<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult>(node, DEFAULT_ACTION_NAME, FibonacciActionGoal._TYPE, FibonacciActionFeedback._TYPE, FibonacciActionResult._TYPE);

        this.actionServer.attachListener(this);
        this.isStarted = true;
        while (true) {
            if (currentGoal != null) {
                result = actionServer.newResultMessage();
                result.getResult().setSequence(fibonacciSequence(currentGoal.getGoal().getOrder()));
                id = currentGoal.getGoalId().getId();
                actionServer.setSucceed(id);
                actionServer.setGoalStatus(result.getStatus(), id);
                logger.trace("Sending feedback...");
                final FibonacciActionFeedback feedbackMessage = this.actionServer.newFeedbackMessage();
                this.actionServer.sendFeedback(feedbackMessage);
                logger.trace("Sending result...");
                actionServer.sendResult(result);
                currentGoal = null;
            }
        }

    }

    @Override
    public void goalReceived(FibonacciActionGoal goal) {
        logger.trace("Goal received.");
    }

    @Override
    public void cancelReceived(GoalID id) {
        logger.trace("Cancel received.");
    }

    @Override
    public boolean acceptGoal(final FibonacciActionGoal goal) {
        // If we don't have a goal, accept it. Otherwise, reject it.
        if (currentGoal == null) {
            currentGoal = goal;
            logger.trace("Goal accepted.");
            return true;
        } else {
            logger.trace("We already have a goal! New goal reject.");
            return false;
        }
    }

    private static int[] fibonacciSequence(final int order) {

        int[] fib = new int[order + 2];

        fib[0] = 0;
        fib[1] = 1;

        for (int i = 2; i < (order + 2); i++) {
            fib[i] = fib[i - 1] + fib[i - 2];
        }
        return fib;
    }


}
