package com.teamarcadia.arcadiatweaks.neoforge.refinedstorage;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ArcadiaIdleBackoffTest {

    private static final Object NETWORK = new Object();

    /** Ticks at which a node working every {@code interval} ticks tries, always idle, over {@code ticks}. */
    private static List<Long> idleAttempts(int interval, int maxTicks, int ticks) {
        final ArcadiaIdleBackoff backoff = new ArcadiaIdleBackoff();
        final List<Long> attempts = new ArrayList<>();
        for (long now = 0; now < ticks; now += interval) {
            if (backoff.shouldAttempt(now, NETWORK)) {
                attempts.add(now);
                backoff.record(now, false, maxTicks);
            }
        }
        return attempts;
    }

    @Test
    void idleNodeEveryTickSlowsDownToNearTheMaximum() {
        final List<Long> attempts = idleAttempts(1, 10, 80);
        assertEquals(List.of(0L, 2L, 6L, 14L), attempts.subList(0, 4));
        for (int i = 4; i < attempts.size(); i++) {
            final long spacing = attempts.get(i) - attempts.get(i - 1);
            assertTrue(spacing >= 8 && spacing <= 10, "spacing " + spacing);
        }
    }

    @Test
    void nodesIdleSinceTheSameTickSpreadTheirAttempts() {
        final java.util.Set<Integer> caps = new java.util.HashSet<>();
        for (int n = 0; n < 50; n++) {
            final ArcadiaIdleBackoff backoff = new ArcadiaIdleBackoff();
            for (long now = 0; now < 100; now++) {
                if (backoff.shouldAttempt(now, NETWORK)) backoff.record(now, false, 10);
            }
            caps.add(backoff.gap());
        }
        assertTrue(caps.size() > 1, "every node ended on the same gap " + caps);
        assertTrue(caps.stream().allMatch(g -> g >= 8 && g <= 10), "gaps " + caps);
    }

    @Test
    void nodeAsSlowAsTheMaximumIsNeverSlowedDown() {
        assertEquals(List.of(0L, 9L, 18L, 27L, 36L), idleAttempts(9, 10, 40));
        assertEquals(List.of(0L, 10L, 20L, 30L), idleAttempts(10, 10, 40));
    }

    @Test
    void idleNodeIsAlwaysTriedWithinTheMaximum() {
        final Random random = new Random(7);
        for (int run = 0; run < 500; run++) {
            final int interval = 1 + random.nextInt(12);
            final int max = 2 + random.nextInt(39);
            final List<Long> attempts = idleAttempts(interval, max, 2_000);
            for (int i = 1; i < attempts.size(); i++) {
                final long spacing = attempts.get(i) - attempts.get(i - 1);
                assertTrue(spacing <= Math.max(max, interval),
                        "interval " + interval + " max " + max + ": " + spacing + " ticks between attempts");
            }
        }
    }

    @Test
    void movingSomethingBringsTheNodeBackToEveryCycle() {
        final ArcadiaIdleBackoff backoff = new ArcadiaIdleBackoff();
        long now = 0;
        for (; now < 100; now++) {
            if (backoff.shouldAttempt(now, NETWORK)) backoff.record(now, false, 10);
        }
        assertTrue(backoff.gap() >= 8 && backoff.gap() <= 10, "gap " + backoff.gap());
        // The next attempt comes within 10 ticks; it moves something.
        long moved = -1;
        for (; now < 120 && moved < 0; now++) {
            if (backoff.shouldAttempt(now, NETWORK)) {
                backoff.record(now, true, 10);
                moved = now;
            }
        }
        assertTrue(moved >= 100 && moved < 110, "attempted at " + moved);
        assertEquals(0, backoff.gap());
        for (long t = moved + 1; t < moved + 20; t++) {
            assertTrue(backoff.shouldAttempt(t, NETWORK), "every cycle again at " + t);
            backoff.record(t, true, 10);
        }
    }

    @Test
    void newNetworkOrResetStartsFromEveryCycle() {
        final ArcadiaIdleBackoff backoff = new ArcadiaIdleBackoff();
        for (long now = 0; now < 50; now++) {
            if (backoff.shouldAttempt(now, NETWORK)) backoff.record(now, false, 10);
        }
        assertTrue(backoff.shouldAttempt(50, new Object()), "other network: try at once");
        for (long now = 51; now < 100; now++) {
            if (backoff.shouldAttempt(now, NETWORK)) backoff.record(now, false, 10);
        }
        backoff.reset();
        assertTrue(backoff.shouldAttempt(100, NETWORK), "reset: try at once");
    }

    @Test
    void maximumBelowTheFirstGapDisablesTheBackoff() {
        assertEquals(List.of(0L, 1L, 2L, 3L, 4L), idleAttempts(1, 1, 5));
    }
}
