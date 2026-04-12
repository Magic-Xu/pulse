package com.magic.mvicore.extensions

import com.magic.mvicore.contract.DispatchResult
import com.magic.mvicore.contract.MviEffect
import com.magic.mvicore.contract.MviIntent
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.StoreError
import com.magic.mvicore.runtime.StorePlugin

fun interface LogSink {
    fun log(line: String)
}

object ConsoleLogSink : LogSink {
    override fun log(line: String) {
        println(line)
    }
}

/**
 * Simple logging plugin for lifecycle + dispatch flow.
 */
class LoggingPlugin<S : MviState, I : MviIntent, E : MviEffect>(
    private val tag: String = "MVI",
    private val sink: LogSink = ConsoleLogSink,
) : StorePlugin<S, I, E> {

    override fun onStart(initialState: S) {
        sink.log("[$tag] start state=$initialState")
    }

    override fun onStop(lastState: S) {
        sink.log("[$tag] stop state=$lastState")
    }

    override fun onClose(lastState: S) {
        sink.log("[$tag] close state=$lastState")
    }

    override fun onIntent(intent: I, stateBeforeReduce: S) {
        sink.log("[$tag] intent=$intent stateBefore=$stateBeforeReduce")
    }

    override fun onState(state: S) {
        sink.log("[$tag] state=$state")
    }

    override fun onEffect(effect: E) {
        sink.log("[$tag] effect=$effect")
    }

    override fun onRejected(result: DispatchResult.Rejected) {
        sink.log("[$tag] rejected error=${result.error}")
    }

    override fun onError(error: StoreError.ReducerFailure) {
        sink.log("[$tag] reducerError cause=${error.cause}")
    }
}
