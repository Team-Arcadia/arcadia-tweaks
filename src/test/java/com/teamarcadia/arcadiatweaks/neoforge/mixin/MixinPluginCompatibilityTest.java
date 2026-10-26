package com.teamarcadia.arcadiatweaks.neoforge.mixin;

import com.teamarcadia.arcadiatweaks.neoforge.botany.BotanyMixinCompatibility;
import com.teamarcadia.arcadiatweaks.neoforge.cosmetic.CosmeticGate;
import com.teamarcadia.arcadiatweaks.neoforge.mixin.botany.BotanyMixinPlugin;
import com.teamarcadia.arcadiatweaks.neoforge.mixin.cosmetic.CosmeticMixinPlugin;
import com.teamarcadia.arcadiatweaks.neoforge.mixin.mekanism.MekanismMixinPlugin;
import com.teamarcadia.arcadiatweaks.neoforge.mixin.refinedstorage.RefinedStorageMixinPlugin;
import com.teamarcadia.arcadiatweaks.neoforge.mixin.simplyswords.SimplySwordsMixinPlugin;
import com.teamarcadia.arcadiatweaks.neoforge.mixin.waystones.WaystonesMixinPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        assertTrue(plugin.shouldApplyMixin(
                "com.refinedmods.refinedstorage.api.core.component.ComponentMap",
                "com.teamarcadia.arcadiatweaks.neoforge.mixin.refinedstorage.ComponentMapMixin"
        ));
        assertTrue(plugin.shouldApplyMixin(
                "com.refinedmods.refinedstorage.common.storage.PlatformStorage",
                "com.teamarcadia.arcadiatweaks.neoforge.mixin.refinedstorage.PlatformStorageMixin"
        ));
        assertTrue(plugin.shouldApplyMixin(
                "com.refinedmods.refinedstorage.common.storage.StorageRepositoryImpl",
                "com.teamarcadia.arcadiatweaks.neoforge.mixin.refinedstorage.StorageRepositoryImplMixin"
        ));
        assertTrue(plugin.shouldApplyMixin(
                "com.refinedmods.refinedstorage.common.storage.externalstorage.AbstractExternalStorageBlockEntity",
                "com.teamarcadia.arcadiatweaks.neoforge.mixin.refinedstorage.ExternalStorageDebugLogMixin"
        ));
        assertTrue(plugin.shouldApplyMixin(
                "com.refinedmods.refinedstorage.common.networking.NetworkTransmitterBlockEntity",
                "com.teamarcadia.arcadiatweaks.neoforge.mixin.refinedstorage.NetworkTransmitterDebugLogMixin"
        ));
    }

    @Test
    void refinedStorageDebugLogMixinsSkipChangedTargets() {
        final RefinedStorageMixinPlugin plugin = new RefinedStorageMixinPlugin();

        // A class whose doWork has no matching Logger.debug call.
        assertFalse(plugin.shouldApplyMixin(
                "com.refinedmods.refinedstorage.common.support.network.AbstractBaseNetworkNodeContainerBlockEntity",
                "com.teamarcadia.arcadiatweaks.neoforge.mixin.refinedstorage.ExternalStorageDebugLogMixin"
        ));
        assertFalse(plugin.shouldApplyMixin(
                "missing.refinedstorage.ComponentMap",
                "com.teamarcadia.arcadiatweaks.neoforge.mixin.refinedstorage.ComponentMapMixin"
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
    void simplySwordsPluginAcceptsBundledRuntimeTargets() {
        final SimplySwordsMixinPlugin plugin = new SimplySwordsMixinPlugin();

        assertTrue(plugin.shouldApplyMixin(
                "net.sweenus.simplyswords.screen.RunicForgeScreenHandler",
                "com.teamarcadia.arcadiatweaks.neoforge.mixin.simplyswords.RunicForgeScreenHandlerMixin"
        ));
        assertTrue(plugin.shouldApplyMixin(
                "net.sweenus.simplyswords.screen.RunicForgeScreenHandler$TabletSlot",
                "com.teamarcadia.arcadiatweaks.neoforge.mixin.simplyswords.RunicForgeTabletSlotMixin"
        ));
        assertFalse(new CosmeticMixinPlugin().shouldApplyMixin(
                "cosmeticweaponsmod.procedures.MissingWeaponAdvProdProcedure",
                COSMETIC_WEAPONS_MIXIN
        ));
    }

    private static final String COSMETIC_WEAPONS_MIXIN = "com.teamarcadia.arcadiatweaks.neoforge.mixin.cosmetic.CosmeticWeaponsProcedureMixin";
    private static final String COSMETIC_ARMOURS_MIXIN = "com.teamarcadia.arcadiatweaks.neoforge.mixin.cosmetic.CosmeticArmoursProcedureMixin";
    private static final String EXECUTE_DESC = "(Lnet/neoforged/bus/api/Event;Lnet/minecraft/world/entity/Entity;)V";

    @Test
    void cosmeticPluginAcceptsAllFifteenBundledProcedures() {
        final CosmeticMixinPlugin plugin = new CosmeticMixinPlugin();
        assertNull(CosmeticMixinPlugin.environmentProblem(), "Curios and Accessories hooks should match the bundled jars");
        assertEquals(15, CosmeticGate.GATED_PROCEDURES.size());
        for (String procedure : CosmeticGate.GATED_PROCEDURES) {
            final String mixin = procedure.startsWith("cosmeticweaponsmod.") ? COSMETIC_WEAPONS_MIXIN : COSMETIC_ARMOURS_MIXIN;
            assertTrue(plugin.shouldApplyMixin(procedure, mixin), procedure + " should match the presence gate template");
        }
        assertTrue(plugin.shouldApplyMixin("net.minecraft.world.entity.player.Inventory",
                "com.teamarcadia.arcadiatweaks.neoforge.mixin.cosmetic.InventoryCompartmentsAccessor"));
    }

    @Test
    void cosmeticPluginRejectsProceduresWithoutAChainOrWithoutContains() {
        // One contains call only: nothing to skip.
        assertFalse(CosmeticMixinPlugin.procedureCompatible("cosmeticweaponsmod.procedures.AmethystSwordAdvProdProcedure"));
        assertFalse(CosmeticMixinPlugin.procedureCompatible("cosmeticarmoursmod.procedures.BlankArmourStyleAdvProdProcedure"));
        // Armour-slot / dimension procedures: no contains at all.
        assertFalse(CosmeticMixinPlugin.procedureCompatible("cosmeticarmoursmod.procedures.FashionFauxPasProdProcedure"));
        assertFalse(CosmeticMixinPlugin.procedureCompatible("missing.cosmetic.Procedure"));
    }

    @Test
    void cosmeticPluginSkipsOnlyTheProcedureWhoseChainGotAnExtraCall() throws IOException {
        final String patched = "cosmeticweaponsmod.procedures.PrismarineWeaponAdvProdProcedure";
        final byte[] bytes = patchClass(patched, node -> {
            final MethodNode execute = method(node, "execute", EXECUTE_DESC);
            execute.instructions.insertBefore(nthCall(execute, "contains", 2),
                    new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/entity/player/Inventory", "clearContent", "()V"));
        });
        withPatchedClass(patched, bytes, () -> {
            assertFalse(CosmeticMixinPlugin.procedureCompatible(patched), "an extra call in the chain must skip the target");
            assertTrue(CosmeticMixinPlugin.procedureCompatible("cosmeticweaponsmod.procedures.RoseGoldWeaponAdvProdProcedure"),
                    "other targets stay optimized");
        });
        assertTrue(CosmeticMixinPlugin.procedureCompatible(patched));
    }

    @Test
    void cosmeticPluginSkipsWhenAwardMovesInsideTheChain() throws IOException {
        final String patched = "cosmeticarmoursmod.procedures.GoldenHaloAchievementProcedureProcedure";
        final byte[] bytes = patchClass(patched, node -> {
            final MethodNode execute = method(node, "execute", EXECUTE_DESC);
            final MethodInsnNode award = (MethodInsnNode) nthCall(execute, "award", 1);
            execute.instructions.insertBefore(nthCall(execute, "contains", 2),
                    new MethodInsnNode(Opcodes.INVOKEVIRTUAL, award.owner, award.name, award.desc));
        });
        withPatchedClass(patched, bytes, () -> assertFalse(CosmeticMixinPlugin.procedureCompatible(patched)));
    }

    @Test
    void accessoriesPinFailsWhenTheirPredicateIsFixed() throws IOException {
        final String accessories = "io.wispforest.accessories.mixin.InventoryMixin";
        assertTrue(CosmeticMixinPlugin.accessoriesHookNeverMatches(accessories), "beta.54 predicate starts with e.isEmpty() &&");
        final byte[] fixed = patchClass(accessories, node -> {
            for (MethodNode method : node.methods) {
                if (!method.desc.equals("(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z")) {
                    continue;
                }
                for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (insn instanceof JumpInsnNode jump && jump.getOpcode() == Opcodes.IFEQ) {
                        jump.setOpcode(Opcodes.IFNE);
                        break;
                    }
                }
            }
        });
        withPatchedClass(accessories, fixed, () -> {
            assertFalse(CosmeticMixinPlugin.accessoriesHookNeverMatches(accessories),
                    "a fixed Accessories predicate must turn the gate off");
            assertNotNull(invokeComputeEnvironmentProblem());
        });
    }

    private static String invokeComputeEnvironmentProblem() {
        try {
            final var method = CosmeticMixinPlugin.class.getDeclaredMethod("computeEnvironmentProblem");
            method.setAccessible(true);
            return (String) method.invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static byte[] patchClass(String className, Consumer<ClassNode> patch) throws IOException {
        final String resource = className.replace('.', '/') + ".class";
        try (InputStream input = MixinPluginCompatibilityTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input, resource);
            final ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, ClassReader.SKIP_FRAMES);
            patch.accept(node);
            final ClassWriter writer = new ClassWriter(0);
            node.accept(writer);
            return writer.toByteArray();
        }
    }

    private static MethodNode method(ClassNode node, String name, String desc) {
        for (MethodNode method : node.methods) {
            if (method.name.equals(name) && method.desc.equals(desc)) {
                return method;
            }
        }
        throw new AssertionError("missing " + name + desc);
    }

    private static AbstractInsnNode nthCall(MethodNode method, String name, int n) {
        int seen = 0;
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof MethodInsnNode call && call.name.equals(name) && ++seen == n) {
                return insn;
            }
        }
        throw new AssertionError("missing call " + n + " to " + name);
    }

    /** Serves {@code bytes} for one class resource through the context class loader the inspector reads first. */
    private static void withPatchedClass(String className, byte[] bytes, Runnable body) {
        final String resource = className.replace('.', '/') + ".class";
        final Thread thread = Thread.currentThread();
        final ClassLoader previous = thread.getContextClassLoader();
        thread.setContextClassLoader(new ClassLoader(previous) {
            @Override
            public InputStream getResourceAsStream(String name) {
                return name.equals(resource) ? new ByteArrayInputStream(bytes) : super.getResourceAsStream(name);
            }
        });
        try {
            body.run();
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    @Test
    void pluginsSkipMissingTargetsInsteadOfCrashing() {
        final BotanyMixinPlugin botany = new BotanyMixinPlugin();
        final RefinedStorageMixinPlugin refinedStorage = new RefinedStorageMixinPlugin();
        final MekanismMixinPlugin mekanism = new MekanismMixinPlugin();
        final WaystonesMixinPlugin waystones = new WaystonesMixinPlugin();
        final SimplySwordsMixinPlugin simplySwords = new SimplySwordsMixinPlugin();

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
        assertFalse(simplySwords.shouldApplyMixin(
                "missing.simplyswords.RunicForgeScreenHandler$TabletSlot",
                "com.teamarcadia.arcadiatweaks.neoforge.mixin.simplyswords.RunicForgeTabletSlotMixin"
        ));
    }
}
