package com.magic.mvicore.android

import com.magic.mvicore.contract.MviIntent
import com.magic.mvicore.contract.MviMutation
import com.magic.mvicore.runtime.PulseStore
import com.magic.mvicore.runtime.PulseTasks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.Test
import java.lang.reflect.Modifier
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PulseApiVisibilityTest {
    @Test
    fun `state host exposes read only state and UI effects`() {
        val methods = PulseStateHost::class.java.declaredMethods
            .filter { Modifier.isPublic(it.modifiers) && !it.isSynthetic }
            .map { it.name }
            .toSet()

        assertEquals(setOf("getState", "getUiEffects"), methods)
    }

    @Test
    fun `ViewModel public input methods accept UI intent only`() {
        val methods = PulseSplitStoreViewModel::class.java.declaredMethods
            .filter {
                Modifier.isPublic(it.modifiers) &&
                    !it.isSynthetic &&
                    '$' !in it.name
            }
        val intentInputs = methods.filter { method ->
            method.parameterTypes.any(MviIntent::class.java::isAssignableFrom)
        }

        assertEquals(setOf("send", "trySend"), intentInputs.map { it.name }.toSet())
        assertTrueNoPublicMutationOrStoreCapability(methods)
    }

    @Test
    fun `execution owner accepts a scope without exposing its context or Job`() {
        val methods = PulseAndroidExecutionOwner::class.java.declaredMethods
            .filter { Modifier.isPublic(it.modifiers) && !it.isSynthetic }

        assertEquals(setOf("from"), methods.map { it.name }.toSet())
        assertEquals(listOf(CoroutineScope::class.java), methods.single().parameterTypes.toList())
        assertFalse(
            methods.any { method ->
                CoroutineScope::class.java.isAssignableFrom(method.returnType) ||
                    CoroutineContext::class.java.isAssignableFrom(method.returnType) ||
                    Job::class.java.isAssignableFrom(method.returnType)
            }
        )
        assertFalse(
            PulseAndroidExecutionOwner::class.java.declaredFields.any { field ->
                Modifier.isPublic(field.modifiers)
            }
        )
    }

    private fun assertTrueNoPublicMutationOrStoreCapability(
        methods: List<java.lang.reflect.Method>,
    ) {
        assertFalse(
            methods.any { method ->
                method.parameterTypes.any(MviMutation::class.java::isAssignableFrom) ||
                    PulseStore::class.java.isAssignableFrom(method.returnType) ||
                    PulseTasks::class.java.isAssignableFrom(method.returnType) ||
                    PulseIntentContext::class.java.isAssignableFrom(method.returnType) ||
                    method.name.contains("mutation", ignoreCase = true) ||
                    method.name == "getStore" ||
                    method.name == "getTasks"
            }
        )
    }
}
