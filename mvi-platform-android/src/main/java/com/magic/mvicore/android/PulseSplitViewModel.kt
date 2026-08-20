package com.magic.mvicore.android

import com.magic.mvicore.contract.DispatchResult
import com.magic.mvicore.contract.FailureContext
import com.magic.mvicore.contract.MviEffect
import com.magic.mvicore.contract.MviMutation
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.MviUiIntent
import com.magic.mvicore.contract.MutationReducer
import com.magic.mvicore.contract.SplitIntent
import com.magic.mvicore.contract.SplitIntentReducer
import com.magic.mvicore.contract.PulseFailure
import com.magic.mvicore.runtime.PulseRuntimeConfig
import com.magic.mvicore.runtime.StorePlugin
import kotlinx.coroutines.Job

/**
 * Two-lane intent ViewModel:
 * - UI lane: send(uiIntent)
 * - reducer lane: dispatchMutation(mutation)
 */
open class PulseSplitViewModel<S : MviState, UI : MviUiIntent, M : MviMutation, E : MviEffect>(
    initialState: S,
    mutationReducer: MutationReducer<S, M, E>,
    private val uiIntentExecutor: UiIntentExecutor<S, UI, M, E> = UiIntentExecutor.noop(),
    plugins: List<StorePlugin<S, SplitIntent<UI, M>, E>> = emptyList(),
    autoStart: Boolean = true,
) : PulseViewModel<S, SplitIntent<UI, M>, E>(
    initialState = initialState,
    reducer = SplitIntentReducer(mutationReducer),
    plugins = plugins,
    autoStart = autoStart,
) {
    private val legacyRuntimeConfig = PulseRuntimeConfig()

    fun send(intent: UI): DispatchResult = dispatch(SplitIntent.Ui(intent))

    protected fun mutate(mutation: M): DispatchResult = dispatch(SplitIntent.Mutation(mutation))

    final override fun onIntentAccepted(
        intent: SplitIntent<UI, M>,
        scope: IntentExecutionScope<S, SplitIntent<UI, M>, E>,
    ) {
        when (intent) {
            is SplitIntent.Ui -> {
                val executionScope = UiIntentExecutionScope(scope)
                try {
                    uiIntentExecutor.execute(intent.value, executionScope)
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    legacyRuntimeConfig.reportFailure(
                        PulseFailure.ExecutorFailure(
                            context = FailureContext(component = "legacy-ui-intent-executor"),
                            cause = failure,
                        )
                    )
                }
            }

            is SplitIntent.Mutation -> Unit
        }
    }
}

fun interface UiIntentExecutor<S : MviState, UI : MviUiIntent, M : MviMutation, E : MviEffect> {
    fun execute(intent: UI, scope: UiIntentExecutionScope<S, UI, M, E>)

    companion object {
        fun <S : MviState, UI : MviUiIntent, M : MviMutation, E : MviEffect> noop():
            UiIntentExecutor<S, UI, M, E> = UiIntentExecutor { _, _ -> }
    }
}

class UiIntentExecutionScope<S : MviState, UI : MviUiIntent, M : MviMutation, E : MviEffect>
internal constructor(
    private val delegate: IntentExecutionScope<S, SplitIntent<UI, M>, E>,
) {
    val currentState: S
        get() = delegate.currentState

    fun dispatchMutation(mutation: M): DispatchResult {
        return delegate.dispatch(SplitIntent.Mutation(mutation))
    }

    fun launch(block: suspend UiIntentExecutionScope<S, UI, M, E>.() -> Unit): Job {
        return delegate.launch {
            block(this@UiIntentExecutionScope)
        }
    }
}
