package com.teamarcadia.arcadiatweaks.neoforge.gametest;

import com.teamarcadia.arcadiatweaks.ArcadiaTweaks;
import com.teamarcadia.arcadiatweaks.common.module.ArcadiaPatchVariant;
import com.teamarcadia.arcadiatweaks.neoforge.support.ArcadiaIntervalGate;
import com.teamarcadia.arcadiatweaks.neoforge.support.ArcadiaTickCoalescing;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Method;

@GameTestHolder(ArcadiaTweaks.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ArcadiaTweaksGameTests {

    private static final String TEMPLATE = "empty";
    private static final String BATCH = ArcadiaTweaks.MOD_ID;
    private static final String BOTANY_PLUGIN = "com.teamarcadia.arcadiatweaks.neoforge.mixin.botany.BotanyMixinPlugin";
    private static final String REFINED_STORAGE_PLUGIN = "com.teamarcadia.arcadiatweaks.neoforge.mixin.refinedstorage.RefinedStorageMixinPlugin";
    private static final String MEKANISM_PLUGIN = "com.teamarcadia.arcadiatweaks.neoforge.mixin.mekanism.MekanismMixinPlugin";
    private static final String WAYSTONES_PLUGIN = "com.teamarcadia.arcadiatweaks.neoforge.mixin.waystones.WaystonesMixinPlugin";

    private ArcadiaTweaksGameTests() {}

    public static void register(RegisterGameTestsEvent event) {
        event.register(ArcadiaTweaksGameTests.class);
        event.register(EntityMoveGuardGameTests.class);
        event.register(WaystoneTeamVisibilityGameTests.class);
        event.register(RunicForgeGameTests.class);
        event.register(CosmeticPresenceGateGameTests.class);
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void patchVariantResourcesAreVisible(GameTestHelper helper) {
        helper.assertTrue(
                ArcadiaPatchVariant.hasBotany()
                        || ArcadiaPatchVariant.hasRefinedStorage()
                        || ArcadiaPatchVariant.hasMekanism()
                        || ArcadiaPatchVariant.hasWaystones(),
                "At least one patch resource should be present"
        );
        if (ArcadiaPatchVariant.hasBotany() && ArcadiaPatchVariant.hasRefinedStorage() && ArcadiaPatchVariant.hasMekanism()) {
            helper.assertValueEqual(ArcadiaPatchVariant.commandRoot(), ArcadiaTweaks.COMMAND_ROOT, "All-patches runtime should use /arcadia_tweaks");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void optionalModMixinTargetsMatchRuntimeBytecode(GameTestHelper helper) {
        if (ArcadiaPatchVariant.hasBotany()) {
            helper.assertTrue(shouldApplyMixin(BOTANY_PLUGIN,
                    "net.darkhax.botanypots.common.impl.block.entity.BotanyPotBlockEntity",
                    "com.teamarcadia.arcadiatweaks.neoforge.mixin.botany.BotanyPotBlockEntityMixin"
            ), "BotanyPotBlockEntityMixin should match bundled BotanyPots bytecode");
        }
        if (ArcadiaPatchVariant.hasRefinedStorage()) {
            helper.assertTrue(shouldApplyMixin(REFINED_STORAGE_PLUGIN,
                    "com.refinedmods.refinedstorage.common.support.network.NetworkNodeBlockEntityTicker",
                    "com.teamarcadia.arcadiatweaks.neoforge.mixin.refinedstorage.NetworkNodeBlockEntityTickerMixin"
            ), "Refined Storage node ticker mixin should match bundled RS2 bytecode");
        }
        if (ArcadiaPatchVariant.hasMekanism()) {
            helper.assertTrue(shouldApplyMixin(MEKANISM_PLUGIN,
                    "mekanism.common.content.network.transmitter.UniversalCable",
                    "com.teamarcadia.arcadiatweaks.neoforge.mixin.mekanism.UniversalCableMixin"
            ), "Mekanism UniversalCable mixin should match bundled Mekanism bytecode");
            helper.assertTrue(shouldApplyMixin(MEKANISM_PLUGIN,
                    "mekanism.common.content.network.transmitter.LogisticalTransporterBase",
                    "com.teamarcadia.arcadiatweaks.neoforge.mixin.mekanism.LogisticalTransporterBaseMixin"
            ), "Mekanism LogisticalTransporterBase mixin should match bundled Mekanism bytecode");
            helper.assertTrue(shouldApplyMixin(MEKANISM_PLUGIN,
                    "mekanism.common.content.transporter.TransporterStack",
                    "com.teamarcadia.arcadiatweaks.neoforge.mixin.mekanism.TransporterStackMixin"
            ), "Mekanism TransporterStack mixin should match bundled Mekanism bytecode");
            helper.assertFalse(shouldApplyMixin(MEKANISM_PLUGIN,
                    "missing.mekanism.UniversalCable",
                    "com.teamarcadia.arcadiatweaks.neoforge.mixin.mekanism.UniversalCableMixin"
            ), "Mekanism mixin safety should skip missing target classes");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void mixinInterfacesAreAppliedToRuntimeClasses(GameTestHelper helper) {
        if (ArcadiaPatchVariant.hasBotany()) {
            helper.assertTrue(isAssignable(
                    "net.darkhax.botanypots.common.impl.block.entity.BotanyPotBlockEntity",
                    "com.teamarcadia.arcadiatweaks.neoforge.botany.ArcadiaPotState"
            ), "Botany pot class should implement ArcadiaPotState after mixin application");
        }
        if (ArcadiaPatchVariant.hasRefinedStorage()) {
            helper.assertTrue(isAssignable(
                    "com.refinedmods.refinedstorage.common.support.network.AbstractBaseNetworkNodeContainerBlockEntity",
                    "com.teamarcadia.arcadiatweaks.neoforge.refinedstorage.ArcadiaRefinedStorageNodeState"
            ), "RS base node class should implement ArcadiaRefinedStorageNodeState after mixin application");
        }
        if (ArcadiaPatchVariant.hasMekanism()) {
            helper.assertTrue(isAssignable(
                    "mekanism.common.content.network.EnergyNetwork",
                    "com.teamarcadia.arcadiatweaks.neoforge.mekanism.ArcadiaMekanismBackoffState"
            ), "Mekanism EnergyNetwork should implement ArcadiaMekanismBackoffState after mixin application");
            helper.assertTrue(isAssignable(
                    "mekanism.common.content.network.transmitter.UniversalCable",
                    "com.teamarcadia.arcadiatweaks.neoforge.mekanism.ArcadiaMekanismPullBackoffState"
            ), "Mekanism UniversalCable should implement ArcadiaMekanismPullBackoffState after mixin application");
            helper.assertTrue(isAssignable(
                    "mekanism.common.content.transporter.TransporterStack",
                    "com.teamarcadia.arcadiatweaks.neoforge.mekanism.ArcadiaMekanismIdlePathBackoffState"
            ), "Mekanism TransporterStack should implement ArcadiaMekanismIdlePathBackoffState after mixin application");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void waystonesTeamTargetsMixinIsApplied(GameTestHelper helper) {
        if (ArcadiaPatchVariant.hasWaystones()) {
            helper.assertTrue(shouldApplyMixin(WAYSTONES_PLUGIN,
                    "net.blay09.mods.waystones.core.WaystoneIndexManager",
                    "com.teamarcadia.arcadiatweaks.neoforge.mixin.waystones.WaystoneIndexManagerMixin"
            ), "Waystones team targets mixin should match bundled Waystones and FTB Teams bytecode");
            helper.assertTrue(hasMergedHandler(
                    "net.blay09.mods.waystones.core.WaystoneIndexManager",
                    "arcadia$resolveTeamTargetsWithFtbTeams"
            ), "WaystoneIndexManager should carry the FTB Teams team targets handler after mixin application");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void coalescingAlgorithmsKeepExpectedCadence(GameTestHelper helper) {
        int cooldown = 0;
        final boolean[] rsPattern = new boolean[8];
        for (int i = 0; i < rsPattern.length; i++) {
            final ArcadiaIntervalGate.Decision decision = ArcadiaIntervalGate.next(cooldown, 4);
            rsPattern[i] = decision.shouldRun();
            cooldown = decision.nextCooldown();
        }
        helper.assertTrue(rsPattern[0] && !rsPattern[1] && !rsPattern[2] && !rsPattern[3]
                && rsPattern[4] && !rsPattern[5] && !rsPattern[6] && !rsPattern[7],
                "Interval gate should run once every 4 ticks");

        int phase = 0;
        final boolean[] botanyPattern = new boolean[8];
        for (int i = 0; i < botanyPattern.length; i++) {
            final ArcadiaTickCoalescing.Decision decision = ArcadiaTickCoalescing.advance(phase, 4);
            botanyPattern[i] = decision.shouldRun();
            phase = decision.nextPhase();
        }
        helper.assertTrue(!botanyPattern[0] && !botanyPattern[1] && !botanyPattern[2] && botanyPattern[3]
                && !botanyPattern[4] && !botanyPattern[5] && !botanyPattern[6] && botanyPattern[7],
                "Botany coalescing should skip 3 ticks then run 1 tick at N=4");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void mekanismBackoffBoundsRemainStable(GameTestHelper helper) {
        helper.assertValueEqual(invokeInt("com.teamarcadia.arcadiatweaks.neoforge.mekanism.ArcadiaMekanismBackoff",
                "nextDelay", 0, 20), 1, "nextDelay should never return zero");
        helper.assertValueEqual(invokeInt("com.teamarcadia.arcadiatweaks.neoforge.mekanism.ArcadiaMekanismBackoff",
                "nextDelay", 40, 20), 20, "nextDelay should cap to max ticks");
        helper.assertValueEqual(invokeInt("com.teamarcadia.arcadiatweaks.neoforge.mekanism.ArcadiaMekanismBackoff",
                "growDelay", 4, 20), 8, "growDelay should double the current delay");
        helper.assertValueEqual(invokeInt("com.teamarcadia.arcadiatweaks.neoforge.mekanism.ArcadiaMekanismBackoff",
                "growDelay", 16, 20), 20, "growDelay should cap to max ticks");
        helper.succeed();
    }

    private static boolean shouldApplyMixin(String pluginClassName, String targetClassName, String mixinClassName) {
        try {
            final Object plugin = Class.forName(pluginClassName).getDeclaredConstructor().newInstance();
            return (boolean) plugin.getClass()
                    .getMethod("shouldApplyMixin", String.class, String.class)
                    .invoke(plugin, targetClassName, mixinClassName);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to invoke mixin plugin " + pluginClassName, e);
        }
    }

    private static boolean isAssignable(String targetClassName, String interfaceClassName) {
        try {
            final Class<?> targetClass = Class.forName(targetClassName);
            final Class<?> interfaceClass = Class.forName(interfaceClassName);
            return interfaceClass.isAssignableFrom(targetClass);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Unable to inspect runtime class " + targetClassName, e);
        }
    }

    private static boolean hasMergedHandler(String targetClassName, String handlerName) {
        try {
            for (Method method : Class.forName(targetClassName).getDeclaredMethods()) {
                if (method.getName().contains(handlerName)) {
                    return true;
                }
            }
            return false;
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Unable to inspect runtime class " + targetClassName, e);
        }
    }

    private static int invokeInt(String className, String methodName, int first, int second) {
        try {
            return (int) Class.forName(className)
                    .getMethod(methodName, int.class, int.class)
                    .invoke(null, first, second);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to invoke " + className + "." + methodName, e);
        }
    }
}
