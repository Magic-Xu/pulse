package com.magic.mvicore.testing

import org.junit.Test

class DefaultPulseTaskTckTest {
    private val tck = PulseTaskTck()

    @Test
    fun latestReplacesActiveRequestBeforeStartingTheNextGeneration() =
        tck.latestReplacesActiveRequestBeforeStartingTheNextGeneration()

    @Test
    fun dropWhileRunningRejectsOverlapWithoutCreatingAHandle() =
        tck.dropWhileRunningRejectsOverlapWithoutCreatingAHandle()

    @Test
    fun queueRunsEveryAdmittedRequestInFifoOrder() =
        tck.queueRunsEveryAdmittedRequestInFifoOrder()

    @Test
    fun parallelStartsEveryAdmittedRequestWithAnIndependentToken() =
        tck.parallelStartsEveryAdmittedRequestWithAnIndependentToken()

    @Test
    fun conflateKeepsTheActiveAndOnlyTheNewestPendingRequest() =
        tck.conflateKeepsTheActiveAndOnlyTheNewestPendingRequest()

    @Test
    fun cancellationAndCloseInvalidateTokensBeforeCompletingHandles() =
        tck.cancellationAndCloseInvalidateTokensBeforeCompletingHandles()

    @Test
    fun staleTokenValidationReportsOneLateMutationDiagnostic() =
        tck.staleTokenValidationReportsOneLateMutationDiagnostic()

    @Test
    fun taskFailureIsTypedAndCancellationRemainsSilent() =
        tck.taskFailureIsTypedAndCancellationRemainsSilent()
}
