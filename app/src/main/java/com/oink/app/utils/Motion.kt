package com.oink.app.utils

import android.content.ContentResolver
import android.provider.Settings

/**
 * Reduce-motion support.
 *
 * Users can disable animations system-wide (Settings > Accessibility > Remove
 * animations, or Developer options > Animator duration scale = off), which sets
 * [Settings.Global.ANIMATOR_DURATION_SCALE] to `0`. Every animated surface must
 * honor that: when motion is reduced, jump straight to the final value instead of
 * animating. Accessibility guidance in docs/habit-psychology.md calls this out
 * explicitly ("support reduced motion").
 *
 * The scale read is split from the predicate so the gating decision is a pure,
 * unit-testable function while the Android read stays a thin wrapper.
 */
object Motion {

    /**
     * The system animator duration scale, or `1f` (normal) when it cannot be
     * read. `0f` means the user has turned animations off.
     */
    fun animatorDurationScale(resolver: ContentResolver): Float =
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)

    /**
     * Whether animations should be skipped for a given [animatorDurationScale].
     *
     * Pure so the gate is testable without an Android framework. A scale of
     * exactly `0f` (animations off) reduces motion; any positive scale animates.
     */
    fun prefersReducedMotion(animatorDurationScale: Float): Boolean = animatorDurationScale == 0f
}
