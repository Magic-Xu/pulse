package com.magic.mvicore.extensions

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class StateSelectionTest {
    @Test
    fun `selector emits initial value and only distinct selected changes`() = runTest {
        val state = MutableStateFlow(RootState(selected = 0, unrelated = 0))
        val values = async(start = CoroutineStart.UNDISPATCHED) {
            state.selectDistinct(RootState::selected).take(3).toList()
        }

        state.value = RootState(selected = 0, unrelated = 1)
        runCurrent()
        state.value = RootState(selected = 1, unrelated = 1)
        runCurrent()
        state.value = RootState(selected = 1, unrelated = 2)
        runCurrent()
        state.value = RootState(selected = 2, unrelated = 2)
        runCurrent()

        assertEquals(listOf(0, 1, 2), values.await())
    }

    @Test
    fun `custom equivalence controls semantic distinctness`() = runTest {
        val state = MutableStateFlow(0)
        val values = async(start = CoroutineStart.UNDISPATCHED) {
            state.selectDistinct(
                selector = { it },
                equivalent = { left, right -> left / 10 == right / 10 },
            ).take(3).toList()
        }

        listOf(1, 9, 10, 19, 20).forEach { value ->
            state.value = value
            runCurrent()
        }

        assertEquals(listOf(0, 10, 20), values.await())
    }

    private data class RootState(
        val selected: Int,
        val unrelated: Int,
    )
}
