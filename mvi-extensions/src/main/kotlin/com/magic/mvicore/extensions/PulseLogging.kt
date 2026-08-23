package com.magic.mvicore.extensions

import com.magic.mvicore.contract.EffectEnvelope
import com.magic.mvicore.contract.MviIntent
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.PulseFailure
import com.magic.mvicore.contract.TransitionFrame
import com.magic.mvicore.contract.TransitionOutcome
import com.magic.mvicore.contract.UiEffect
import com.magic.mvicore.runtime.PulseRedactor
import com.magic.mvicore.runtime.PulseStorePlugin
import com.magic.mvicore.runtime.TypeOnlyPulseRedactor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

/**
 * Safe logger for the ordered Pulse runtime.
 *
 * Application-owned values are rendered only through [redactor]. The default redactor exposes
 * their types, while throwable messages and stack traces are never rendered.
 */
class PulseLoggingPlugin<S : MviState, I : MviIntent, E : UiEffect>(
    private val tag: String = DEFAULT_PULSE_LOG_TAG,
    private val sink: LogSink = ConsoleLogSink,
    private val redactor: PulseRedactor = TypeOnlyPulseRedactor,
    override val pluginId: String = DEFAULT_PULSE_LOG_PLUGIN_ID,
) : PulseStorePlugin<S, I, E> {
    override fun onTransition(frame: TransitionFrame<S, I, E>) {
        sink.log(frame.toSafePulseLogLine(tag, redactor))
    }

    override fun onFailure(failure: PulseFailure) {
        sink.log(failure.toSafePulseLogLine(tag, redactor))
    }
}

/**
 * Logs each completed frame without changing the upstream values or collection semantics.
 *
 * This operator is useful for read-only transition flows that are not exposed through a
 * [PulseStorePlugin], including platform-owned Store facades.
 */
fun <S : MviState, I : MviIntent, E : UiEffect>
    Flow<TransitionFrame<S, I, E>>.logPulseTransitions(
        tag: String = DEFAULT_PULSE_LOG_TAG,
        sink: LogSink = ConsoleLogSink,
        redactor: PulseRedactor = TypeOnlyPulseRedactor,
    ): Flow<TransitionFrame<S, I, E>> = onEach { frame ->
        sink.log(frame.toSafePulseLogLine(tag, redactor))
    }

private fun TransitionFrame<*, *, *>.toSafePulseLogLine(
    tag: String,
    redactor: PulseRedactor,
): String = buildString {
    append("[")
    append(tag)
    append("] transition")
    append(" requestId=")
    append(requestId)
    append(" sequenceId=")
    append(sequenceId)
    append(" stateRevision=")
    append(stateRevision)
    append(" outcome=")
    append(outcome.toSafePulseLogValue(redactor))
    append(" input=")
    append(redactor.redact(input))
    append(" stateBefore=")
    append(redactor.redact(stateBefore))
    append(" stateAfter=")
    append(redactor.redact(stateAfter))
    append(" effects=")
    append(uiEffects.toSafePulseLogValue(redactor))
    append(" durationNanos=")
    append(completedAtNanos - startedAtNanos)
    append(" dispatcher=")
    append(redactor.redact(dispatcher))
    append(" reducerFailure=")
    append(reducerFailure?.cause.typeName())
    append(" mailboxDepthAtStart=")
    append(mailboxDepthAtStart)
    append(" mailboxHighWater=")
    append(mailboxHighWater)
}

private fun PulseFailure.toSafePulseLogLine(
    tag: String,
    redactor: PulseRedactor,
): String = buildString {
    append("[")
    append(tag)
    append("] failure")
    append(" phase=")
    append(phase)
    append(" storeId=")
    append(redactor.redact(context.storeId))
    append(" requestId=")
    append(context.requestId)
    append(" sequenceId=")
    append(context.sequenceId)
    append(" stateRevision=")
    append(context.stateRevision)
    append(" component=")
    append(redactor.redact(context.component))
    append(" inputType=")
    append(redactor.redact(context.inputType))
    append(" thread=")
    append(redactor.redact(context.thread))
    append(" cause=")
    append(cause.typeName())

    when (this@toSafePulseLogLine) {
        is PulseFailure.MailboxOverflow -> {
            append(" capacity=")
            append(capacity)
        }

        is PulseFailure.SplitAdmissionOverflow -> {
            append(" capacity=")
            append(capacity)
        }

        is PulseFailure.TaskFailure -> {
            append(" taskKey=")
            append(redactor.redact(taskKey))
            append(" token=")
            append(token)
        }

        is PulseFailure.UndeliveredUiEffect -> {
            append(" effectId=")
            append(envelope.effectId)
            append(" effectRequestId=")
            append(envelope.requestId)
            append(" effectSequenceId=")
            append(envelope.sequenceId)
            append(" effectStateRevision=")
            append(envelope.stateRevision)
            append(" effectIndex=")
            append(envelope.index)
            append(" effect=")
            append(redactor.redact(envelope.payload))
            append(" reason=")
            append(redactor.redact(reason))
        }

        is PulseFailure.LateMutation -> {
            append(" taskKey=")
            append(redactor.redact(taskKey))
            append(" token=")
            append(token)
        }

        else -> Unit
    }
}

private fun TransitionOutcome.toSafePulseLogValue(redactor: PulseRedactor): String {
    return when (this) {
        TransitionOutcome.Changed -> "Changed"
        TransitionOutcome.ReducerFailed -> "ReducerFailed"
        TransitionOutcome.Unchanged -> "Unchanged"
        is TransitionOutcome.Ignored -> "Ignored(reason=${redactor.redact(reason)})"
    }
}

private fun List<EffectEnvelope<*>>.toSafePulseLogValue(redactor: PulseRedactor): String {
    return joinToString(prefix = "[", postfix = "]") { envelope ->
        "{effectId=${envelope.effectId}, index=${envelope.index}, " +
            "payload=${redactor.redact(envelope.payload)}}"
    }
}

private fun Throwable?.typeName(): String {
    if (this == null) return "<none>"
    return this::class.qualifiedName ?: javaClass.name
}

private const val DEFAULT_PULSE_LOG_TAG = "Pulse"
private const val DEFAULT_PULSE_LOG_PLUGIN_ID = "pulse-safe-logging"
