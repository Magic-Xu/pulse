package com.magic.mvicore.runtime

import com.magic.mvicore.contract.DispatchResult
import com.magic.mvicore.contract.MviEffect
import com.magic.mvicore.contract.MviIntent
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.Reducer
import com.magic.mvicore.contract.Store
import com.magic.mvicore.contract.StoreError
import com.magic.mvicore.contract.Subscription
import java.util.concurrent.atomic.AtomicLong

/**
 * Minimal, platform-neutral Store runtime.
 *
 * Design goals:
 * - deterministic serial dispatch
 * - simple lifecycle
 * - optional plugin hooks for extension
 */
class DefaultStore<S : MviState, I : MviIntent, E : MviEffect>(
    initialState: S,
    private val reducer: Reducer<S, I, E>,
    private val plugins: List<StorePlugin<S, I, E>> = emptyList(),
    autoStart: Boolean = true,
) : Store<S, I, E> {

    private val lock = Any()
    private val nextObserverId = AtomicLong(0L)

    private var stateValue: S = initialState
    private var started: Boolean = false
    private var closed: Boolean = false

    private val stateObservers = LinkedHashMap<Long, (S) -> Unit>()
    private val effectObservers = LinkedHashMap<Long, (E) -> Unit>()

    override val currentState: S
        get() = synchronized(lock) { stateValue }

    override val isStarted: Boolean
        get() = synchronized(lock) { started }

    override val isClosed: Boolean
        get() = synchronized(lock) { closed }

    init {
        if (autoStart) start()
    }

    override fun start() {
        val snapshot = synchronized(lock) {
            if (closed || started) return
            started = true
            stateValue
        }
        callPlugins { it.onStart(snapshot) }
    }

    override fun stop() {
        val snapshot = synchronized(lock) {
            if (closed || !started) return
            started = false
            stateValue
        }
        callPlugins { it.onStop(snapshot) }
    }

    override fun close() {
        val snapshot = synchronized(lock) {
            if (closed) return
            closed = true
            started = false
            val current = stateValue
            stateObservers.clear()
            effectObservers.clear()
            current
        }
        callPlugins { it.onClose(snapshot) }
    }

    override fun dispatch(intent: I): DispatchResult {
        val outcome = synchronized(lock) {
            if (closed) {
                return@synchronized DispatchOutcome.Rejected(StoreError.StoreClosed)
            }
            if (!started) {
                return@synchronized DispatchOutcome.Rejected(StoreError.StoreNotStarted)
            }

            val stateBefore = stateValue
            val next = try {
                reducer.reduce(stateBefore, intent)
            } catch (throwable: Throwable) {
                return@synchronized DispatchOutcome.Rejected(StoreError.ReducerFailure(throwable))
            }

            stateValue = next.state
            DispatchOutcome.Accepted(
                DispatchFrame(
                    intent = intent,
                    stateBeforeReduce = stateBefore,
                    nextState = next.state,
                    effects = next.effects.toList(),
                    stateObservers = stateObservers.values.toList(),
                    effectObservers = effectObservers.values.toList(),
                )
            )
        }

        val frame = when (outcome) {
            is DispatchOutcome.Rejected -> return reject(outcome.error)
            is DispatchOutcome.Accepted -> outcome.frame
        }
        callPlugins { it.onIntent(frame.intent, frame.stateBeforeReduce) }

        frame.stateObservers.forEach { observer -> observer(frame.nextState) }
        callPlugins { it.onState(frame.nextState) }

        frame.effects.forEach { effect ->
            frame.effectObservers.forEach { observer -> observer(effect) }
            callPlugins { it.onEffect(effect) }
        }

        return DispatchResult.Accepted
    }

    override fun observeState(observer: (S) -> Unit): Subscription {
        val id = nextObserverId.incrementAndGet()
        val snapshot: S
        val registered: Boolean
        synchronized(lock) {
            snapshot = stateValue
            if (closed) {
                registered = false
            } else {
                stateObservers[id] = observer
                registered = true
            }
        }
        observer(snapshot)
        return Subscription {
            if (!registered) return@Subscription
            synchronized(lock) {
                stateObservers.remove(id)
            }
        }
    }

    override fun observeEffect(observer: (E) -> Unit): Subscription {
        val id = nextObserverId.incrementAndGet()
        val registered: Boolean
        synchronized(lock) {
            if (closed) {
                registered = false
            } else {
                effectObservers[id] = observer
                registered = true
            }
        }
        return Subscription {
            if (!registered) return@Subscription
            synchronized(lock) {
                effectObservers.remove(id)
            }
        }
    }

    private fun reject(error: StoreError): DispatchResult.Rejected {
        val rejected = DispatchResult.Rejected(error)
        if (error is StoreError.ReducerFailure) {
            callPlugins { it.onError(error) }
        }
        callPlugins { it.onRejected(rejected) }
        return rejected
    }

    private inline fun callPlugins(block: (StorePlugin<S, I, E>) -> Unit) {
        plugins.forEach { plugin ->
            try {
                block(plugin)
            } catch (_: Throwable) {
                // Keep runtime stable even when plugin code fails.
            }
        }
    }

    private data class DispatchFrame<S : MviState, I : MviIntent, E : MviEffect>(
        val intent: I,
        val stateBeforeReduce: S,
        val nextState: S,
        val effects: List<E>,
        val stateObservers: List<(S) -> Unit>,
        val effectObservers: List<(E) -> Unit>,
    )

    private sealed interface DispatchOutcome<S : MviState, I : MviIntent, E : MviEffect> {
        data class Accepted<S : MviState, I : MviIntent, E : MviEffect>(val frame: DispatchFrame<S, I, E>) :
            DispatchOutcome<S, I, E>

        data class Rejected<S : MviState, I : MviIntent, E : MviEffect>(val error: StoreError) :
            DispatchOutcome<S, I, E>
    }
}
