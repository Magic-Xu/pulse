package com.magic.mvicore.android

import com.magic.mvicore.contract.EnqueueResult
import com.magic.mvicore.contract.MviUiIntent

/** Listener-facing adapter that makes bounded callback rejection impossible to ignore silently. */
class PulseCallbackIngress<UI : MviUiIntent> internal constructor(
    private val submitter: (UI) -> EnqueueResult,
    private val onRejected: (intent: UI, result: EnqueueResult) -> Unit,
) {
    /** Returns the immediate admission result and reports every non-enqueued result to the handler. */
    fun submit(intent: UI): EnqueueResult {
        return submitter(intent).also { result ->
            if (result !is EnqueueResult.Enqueued) onRejected(intent, result)
        }
    }
}
