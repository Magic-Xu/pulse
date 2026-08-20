package com.magic.mvicore.testing

import org.junit.Test

class DefaultPulseStoreTckTest {
    private val tck = PulseStoreTck()

    @Test
    fun sequentialOrdering() = tck.sequentialOrdering()

    @Test
    fun concurrentTotalOrder() = tck.concurrentTotalOrder()

    @Test
    fun stateSubscriptionStartsWithAtomicSnapshot() = tck.stateSubscriptionStartsWithAtomicSnapshot()

    @Test
    fun equalChangedNormalizesToUnchanged() = tck.equalChangedNormalizesToUnchanged()

    @Test
    fun ignoredInputIsObservable() = tck.ignoredInputIsObservable()

    @Test
    fun reducerFailureIsTypedAndProcessorContinues() = tck.reducerFailureIsTypedAndProcessorContinues()

    @Test
    fun effectCardinalityAndEnvelopeOrder() = tck.effectCardinalityAndEnvelopeOrder()

    @Test
    fun effectConsumerFailureIsIsolatedAndReportedOnce() =
        tck.effectConsumerFailureIsIsolatedAndReportedOnce()

    @Test
    fun overflowIsExplicitAndReported() = tck.overflowIsExplicitAndReported()

    @Test
    fun closeEstablishesCutoffAndDrains() = tck.closeEstablishesCutoffAndDrains()

    @Test
    fun cancelledWaitingSenderDoesNotConsumeSequence() = tck.cancelledWaitingSenderDoesNotConsumeSequence()

    @Test
    fun reentrantSendRunsAfterCurrentFrame() = tck.reentrantSendRunsAfterCurrentFrame()

    @Test
    fun pluginFailureIsIsolatedAndReportedOnce() = tck.pluginFailureIsIsolatedAndReportedOnce()

    @Test
    fun tenThousandInputStress() = tck.tenThousandInputStress()
}
