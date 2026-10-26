package com.teamarcadia.arcadiatweaks.neoforge.refinedstorage;

/**
 * Keeps the Refined Storage 2 activeness debounce on plain RS timing while S1 only runs the
 * activeness check every N ticks.
 *
 * RS 2.0.9 {@code AbstractBaseNetworkNodeContainerBlockEntity.updateActiveness} does, once per
 * call:
 * <pre>
 * if (changePending) {
 *     if (activenessChangeTicks++ % 20 == 0) applyChange();
 * }
 * </pre>
 * The counter only moves on calls that see a pending change. Called once every N ticks instead
 * of every tick, it would need 20 gated calls (up to 20 * N ticks) to reach the next multiple
 * of 20 after the first change.
 *
 * A gated check stands for the {@code k} ticks since the previous check. Before the single RS
 * call the counter is set to the first value plain RS would have fired on within those ticks
 * (or to the last value if none), so RS fires exactly when plain RS would have fired at some
 * tick of the window, assuming the change was pending for the whole window. After the call:
 * <ul>
 *   <li>counter untouched by RS (no change pending): restored, as plain RS never moves it;</li>
 *   <li>counter moved: kept, which is the plain RS value after the window (the fired value
 *       plus one, or the counter advanced by {@code k}).</li>
 * </ul>
 * The fire value is the same multiple of 20 as in plain RS, so the debounce phase after a
 * change matches plain RS, and the tick a change is applied on differs from plain RS by at
 * most {@code k - 1} ticks (earlier or later). Proven by {@code ArcadiaActivenessDebounceTest}.
 *
 * @author vyrriox
 */
public final class ArcadiaActivenessDebounce {

    /** The modulus of the RS2 debounce ({@code activenessChangeTicks++ % 20}). */
    public static final int RS_DEBOUNCE_TICKS = 20;

    /** Value of the last-check tick before the first gated check of a block entity. */
    public static final long NEVER_CHECKED = Long.MIN_VALUE;

    private static volatile boolean legacyBehaviourForTests;

    private ArcadiaActivenessDebounce() {}

    /**
     * Number of ticks a gated check stands for: the game ticks since the previous check,
     * clamped to {@code [1, interval]}. A first check stands for one tick.
     */
    public static int windowTicks(long now, long lastCheck, int interval) {
        final int safeInterval = Math.max(1, interval);
        if (lastCheck == NEVER_CHECKED || now <= lastCheck) {
            return 1;
        }
        final long elapsed = now - lastCheck;
        return (int) Math.min(elapsed, safeInterval);
    }

    /**
     * Counter value to give RS before its single call so that it fires if and only if plain RS
     * would have fired on one of the {@code windowTicks} ticks of the window. Uses Java int
     * arithmetic and {@code %} exactly as RS does, overflow included.
     */
    public static int preCounter(int counter, int windowTicks) {
        final int ticks = Math.max(1, windowTicks);
        for (int i = 0; i < ticks; i++) {
            final int value = counter + i;
            if (value % RS_DEBOUNCE_TICKS == 0) {
                return value;
            }
        }
        return counter + ticks - 1;
    }

    /** Counter to keep after the RS call. */
    public static int postCounter(int counterBefore, int preCounter, int counterAfterCall) {
        return counterAfterCall == preCounter ? counterBefore : counterAfterCall;
    }

    /**
     * GameTest hook: when true the ticker Mixin skips the counter adjustment and behaves like
     * S1 did before this fix, so a test can show the old timing. Never set in game.
     */
    public static void setLegacyBehaviourForTests(boolean legacy) {
        legacyBehaviourForTests = legacy;
    }

    public static boolean legacyBehaviourForTests() {
        return legacyBehaviourForTests;
    }
}
