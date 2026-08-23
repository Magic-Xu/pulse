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
    fun `Split transitions expose observation values without live runtime authority`() {
        val transitionGetter = PulseSplitStoreViewModel::class.java.declaredMethods
            .single { it.name == "getTransitions" }
        val observedTypes = listOf(
            PulseSplitInput.Ui::class.java,
            PulseSplitInput.Mutation::class.java,
        )

        assertFalse(transitionGetter.genericReturnType.typeName.contains("SplitStoreInput"))
        observedTypes.forEach { type ->
            val exposedTypeNames = buildList {
                type.declaredFields.mapTo(this) { it.genericType.typeName }
                type.declaredMethods.mapTo(this) { it.genericReturnType.typeName }
                type.declaredMethods.flatMapTo(this) { method ->
                    method.genericParameterTypes.map(java.lang.reflect.Type::getTypeName)
                }
                type.declaredConstructors.flatMapTo(this) { constructor ->
                    constructor.genericParameterTypes.map(java.lang.reflect.Type::getTypeName)
                }
            }
            assertFalse(exposedTypeNames.any { it.contains("CompletableDeferred") })
            assertFalse(exposedTypeNames.any { it.contains("SplitAdmissionLease") })
            assertFalse(exposedTypeNames.any { it.contains("TaskToken") })
        }
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
