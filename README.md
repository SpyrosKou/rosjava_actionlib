# rosjava_actionlib

Pure Java ROS1 `actionlib` for `rosjava`, maintained as a Gradle-only library.

This library is built for Java applications that need ROS1 action clients and servers on a modern JVM.
Focuses on: connection readiness, cancellation, lifecycle correctness, and testability.

It supports cross-platform development on Windows, Linux, and macOS, and it is a Gradle-first project for straightforward library consumption from Java codebases.

## Main Features

- Pure Java `ActionClient` and `ActionServer` implementations for ROS1 `actionlib`
- Modern toolchain: Gradle, Java 21,
- Uses [`rosjava 0.4.1.1`](https://github.com/SpyrosKou/rosjava_core) available as a dependency in [SpyrosKou/rosjava_mvn_repo](https://github.com/SpyrosKou/rosjava_mvn_repo)
- Cross-platform development and testing
- No local ROS installation is required for default Gradle build or test
- Future-based client API with typed feedback, result, and status listeners
- Explicit connection wait APIs
- Reduce use of sleep/polling logic
- Stronger cancellation handling, including cancel-by-id, cancel-all, cancel-before-time, and early-cancel scenarios
- Broad lifecycle and interoperability testing
- ActionServerListener.acceptGoal() can defer the accept/reject decision, allowing manual goal handling.

## What the library focuses on

- Correct ROS1 action behavior. Goal lifecycle handling is tighter around terminal states, result/status consistency, and cancel processing.
- Better startup and connection handling. Clients can explicitly wait for registration, publishers, subscribers, or full server connectivity without using polling and sleeps.
- A cleaner client-facing API. `ActionFuture`, typed listeners, removable listeners, and one-time connection hooks make the library easier to embed in larger Java applications.
- Lower overhead on long-running servers. Goal retention and status publication were tightened, and reflective hot paths were reduced with caching.
- Strong coverage in tests. The repository exercises repeated goals, cancellation, terminal status publication, connection readiness, and interoperability scenarios.

## Requirements

- Java 21 to build the current codebase
- A ROS1 master is only needed when interoperating with external ROS nodes
- A local ROS installation is not required for normal compilation or for the default test suite

## Build and test

### Windows (PowerShell)

```powershell
git clone https://github.com/SpyrosKou/rosjava_actionlib.git
cd .\rosjava_actionlib\
.\gradlew.bat build
```

Run tests explicitly:

```powershell
.\gradlew.bat test
```

### Linux / macOS

```bash
git clone https://github.com/SpyrosKou/rosjava_actionlib.git
cd rosjava_actionlib
chmod +x gradlew
./gradlew build
```

Run tests explicitly:

```bash
./gradlew test
```

The integration tests start a private `RosCore` by default. To point the tests at an external master, update [`src/test/resources/test_configurations.properties`](src/test/resources/test_configurations.properties) and set `USE_EXTERNAL_ROS_MASTER=true`.

## Quick client example

```java
ActionClient<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult> client =
        new ActionClient<>(
                connectedNode,
                "/fibonacci",
                FibonacciActionGoal._TYPE,
                FibonacciActionFeedback._TYPE,
                FibonacciActionResult._TYPE);

client.addActionClientFeedbackListener(feedback -> {
    // inspect incremental feedback
});

client.addActionClientResultListener(result -> {
    // inspect the terminal result
});

if (!client.waitForServerConnection(5, TimeUnit.SECONDS)) {
    throw new IllegalStateException("Action server did not appear in time");
}

FibonacciActionGoal goal = client.newGoalMessage();
goal.getGoal().setOrder(10);

ActionFuture<FibonacciActionGoal, FibonacciActionFeedback, FibonacciActionResult> future =
        client.sendGoal(goal);

FibonacciActionResult result = future.get(10, TimeUnit.SECONDS);
```

Useful client-side APIs include:

- `waitForRegistration(...)`
- `waitForServerPublishers(...)`
- `waitForClientSubscribers(...)`
- `waitForServerConnection(...)`
- `cancelGoal(goalId)`
- `cancelAll()`
- `cancelBefore(stamp)`

## Server-side model

`ActionServer` keeps the familiar ROS1 actionlib model, but the fork tightens status publication, result/status synchronization, and cancel handling.

Servers implement `ActionServerListener<T_ACTION_GOAL>`. The `acceptGoal(...)` method returns `Optional<Boolean>`, which lets a server:

- explicitly accept a goal
- explicitly reject a goal
- keep acceptance under manual control when needed

See [`src/test/java/com/github/rosjava_actionlib/FibonacciActionLibServer.java`](src/test/java/com/github/rosjava_actionlib/FibonacciActionLibServer.java) for a maintained minimal server example.

## Examples and tests

The most useful examples live in the test and integration code, because that is where the current fork is actively exercised.

- [`src/test/java/com/github/rosjava_actionlib/FutureBasedClientNode.java`](src/test/java/com/github/rosjava_actionlib/FutureBasedClientNode.java): future-based client usage
- [`src/test/java/com/github/rosjava_actionlib/FibonacciActionLibServer.java`](src/test/java/com/github/rosjava_actionlib/FibonacciActionLibServer.java): minimal server implementation
- [`src/test/java/com/github/rosjava_actionlib/FibonacciFutureBasedClientNodeTest.java`](src/test/java/com/github/rosjava_actionlib/FibonacciFutureBasedClientNodeTest.java): repeated goals, cancellation paths, and terminal status behavior
- [`src/test/java/com/github/rosjava_actionlib/WaitMethodsClientServerTest.java`](src/test/java/com/github/rosjava_actionlib/WaitMethodsClientServerTest.java): wait and connection behavior
- [`src/test/java/com/github/rosjava_actionlib/ActionServerResultStatusCompatibilityTest.java`](src/test/java/com/github/rosjava_actionlib/ActionServerResultStatusCompatibilityTest.java): result/status consistency
- [`src/test/java/com/github/rosjava_actionlib/TurtleSimActionLibClientTest.java`](src/test/java/com/github/rosjava_actionlib/TurtleSimActionLibClientTest.java): `turtle_actionlib` interoperability scenario, ignored by default

## Using as a dependency
The library can be used directly as a dependency without cloning the repo, or it can build and used locally after cloning the repo.

### Use as a dependency
The library can be used directly, without clonging and building this repo.
Artifacts are available from [`SpyrosKou/rosjava_mvn_repo`](https://github.com/SpyrosKou/rosjava_mvn_repo). To use directly as a dependency in Gradle:

```gradle
repositories {
    mavenCentral()
    maven {
        url "https://github.com/SpyrosKou/rosjava_mvn_repo/raw/noetic"
        name "GithubSpyrosKouRepository"
    }
}

dependencies {
    implementation "com.github.rosjava:rosjava_actionlib:2026.03.29.02"
}
```

### Build, Publish and Consume locally
The Gradle build is configured for `maven-publish`, including source and javadoc jars.

Publish to your local Maven cache:

```bash
./gradlew publishToMavenLocal
```

Then consume it from another Gradle project:

```gradle
repositories {
    mavenLocal()
}

dependencies {
    implementation "com.github.rosjava:rosjava_actionlib:2026.03.29.02"
}
```

## Changelog

Fork-specific changes are tracked in [`CHANGELOG.rst`](CHANGELOG.rst).

## License

Apache License 2.0
