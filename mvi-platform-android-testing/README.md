# Pulse Android Split Testing

`mvi-platform-android-testing` runs a real `PulseSplitStoreViewModel` with one virtual-time
scheduler shared by Android Main, the Pulse runtime, and the explicit execution owner.

This is an optional test-only module. Pulse `0.4.0` is available from Maven Central.

```kotlin
dependencies {
    testImplementation("io.github.magic-xu:mvi-platform-android-testing:0.4.0")
}
```

```kotlin
@Test
fun counter() = runPulseSplitTest {
    val host = splitHost(
        initialState = CounterState(),
        mutationReducer = CounterReducer,
        uiIntentExecutor = CounterExecutor,
    )

    assertEquals(
        PulseIntentExecutionResult.Completed,
        host.sendAndDrain(CounterIntent.Increment),
    )
    assertEquals(CounterState(count = 1), host.stateProbe.latest())
    assertEquals(2, host.transitionProbe.snapshot().size)
    host.failureProbe.assertEmpty()
}
```

Use the factory overload to construct an application-owned ViewModel subtype:

```kotlin
val host = splitHost<CounterState, CounterIntent, CounterMutation, CounterEffect, CounterViewModel> {
        runtimeConfig, executionOwner ->
    CounterViewModel(runtimeConfig, executionOwner)
}
```

`sendAndDrain` waits for the selected intent's serial executor decision and mutations directly
awaited by that executor. It does not wait for keyed tasks, tickers, or infinite sources. Observe
those through their own task handles or probes. Every host is closed automatically when
`runPulseSplitTest` ends; `closeAndDrain` is available for assertions about the close boundary.
Because Android Main is process-global, `runPulseSplitTest` serializes callers and rejects nesting.
