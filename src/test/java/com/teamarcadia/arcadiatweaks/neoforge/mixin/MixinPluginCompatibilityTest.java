package com.teamarcadia.arcadiatweaks.neoforge.mixin;

import com.teamarcadia.arcadiatweaks.neoforge.botany.BotanyMixinCompatibility;
import com.teamarcadia.arcadiatweaks.neoforge.mixin.botany.BotanyMixinPlugin;
import com.teamarcadia.arcadiatweaks.neoforge.mixin.mekanism.MekanismMixinPlugin;
import com.teamarcadia.arcadiatweaks.neoforge.mixin.refinedstorage.RefinedStorageMixinPlugin;
import com.teamarcadia.arcadiatweaks.neoforge.mixin.waystones.WaystonesMixinPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MixinPluginCompatibilityTest {

    @AfterEach
    void resetBotanyCompatibilityFlag() {
        BotanyMixinCompatibility.setTickPotCoalescingCompatible(true);
    }

    @Test
    void botanyPluginAcceptsBundledRuntimeTargets() {
        final BotanyMixinPlugin plugin = new BotanyMixinPlugin();

        assertTrue(plugin.shouldApplyMixin(
                "net.darkhax.botanypots.common.impl.block.entity.AbstractBotanyPotBlockEntity",
                "com.teamarcadia.arcadiatweaks.neoforge.mixin.botany.AbstractBotanyPotBlockEntityMixin"
        ));
        assertTrue(plugin.shouldApplyMixin(
                "net.darkhax.botanypots.common.impl.block.entity.BotanyPotBlockEntity",
                "com.teamarcadia.arcadiatweaks.neoforge.mixin.botany.BotanyPotBlockEntityMixin"
        ));
        assertTrue(BotanyMixinCompatibility.isTickPotCoalescingCompatible());
    }

    @Test
    void refinedStoragePluginAcceptsBundledRuntimeTargets() {
        final RefinedStorageMixinPlugin plugin = new RefinedStorageMixinPlugin();

        assertTrue(plugin.shouldApplyMixin(
                "com.refinedmods.refinedstorage.common.support.network.AbstractBaseNetworkNodeContainerBlockEntity",
                "com.teamarcadia.arcadiatweaks.neoforge.mixin.refinedstorage.AbstractBaseNetworkNodeContainerBlockEntityMixin"
        ));
        assertTrue(plugin.shouldApplyMixin(
                "com.refinedmods.refinedstorage.common.support.network.NetworkNodeBlockEntityTicker",
                "com.teamarcadia.arcadiatweaks.neoforge.mixin.refinedstorage.NetworkNodeBlockEntityTickerMixin"
        ));
        assertTrue(plugin.shouldApplyMixin(
                "com.refinedmods.refinedstorage.common.networking.NetworkTransmitterBlockEntityTicker",
                "com.teamarcadia.arcadiatweaks.neoforge.mixin.refinedstorage.NetworkTransmitterBlockEntityTickerMixin"
        ));
    }

    @Test
    void mekanismPluginAcceptsBundledRuntimeTargets() {
        final MekanismMixinPlugin plugin = new MekanismMixinPlugin();

        assertTrue(plugin.shouldApplyMixin(
                "mekanism.common.lib.transmitter.DynamicNetwork",
                "com.teamarcadia.arcadiatweaks.neoforge.mixin.mekanism.DynamicNetworkMixin"
        ));
        assertTrue(plugin.shouldApplyMixin(
                "mekanism.common.content.network.EnergyNetwork",
                "com.teamarcadia.arcadiatweaks.neoforge.mixin.mekanism.EnergyNetworkMixin"
        ));
        assertTrue(plugin.shouldApplyMixin(
                "mekanism.common.content.network.FluidNetwork",
                "com.teamarcadia.arcadiatweaks.neoforge.mixin.mekanism.FluidNetworkMixin"
        ));
        assertTrue(plugin.shouldApplyMixin(
                "mekanism.common.content.network.ChemicalNetwork",
                "com.teamarcadia.arcadiatweaks.neoforge.mixin.mekanism.ChemicalNetworkMixin"
        ));
        assertTrue(plugin.shouldApplyMixin(
                "mekanism.common.content.network.transmitter.LogisticalTransporterBase",
                "com.teamarcadia.arcadiatweaks.neoforge.mixin.mekanism.LogisticalTransporterBaseMixin"
        ));
        assertTrue(plugin.shouldApplyMixin(
                "mekanism.common.content.transporter.TransporterStack",
                "com.teamarcadia.arcadiatweaks.neoforge.mixin.mekanism.TransporterStackMixin"
        ));
        assertTrue(plugin.shouldApplyMixin(
                "mekanism.common.content.network.transmitter.UniversalCable",
                "com.teamarcadia.arcadiatweaks.neoforge.mixin.mekanism.UniversalCableMixin"
        ));
        assertTrue(plugin.shouldApplyMixin(
                "mekanism.common.content.network.transmitter.MechanicalPipe",
                "com.teamarcadia.arcadiatweaks.neoforge.mixin.mekanism.MechanicalPipeMixin"
        ));
        assertTrue(plugin.shouldApplyMixin(
                "mekanism.common.content.network.transmitter.PressurizedTube",
                "com.teamarcadia.arcadiatweaks.neoforge.mixin.mekanism.PressurizedTubeMixin"
        ));
        assertTrue(plugin.shouldApplyMixin(
                "mekanism.client.render.obj.TransmitterBakedModel",
                "com.teamarcadia.arcadiatweaks.neoforge.mixin.mekanism.TransmitterBakedModelMixin"
        ));
    }

    @Test
    void waystonesPluginAcceptsBundledRuntimeTargets() {
        final WaystonesMixinPlugin plugin = new WaystonesMixinPlugin();

        assertTrue(plugin.shouldApplyMixin(
                "net.blay09.mods.waystones.core.WaystoneIndexManager",
                "com.teamarcadia.arcadiatweaks.neoforge.mixin.waystones.WaystoneIndexManagerMixin"
        ));
    }

    @Test
    void pluginsSkipMissingTargetsInsteadOfCrashing() {
        final BotanyMixinPlugin botany = new BotanyMixinPlugin();
        final RefinedStorageMixinPlugin refinedStorage = new RefinedStorageMixinPlugin();
        final MekanismMixinPlugin mekanism = new MekanismMixinPlugin();
        final WaystonesMixinPlugin waystones = new WaystonesMixinPlugin();

        assertFalse(botany.shouldApplyMixin(
                "missing.botany.BotanyPotBlockEntity",
                "com.teamarcadia.arcadiatweaks.neoforge.mixin.botany.BotanyPotBlockEntityMixin"
        ));
        assertFalse(refinedStorage.shouldApplyMixin(
                "missing.refinedstorage.NetworkNodeBlockEntityTicker",
                "com.teamarcadia.arcadiatweaks.neoforge.mixin.refinedstorage.NetworkNodeBlockEntityTickerMixin"
        ));
        assertFalse(mekanism.shouldApplyMixin(
                "missing.mekanism.UniversalCable",
                "com.teamarcadia.arcadiatweaks.neoforge.mixin.mekanism.UniversalCableMixin"
        ));
        assertFalse(waystones.shouldApplyMixin(
                "missing.waystones.WaystoneIndexManager",
                "com.teamarcadia.arcadiatweaks.neoforge.mixin.waystones.WaystoneIndexManagerMixin"
        ));
    }
}
