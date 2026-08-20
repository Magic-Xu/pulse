package com.magic.mvicore.contract

/**
 * Marker for foreground, one-shot UI instructions.
 *
 * Durable work and business state must not be represented as a [UiEffect].
 */
interface UiEffect : MviEffect
