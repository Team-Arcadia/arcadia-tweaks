package com.teamarcadia.arcadiatweaks.neoforge.waystones;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ArcadiaWaystoneTeamVisibilityTest {

    private static final UUID VIEWER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void ownerSeesOwnTeamWaystoneEvenWithoutTeamData() {
        assertTrue(ArcadiaWaystoneTeamVisibility.isTeamTarget(OWNER, OWNER, (viewer, owner) -> false));
    }

    @Test
    void memberOfOwnerTeamSeesTeamWaystone() {
        assertTrue(ArcadiaWaystoneTeamVisibility.isTeamTarget(VIEWER, OWNER, (viewer, owner) -> true));
    }

    @Test
    void playerOutsideOwnerTeamDoesNotSeeTeamWaystone() {
        assertFalse(ArcadiaWaystoneTeamVisibility.isTeamTarget(VIEWER, OWNER, (viewer, owner) -> false));
    }

    @Test
    void waystoneWithoutOwnerIsNeverATeamTarget() {
        assertFalse(ArcadiaWaystoneTeamVisibility.isTeamTarget(VIEWER, null, (viewer, owner) -> true));
    }
}
