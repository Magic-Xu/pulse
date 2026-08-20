@file:Suppress("unused")

package com.magic.pulse.compat.surface

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import com.magic.mvicore.android.IntentExecutionScope
import com.magic.mvicore.android.IntentExecutor
import com.magic.mvicore.android.PulseSplitViewModel
import com.magic.mvicore.android.PulseViewModel
import com.magic.mvicore.android.UiIntentExecutionScope
import com.magic.mvicore.android.UiIntentExecutor
import com.magic.mvicore.android.compose.collectStateAsState
import com.magic.mvicore.android.compose.observeEffects
import com.magic.mvicore.contract.DispatchResult
import com.magic.mvicore.contract.MutationReducer
import com.magic.mvicore.contract.MviEffect
import com.magic.mvicore.contract.MviIntent
import com.magic.mvicore.contract.MviMutation
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.MviUiIntent
import com.magic.mvicore.contract.Next
import com.magic.mvicore.contract.Reducer
import com.magic.mvicore.contract.SplitIntent
import com.magic.mvicore.contract.SplitIntentReducer
import com.magic.mvicore.contract.Store
import com.magic.mvicore.contract.StoreError
import com.magic.mvicore.contract.StoreLifecycle
import com.magic.mvicore.contract.Subscription
import com.magic.mvicore.extensions.ConsoleLogSink
import com.magic.mvicore.extensions.LogSink
import com.magic.mvicore.extensions.LoggingPlugin
import com.magic.mvicore.extensions.StateTransition
import com.magic.mvicore.extensions.StateTransitionPlugin
import com.magic.mvicore.runtime.DefaultStore
import com.magic.mvicore.runtime.StorePlugin
import kotlinx.coroutines.Job

/**
 * Frozen source fixture derived from the public declarations in tag v0.2.0.
 *
 * Both Android flavors compile this exact file: one against 0.2.0 from Maven Central and one against
 * the staged candidate. Archive-level japicmp tasks cover every public/protected bytecode member;
 * this fixture additionally protects Kotlin call shapes, named parameters, companion factories,
 * extension functions, protected hooks, and Compose entry points.
 */
object LegacyContractSurface {
    fun nextFactories(state: LegacyState): Next<LegacyState, LegacyEffect> {
        val constructed = Next(state = state, effects = listOf(LegacyEffect.Completed))
        val (copiedState, copiedEffects) = constructed.copy().let { it.state to it.effects }
        check(copiedState == constructed.component1())
        check(copiedEffects == constructed.component2())

        Next.just<LegacyState, LegacyEffect>(state)
        Next.withEffect(state, LegacyEffect.Completed)
        return Next.withEffects(state, setOf(LegacyEffect.Completed))
    }

    fun reducers(state: LegacyState): Next<LegacyState, LegacyEffect> {
        val reducer = Reducer<LegacyState, LegacyIntent, LegacyEffect> { previous, _ ->
            Next.just(previous)
        }
        val mutationReducer = MutationReducer<LegacyState, LegacyMutation, LegacyEffect> { previous, _ ->
            Next.just(previous)
        }
        val splitReducer = SplitIntentReducer<LegacyState, LegacyUiIntent, LegacyMutation, LegacyEffect>(
            mutationReducer = mutationReducer,
        )
        reducer.reduce(state, LegacyIntent.Refresh)
        splitReducer.reduce(state, SplitIntent.Ui(LegacyUiIntent.Refresh))
        return splitReducer.reduce(state, SplitIntent.Mutation(LegacyMutation.Loaded))
    }

    fun splitValues(): Pair<LegacyUiIntent, LegacyMutation> {
        val ui = SplitIntent.Ui(LegacyUiIntent.Refresh)
        val mutation = SplitIntent.Mutation(LegacyMutation.Loaded)
        return ui.value to mutation.value
    }

    fun lifecycle(lifecycle: StoreLifecycle) {
        lifecycle.start()
        lifecycle.stop()
        lifecycle.close()
        lifecycle.isStarted
        lifecycle.isClosed
    }

    fun results(): Throwable {
        DispatchResult.Accepted
        val cause = IllegalStateException("fixture")
        val reducerFailure = StoreError.ReducerFailure(cause)
        StoreError.StoreNotStarted
        StoreError.StoreClosed
        val rejected = DispatchResult.Rejected(reducerFailure)
        check(rejected.error === reducerFailure)
        return reducerFailure.cause
    }

    fun subscription(): Subscription {
        val subscription = Subscription { }
        subscription.cancel()
        return subscription
    }
}

object LegacyRuntimeSurface {
    fun store(): Store<LegacyState, LegacyIntent, LegacyEffect> {
        val store = DefaultStore(
            initialState = LegacyState(0),
            reducer = LegacyReducer,
            plugins = listOf(LegacyPlugin),
            autoStart = false,
        )
        store.currentState
        store.isStarted
        store.isClosed
        store.start()
        store.dispatch(LegacyIntent.Refresh)
        store.observeState { state -> state.value }
        store.observeEffect { effect -> effect.hashCode() }
        store.stop()
        store.close()
        return store
    }

    fun pluginCallbacks(plugin: StorePlugin<LegacyState, LegacyIntent, LegacyEffect>) {
        val state = LegacyState(0)
        plugin.onStart(state)
        plugin.onIntent(LegacyIntent.Refresh, state)
        plugin.onState(state)
        plugin.onEffect(LegacyEffect.Completed)
        plugin.onRejected(DispatchResult.Rejected(StoreError.StoreClosed))
        plugin.onError(StoreError.ReducerFailure(IllegalStateException("fixture")))
        plugin.onStop(state)
        plugin.onClose(state)
    }
}

object LegacyExtensionsSurface {
    fun logging(): Pair<LoggingPlugin<LegacyState, LegacyIntent, LegacyEffect>, LogSink> {
        val sink = LogSink { line -> line.length }
        sink.log("fixture")
        ConsoleLogSink.log("fixture")
        LoggingPlugin<LegacyState, LegacyIntent, LegacyEffect>()
        return LoggingPlugin<LegacyState, LegacyIntent, LegacyEffect>(
            tag = "compat",
            sink = sink,
        ) to sink
    }

    fun transition(): StateTransition<LegacyState, LegacyIntent> {
        val state = LegacyState(0)
        val transition = StateTransition<LegacyState, LegacyIntent>(
            previous = state,
            intent = LegacyIntent.Refresh,
            next = state,
        )
        val (previous, intent, next) = transition.copy()
        check(previous == transition.previous)
        check(intent == transition.intent)
        check(next == transition.next)

        val plugin = StateTransitionPlugin<LegacyState, LegacyIntent, LegacyEffect> { observed ->
            observed.previous
            observed.intent
            observed.next
        }
        LegacyRuntimeSurface.pluginCallbacks(plugin)
        return transition
    }
}

object LegacyAndroidSurface {
    fun executor(scope: IntentExecutionScope<LegacyState, LegacyIntent, LegacyEffect>): Job {
        val executor = IntentExecutor<LegacyState, LegacyIntent, LegacyEffect> { intent, executionScope ->
            executionScope.currentState
            executionScope.dispatch(intent)
        }
        executor.execute(LegacyIntent.Refresh, scope)
        IntentExecutor.noop<LegacyState, LegacyIntent, LegacyEffect>().execute(LegacyIntent.Refresh, scope)
        scope.currentState
        scope.dispatch(LegacyIntent.Refresh)
        return scope.launch { currentState }
    }

    fun splitExecutor(
        scope: UiIntentExecutionScope<LegacyState, LegacyUiIntent, LegacyMutation, LegacyEffect>,
    ): Job {
        val executor = UiIntentExecutor<LegacyState, LegacyUiIntent, LegacyMutation, LegacyEffect> {
                intent, executionScope ->
            executionScope.currentState
            when (intent) {
                LegacyUiIntent.Refresh -> executionScope.dispatchMutation(LegacyMutation.Loaded)
            }
        }
        executor.execute(LegacyUiIntent.Refresh, scope)
        UiIntentExecutor.noop<LegacyState, LegacyUiIntent, LegacyMutation, LegacyEffect>()
            .execute(LegacyUiIntent.Refresh, scope)
        scope.currentState
        scope.dispatchMutation(LegacyMutation.Loaded)
        return scope.launch { currentState }
    }
}

open class LegacyPulseViewModel : PulseViewModel<LegacyState, LegacyIntent, LegacyEffect>(
    initialState = LegacyState(0),
    reducer = LegacyReducer,
    plugins = listOf(LegacyPlugin),
    autoStart = false,
) {
    override fun onIntentAccepted(
        intent: LegacyIntent,
        scope: IntentExecutionScope<LegacyState, LegacyIntent, LegacyEffect>,
    ) {
        scope.currentState
    }

    fun executionScope(): IntentExecutionScope<LegacyState, LegacyIntent, LegacyEffect> {
        return intentExecutionScope
    }

    fun clearForFixture() {
        onCleared()
    }
}

class LegacyPulseSplitViewModel : PulseSplitViewModel<
    LegacyState,
    LegacyUiIntent,
    LegacyMutation,
    LegacyEffect,
>(
    initialState = LegacyState(0),
    mutationReducer = LegacyMutationReducer,
    uiIntentExecutor = UiIntentExecutor { _, scope -> scope.currentState },
    plugins = emptyList(),
    autoStart = false,
) {
    fun sendForFixture(): DispatchResult = send(LegacyUiIntent.Refresh)

    fun mutateForFixture(): DispatchResult = mutate(LegacyMutation.Loaded)
}

@Composable
fun LegacyComposeSurface(store: Store<LegacyState, LegacyIntent, LegacyEffect>) {
    val state: State<LegacyState> = store.collectStateAsState()
    state.value
    store.observeEffects { effect -> effect.hashCode() }
}

data class LegacyState(val value: Int) : MviState

sealed interface LegacyIntent : MviIntent {
    data object Refresh : LegacyIntent
}

sealed interface LegacyUiIntent : MviUiIntent {
    data object Refresh : LegacyUiIntent
}

sealed interface LegacyMutation : MviMutation {
    data object Loaded : LegacyMutation
}

sealed interface LegacyEffect : MviEffect {
    data object Completed : LegacyEffect
}

object LegacyReducer : Reducer<LegacyState, LegacyIntent, LegacyEffect> {
    override fun reduce(
        previous: LegacyState,
        intent: LegacyIntent,
    ): Next<LegacyState, LegacyEffect> = Next.just(previous)
}

object LegacyMutationReducer : MutationReducer<LegacyState, LegacyMutation, LegacyEffect> {
    override fun reduce(
        previous: LegacyState,
        mutation: LegacyMutation,
    ): Next<LegacyState, LegacyEffect> = Next.just(previous)
}

object LegacyPlugin : StorePlugin<LegacyState, LegacyIntent, LegacyEffect>
