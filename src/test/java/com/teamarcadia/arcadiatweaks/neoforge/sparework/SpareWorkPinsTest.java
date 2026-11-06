package com.teamarcadia.arcadiatweaks.neoforge.sparework;

import com.teamarcadia.arcadiatweaks.neoforge.mixin.ArcadiaMixinTargetInspector;
import com.teamarcadia.arcadiatweaks.neoforge.mixin.sparework.SpareWorkMixinPlugin;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pinned fingerprints match the production jars in libs/, the plugin accepts them, and a target that is
 * missing or that changed is refused.
 *
 * @author vyrriox
 */
final class SpareWorkPinsTest {

    private static final String PACKAGE = "com.teamarcadia.arcadiatweaks.neoforge.mixin.sparework.";

    @Test
    void pinnedFingerprintsMatchTheProductionJars() {
        for (List<SpareWorkPins.Pin> pins : List.of(SpareWorkPins.FASTSUITE, SpareWorkPins.SIMPLY_SWORDS, SpareWorkPins.CRABBERS_DELIGHT)) {
            for (SpareWorkPins.Pin pin : pins) {
                System.out.println(pin.describe() + " = "
                        + ArcadiaMixinTargetInspector.methodFingerprint(pin.className(), pin.method(), pin.descriptor()));
            }
        }
        assertEquals(List.of(), SpareWorkPins.mismatches(SpareWorkPins.FASTSUITE));
        assertEquals(List.of(), SpareWorkPins.mismatches(SpareWorkPins.SIMPLY_SWORDS));
        assertEquals(List.of(), SpareWorkPins.mismatches(SpareWorkPins.CRABBERS_DELIGHT));
    }

    @Test
    void pluginAppliesEveryPatchOnTheProductionJars() {
        final SpareWorkMixinPlugin plugin = new SpareWorkMixinPlugin();
        assertTrue(plugin.shouldApplyMixin("dev.shadowsoffire.fastsuite.CachedRecipeList", PACKAGE + "CachedRecipeListEmptyParallelMixin"));
        assertTrue(plugin.shouldApplyMixin("net.sweenus.simplyswords.loot.PityLootContext", PACKAGE + "PityLootContextReuseMixin"));
        assertTrue(plugin.shouldApplyMixin("alabaster.crabbersdelight.common.utils.TackleBoxProximity", PACKAGE + "TackleBoxProximitySkipMixin"));
        assertTrue(plugin.shouldApplyMixin("net.minecraft.world.level.chunk.ChunkAccess", PACKAGE + "ChunkAccessPendingAccessor"));
        assertTrue(plugin.shouldApplyMixin("net.minecraft.world.level.StructureManager", PACKAGE + "StructureManagerLoadedChunkMixin"));
        assertTrue(SpareWorkMixinPlugin.structureLookupsCompatible());
    }

    @Test
    void pluginSkipsAModThatIsNotInstalled() {
        assertFalse(new SpareWorkMixinPlugin().shouldApplyMixin("not.installed.Target", PACKAGE + "TackleBoxProximitySkipMixin"));
    }

    @Test
    void aChangedOrMissingMethodIsAMismatch() {
        final SpareWorkPins.Pin real = SpareWorkPins.SIMPLY_SWORDS.getFirst();
        assertEquals(1, SpareWorkPins.mismatches(List.of(new SpareWorkPins.Pin(real.className(), real.method(), real.descriptor(), "changed"))).size());
        assertEquals(1, SpareWorkPins.mismatches(List.of(new SpareWorkPins.Pin(real.className(), "missing", "()V", real.sha256()))).size());
        assertEquals(1, SpareWorkPins.mismatches(List.of(new SpareWorkPins.Pin("not.installed.Target", "missing", "()V", real.sha256()))).size());
    }
}
