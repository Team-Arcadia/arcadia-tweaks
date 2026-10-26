package com.teamarcadia.arcadiatweaks.neoforge.refinedstorage;

import com.teamarcadia.arcadiatweaks.neoforge.support.ArcadiaIntervalGate;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compares the coalesced activeness check against a tick-by-tick model of the RS 2.0.9
 * debounce ({@code if (pending) { if (activenessChangeTicks++ % 20 == 0) apply(); }}).
 *
 * @author vyrriox
 */
final class ArcadiaActivenessDebounceTest {

    /** One RS updateActiveness call on the model: returns true when the change is applied. */
    private static boolean rsCall(int[] counter, boolean pending) {
        if (pending) {
            return counter[0]++ % ArcadiaActivenessDebounce.RS_DEBOUNCE_TICKS == 0;
        }
        return false;
    }

    @Test
    void singleWindowMatchesPlainRsWhenPendingForTheWholeWindow() {
        for (int c = 0; c < 60; c++) {
            for (int k = 1; k <= 20; k++) {
                final int[] plain = {c};
                boolean plainFired = false;
                for (int tick = 0; tick < k && !plainFired; tick++) {
                    plainFired = rsCall(plain, true);
                }

                final int pre = ArcadiaActivenessDebounce.preCounter(c, k);
                final int[] gated = {pre};
                final boolean gatedFired = rsCall(gated, true);
                final int post = ArcadiaActivenessDebounce.postCounter(c, pre, gated[0]);

                assertEquals(plainFired, gatedFired, "fire decision c=" + c + " k=" + k);
                assertEquals(plain[0], post, "counter after window c=" + c + " k=" + k);
            }
        }
    }

    @Test
    void counterIsUntouchedWhenNothingIsPending() {
        for (int c = 0; c < 60; c++) {
            for (int k = 1; k <= 20; k++) {
                final int pre = ArcadiaActivenessDebounce.preCounter(c, k);
                final int[] gated = {pre};
                assertFalse(rsCall(gated, false));
                assertEquals(c, ArcadiaActivenessDebounce.postCounter(c, pre, gated[0]));
            }
        }
    }

    @Test
    void changeStartingMidWindowIsNeverAppliedLaterThanPlainRs() {
        for (int c = 0; c < 60; c++) {
            for (int k = 1; k <= 20; k++) {
                for (int start = 0; start < k; start++) {
                    final int[] plain = {c};
                    boolean plainFired = false;
                    for (int tick = start; tick < k && !plainFired; tick++) {
                        plainFired = rsCall(plain, true);
                    }
                    final int[] gated = {ArcadiaActivenessDebounce.preCounter(c, k)};
                    final boolean gatedFired = rsCall(gated, true);
                    if (plainFired) {
                        assertTrue(gatedFired, "plain RS fired inside the window, so must the gated call c=" + c + " k=" + k);
                    }
                }
            }
        }
    }

    @Test
    void preCounterUsesRsIntArithmeticAcrossOverflow() {
        final int start = Integer.MAX_VALUE - 3;
        for (int k = 1; k <= 20; k++) {
            int expected = start + k - 1;
            for (int i = 0; i < k; i++) {
                if ((start + i) % 20 == 0) {
                    expected = start + i;
                    break;
                }
            }
            assertEquals(expected, ArcadiaActivenessDebounce.preCounter(start, k));
        }
    }

    @Test
    void windowCoversTheTicksSinceTheLastCheckClampedToTheInterval() {
        assertEquals(1, ArcadiaActivenessDebounce.windowTicks(500L, ArcadiaActivenessDebounce.NEVER_CHECKED, 20));
        assertEquals(20, ArcadiaActivenessDebounce.windowTicks(520L, 500L, 20));
        assertEquals(7, ArcadiaActivenessDebounce.windowTicks(507L, 500L, 20));
        assertEquals(20, ArcadiaActivenessDebounce.windowTicks(9_000L, 500L, 20));
        assertEquals(1, ArcadiaActivenessDebounce.windowTicks(500L, 500L, 20));
        assertEquals(1, ArcadiaActivenessDebounce.windowTicks(400L, 500L, 20));
    }

    /**
     * Long random timelines of activeness changes (redstone toggles, power loss...) spaced so
     * that each is applied before the next one. With the fix every change is applied at most
     * interval - 1 ticks away from plain RS and the counters agree after each change; without it
     * (the old S1) changes after the first are applied up to about 20 * interval ticks late.
     */
    @Test
    void timelinesStayWithinIntervalMinusOneOfPlainRs() {
        final Random random = new Random(20261026L);
        for (int interval : new int[] {2, 5, 7, 13, 20, 33, 60, 200}) {
            for (int run = 0; run < 40; run++) {
                final List<Integer> toggles = new ArrayList<>();
                int t = random.nextInt(50);
                for (int i = 0; i < 12; i++) {
                    toggles.add(t);
                    t += 60 + 2 * interval + random.nextInt(150);
                }
                final int end = t + 60 + 2 * interval;
                final int[] plain = simulate(toggles, end, 1, false);
                final int[] fixed = simulate(toggles, end, interval, false);
                for (int i = 0; i < toggles.size(); i++) {
                    assertTrue(plain[i] >= 0 && fixed[i] >= 0, "every change applied");
                    assertTrue(Math.abs(fixed[i] - plain[i]) <= interval - 1,
                            "interval=" + interval + " change " + i + " plain=" + plain[i] + " fixed=" + fixed[i]);
                }
                assertEquals(plain[toggles.size()], fixed[toggles.size()], "debounce counter at the end");
            }
        }
    }

    @Test
    void oldBehaviourAppliedLaterChangesUpToTwentyIntervalsLate() {
        final List<Integer> toggles = List.of(10, 600, 1200, 1800);
        final int[] plain = simulate(toggles, 2400, 1, false);
        final int[] legacy = simulate(toggles, 2400, 20, true);
        int worst = 0;
        for (int i = 0; i < toggles.size(); i++) {
            worst = Math.max(worst, legacy[i] - plain[i]);
        }
        assertTrue(worst > 300, "the old S1 delayed a change by " + worst + " ticks");
    }

    /**
     * Runs the model. Returns the apply tick of every toggle (-1 if never applied) and, as last
     * element, the final debounce counter.
     */
    private static int[] simulate(List<Integer> toggles, int endTick, int interval, boolean legacy) {
        final int[] counter = {0};
        boolean target = false;
        boolean applied = false;
        int cooldown = 0;
        long lastCheck = ArcadiaActivenessDebounce.NEVER_CHECKED;
        final int[] result = new int[toggles.size() + 1];
        java.util.Arrays.fill(result, -1);
        int next = 0;
        int awaiting = -1;
        for (int tick = 0; tick <= endTick; tick++) {
            if (next < toggles.size() && toggles.get(next) == tick) {
                target = !target;
                awaiting = next;
                next++;
            }
            boolean call;
            if (interval <= 1) {
                call = true;
            } else {
                final ArcadiaIntervalGate.Decision decision = ArcadiaIntervalGate.next(cooldown, interval);
                cooldown = decision.nextCooldown();
                call = decision.shouldRun();
            }
            if (!call) {
                continue;
            }
            final boolean fired;
            if (interval <= 1 || legacy) {
                fired = rsCall(counter, applied != target);
            } else {
                final int k = ArcadiaActivenessDebounce.windowTicks(tick, lastCheck, interval);
                lastCheck = tick;
                final int before = counter[0];
                final int pre = ArcadiaActivenessDebounce.preCounter(before, k);
                counter[0] = pre;
                fired = rsCall(counter, applied != target);
                counter[0] = ArcadiaActivenessDebounce.postCounter(before, pre, counter[0]);
            }
            if (fired) {
                applied = target;
                if (awaiting >= 0) {
                    result[awaiting] = tick;
                    awaiting = -1;
                }
            }
        }
        result[toggles.size()] = counter[0];
        return result;
    }
}
