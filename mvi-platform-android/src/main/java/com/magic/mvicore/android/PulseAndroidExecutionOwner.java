package com.magic.mvicore.android;

import java.util.Objects;

import kotlin.coroutines.CoroutineContext;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.Job;

/**
 * Opaque input-only owner that binds Pulse adapter work to a caller-controlled coroutine lifetime.
 *
 * <p>Pulse does not expose or cancel the supplied parent scope. Runtime dispatchers still decide
 * where reducer, executor, saved-state, and UI delivery work runs.
 */
public final class PulseAndroidExecutionOwner {
    private final CoroutineContext coroutineContext;

    private PulseAndroidExecutionOwner(CoroutineContext coroutineContext) {
        this.coroutineContext = coroutineContext;
    }

    /** Creates an opaque owner from a scope whose Job is cancelled when the owner lifetime ends. */
    public static PulseAndroidExecutionOwner from(CoroutineScope scope) {
        Objects.requireNonNull(scope, "scope");
        CoroutineContext context = scope.getCoroutineContext();
        if (context.get(Job.Key) == null) {
            throw new IllegalArgumentException("Pulse execution owner scope must contain a Job.");
        }
        return new PulseAndroidExecutionOwner(context);
    }

    // Package-private by design: the public API accepts an owner but cannot recover its mutable
    // CoroutineContext or Job. PulseSplitStoreViewModel is the only production consumer.
    CoroutineContext coroutineContext() {
        return coroutineContext;
    }
}
