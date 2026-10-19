package com.teamarcadia.arcadiatweaks.neoforge.waystones;

import java.util.UUID;
import java.util.function.BiPredicate;

/**
 * W1 - Membership rule for "Visible to Team" waystones.
 *
 * Kept free of Minecraft, Waystones and FTB Teams types so the rule can be
 * unit tested without a game runtime.
 */
public final class ArcadiaWaystoneTeamVisibility {

    private ArcadiaWaystoneTeamVisibility() {}

    public static boolean isTeamTarget(UUID viewer, UUID owner, BiPredicate<UUID, UUID> sameTeam) {
        if (viewer == null || owner == null) {
            return false;
        }
        return viewer.equals(owner) || sameTeam.test(viewer, owner);
    }
}
