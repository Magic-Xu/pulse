package com.magic.mvicore.runtime

import com.magic.mvicore.contract.PulseFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.atomic.AtomicLong

private const val DEFAULT_MAILBOX_CAPACITY = 64
private const val DEFAULT_EFFECT_BUFFER_CAPACITY = 16

/** Full-mailbox behavior for the non-suspending [PulseStore.trySend] path. */
enum class MailboxOverflowPolicy {
    /** Return `EnqueueResult.Full`; the result itself is the caller-visible signal. */
    REJECT,

    /** Return `EnqueueResult.Full` and publish one coalesced typed overflow diagnostic. */
    REJECT_AND_REPORT,
}

/** Monotonic time source used for transition timing. */
fun interface PulseClock {
    fun nanoTime(): Long
}

/** Redacts application-owned values before they reach a diagnostic sink. */
fun interface PulseRedactor {
    fun redact(value: Any?): String
}

/** Receives typed, non-fatal runtime failures. */
fun interface PulseErrorHandler {
    fun onFailure(
        storeId: String,
        failure: PulseFailure,
        redactor: PulseRedactor,
    )
}

/** Default clock backed by the JVM monotonic clock. */
object SystemPulseClock : PulseClock {
    override fun nanoTime(): Long = System.nanoTime()
}

/**
 * Safe default redactor. It exposes the value type, never the value's string form.
 */
object TypeOnlyPulseRedactor : PulseRedactor {
    override fun redact(value: Any?): String {
        if (value == null) return "<null>"
        val type = value::class.qualifiedName ?: value.javaClass.name
        return "<$type>"
    }
}

/**
 * Non-silent default reporter. It deliberately omits throwable messages and effect payloads.
 */
object StandardErrorPulseErrorHandler : PulseErrorHandler {
    override fun onFailure(
        storeId: String,
        failure: PulseFailure,
        redactor: PulseRedactor,
    ) {
        val context = failure.context
        val component = context.component?.let(redactor::redact) ?: "<none>"
        val causeType = failure.cause?.javaClass?.name ?: "<none>"
        System.err.println(
            "[Pulse] store=$storeId phase=${failure.phase} " +
                "requestId=${context.requestId} sequenceId=${context.sequenceId} " +
                "stateRevision=${context.stateRevision} inputType=${context.inputType} " +
                "thread=${context.thread} component=$component cause=$causeType"
        )
    }
}

/** Runtime dependencies and bounded-capacity settings shared by the v0.3 engine. */
data class PulseRuntimeConfig(
    val mailboxCapacity: Int = DEFAULT_MAILBOX_CAPACITY,
    val overflowPolicy: MailboxOverflowPolicy = MailboxOverflowPolicy.REJECT_AND_REPORT,
    val effectBufferCapacity: Int = DEFAULT_EFFECT_BUFFER_CAPACITY,
    val storeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    val consumerDispatcher: CoroutineDispatcher = Dispatchers.Default,
    val clock: PulseClock = SystemPulseClock,
    val errorHandler: PulseErrorHandler = StandardErrorPulseErrorHandler,
    val strictMode: Boolean = false,
    val redactor: PulseRedactor = TypeOnlyPulseRedactor,
    val storeId: String = nextStoreId(),
) {
    init {
        require(mailboxCapacity > 0) { "mailboxCapacity must be greater than zero." }
        require(effectBufferCapacity > 0) { "effectBufferCapacity must be greater than zero." }
        require(storeId.isNotBlank()) { "storeId must not be blank." }
    }

    /** Reports a typed framework failure through the configured redacted boundary. */
    fun reportFailure(failure: PulseFailure) {
        try {
            errorHandler.onFailure(storeId, failure, redactor)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            StandardErrorPulseErrorHandler.onFailure(storeId, failure, redactor)
            if (strictMode) throw error
        }
    }

    private companion object {
        val nextStoreNumber = AtomicLong(0L)

        fun nextStoreId(): String = "pulse-store-${nextStoreNumber.incrementAndGet()}"
    }
}
