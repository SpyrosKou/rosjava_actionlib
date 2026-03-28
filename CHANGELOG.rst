rosjava_actionlib changelog
===========================

Scope
-----

This file aims to summarize meaningful changes after forking.
A sort summary of upstream is also included for reference.


Unreleased
----------

- Preserved terminal action status visibility by publishing one terminal
  `/status` update before evicting finished goals from the server.
- Corrected client-side cancel bookkeeping so cancelling an unrelated `GoalID`
  no longer moves the currently tracked goal into cancel-wait state.
- Hardened `ActionClient` result handling so unrelated results received before
  the first local goal are ignored instead of dereferencing a null goal id.
- Reduced future/listener lifetime leaks by letting abandoned
  `ActionClientFuture` instances unregister their listeners when they become
  unreachable.
- Added regression tests for terminal status publication, wrong-goal cancel
  handling, ignored pre-goal results, and abandoned future lifecycle cleanup.


2026-03
-------

- Reduced server-side status lifecycle overhead by evicting completed goals
  instead of retaining them indefinitely.
- Improved status publication behavior to avoid unnecessary list clearing and
  to simplify server goal lifecycle management.
- Added reflection method caching in `ActionLibMessagesUtils` to cut repeated
  reflective lookup costs on hot paths.
- Improved topic registration detection by consulting the ROS master as a
  fallback, making connection readiness checks less dependent on callbacks.
- Simplified waiting logic and cancellation/state tracking on the client side.
- Refreshed dependencies and moved the project to `rosjava 0.4.1.1`.


2024-04
-------

- Updated the codebase for newer `rosjava` and Java 21.
- Propagated `InterruptedException` more consistently through connection and
  wait paths instead of swallowing interruption.
- Improved test harness behavior around internal vs external ROS master usage.
- Tightened API behavior around connection waits and result waits.


2024-03
-------

- Reworked connection awareness so clients can check readiness without relying
  on sleep-based polling.
- Added and expanded `FutureBasedClientNode`-based integration tests for
  startup, connection, goal completion, and cancellation.
- Replaced older test patterns with future-based flows and countdown-latch
  synchronization.
- Changed `ActionServerListener.acceptGoal()` to return `Optional<Boolean>` so
  server implementations can explicitly accept, reject, or handle goals
  manually.
- Improved logging with goal-status text mapping and more detailed client
  transition information.
- Fixed goal status naming and server-side abort/cancel handling for missing
  goals.
- Cleaned up deprecated client code and aligned tests with the newer API.
- Added support for running tests against an external ROS master when needed.


2023-06
-------

- Modernized the Gradle build: moved to Gradle 7.6, updated dependency
  configuration, refreshed project metadata, and improved publishing setup for
  Maven local/JitPack style consumption.
- Switched logging to SLF4J and cleaned up remaining logging imports/usages.
- Added typed topic participant listeners:
  `TopicParticipantListener`, `TopicSubscriberListener`, and
  `TopicPublisherListener`.
- Introduced explicit connection wait APIs:
  `waitForRegistration`, `waitForServerPublishers`,
  `waitForClientSubscribers`, and `waitForServerConnection`.
- Removed unbounded waits and pushed the codebase toward explicit timeout-based
  connection handling.
- Added a one-shot on-connection callback hook for clients once the full
  actionlib protocol wiring is observed.
- Tightened client filtering so listeners only react to messages whose
  `GoalID` matches the active client goal.
- Added and reorganized tests around the new wait methods and connection model.


2020-09 to 2020-11
------------------

- Added the Gradle wrapper and improved README/build instructions.
- Continued API cleanup by reducing visibility of internal helpers and removing
  unused or deprecated methods.
- Refined goal id generation so uniqueness does not depend on wall-clock time
  alone and remains robust across restarts.
- Reworked logging for lower overhead and better operational diagnostics.
- Replaced remaining println-style diagnostics with structured logging.
- Added and documented the `ActionFuture` usage example and future-based client
  testing approach.
- Normalized state naming by replacing the old `ERROR` client state with
  `NO_GOAL`.


2020-06 to 2020-08
------------------

- Improved server detection in `waitForActionServerToStart()` by consulting the
  ROS master via `MasterStateClient` instead of relying only on publisher
  callbacks.
- Added client-side optimizations and performance-oriented logging guards.
- Strengthened `ServerStateMachine` diagnostics and transition logging.
- Added support for multiple server callbacks/listeners and corresponding API
  cleanup around listener management.
- Added helper methods for retrieving action topic names from the server.


2019-04 to 2020-05
------------------

- Started fork-specific maintenance and refactoring work.
- Moved tests into the current package layout and reduced visibility of
  internals to clarify the public API surface.
- Deprecated older integer-based client-state access in favor of enum-based
  usage.
- Refactored `ActionClient`, `ClientGoalManager`, and related goal/state code.
- Added `ActionLibMessagesUtils` to centralize reflective message access and
  reduce duplicated reflection logic.
- Added Apache Commons Lang support used by the refactored internals.
- Cleaned up goal-id handling, test coverage, and package/build details in
  preparation for the later connection and future API work.

2018 to early 2019 upstream lineage
-----------------------------------

- Added server-side convenience helpers such as preempt/abort support.
- Fixed several correctness issues around string comparison, goal-id handling,
  recursion, null safety, and concurrent modification during status
  publication.
- Matched the default server status publication rate used by Python and C++
  ROS1 actionlib implementations.
- Improved offline compilation and modernized parts of the Gradle build and
  dependency configuration.
- Expanded future utilities and client-state exposure to make the Java API
  easier to consume.
- Performed repository cleanup, build fixes, and incremental test maintenance.


2015 to 2017 upstream lineage
-----------------------------

- Established the core Java ROS1 actionlib implementation:
  `ActionClient`, `ActionServer`, client/server state machines, goal tracking,
  goal-id generation, feedback/result wrappers, and the fibonacci-based demo
  client/server.
- Added cancel support, status heartbeat publication, result/feedback
  callbacks, and the initial wait-for-server logic.
- Built the initial test and demo infrastructure, including launch files and
  README instructions for building and running the examples.
- Iterated on client-state transitions, skipped-state handling, and basic
  actionlib protocol conformance.
- Opened the API surface for external consumption and improved packaging for
  catkin/rosjava usage.
