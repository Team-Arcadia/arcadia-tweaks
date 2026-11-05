package com.teamarcadia.arcadiatweaks.neoforge.refinedstorage;

/**
 * B1 to B3 - How often an idle Refined Storage node tries again.
 *
 * <p>A node (importer, exporter, external crafting pattern) calls {@link #shouldAttempt} on each of
 * its work cycles and, when it did try, {@link #record} with whether anything moved. While attempts
 * move nothing, the allowed time between two attempts grows: 2 ticks, then 4, 8 and so on, up to
 * {@code maxTicks}. The first attempt that moves something brings the node back to every cycle.</p>
 *
 * <p>An idle node is still tried at least once every {@code maxTicks} ticks: a cycle is only skipped
 * when the next cycle would still come within the allowed time. A node whose own cycle is as slow as
 * {@code maxTicks} (an importer without speed upgrades works every 9 ticks) is never slowed down.</p>
 *
 * <p>Each node stops growing a little below the maximum (by up to a quarter of it, fixed per node), so
 * nodes that went idle on the same tick do not all try again on the same ticks.</p>
 *
 * <p>Server thread only; plain fields.</p>
 *
 * @author vyrriox
 */
public final class ArcadiaIdleBackoff {

    /** Time allowed after the first idle attempt. */
    static final int FIRST_GAP = 2;

    private long lastCycleTick = Long.MIN_VALUE;
    private long lastAttemptTick = Long.MIN_VALUE;
    private int gap;
    private Object owner;
    private final int spread = mix(System.identityHashCode(this));

    /**
     * Called on every work cycle. True when the node should try this cycle. {@code owner} is what the
     * node works for (its network): when it changes, the node starts again from every cycle.
     */
    public boolean shouldAttempt(long now, Object owner) {
        final long interval = lastCycleTick == Long.MIN_VALUE ? 1 : Math.max(1, now - lastCycleTick);
        lastCycleTick = now;
        if (owner != this.owner) {
            this.owner = owner;
            gap = 0;
        }
        if (gap <= 0 || lastAttemptTick == Long.MIN_VALUE) {
            return true;
        }
        // Skip only if the next cycle still falls within the allowed time since the last attempt.
        return now + interval - lastAttemptTick > gap;
    }

    /** Called after an attempt: {@code moved} is whether anything moved. */
    public void record(long now, boolean moved, int maxTicks) {
        lastAttemptTick = now;
        if (moved || maxTicks < FIRST_GAP) {
            gap = 0;
        } else {
            final int cap = Math.max(FIRST_GAP, maxTicks - Math.floorMod(spread, maxTicks / 4 + 1));
            gap = gap <= 0 ? FIRST_GAP : (int) Math.min((long) gap * 2, cap);
        }
    }

    /** Spreads identity hash codes, which are often close for objects created together. */
    private static int mix(int h) {
        h ^= h >>> 16;
        h *= 0x45d9f3b;
        h ^= h >>> 16;
        return h;
    }

    /** Back to every cycle (configuration changed, patch turned off). */
    public void reset() {
        gap = 0;
        lastAttemptTick = Long.MIN_VALUE;
    }

    /** Current allowed time between attempts, 0 when the node works every cycle. For tests. */
    public int gap() {
        return gap;
    }
}
