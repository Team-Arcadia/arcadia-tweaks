package com.teamarcadia.arcadiatweaks.neoforge.mixin.macawsdoors;

import com.teamarcadia.arcadiatweaks.neoforge.mixin.ArcadiaMixinTargetInspector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The guard is pinned to the Macaw's Doors jar of the production pack, in libs/.
 *
 * @author vyrriox
 */
final class MacawsDoorsMixinPluginTest {

    private static final String MIXIN = "com.teamarcadia.arcadiatweaks.neoforge.mixin.macawsdoors.GarageDoorRefusedWriteMixin";

    @Test
    void pinnedFingerprintMatchesTheProductionJar() {
        final String actual = ArcadiaMixinTargetInspector.methodFingerprint(
                MacawsDoorsMixinPlugin.GARAGE_DOOR, MacawsDoorsMixinPlugin.TOGGLE_DOOR, MacawsDoorsMixinPlugin.TOGGLE_DOOR_DESC);
        System.out.println("toggleDoor = " + actual);
        assertEquals(MacawsDoorsMixinPlugin.TOGGLE_DOOR_FINGERPRINT, actual);
    }

    @Test
    void pluginAppliesTheGuardOnTheProductionJar() {
        assertTrue(new MacawsDoorsMixinPlugin().shouldApplyMixin(MacawsDoorsMixinPlugin.GARAGE_DOOR, MIXIN));
    }

    @Test
    void pluginSkipsAModThatIsNotInstalled() {
        assertFalse(new MacawsDoorsMixinPlugin().shouldApplyMixin("not.installed.GarageDoor", MIXIN));
    }
}
