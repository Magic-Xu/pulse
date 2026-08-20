package com.magic.mvicore.contract

/** Ordered runtime boundary at which a failure was observed. */
enum class FailurePhase {
    REDUCER,
    STATE_CONSUMER,
    UI_EFFECT_CONSUMER,
    PLUGIN,
    EXECUTOR,
    OVERFLOW,
    UNDELIVERED_UI_EFFECT,
    LATE_MUTATION,
    RESTORE,
    SAVE,
}

/** Redacted correlation metadata shared by all Pulse failures. */
data class FailureContext(
    val storeId: String? = null,
    val requestId: Long? = null,
    val sequenceId: Long? = null,
    val stateRevision: Long? = null,
    val component: String? = null,
    val inputType: String? = null,
    val thread: String = Thread.currentThread().name,
)

/**
 * Typed, non-fatal failures reported by controlled Pulse boundaries.
 *
 * Cancellation and fatal JVM errors are never represented by this hierarchy.
 */
sealed interface PulseFailure {
    val phase: FailurePhase
    val context: FailureContext
    val cause: Throwable?

    data class ReducerFailure(
        override val context: FailureContext,
        override val cause: Throwable,
    ) : PulseFailure {
        override val phase: FailurePhase = FailurePhase.REDUCER
    }

    data class StateConsumerFailure(
        override val context: FailureContext,
        override val cause: Throwable,
    ) : PulseFailure {
        override val phase: FailurePhase = FailurePhase.STATE_CONSUMER
    }

    data class UiEffectConsumerFailure(
        override val context: FailureContext,
        override val cause: Throwable,
    ) : PulseFailure {
        override val phase: FailurePhase = FailurePhase.UI_EFFECT_CONSUMER
    }

    data class PluginFailure(
        override val context: FailureContext,
        override val cause: Throwable,
    ) : PulseFailure {
        override val phase: FailurePhase = FailurePhase.PLUGIN
    }

    data class ExecutorFailure(
        override val context: FailureContext,
        override val cause: Throwable,
    ) : PulseFailure {
        override val phase: FailurePhase = FailurePhase.EXECUTOR
    }

    data class MailboxOverflow(
        override val context: FailureContext,
        val capacity: Int,
    ) : PulseFailure {
        override val phase: FailurePhase = FailurePhase.OVERFLOW
        override val cause: Throwable? = null
    }

    data class UndeliveredUiEffect(
        override val context: FailureContext,
        val envelope: EffectEnvelope<UiEffect>,
        val reason: String,
    ) : PulseFailure {
        override val phase: FailurePhase = FailurePhase.UNDELIVERED_UI_EFFECT
        override val cause: Throwable? = null
    }

    data class LateMutation(
        override val context: FailureContext,
        val taskKey: String,
        val token: Long,
    ) : PulseFailure {
        override val phase: FailurePhase = FailurePhase.LATE_MUTATION
        override val cause: Throwable? = null
    }

    data class StateRestoreFailure(
        override val context: FailureContext,
        override val cause: Throwable,
    ) : PulseFailure {
        override val phase: FailurePhase = FailurePhase.RESTORE
    }

    data class StateSaveFailure(
        override val context: FailureContext,
        override val cause: Throwable,
    ) : PulseFailure {
        override val phase: FailurePhase = FailurePhase.SAVE
    }
}
