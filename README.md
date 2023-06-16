# Actionlib for Rosjava
A pure java implementation of [actionlib](http://wiki.ros.org/actionlib) for [rosjava](http://wiki.ros.org/rosjava).

## Key Features:
1. Easy developing and testing in any OS (Windows, Linux, Mac) without a ROS installation. 
2. Compatible and usable with [ROS](https://www.ros.org). Tested with [ROS Indigo](http://wiki.ros.org/indigo).
3. Uses [Log4j2](https://logging.apache.org/log4j/2.x/) and [SL4J](http://www.slf4j.org/)
4. Smaller public API, with all the needed functionality plus some extras. 
    * The API favours composition based designs and prevents inheriting the project classes. Where possible classes and methods are marked with the `final` modifier to prevent extension. 
    * Includes separate ActionClientResult, ActionClientStatus and ActionClientFeedbackListener as well a complete ActionClientListener
    * ActionClient can accept multiple simultaneous Client listeners of different types, that can also be removed
    * The public methods and API includes only required elements. Only needed classes and methods are public.
    * Refactored code, grouping together common elements
5. Robust detection of started ActionServer in waitForActionServerToStart
6. Targeted to be used as part of other Java / ROS Java applications via gradle or maven, similar to other java libraries

Other Changes:   
1. `ActionClient` only responds to messages for the same GoalId.
2. `ActionClient` will cancel a `GoalId` after receiving it, to allow the server to remove it from the `GoalStatusArray`
3. `ActionClient` can call a [`Runnable`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Runnable.html) once and only if and when connected to the ActionLib protocol.

 
## Requirements:
* Java 11 or greater (OpenJDK should work).
* For usage with a ROS installation the following packages:
    * are needed in runtime: ```ros-$ROS_DISTRO-actionlib``` ```ros-$ROS_DISTRO-genjava```
    * could be helpful for testing/development: ```ros-$ROS_DISTRO-actionlib-tutorials``` 
 

## Downloading:
 TODO

## Building
The project will be compiled and tested in any OS (Windows, Linux, Mac) without any requirement for a ROS installation.
Instructions are provided hereafter for Windows and Linux.  
### Windows Power Shell
1. [Clone](https://git-scm.com/docs/git-clone) the [project repository](https://github.com/SpyrosKou/rosjava_actionlib.git):
`git clone https://github.com/SpyrosKou/rosjava_actionlib.git`

2. Go into the cloned repository directory:
`cd .\rosjava_actionlib\`

3. Build the project.
`.\gradlew.bat build`

4. Running the Unit tests. The tests will run during the build, but the following command can run the tests on demand. 
`.\gradlew.bat test`


### Linux
1. [Clone](https://git-scm.com/docs/git-clone) the [project repository](https://github.com/SpyrosKou/rosjava_actionlib.git):
`git clone https://github.com/SpyrosKou/rosjava_actionlib.git`

2. Go into the cloned repository directory:
`cd rosjava_actionlib/`

3. [Add execute permission](http://manpages.ubuntu.com/manpages/focal/man1/chmod.1.html) to gradlew script:
`sudo chmod +x gradlew`

4. Build the project. Tests will be executed automatically.
`./gradlew build`

5. Running the Unit tests. The tests will run during the build, but the following command can run the tests on demand. 
./gradlew test`


## Running a test client:
1. TODO

## Using a Client

When an [ActionClient](https://github.com/SpyrosKou/rosjava_actionlib/blob/kinetic/src/main/java/com/github/rosjava_actionlib/ActionClient.java) invokes a server it returns an [ActionFuture](https://github.com/SpyrosKou/rosjava_actionlib/blob/kinetic/src/main/java/com/github/rosjava_actionlib/ActionFuture.java).

See an example client at [FutureBasedClient](https://github.com/SpyrosKou/rosjava_actionlib/blob/kinetic/src/test/java/com/github/rosjava_actionlib/FutureBasedClient.java) .

The [FutureBasedClient](https://github.com/SpyrosKou/rosjava_actionlib/blob/kinetic/src/test/java/com/github/rosjava_actionlib/FutureBasedClient.java) usage is demonstrated in [FutureBasedClientTest](https://github.com/SpyrosKou/rosjava_actionlib/blob/kinetic/src/test/java/com/github/rosjava_actionlib/FutureBasedClientTest.java). 

## Output from the test client
The test client will connect to the fibonacci server and send it a goal. It
should then receive feedback from the server and a final response. The output
should look something like this:
```
Loading node class: com.github.rosjava_actionlib.SimpleClient

Waiting for action server to start...
Action server started.

Sending goal...
Sent goal with ID: /fibonacci_test_client-1-1453494018.17000000
Waiting for goal to complete...
Feedback from Fibonacci server: 0 1 1
Feedback from Fibonacci server: 0 1 1 2
Feedback from Fibonacci server: 0 1 1 2 3
Got Fibonacci result sequence: 0 1 1 2 3
Goal completed!

Sending a new goal...
Sent goal with ID: /fibonacci_test_client-2-1453494021.25000000
Cancelling this goal...
Feedback from Fibonacci server: 0 1 1
Got Fibonacci result sequence:
Goal cancelled succesfully.

Bye!
```

## Running a test server:
1. TODO

## Output from the test server
The test server will start running and wait for clients to connect and send goal messages.
Once the fibonacci client sends a goal, the server accepts it and sends a result. The output
should look something like this:
```
Goal received.
Goal accepted.
Sending result...
```

## Running demos for the server and the client



You can launch a demo client and a fibonacci action server all at once using:
```
roslaunch rosjava_actionlib client_demo.launch --screen
```


You can also launch a demo server and a fibonacci action client all at once using:
```
roslaunch rosjava_actionlib server_demo.launch --screen
```

Use Ctrl+C to stop the execution once it's finished.



## Motivation
 TODO