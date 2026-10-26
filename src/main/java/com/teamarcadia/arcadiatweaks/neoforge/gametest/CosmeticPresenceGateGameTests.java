package com.teamarcadia.arcadiatweaks.neoforge.gametest;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.authlib.GameProfile;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.teamarcadia.arcadiatweaks.ArcadiaTweaks;
import com.teamarcadia.arcadiatweaks.common.config.ArcadiaConfig;
import com.teamarcadia.arcadiatweaks.neoforge.cosmetic.CosmeticGate;
import com.teamarcadia.arcadiatweaks.neoforge.cosmetic.ItemPresence;
import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.AccessoriesContainer;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.DeferredItem;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * Differential checks of the CosmeticWeapons / CosmeticArmours item-presence gate ({@link CosmeticGate}).
 *
 * Every scenario runs all 26 {@code contains}-based procedures of both mods through their public
 * {@code execute(Entity)} on a real {@code ServerPlayer} twice, gate off then gate on, from the same starting
 * state, and compares the outcome: done flag and completed criteria of every advancement, plus every
 * vanilla, Curios and Accessories stack with its components. Item lists and advancement ids are read from
 * the procedures' own bytecode, so the tests follow the shipped jars.
 *
 * "Gate off" forces {@link CosmeticGate#active} to false (the same path as the kill switch); the Mixin is
 * still in place and passes every call to the original.
 *
 * @author vyrriox
 */
@GameTestHolder(ArcadiaTweaks.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CosmeticPresenceGateGameTests {

    private static final String TEMPLATE = "empty";
    private static final String BATCH = ArcadiaTweaks.MOD_ID;
    private static final String EXECUTE_DESC = "(Lnet/neoforged/bus/api/Event;Lnet/minecraft/world/entity/Entity;)V";

    /** Every tick procedure of both mods that calls Inventory.contains (design table, N >= 1). */
    private static final List<String> CONTAINS_PROCEDURES = List.of(
            "cosmeticarmoursmod.procedures.AngelHaloAchivementProcedureProcedure",
            "cosmeticarmoursmod.procedures.AwokenHaloAdvProdProcedure",
            "cosmeticarmoursmod.procedures.BlankArmourStyleAdvProdProcedure",
            "cosmeticarmoursmod.procedures.FallenAngelHaloAchievementProcedureProcedure",
            "cosmeticarmoursmod.procedures.GloriousCoronationProdProcedure",
            "cosmeticarmoursmod.procedures.GoldenHaloAchievementProcedureProcedure",
            "cosmeticarmoursmod.procedures.ReclaimedArmourStyleAdvProdProcedure",
            "cosmeticarmoursmod.procedures.SculkedArmourStyleAdvProdProcedure",
            "cosmeticarmoursmod.procedures.WingedHaloAchievementProcedureProcedure",
            "cosmeticweaponsmod.procedures.AmethystSwordAdvProdProcedure",
            "cosmeticweaponsmod.procedures.BlankWeaponStyleAdvProdProcedure",
            "cosmeticweaponsmod.procedures.BloomingWeaponStyleTemplateAdvProdProcedure",
            "cosmeticweaponsmod.procedures.DiamondTierWeaponAdvProdProcedure",
            "cosmeticweaponsmod.procedures.EmeraldSwordAdvProdProcedure",
            "cosmeticweaponsmod.procedures.GildedNetheriteTierWeaponAdvProdProcedure",
            "cosmeticweaponsmod.procedures.GlassSwordAdvProdProcedure",
            "cosmeticweaponsmod.procedures.GoldTierWeaponAdvProdProcedure",
            "cosmeticweaponsmod.procedures.IronTierWeaponAdvProdProcedure",
            "cosmeticweaponsmod.procedures.NetheriteTierWeaponAdvProdProcedure",
            "cosmeticweaponsmod.procedures.PrismarineWeaponAdvProdProcedure",
            "cosmeticweaponsmod.procedures.ReclaimedWeaponStyleAdvProdProcedure",
            "cosmeticweaponsmod.procedures.RoseGoldWeaponAdvProdProcedure",
            "cosmeticweaponsmod.procedures.SculkedWeaponStyleAdvProdProcedure",
            "cosmeticweaponsmod.procedures.ShroudedWeaponStyleTemplateAdvProdProcedure",
            "cosmeticweaponsmod.procedures.StoneTierWeaponAdvProdProcedure",
            "cosmeticweaponsmod.procedures.WoodTierWeaponAdvProdProcedure"
    );

    private static final Item[] FILLER = {
            Items.STONE, Items.DIRT, Items.COBBLESTONE, Items.OAK_LOG, Items.TORCH, Items.BREAD, Items.COAL,
            Items.IRON_INGOT, Items.GOLD_INGOT, Items.DIAMOND, Items.ARROW, Items.BOW, Items.SHIELD, Items.IRON_PICKAXE,
            Items.IRON_SHOVEL, Items.OAK_PLANKS, Items.STICK, Items.STRING, Items.BONE, Items.ROTTEN_FLESH,
            Items.APPLE, Items.COOKED_BEEF, Items.WATER_BUCKET, Items.LAVA_BUCKET, Items.FLINT_AND_STEEL,
            Items.REDSTONE, Items.GLASS, Items.SAND, Items.GRAVEL, Items.CRAFTING_TABLE, Items.FURNACE, Items.CHEST,
            Items.LADDER, Items.ENDER_PEARL, Items.EMERALD, Items.LAPIS_LAZULI
    };

    private static List<Procedure> procedures;

    private CosmeticPresenceGateGameTests() {}

    public record Procedure(String className, MethodHandle execute, ResourceLocation advancement, List<Item> items, boolean gated) {}

    // ------------------------------------------------------------------------------------------------ tests

    /** The real startup audit passes in the dev runtime (Curios, Accessories, GeckoLib, RecipeEssentials). */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void cosmeticStartupAuditPassesInModpackShape(GameTestHelper helper) {
        if (!requireMods(helper)) {
            return;
        }
        helper.assertTrue(CosmeticGate.auditPassed(), "Startup audit should pass, failure: " + CosmeticGate.auditFailure());
        helper.assertTrue(ArcadiaConfig.COSMETIC.containsPresenceGate.get(), "Presence gate should default to on");
        helper.succeed();
    }

    /**
     * Empty inventory: each gated procedure calls the original contains once, takes one snapshot and skips
     * the other N - 1 lookups. Single-item procedures are untouched.
     */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void cosmeticGateSkipsEveryAbsentLookupAfterTheFirst(GameTestHelper helper) {
        if (!requireMods(helper)) {
            return;
        }
        final ServerPlayer player = testPlayer(helper);
        final boolean audit = CosmeticGate.auditPassed();
        try {
            CosmeticGate.setAuditPassedForTesting(true);
            reset(player);
            for (Procedure procedure : procedures()) {
                final long skipped = CosmeticGate.skippedLookups();
                final long snapshots = CosmeticGate.snapshots();
                invoke(procedure, player);
                final long expectedSkips = procedure.gated() ? procedure.items().size() - 1 : 0;
                helper.assertValueEqual(CosmeticGate.skippedLookups() - skipped, expectedSkips,
                        "skipped lookups of " + procedure.className());
                helper.assertValueEqual(CosmeticGate.snapshots() - snapshots, procedure.gated() ? 1L : 0L,
                        "snapshots of " + procedure.className());
            }
        } finally {
            CosmeticGate.setAuditPassedForTesting(audit);
            remove(player);
        }
        helper.succeed();
    }

    /** Every target item of every procedure, one at a time, in main slot 0 and 35, armour, offhand, Curios, Accessories. */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 200)
    public static void cosmeticEveryTargetInEverySlotMatchesOriginal(GameTestHelper helper) {
        if (!requireMods(helper)) {
            return;
        }
        final ServerPlayer player = testPlayer(helper);
        try {
            int curioHits = 0;
            int scenarios = 0;
            for (Item item : allTargetItems()) {
                for (Placement placement : Placement.values()) {
                    final ItemStack stack = new ItemStack(item);
                    final String mismatch = differential(player, p -> placement.put(p, stack.copy()));
                    if (mismatch != null) {
                        helper.fail("Gate changed the outcome for " + id(item) + " in " + placement + ": " + mismatch);
                        return;
                    }
                    if (placement == Placement.CURIO) {
                        reset(player);
                        placement.put(player, stack.copy());
                        if (player.getInventory().contains(new ItemStack(item))) {
                            curioHits++;
                        }
                    }
                    scenarios++;
                }
            }
            helper.assertTrue(curioHits > 0, "Items in a Curios slot should be seen by Inventory.contains (Curios hook); "
                    + curiosDiagnostic(player));
            reset(player);
            putAccessory(player, new ItemStack(Items.EMERALD), 0);
            final boolean accessoryPlaced = describe(player).contains(" a");
            ArcadiaTweaks.LOGGER.info("[cosmetic-test] {} placement scenarios identical with the gate on and off ({} curio hits, "
                    + "accessory slot available={})", scenarios, curioHits, accessoryPlaced);
        } finally {
            remove(player);
        }
        helper.succeed();
    }

    /** Same items damaged, renamed, enchanted, with an empty custom_data, or as a stack of 2: same outcome. */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 200)
    public static void cosmeticNearMissesMatchOriginal(GameTestHelper helper) {
        if (!requireMods(helper)) {
            return;
        }
        final ServerPlayer player = testPlayer(helper);
        try {
            final List<UnaryOperator<ItemStack>> variants = variants(player);
            for (Item item : allTargetItems()) {
                for (int v = 0; v < variants.size(); v++) {
                    final ItemStack stack = variants.get(v).apply(new ItemStack(item));
                    final Placement placement = Placement.values()[Math.floorMod(v + id(item).hashCode(), Placement.values().length)];
                    final String mismatch = differential(player, p -> placement.put(p, stack.copy()));
                    if (mismatch != null) {
                        helper.fail("Gate changed the outcome for variant " + v + " of " + id(item) + ": " + mismatch);
                        return;
                    }
                }
            }
            // Renamed target: the original needs the same components, so nothing is granted either way.
            final Procedure prismarine = procedure("PrismarineWeaponAdvProdProcedure");
            final ItemStack renamed = new ItemStack(prismarine.items().get(0));
            renamed.set(DataComponents.CUSTOM_NAME, Component.literal("renamed"));
            reset(player);
            player.getInventory().setItem(0, renamed);
            invoke(prismarine, player);
            helper.assertFalse(isDone(player, prismarine.advancement()), "A renamed prismarine sword must not count");
        } finally {
            remove(player);
        }
        helper.succeed();
    }

    /** A rose gold sword grants RoseGold and GoldTier but not Prismarine, with the gate on. */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void cosmeticOtherChainItemOnlyGrantsItsOwnAdvancements(GameTestHelper helper) {
        if (!requireMods(helper)) {
            return;
        }
        final ServerPlayer player = testPlayer(helper);
        final boolean audit = CosmeticGate.auditPassed();
        try {
            CosmeticGate.setAuditPassedForTesting(true);
            reset(player);
            final Procedure roseGold = procedure("RoseGoldWeaponAdvProdProcedure");
            player.getInventory().setItem(20, new ItemStack(roseGold.items().get(0)));
            runAll(player);
            helper.assertTrue(isDone(player, roseGold.advancement()), "rose_gold_weapon_adv should be granted");
            helper.assertTrue(isDone(player, procedure("GoldTierWeaponAdvProdProcedure").advancement()), "gold tier should be granted");
            helper.assertFalse(isDone(player, procedure("PrismarineWeaponAdvProdProcedure").advancement()), "prismarine must not be granted");
            helper.assertFalse(isDone(player, procedure("IronTierWeaponAdvProdProcedure").advancement()), "iron tier must not be granted");
        } finally {
            CosmeticGate.setAuditPassedForTesting(audit);
            remove(player);
        }
        helper.succeed();
    }

    /** Two players, procedures interleaved player by player: same result as each player alone with the gate off. */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 40)
    public static void cosmeticTwoInterleavedPlayersDoNotLeak(GameTestHelper helper) {
        if (!requireMods(helper)) {
            return;
        }
        final ServerPlayer holder = testPlayer(helper);
        final ServerPlayer empty = testPlayer(helper);
        final boolean audit = CosmeticGate.auditPassed();
        try {
            final List<Item> items = allTargetItems();
            final Random random = new Random(7);
            for (int round = 0; round < 40; round++) {
                final Item a = items.get(random.nextInt(items.size()));
                final Item b = items.get(random.nextInt(items.size()));
                final Consumer<ServerPlayer> setupHolder = p -> {
                    p.getInventory().setItem(3, new ItemStack(a));
                    p.getInventory().setItem(30, new ItemStack(b));
                };
                CosmeticGate.setAuditPassedForTesting(false);
                prepare(holder, setupHolder);
                prepare(empty, p -> { });
                runAll(holder);
                runAll(empty);
                final String expectedHolder = describe(holder);
                final String expectedEmpty = describe(empty);

                CosmeticGate.setAuditPassedForTesting(true);
                prepare(holder, setupHolder);
                prepare(empty, p -> { });
                for (Procedure procedure : procedures()) {
                    invoke(procedure, holder);
                    invoke(procedure, empty);
                }
                helper.assertValueEqual(describe(holder), expectedHolder, "holder outcome, round " + round);
                helper.assertValueEqual(describe(empty), expectedEmpty, "empty player outcome, round " + round);
            }
        } finally {
            CosmeticGate.setAuditPassedForTesting(audit);
            remove(holder);
            remove(empty);
        }
        helper.succeed();
    }

    /**
     * 500 seeded inventories: targets, near-misses, filler, empty and count-0 stacks, Curios and Accessories
     * stacks, and some advancements already done. Identical outcome with the gate on and off.
     */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 400)
    public static void cosmeticRandomInventoriesMatchOriginal(GameTestHelper helper) {
        if (!requireMods(helper)) {
            return;
        }
        final ServerPlayer player = testPlayer(helper);
        try {
            final List<Item> targets = allTargetItems();
            final List<UnaryOperator<ItemStack>> variants = variants(player);
            int granted = 0;
            for (int seed = 0; seed < 500; seed++) {
                final Random random = new Random(seed * 31L + 5);
                final List<ResourceLocation> preDone = new ArrayList<>();
                for (Procedure procedure : procedures()) {
                    if (random.nextInt(6) == 0) {
                        preDone.add(procedure.advancement());
                    }
                }
                final Consumer<ServerPlayer> setup = p -> randomInventory(p, random, targets, variants, preDone);
                final long seedForReplay = seed * 31L + 5;
                final String mismatch = differential(player, p -> {
                    random.setSeed(seedForReplay);
                    preDoneReplay(random);
                    setup.accept(p);
                });
                if (mismatch != null) {
                    helper.fail("Gate changed the outcome for seed " + seed + ": " + mismatch);
                    return;
                }
                granted += countDone(player);
            }
            helper.assertTrue(granted > 0, "Random inventories should grant some advancements");
            ArcadiaTweaks.LOGGER.info("[cosmetic-test] 500 random inventories identical with the gate on and off ({} grants)", granted);
        } finally {
            remove(player);
        }
        helper.succeed();
    }

    /** A deliberately wrong gate (skips present items too) must be caught by the differential check. */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 40)
    public static void cosmeticDifferentialCatchesAWrongGate(GameTestHelper helper) {
        if (!requireMods(helper)) {
            return;
        }
        final ServerPlayer player = testPlayer(helper);
        int caught = 0;
        try {
            CosmeticGate.setBrokenForTesting(true);
            for (Procedure procedure : procedures()) {
                if (!procedure.gated()) {
                    continue;
                }
                // The last item of the chain is only reached after the first lookup was false.
                final Item last = procedure.items().get(procedure.items().size() - 1);
                if (differential(player, p -> p.getInventory().setItem(10, new ItemStack(last))) != null) {
                    caught++;
                }
            }
        } finally {
            CosmeticGate.setBrokenForTesting(false);
            remove(player);
        }
        helper.assertValueEqual(caught, 15, "every gated procedure should expose the wrong gate");
        helper.succeed();
    }

    /** Kill switch, failed audit and a call off the server thread all pass every lookup to the original. */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 40)
    public static void cosmeticDisabledPathsCallTheOriginalEveryTime(GameTestHelper helper) {
        if (!requireMods(helper)) {
            return;
        }
        final ServerPlayer player = testPlayer(helper);
        final boolean audit = CosmeticGate.auditPassed();
        try {
            reset(player);
            CosmeticGate.setAuditPassedForTesting(true);
            ArcadiaConfig.COSMETIC.containsPresenceGate.set(false);
            long skipped = CosmeticGate.skippedLookups();
            runAll(player);
            helper.assertValueEqual(CosmeticGate.skippedLookups() - skipped, 0L, "kill switch off: nothing skipped");
            ArcadiaConfig.COSMETIC.containsPresenceGate.set(true);

            CosmeticGate.setAuditPassedForTesting(false);
            skipped = CosmeticGate.skippedLookups();
            runAll(player);
            helper.assertValueEqual(CosmeticGate.skippedLookups() - skipped, 0L, "audit failed: nothing skipped");

            CosmeticGate.setAuditPassedForTesting(true);
            skipped = CosmeticGate.skippedLookups();
            final AtomicReference<Throwable> error = new AtomicReference<>();
            final Thread other = new Thread(() -> {
                try {
                    invoke(procedure("PrismarineWeaponAdvProdProcedure"), player);
                } catch (Throwable t) {
                    error.set(t);
                }
            }, "arcadia-cosmetic-offthread-test");
            other.start();
            other.join();
            helper.assertTrue(error.get() == null, "off-thread call failed: " + error.get());
            helper.assertValueEqual(CosmeticGate.skippedLookups() - skipped, 0L, "off the server thread: nothing skipped");

            skipped = CosmeticGate.skippedLookups();
            runAll(player);
            helper.assertTrue(CosmeticGate.skippedLookups() - skipped > 0, "gate back on: lookups skipped again");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            helper.fail("interrupted");
        } finally {
            ArcadiaConfig.COSMETIC.containsPresenceGate.set(true);
            CosmeticGate.setAuditPassedForTesting(audit);
            remove(player);
        }
        helper.succeed();
    }

    /**
     * The gate state machine on a real inventory with a counting original: once per call when every later
     * target is absent, once more per present target, never a different answer.
     */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void cosmeticGateCallsTheOriginalOnlyWhenItCanMatch(GameTestHelper helper) {
        if (!requireMods(helper)) {
            return;
        }
        final ServerPlayer player = testPlayer(helper);
        final boolean audit = CosmeticGate.auditPassed();
        try {
            CosmeticGate.setAuditPassedForTesting(true);
            reset(player);
            final Inventory inventory = player.getInventory();
            inventory.setItem(5, new ItemStack(Items.DIAMOND_SWORD));
            final ItemStack renamedAxe = new ItemStack(Items.IRON_AXE);
            renamedAxe.set(DataComponents.CUSTOM_NAME, Component.literal("x"));
            inventory.setItem(6, renamedAxe);

            final int[] calls = {0};
            final Operation<Boolean> original = args -> {
                calls[0]++;
                return ((Inventory) args[0]).contains((ItemStack) args[1]);
            };

            // All absent: original once.
            final LocalRef<ItemPresence> ref1 = new Ref();
            for (Item item : List.of(Items.WOODEN_SWORD, Items.STONE_SWORD, Items.GOLDEN_SWORD, Items.NETHERITE_SWORD)) {
                helper.assertFalse(CosmeticGate.contains(inventory, new ItemStack(item), original, ref1), "absent " + id(item));
            }
            helper.assertValueEqual(calls[0], 1, "original calls, all absent");

            // Present but other components (renamed iron axe): the real contains decides, answers false.
            calls[0] = 0;
            final LocalRef<ItemPresence> ref2 = new Ref();
            helper.assertFalse(CosmeticGate.contains(inventory, new ItemStack(Items.WOODEN_AXE), original, ref2), "absent wooden axe");
            helper.assertFalse(CosmeticGate.contains(inventory, new ItemStack(Items.IRON_AXE), original, ref2), "renamed iron axe");
            helper.assertFalse(CosmeticGate.contains(inventory, new ItemStack(Items.GOLDEN_AXE), original, ref2), "absent golden axe");
            helper.assertTrue(CosmeticGate.contains(inventory, new ItemStack(Items.DIAMOND_SWORD), original, ref2), "diamond sword");
            helper.assertValueEqual(calls[0], 3, "original calls: first, renamed axe, diamond sword");

            // First lookup true: the chain stops, the gate stays out of the way.
            calls[0] = 0;
            final LocalRef<ItemPresence> ref3 = new Ref();
            helper.assertTrue(CosmeticGate.contains(inventory, new ItemStack(Items.DIAMOND_SWORD), original, ref3), "first true");
            helper.assertTrue(ref3.get() == ItemPresence.DISABLED, "a true first lookup disables the gate for the call");

            // Empty target always goes to the original.
            calls[0] = 0;
            final LocalRef<ItemPresence> ref4 = new Ref();
            CosmeticGate.contains(inventory, new ItemStack(Items.WOODEN_SWORD), original, ref4);
            CosmeticGate.contains(inventory, ItemStack.EMPTY, original, ref4);
            helper.assertValueEqual(calls[0], 2, "empty target goes to the original");
        } finally {
            CosmeticGate.setAuditPassedForTesting(audit);
            remove(player);
        }
        helper.succeed();
    }

    /**
     * Microbenchmark, only with {@code -ParcadiaCosmeticBench=true}: one player with a full, realistic
     * inventory and none of the advancements done, all 26 procedures per "player tick", gate off vs on.
     */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void cosmeticPresenceGateBenchmark(GameTestHelper helper) {
        if (!Boolean.getBoolean("arcadia.cosmeticBench") || !ModList.get().isLoaded("cosmeticweaponsmod")) {
            helper.succeed();
            return;
        }
        final ServerPlayer player = testPlayer(helper);
        final boolean audit = CosmeticGate.auditPassed();
        try {
            reset(player);
            benchInventory(player);
            final List<Procedure> all = procedures();
            final List<Procedure> gated = all.stream().filter(Procedure::gated).toList();
            final int warmup = 20_000;
            final int timed = 100_000;
            final long[][] results = new long[2][2];
            for (int round = 0; round < 2; round++) {
                for (int mode = 0; mode < 2; mode++) {
                    CosmeticGate.setAuditPassedForTesting(mode == 1);
                    loop(all, player, warmup);
                    results[mode][0] += loop(all, player, timed);
                    results[mode][1] += loop(gated, player, timed);
                }
            }
            final double offAll = results[0][0] / (2.0 * timed);
            final double onAll = results[1][0] / (2.0 * timed);
            final double offGated = results[0][1] / (2.0 * timed);
            final double onGated = results[1][1] / (2.0 * timed);
            ArcadiaTweaks.LOGGER.info("[cosmetic-bench] all 26 procedures: off={} ns/tick on={} ns/tick ratio={}",
                    Math.round(offAll), Math.round(onAll), String.format("%.3f", onAll / offAll));
            ArcadiaTweaks.LOGGER.info("[cosmetic-bench] 15 gated procedures: off={} ns/tick on={} ns/tick ratio={}",
                    Math.round(offGated), Math.round(onGated), String.format("%.3f", onGated / offGated));
            final String method = " GameTest-hosted bench (-ParcadiaCosmeticBench=true), same JVM, 2 rounds x (20k warm-up + " + timed
                    + " timed iterations) per mode, alternating off/on. Unplaced ServerPlayer: 36 filler main stacks, 4 iron armour,"
                    + " offhand torches, up to 5 Curios and 5 Accessories items (as many as the slots take), no advancement done. 'before' = gate off through the kill-switch path"
                    + " (Mixin present, every call passed to the original).";
            MicroBenchResults.record("cosmetic", "cosmetic: all 26 contains procedures (CosmeticWeapons + CosmeticArmours) per player tick",
                    "ns/player tick", offAll, onAll, "All 26 procedures' execute per iteration." + method
                            + String.format(java.util.Locale.ROOT, " Ratio %.3f.", onAll / offAll));
            MicroBenchResults.record("cosmetic", "cosmetic: 15 gated procedures (N >= 2) per player tick",
                    "ns/player tick", offGated, onGated, "Only the 15 gated procedures; the 11 single-item ones are not targeted." + method
                            + String.format(java.util.Locale.ROOT, " Ratio %.3f.", onGated / offGated));
            helper.assertTrue(countDone(player) == 0, "bench inventory must not grant anything");
        } finally {
            CosmeticGate.setAuditPassedForTesting(audit);
            remove(player);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------------------------------- harness

    private enum Placement {
        MAIN_FIRST, MAIN_LAST, ARMOUR, OFFHAND, CURIO, ACCESSORY;

        void put(ServerPlayer player, ItemStack stack) {
            final Inventory inventory = player.getInventory();
            switch (this) {
                case MAIN_FIRST -> inventory.items.set(0, stack);
                case MAIN_LAST -> inventory.items.set(35, stack);
                case ARMOUR -> inventory.armor.set(3, stack);
                case OFFHAND -> inventory.offhand.set(0, stack);
                case CURIO -> putCurio(player, stack, 0);
                case ACCESSORY -> putAccessory(player, stack, 0);
            }
        }
    }

    /** Runs all procedures gate off then gate on from the same state; null when the outcomes are equal. */
    private static String differential(ServerPlayer player, Consumer<ServerPlayer> setup) {
        final boolean audit = CosmeticGate.auditPassed();
        try {
            CosmeticGate.setAuditPassedForTesting(false);
            prepare(player, setup);
            runAll(player);
            final String off = describe(player);

            CosmeticGate.setAuditPassedForTesting(true);
            prepare(player, setup);
            runAll(player);
            final String on = describe(player);
            return off.equals(on) ? null : "\n off: " + off + "\n on:  " + on;
        } finally {
            CosmeticGate.setAuditPassedForTesting(audit);
        }
    }

    private static void prepare(ServerPlayer player, Consumer<ServerPlayer> setup) {
        reset(player);
        setup.accept(player);
    }

    private static void runAll(ServerPlayer player) {
        for (Procedure procedure : procedures()) {
            invoke(procedure, player);
        }
    }

    private static long loop(List<Procedure> list, ServerPlayer player, int iterations) {
        final long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            for (int p = 0, n = list.size(); p < n; p++) {
                invoke(list.get(p), player);
            }
        }
        return System.nanoTime() - start;
    }

    private static void invoke(Procedure procedure, Entity entity) {
        try {
            procedure.execute().invokeExact(entity);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    /** Advancements of every procedure (done flag, completed criteria) and every stack the gate can see. */
    private static String describe(ServerPlayer player) {
        final StringBuilder out = new StringBuilder();
        final Set<ResourceLocation> seen = new LinkedHashSet<>();
        for (Procedure procedure : procedures()) {
            if (!seen.add(procedure.advancement())) {
                continue;
            }
            final AdvancementHolder holder = player.server.getAdvancements().get(procedure.advancement());
            final AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
            final Set<String> done = new TreeSet<>();
            progress.getCompletedCriteria().forEach(done::add);
            out.append(procedure.advancement().getPath()).append(progress.isDone() ? "=done" : "=open").append(done).append(' ');
        }
        out.append("| inv ");
        final Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            appendStack(out, inventory.getItem(i), "i" + i);
        }
        CuriosApi.getCuriosInventory(player).ifPresent(handler -> handler.getCurios().forEach((id, stacks) -> {
            for (int i = 0; i < stacks.getSlots(); i++) {
                appendStack(out, stacks.getStacks().getStackInSlot(i), "c" + id + i);
                appendStack(out, stacks.getCosmeticStacks().getStackInSlot(i), "cc" + id + i);
            }
        }));
        final AccessoriesCapability accessories = AccessoriesCapability.get(player);
        if (accessories != null) {
            accessories.getContainers().forEach((id, container) -> {
                final Container items = container.getAccessories();
                for (int i = 0; i < items.getContainerSize(); i++) {
                    appendStack(out, items.getItem(i), "a" + id + i);
                }
            });
        }
        return out.toString();
    }

    private static void appendStack(StringBuilder out, ItemStack stack, String slot) {
        if (!stack.isEmpty()) {
            out.append(slot).append('=').append(id(stack.getItem())).append('x').append(stack.getCount())
                    .append(stack.getComponentsPatch()).append(' ');
        }
    }

    private static int countDone(ServerPlayer player) {
        int done = 0;
        for (Procedure procedure : procedures()) {
            if (isDone(player, procedure.advancement())) {
                done++;
            }
        }
        return done;
    }

    private static boolean isDone(ServerPlayer player, ResourceLocation advancement) {
        final AdvancementHolder holder = player.server.getAdvancements().get(advancement);
        return player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    /** Empty inventory, Curios and Accessories, no advancement of the procedures done. */
    private static void reset(ServerPlayer player) {
        player.getInventory().clearContent();
        CuriosApi.getCuriosInventory(player).ifPresent(handler -> handler.getCurios().values().forEach(stacks -> {
            for (int i = 0; i < stacks.getSlots(); i++) {
                stacks.getStacks().setStackInSlot(i, ItemStack.EMPTY);
                stacks.getCosmeticStacks().setStackInSlot(i, ItemStack.EMPTY);
            }
        }));
        final AccessoriesCapability accessories = AccessoriesCapability.get(player);
        if (accessories != null) {
            accessories.getContainers().values().forEach(container -> container.getAccessories().clearContent());
        }
        for (Procedure procedure : procedures()) {
            final AdvancementHolder holder = player.server.getAdvancements().get(procedure.advancement());
            final AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
            for (String criterion : progress.getCompletedCriteria()) {
                player.getAdvancements().revoke(holder, criterion);
            }
        }
    }

    private static void grant(ServerPlayer player, ResourceLocation advancement) {
        final AdvancementHolder holder = player.server.getAdvancements().get(advancement);
        for (String criterion : player.getAdvancements().getOrStartProgress(holder).getRemainingCriteria()) {
            player.getAdvancements().award(holder, criterion);
        }
    }

    private static void putCurio(ServerPlayer player, ItemStack stack, int nth) {
        final ICuriosItemHandler handler = CuriosApi.getCuriosInventory(player).orElse(null);
        if (handler == null) {
            return;
        }
        if (handler.getCurios().values().stream().noneMatch(stacks -> stacks.getSlots() > 0)) {
            handler.addTransientSlotModifier("ring", ResourceLocation.fromNamespaceAndPath(ArcadiaTweaks.MOD_ID, "cosmetic_test"),
                    2, AttributeModifier.Operation.ADD_VALUE);
        }
        int index = 0;
        for (ICurioStacksHandler stacks : handler.getCurios().values()) {
            for (int i = 0; i < stacks.getSlots(); i++) {
                if (index++ == nth) {
                    stacks.getStacks().setStackInSlot(i, stack);
                    return;
                }
            }
        }
    }

    private static String curiosDiagnostic(ServerPlayer player) {
        final Optional<ICuriosItemHandler> handler = CuriosApi.getCuriosInventory(player);
        if (handler.isEmpty()) {
            return "no Curios inventory on the test player";
        }
        reset(player);
        putCurio(player, new ItemStack(Items.EMERALD), 0);
        final StringBuilder out = new StringBuilder("slots:");
        handler.get().getCurios().forEach((id, stacks) -> out.append(' ').append(id).append('=').append(stacks.getSlots()));
        out.append(" findFirstCurio(emerald)=").append(handler.get().findFirstCurio(Items.EMERALD).isPresent());
        out.append(" contains=").append(player.getInventory().contains(new ItemStack(Items.EMERALD)));
        return out.toString();
    }

    private static void putAccessory(ServerPlayer player, ItemStack stack, int nth) {
        final AccessoriesCapability accessories = AccessoriesCapability.get(player);
        if (accessories == null) {
            return;
        }
        int index = 0;
        for (AccessoriesContainer container : accessories.getContainers().values()) {
            final Container items = container.getAccessories();
            for (int i = 0; i < items.getContainerSize(); i++) {
                if (index++ == nth) {
                    items.setItem(i, stack);
                    return;
                }
            }
        }
    }

    private static List<UnaryOperator<ItemStack>> variants(ServerPlayer player) {
        final Holder<Enchantment> sharpness = player.level().registryAccess()
                .registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.SHARPNESS);
        return List.of(
                stack -> {
                    if (stack.isDamageableItem()) {
                        stack.setDamageValue(1);
                    } else {
                        stack.set(DataComponents.CUSTOM_NAME, Component.literal("worn"));
                    }
                    return stack;
                },
                stack -> {
                    stack.set(DataComponents.CUSTOM_NAME, Component.literal("renamed"));
                    return stack;
                },
                stack -> {
                    stack.enchant(sharpness, 1);
                    return stack;
                },
                stack -> {
                    stack.set(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
                    return stack;
                },
                stack -> {
                    stack.setCount(Math.min(2, stack.getMaxStackSize()));
                    return stack;
                },
                stack -> {
                    stack.setCount(0);
                    return stack;
                }
        );
    }

    private static void preDoneReplay(Random random) {
        for (int i = 0; i < procedures().size(); i++) {
            random.nextInt(6);
        }
    }

    private static void randomInventory(ServerPlayer player, Random random, List<Item> targets,
                                        List<UnaryOperator<ItemStack>> variants, List<ResourceLocation> preDone) {
        for (ResourceLocation advancement : preDone) {
            grant(player, advancement);
        }
        final Inventory inventory = player.getInventory();
        final int filled = random.nextInt(inventory.getContainerSize() + 1);
        for (int n = 0; n < filled; n++) {
            final int slot = random.nextInt(inventory.getContainerSize());
            inventory.setItem(slot, randomStack(random, targets, variants));
        }
        final int curios = random.nextInt(4);
        for (int n = 0; n < curios; n++) {
            putCurio(player, randomStack(random, targets, variants), random.nextInt(4));
        }
        final int accessories = random.nextInt(4);
        for (int n = 0; n < accessories; n++) {
            putAccessory(player, randomStack(random, targets, variants), random.nextInt(6));
        }
    }

    private static ItemStack randomStack(Random random, List<Item> targets, List<UnaryOperator<ItemStack>> variants) {
        final int kind = random.nextInt(10);
        if (kind < 2) {
            return new ItemStack(targets.get(random.nextInt(targets.size())));
        }
        if (kind < 4) {
            return variants.get(random.nextInt(variants.size())).apply(new ItemStack(targets.get(random.nextInt(targets.size()))));
        }
        if (kind == 4) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(FILLER[random.nextInt(FILLER.length)], 1 + random.nextInt(16));
    }

    private static void benchInventory(ServerPlayer player) {
        final Inventory inventory = player.getInventory();
        for (int i = 0; i < 36; i++) {
            inventory.items.set(i, new ItemStack(FILLER[i % FILLER.length], 1 + i % 16));
        }
        inventory.armor.set(0, new ItemStack(Items.IRON_BOOTS));
        inventory.armor.set(1, new ItemStack(Items.IRON_LEGGINGS));
        inventory.armor.set(2, new ItemStack(Items.IRON_CHESTPLATE));
        inventory.armor.set(3, new ItemStack(Items.IRON_HELMET));
        inventory.offhand.set(0, new ItemStack(Items.TORCH, 32));
        final Item[] small = {Items.EMERALD, Items.GOLD_NUGGET, Items.IRON_NUGGET, Items.AMETHYST_SHARD, Items.QUARTZ};
        for (int i = 0; i < 5; i++) {
            putCurio(player, new ItemStack(small[i]), i);
            putAccessory(player, new ItemStack(small[(i + 2) % 5]), i);
        }
    }

    private static List<Item> allTargetItems() {
        final Set<Item> items = new LinkedHashSet<>();
        for (Procedure procedure : procedures()) {
            items.addAll(procedure.items());
        }
        return new ArrayList<>(items);
    }

    private static Procedure procedure(String simpleName) {
        for (Procedure procedure : procedures()) {
            if (procedure.className().endsWith("." + simpleName)) {
                return procedure;
            }
        }
        throw new IllegalArgumentException(simpleName);
    }

    private static String id(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    private static boolean requireMods(GameTestHelper helper) {
        if (!ModList.get().isLoaded("cosmeticweaponsmod") || !ModList.get().isLoaded("cosmeticarmoursmod")
                || !ModList.get().isLoaded("curios") || !ModList.get().isLoaded("accessories")) {
            helper.fail("CosmeticWeapons, CosmeticArmours, Curios and Accessories must be in the dev runtime");
            return false;
        }
        procedures();
        return true;
    }

    /**
     * A real {@code ServerPlayer} (its constructor loads real {@code PlayerAdvancements}) that is not placed in
     * the world: placing one through a mock connection fails because Accessories syncs its data to a client
     * that never negotiated its channel. The procedures only need the player, its inventory, its Curios and
     * Accessories data and its advancements; nothing ticks it.
     */
    private static ServerPlayer testPlayer(GameTestHelper helper) {
        final MinecraftServer server = helper.getLevel().getServer();
        final ServerPlayer player = new ServerPlayer(server, helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "cosmetic-test"), ClientInformation.createDefault());
        player.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(1, 2, 1)));
        return player;
    }

    private static void remove(ServerPlayer player) {
        reset(player);
        player.getAdvancements().stopListening();
    }

    /** Procedures, their item chain and advancement id, read from the shipped bytecode. */
    public static synchronized List<Procedure> procedures() {
        if (procedures != null) {
            return procedures;
        }
        final List<Procedure> list = new ArrayList<>();
        for (String className : CONTAINS_PROCEDURES) {
            try {
                final Class<?> type = Class.forName(className, true, CosmeticPresenceGateGameTests.class.getClassLoader());
                final MethodHandle execute = MethodHandles.publicLookup()
                        .findStatic(type, "execute", MethodType.methodType(void.class, Entity.class));
                final ClassNode node = new ClassNode();
                try (InputStream input = type.getClassLoader().getResourceAsStream(className.replace('.', '/') + ".class")) {
                    new ClassReader(input).accept(node, ClassReader.SKIP_FRAMES);
                }
                MethodNode body = null;
                for (MethodNode method : node.methods) {
                    if (method.name.equals("execute") && method.desc.equals(EXECUTE_DESC)) {
                        body = method;
                    }
                }
                if (body == null) {
                    throw new IllegalStateException("no execute(Event, Entity) in " + className);
                }
                final List<Item> items = new ArrayList<>();
                ResourceLocation advancement = null;
                int containsCalls = 0;
                for (AbstractInsnNode insn = body.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof String text && advancement == null) {
                        advancement = ResourceLocation.parse(text);
                    } else if (insn instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC) {
                        final Object value = Class.forName(field.owner.replace('/', '.'), true, type.getClassLoader())
                                .getField(field.name).get(null);
                        if (value instanceof DeferredItem<?> deferred) {
                            items.add(deferred.get());
                        } else if (value instanceof Item item) {
                            items.add(item);
                        }
                    } else if (insn instanceof MethodInsnNode call && call.name.equals("contains")
                            && call.owner.equals("net/minecraft/world/entity/player/Inventory")) {
                        containsCalls++;
                    }
                }
                if (items.size() != containsCalls || advancement == null) {
                    throw new IllegalStateException(className + ": " + items.size() + " items for " + containsCalls + " contains calls");
                }
                list.add(new Procedure(className, execute, advancement, List.copyOf(items), CosmeticGate.GATED_PROCEDURES.contains(className)));
            } catch (ReflectiveOperationException | java.io.IOException e) {
                throw new IllegalStateException("Cannot read cosmetic procedure " + className, e);
            }
        }
        procedures = List.copyOf(list);
        return procedures;
    }

    private static final class Ref implements LocalRef<ItemPresence> {
        private ItemPresence value;

        @Override
        public ItemPresence get() {
            return value;
        }

        @Override
        public void set(ItemPresence value) {
            this.value = value;
        }
    }
}
