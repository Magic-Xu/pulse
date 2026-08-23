# Migrating from Pulse 0.3 to 0.4

Chinese version: [MIGRATION_0.3_TO_0.4.zh-CN.md](./MIGRATION_0.3_TO_0.4.zh-CN.md)

> Status: **release candidate — not public yet**. The `0.4.0` coordinates below become usable only
> after publication is verified and this status is updated.

Pulse 0.4 is additive over 0.3. Existing features can upgrade without changing their reducer or
ViewModel architecture, then adopt the new diagnostics, callback adapter, and Android test host
where they solve a concrete integration problem.

## 1. Align dependencies after publication

Upgrade the highest-level Pulse entry that the application already uses, then add only optional
capabilities it needs:

```kotlin
dependencies {
    implementation("io.github.magic-xu:mvi-platform-android-compose:0.4.0")

    // Optional production extensions.
    implementation("io.github.magic-xu:mvi-extensions:0.4.0")

    // Optional: real PulseSplitStoreViewModel tests in an Android test module.
    testImplementation("io.github.magic-xu:mvi-platform-android-testing:0.4.0")
}
```

Use `mvi-platform-android` when Compose is not needed and `mvi-core-runtime` for pure Kotlin/JVM.
`mvi-platform-android-testing` exposes `mvi-testing` transitively; pure-JVM Store tests can continue
to depend on `mvi-testing` directly. Keep every Pulse module in one project on version `0.4.0`.

## 2. Choose the correct Split input path

From a coroutine, prefer suspending `send(UI)`. It applies bounded backpressure and returns after
the serial executor has made its decision:

```kotlin
when (val result = viewModel.send(ScreenUiIntent.Refresh)) {
    PulseIntentExecutionResult.Completed -> Unit
    is PulseIntentExecutionResult.Ignored -> handleIgnored(result.reason)
    is PulseIntentExecutionResult.Failed -> handleExecutorFailure(result.cause)
    PulseIntentExecutionResult.Cancelled -> Unit
    is PulseIntentExecutionResult.Rejected -> handleClosed(result.reason)
}
```

If the caller is cancelled while waiting for capacity, the UI intent is not admitted. Cancellation
after admission can still suppress work until the frame transfers to the executor; after transfer,
the accepted work is runtime-owned and releases capacity during executor cleanup.

For a listener that cannot suspend, create one callback ingress and provide an overload/lifecycle
policy:

```kotlin
val ingress = viewModel.callbackIngress { intent, result ->
    callbackRejectionPolicy.handle(intent, result)
}

listener.onEvent { event ->
    ingress.submit(ScreenUiIntent.ExternalEvent(event))
}
```

`PulseCallbackIngress.submit` returns the immediate `EnqueueResult` and calls the mandatory handler
for every result other than `Enqueued`. Direct `trySend` remains available, but its caller must
handle `Full` and `Rejected` itself.

In 0.4, Split capacity covers the complete admitted UI-to-executor path. Code that previously
assumed a successful core-mailbox offer meant downstream executor capacity was available must use
the returned result. Do not add an unbounded queue to hide `Full`: use `send` for backpressure, or
make event conflation, resynchronization, or loss reporting an explicit application policy.

## 3. Observe Split transitions without exposing mutation authority

`PulseSplitStoreViewModel.transitions` is now public and read-only:

```kotlin
viewModel.transitions.collect { frame ->
    when (val input = frame.input) {
        is PulseSplitInput.Ui -> observeUiInput(frame.requestId, input.value)
        is PulseSplitInput.Mutation -> observeMutation(frame.requestId, input.value)
    }
}
```

The observed `PulseSplitInput` variants contain only their UI or Mutation value; the live runtime
input and its completion, token, and admission metadata remain internal. Use this Flow for
diagnostics and tests, not as a second command path. It does not expose the Store or a mutation
sender, and collectors should not infer executor or task completion from transition publication.

## 4. Overlay Android defaults onto custom runtime options

When constructing a production Split ViewModel from a custom `PulseRuntimeConfig`, apply the Android
overlay:

```kotlin
val baseConfig = PulseRuntimeConfig(
    mailboxCapacity = 128,
    errorHandler = applicationFailureHandler,
    storeId = "checkout",
)

val viewModel = PulseSplitStoreViewModel(
    initialState = initialState,
    mutationReducer = reducer,
    uiIntentExecutor = executor,
    runtimeConfig = androidPulseRuntimeConfig(baseConfig),
)
```

The overlay preserves every non-dispatcher option and applies `Dispatchers.Main.immediate` to Store
and consumer work. Continue using the no-argument `androidPulseRuntimeConfig()` when no custom
options are required. Tests that deliberately own dispatchers should use the testing factories
instead of applying the production overlay.

## 5. Handle correlated task failures

Keyed-task exceptions are no longer classified as executor failures. Update exhaustive failure
handling for the new phase and failure type:

```kotlin
val applicationFailureHandler = PulseErrorHandler { storeId, failure, redactor ->
    if (failure is PulseFailure.TaskFailure) {
        reportTaskFailure(
            storeId = storeId,
            taskKey = failure.taskKey,
            token = failure.token,
            requestId = failure.context.requestId,
            inputType = failure.context.inputType,
            causeType = failure.cause::class.qualifiedName,
        )
    } else {
        reportPulseFailure(storeId, failure, redactor)
    }
}
```

Also add `FailurePhase.TASK`, `FailurePhase.ADMISSION`,
`PulseFailure.SplitAdmissionOverflow`, and `PulseFailure.TaskFailure` to exhaustive mappings.
Cancellation is still cancellation, not a `TaskFailure`; fatal JVM errors are still propagated.

Do not put raw exception messages into State or `UiEffect`. Map expected failures to typed domain
statuses or UI resource identifiers, and keep diagnostic detail behind the configured redacted
failure boundary.

## 6. Test a real Split ViewModel

Use `mvi-platform-android-testing` when the behavior under test crosses UI admission, the serial
executor, mutations, effects, or Android ownership:

```kotlin
@Test
fun increment() = runPulseSplitTest {
    val host = splitHost(
        initialState = CounterState(),
        mutationReducer = CounterReducer,
        uiIntentExecutor = CounterExecutor,
    )

    assertEquals(
        PulseIntentExecutionResult.Completed,
        host.sendAndDrain(CounterUiIntent.Increment),
    )
    assertEquals(CounterState(count = 1), host.stateProbe.latest())
    assertTrue(host.transitionProbe.snapshot().isNotEmpty())
    host.failureProbe.assertEmpty()
}
```

The host also exposes `effectProbe` and `viewModel`, and accepts an application-owned ViewModel
factory when a subtype must be tested. Android Main, the runtime, and the execution owner share one
`TestCoroutineScheduler`; hosts close automatically when `runPulseSplitTest` finishes.

`sendAndDrain` waits for the executor decision and directly awaited mutations only. It does not wait
for keyed tasks, tickers, or infinite Flows. Observe those through task handles, probes, or explicit
virtual-time advancement. Use `closeAndDrain` when the close boundary itself is under test.

## 7. Adopt safe modern logging

For a `DefaultPulseStore`, attach the new plugin:

```kotlin
val logging = PulseLoggingPlugin<AppState, AppIntent, AppEffect>(
    tag = "Checkout",
    sink = LogSink(logger::debug),
)

val store = DefaultPulseStore(
    initialState = initialState,
    reducer = reducer,
    plugins = listOf(logging),
)
```

For a read-only transition Flow, including a Split ViewModel:

```kotlin
viewModel.transitions
    .logPulseTransitions(tag = "Checkout", sink = LogSink(logger::debug))
    .collect { frame -> consume(frame) }
```

The default `TypeOnlyPulseRedactor` reveals types, not application values. Throwable messages and
stack traces are never rendered by these helpers. Pass a custom `PulseRedactor` only after the
application has defined the data policy for its log destination. The Flow operator is lazy and does
not change frame values. It follows normal `onEach` failure semantics, so its `LogSink` must not
throw unless terminating that diagnostic collection is intentional.

## 8. Separate admission, executor decisions, and task completion

Handle `TaskLaunchResult` inside the executor instead of treating every launch request as accepted:

```kotlin
val launch = context.launchTask(TaskKey("refresh"), TaskPolicy.Latest) {
    mutate(ScreenMutation.Loaded(repository.load()))
}

when (launch) {
    is TaskLaunchResult.Accepted -> PulseIntentExecutionDecision.Completed
    TaskLaunchResult.DroppedWhileRunning ->
        PulseIntentExecutionDecision.Ignored("refresh-already-running")
    is TaskLaunchResult.QueueFull ->
        PulseIntentExecutionDecision.Ignored("refresh-queue-full")
    is TaskLaunchResult.ParallelLimitReached ->
        PulseIntentExecutionDecision.Ignored("refresh-parallel-limit")
    TaskLaunchResult.Closed ->
        PulseIntentExecutionDecision.Ignored("task-registry-closed")
}
```

`PulseIntentExecutionResult.Completed` only mirrors the executor's `Completed` decision. It does not
mean the accepted keyed task has finished. `EnqueueResult.Enqueued` means only that the UI input was
admitted. Await an accepted task handle only for a process-local coordination need; represent
business completion in typed State or a durable operation record.

## 9. Keep durable and multi-owner work outside Pulse

Use keyed tasks to own process-local callback/Flow collection. Pair callback registration with
`callbackFlow` and `awaitClose`, apply `conflate` only to replaceable snapshots, and emit results
through token-bound `mutate`. Replacement and ViewModel close then cancel the same owned path and
stale task generations cannot commit mutations.

Work that must survive process death needs an application-owned `operationId`, persisted command and
status, WorkManager or Service execution, and startup reconciliation. Pulse State is a process-local
projection of that durable record. A root Store, feature Store, and Service-owned Store should each
have one explicit lifecycle owner and coordinate through domain ports, repositories, persistence,
or explicit IPC—not through a global mutation bus.

Pulse 0.4 intentionally does not add a SourceRegistry, generic scheduler/recovery API, progress
sink, Service base class, global Store bus, writer DSL/KSP layer, lint suite, or developer panel.
See [Pulse 0.4 Integration Patterns](./INTEGRATION_PATTERNS.md) for reference patterns.

## Migration checklist

- [ ] Keep all Pulse modules on one version and add Android testing only where a real Split host is
      needed.
- [ ] Use `send` for suspending backpressure; handle every non-suspending admission result.
- [ ] Give callback ingress an explicit `Full`/`Rejected` policy.
- [ ] Update exhaustive failure handling for task and Split-admission failures.
- [ ] Apply `androidPulseRuntimeConfig(base)` to custom production Android runtime options.
- [ ] Treat Split transitions as read-only diagnostics, never as a mutation path.
- [ ] Handle every `TaskLaunchResult`; do not equate executor `Completed` with task completion.
- [ ] Keep exception messages out of State, effects, and default logs.
- [ ] Test real Split ordering, effects, failures, overload, task replacement, and close where they
      matter to the feature.
- [ ] Keep durable scheduling, recovery, and coordination between owners in the application layer.
