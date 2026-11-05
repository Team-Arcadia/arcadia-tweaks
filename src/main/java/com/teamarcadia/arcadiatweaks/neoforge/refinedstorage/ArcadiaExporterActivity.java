package com.teamarcadia.arcadiatweaks.neoforge.refinedstorage;

/**
 * B2 - Whether one exporter work cycle did anything.
 *
 * <p>The exporter's scheduling mode runs its filters and returns nothing. Each filter's result is
 * reported here while the cycle runs ({@link #markActive}); {@link #begin} and {@link #end} bracket
 * the cycle and keep an outer cycle's state if one exporter cycle ever runs inside another. Server
 * thread only.</p>
 *
 * @author vyrriox
 */
public final class ArcadiaExporterActivity {

    private static boolean active;

    private ArcadiaExporterActivity() {}

    /** Starts a cycle; returns the state to hand back to {@link #end}. */
    public static boolean begin() {
        final boolean outer = active;
        active = false;
        return outer;
    }

    /** A filter exported or started an autocrafting task. */
    public static void markActive() {
        active = true;
    }

    /** Ends a cycle: returns whether it did anything and restores the outer state. */
    public static boolean end(boolean outer) {
        final boolean cycleActive = active;
        active = outer;
        return cycleActive;
    }
}
