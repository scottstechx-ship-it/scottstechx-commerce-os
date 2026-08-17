package com.scottstechx.commerceos.ui.animation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavBackStackEntry
import kotlinx.coroutines.delay

/**
 * Motion design tokens. Kept short (≤300ms) for "fast" feel; use longer
 * values only for hero moments like a seller card entering the top of a
 * ranked list. Cubic-bezier (0.2, 0.0, 0.0, 1.0) approximates Material 3
 * "emphasized" easing — quick start, gentle settle.
 */
object Motion {
    /** Standard screen-element entrance. */
    const val ENTER_DURATION = 260
    /** Standard screen-element exit. */
    const val EXIT_DURATION = 180
    /** Per-row stagger delay (ms). 30ms keeps a 20-row list under 1s. */
    const val STAGGER_MS = 30L
    /** Hero entrance, used sparingly for top-ranked items. */
    const val HERO_DURATION = 420

    val Emphasized = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val Standard = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)
}

/**
 * Returns a [MutableTransitionState] that flips to `true` on the first
 * composition frame, then stays true. Use this to drive one-shot enter
 * animations off `animateFloat`-style state without re-triggering on
 * recomposition.
 */
@Composable
fun rememberEnterTransition(delayMs: Int = 0): MutableTransitionState<Boolean> {
    val state = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) {
        if (delayMs > 0) delay(delayMs.toLong())
        state.targetState = true
    }
    return state
}

/**
 * NavHost-level transitions: push left/right with a small fade, like
 * the platform default but snappier. Both forward and back navigation
 * are covered.
 */
object NavTransitions {
    fun enter(): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
        {
            slideInHorizontally(
                animationSpec = tween(Motion.ENTER_DURATION, easing = Motion.Emphasized)
            ) { it / 6 } + fadeIn(tween(Motion.ENTER_DURATION))
        }

    fun exit(): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
        {
            slideOutHorizontally(
                animationSpec = tween(Motion.EXIT_DURATION, easing = Motion.Emphasized)
            ) { -it / 8 } + fadeOut(tween(Motion.EXIT_DURATION))
        }

    fun popEnter(): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
        {
            slideInHorizontally(
                animationSpec = tween(Motion.ENTER_DURATION, easing = Motion.Emphasized)
            ) { -it / 8 } + fadeIn(tween(Motion.ENTER_DURATION))
        }

    fun popExit(): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
        {
            slideOutHorizontally(
                animationSpec = tween(Motion.EXIT_DURATION, easing = Motion.Emphasized)
            ) { it / 6 } + fadeOut(tween(Motion.EXIT_DURATION))
        }
}

/**
 * One-shot fade + slide-up wrapper. Drop a composable inside it to
 * animate in with stagger support:
 *
 *     AnimatedFadeInUp(delayMs = staggerDelayMs(index)) {
 *         SellerCard(seller)
 *     }
 */
@Composable
fun AnimatedFadeInUp(
    delayMs: Int = 0,
    durationMs: Int = Motion.ENTER_DURATION,
    content: @Composable () -> Unit
) {
    val state = rememberEnterTransition(delayMs)
    AnimatedVisibility(
        visibleState = state,
        enter = fadeIn(tween(durationMs, easing = Motion.Emphasized)) +
            slideInVertically(tween(durationMs, easing = Motion.Emphasized)) { it / 2 },
        exit = fadeOut(tween(Motion.EXIT_DURATION))
    ) {
        content()
    }
}

/**
 * Hero-scale entrance for the top-ranked item in a list. Larger slide
 * distance, longer duration, no per-item stagger.
 */
@Composable
fun AnimatedHeroEnter(
    delayMs: Int = 0,
    content: @Composable () -> Unit
) {
    val state = rememberEnterTransition(delayMs)
    AnimatedVisibility(
        visibleState = state,
        enter = fadeIn(tween(Motion.HERO_DURATION, easing = Motion.Emphasized)) +
            scaleIn(
                initialScale = 0.92f,
                animationSpec = tween(Motion.HERO_DURATION, easing = Motion.Emphasized)
            ),
        exit = fadeOut(tween(Motion.EXIT_DURATION)) + scaleOut(tween(Motion.EXIT_DURATION))
    ) {
        content()
    }
}

/**
 * Continuous, slow alpha pulse used for the "live location" indicator
 * on the buyer-side nearby screen. Cheap (no recomposition of the
 * caller) — just animates the float each frame.
 */
@Composable
fun rememberPulseAlpha(
    min: Float = 0.45f,
    max: Float = 1.0f,
    durationMs: Int = 1400
): Float {
    val transition = rememberInfiniteTransition(label = "pulse")
    val a by transition.animateFloat(
        initialValue = min,
        targetValue = max,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs, easing = Motion.Standard),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    return a
}

/**
 * Apply a staggered enter delay to each row based on its index. Capped
 * at 12 rows so a 1000-item list doesn't produce a 30-second stagger.
 */
fun staggerDelayMs(index: Int, perItemMs: Long = Motion.STAGGER_MS): Int =
    (index.coerceAtMost(12) * perItemMs).toInt()
