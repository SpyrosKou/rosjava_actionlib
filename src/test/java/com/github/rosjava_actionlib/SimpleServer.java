package com.github.rosjava_actionlib;

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
import actionlib_tutorials.FibonacciActionFeedback;
import actionlib_tutorials.FibonacciActionGoal;
import actionlib_tutorials.FibonacciActionResult;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.ros.namespace.GraphName;
import org.ros.node.AbstractNodeMain;
import org.ros.node.ConnectedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

/**
 * @deprecated see {@link FutureBasedClient} which utilizes the {@link ActionFuture}
 * Class to test the actionlib server.
 * This is a simple server with the ability to process only a single goal.
 *  @author Ernesto Corbellini ecorbellini@ekumenlabs.com
 *  @author Spyros Koukas
 */
@Deprecated
class SimpleServer extends AbstractNodeMain implements ActionServerListener<FibonacciActionGoal> {
  private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private ActionServer<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult> actionServer = null;
    private volatile FibonacciActionGoal currentGoal = null;
    private volatile boolean isStarted=false;

    @Override
    public GraphName getDefaultNodeName() {
        return GraphName.of("fibonacci_test_server");
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
          LOGGER.error(ExceptionUtils.getStackTrace(ie));
        }catch (final Exception e){
          LOGGER.error(ExceptionUtils.getStackTrace(e));
        }
      }
    }
    @Override
    public void onStart(ConnectedNode node) {
        FibonacciActionResult result;
        String id;

        actionServer = new ActionServer<>(node, this,"/fibonacci", FibonacciActionGoal._TYPE,
                FibonacciActionFeedback._TYPE, FibonacciActionResult._TYPE);


 		this.isStarted=true;
        while (node != null) {
            if (currentGoal != null) {
                result = actionServer.newResultMessage();
                result.getResult().setSequence(fibonacciSequence(currentGoal.getGoal().getOrder()));
                id = currentGoal.getGoalId().getId();
                actionServer.setSucceed(id);
                actionServer.setGoalStatus(result.getStatus(), id);
                LOGGER.trace("Sending result...");
                actionServer.sendResult(result);
                currentGoal = null;
            }
        }
    }

    @Override
    public void goalReceived(FibonacciActionGoal goal) {
        LOGGER.trace("Goal received.");
    }

    @Override
    public void cancelReceived(GoalID id) {
        LOGGER.trace("Cancel received.");
    }

    @Override
    public boolean acceptGoal(FibonacciActionGoal goal) {
        // If we don't have a goal, accept it. Otherwise, reject it.
        if (currentGoal == null) {
            currentGoal = goal;
            LOGGER.trace("Goal accepted.");
            return true;
        } else {
            LOGGER.trace("We already have a goal! New goal reject.");
            return false;
        }
    }

    private static final int[] fibonacciSequence(int order) {
        int i;
        int[] fib = new int[order + 2];

        fib[0] = 0;
        fib[1] = 1;

        for (i = 2; i < (order + 2); i++) {
            fib[i] = fib[i - 1] + fib[i - 2];
        }
        return fib;
    }

    /*
     * Sleep for an amount on miliseconds.
     * @param msec Number or miliseconds to sleep.
     */
    private void sleep(long msec) {
        try {
            Thread.sleep(msec);
        } catch (InterruptedException ex) {
        }
    }

}
