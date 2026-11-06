package com.teamarcadia.arcadiatweaks.neoforge.gametest;

import alabaster.crabbersdelight.common.block.entity.TackleBoxBlockEntity;
import alabaster.crabbersdelight.common.utils.TackleBoxProximity;
import com.mojang.authlib.GameProfile;
import com.teamarcadia.arcadiatweaks.ArcadiaTweaks;
import com.teamarcadia.arcadiatweaks.common.config.ArcadiaConfig;
import com.teamarcadia.arcadiatweaks.common.modules.sparework.SpareWorkFlags;
import com.teamarcadia.arcadiatweaks.neoforge.sparework.ArcadiaReusableDequeLocal;
import com.teamarcadia.arcadiatweaks.neoforge.sparework.SpareWorkCounters;
import com.teamarcadia.arcadiatweaks.neoforge.sparework.SpareWorkPatchAudit;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.sweenus.simplyswords.loot.PityLootContext;
import net.sweenus.simplyswords.loot.PityLootManager;
import net.sweenus.simplyswords.loot.PityStateHolder;
import net.sweenus.simplyswords.loot.PlayerPityState;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Differential checks of the spare work patches: each scenario runs with the patch off (the original code)
 * and on, from the same state, and compares every answer and every side effect the original has. Counters
 * prove that the shortcut really ran where it should, and did not run where the original must decide.
 *
 * @author vyrriox
 */
@GameTestHolder(ArcadiaTweaks.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SpareWorkGameTests {

    private static final String TEMPLATE = "empty";
    private static final String BATCH = ArcadiaTweaks.MOD_ID;

    /** Far from the test structures. Every scene takes its own chunks, so tests never see each other. */
    private static int nextSceneChunkX = 4000;

    private SpareWorkGameTests() {}

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void everyPatchIsAppliedAndOnByDefault(GameTestHelper helper) {
        helper.assertTrue(SpareWorkPatchAudit.fastSuiteApplied(), "FastSuite patch should be applied");
        helper.assertTrue(SpareWorkPatchAudit.simplySwordsApplied(), "Simply Swords patch should be applied");
        helper.assertTrue(SpareWorkPatchAudit.structuresApplied(), "Structure lookup patch should be applied");
        helper.assertTrue(SpareWorkPatchAudit.crabbersDelightApplied(), "Crabber's Delight patch should be applied");
        helper.assertTrue(SpareWorkFlags.fastSuiteSkipEmptyParallelLookup(), "FastSuite patch should default to on");
        helper.assertTrue(SpareWorkFlags.simplySwordsReusePityStack(), "Simply Swords patch should default to on");
        helper.assertTrue(SpareWorkFlags.structureLookupUsesLoadedChunk(), "Structure lookup patch should default to on");
        helper.assertTrue(SpareWorkFlags.crabbersDelightSkipEmptyTackleBoxSearch(), "Crabber's Delight patch should default to on");
        helper.assertTrue(SpareWorkFlags.onServerThread(), "GameTests run on the server thread");
        helper.succeed();
    }

    // ---------------------------------------------------------------- FastSuite

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void fastSuiteListWithoutParallelRecipesAnswersTheSame(GameTestHelper helper) {
        final FastSuiteList list = FastSuiteList.of(List.of(
                holder("stone_to_gravel", new SerialRecipe(Items.STONE, Items.GRAVEL)),
                holder("stone_to_andesite", new SerialRecipe(Items.STONE, Items.ANDESITE)),
                holder("dirt_to_mud", new SerialRecipe(Items.DIRT, Items.MUD))));
        final Level level = helper.getLevel();
        final ModConfigSpec.BooleanValue flag = ArcadiaConfig.SPARE_WORK.fastSuiteSkipEmptyParallelLookup;

        final long beforeOff = SpareWorkCounters.fastSuiteLookupsSkipped;
        final List<String> off = with(flag, false, () -> list.answers(level));
        helper.assertValueEqual(SpareWorkCounters.fastSuiteLookupsSkipped, beforeOff, "lookups skipped with the patch off");

        final long beforeOn = SpareWorkCounters.fastSuiteLookupsSkipped;
        final List<String> on = with(flag, true, () -> list.answers(level));
        helper.assertValueEqual(SpareWorkCounters.fastSuiteLookupsSkipped, beforeOn + 6, "lookups skipped with the patch on");

        helper.assertValueEqual(on, off, "answers of a list without parallel recipes");
        helper.assertValueEqual(off, List.of(
                "first " + Items.STONE + "=arcadiatweaks:stone_to_gravel",
                "first " + Items.DIRT + "=arcadiatweaks:dirt_to_mud",
                "first " + Items.SAND + "=none",
                "all " + Items.STONE + "=[arcadiatweaks:stone_to_andesite, arcadiatweaks:stone_to_gravel]",
                "all " + Items.DIRT + "=[arcadiatweaks:dirt_to_mud]",
                "all " + Items.SAND + "=[]"), "answers of the original");
        // Callers own the list they get, as before.
        with(flag, true, () -> {
            list.all(Items.STONE, level).clear();
            list.all(Items.SAND, level).add(holder("added", new SerialRecipe(Items.SAND, Items.GLASS)));
            return null;
        });
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void fastSuiteListWithParallelRecipesStillUsesThePool(GameTestHelper helper) {
        final ShapelessRecipe vanilla = new ShapelessRecipe("", CraftingBookCategory.MISC, new ItemStack(Items.COBBLESTONE),
                NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.STONE)));
        final FastSuiteList list = FastSuiteList.of(List.of(
                holder("stone_to_gravel", new SerialRecipe(Items.STONE, Items.GRAVEL)),
                holder("stone_to_cobblestone", vanilla),
                holder("dirt_to_mud", new SerialRecipe(Items.DIRT, Items.MUD))));
        final Level level = helper.getLevel();
        final ModConfigSpec.BooleanValue flag = ArcadiaConfig.SPARE_WORK.fastSuiteSkipEmptyParallelLookup;

        final List<String> off = with(flag, false, () -> list.answers(level));
        final long before = SpareWorkCounters.fastSuiteLookupsSkipped;
        final List<String> on = with(flag, true, () -> list.answers(level));
        helper.assertValueEqual(SpareWorkCounters.fastSuiteLookupsSkipped, before, "lookups skipped with a parallel recipe in the list");
        helper.assertValueEqual(on, off, "answers of a list with a parallel recipe");
        helper.assertTrue(off.getFirst().endsWith("arcadiatweaks:stone_to_cobblestone"), "The parallel recipe comes first: " + off.getFirst());
        helper.succeed();
    }

    private static <T extends Recipe<?>> RecipeHolder<T> holder(String name, T recipe) {
        return new RecipeHolder<>(ResourceLocation.fromNamespaceAndPath(ArcadiaTweaks.MOD_ID, name), recipe);
    }

    /** Not a vanilla class, so FastSuite keeps it out of its parallel list, like every recipe of a mod. */
    private record SerialRecipe(Item input, Item result) implements Recipe<CraftingInput> {
        @Override
        public boolean matches(CraftingInput in, Level level) {
            return in.getItem(0).is(input);
        }

        @Override
        public ItemStack assemble(CraftingInput in, HolderLookup.Provider registries) {
            return new ItemStack(result);
        }

        @Override
        public boolean canCraftInDimensions(int width, int height) {
            return true;
        }

        @Override
        public ItemStack getResultItem(HolderLookup.Provider registries) {
            return new ItemStack(result);
        }

        @Override
        public RecipeSerializer<?> getSerializer() {
            return RecipeSerializer.SHAPELESS_RECIPE;
        }

        @Override
        public RecipeType<?> getType() {
            return RecipeType.CRAFTING;
        }
    }

    /** FastSuite's package-private list, through reflection. */
    private record FastSuiteList(Object list, Method getRecipeFor, Method getRecipesFor) {

        static FastSuiteList of(Collection<? extends RecipeHolder<?>> recipes) {
            try {
                final Class<?> type = Class.forName("dev.shadowsoffire.fastsuite.CachedRecipeList");
                final Constructor<?> constructor = type.getDeclaredConstructor(RecipeType.class, Collection.class);
                constructor.setAccessible(true);
                final Method first = type.getDeclaredMethod("getRecipeFor", RecipeInput.class, Level.class);
                final Method all = type.getDeclaredMethod("getRecipesFor", RecipeInput.class, Level.class);
                first.setAccessible(true);
                all.setAccessible(true);
                return new FastSuiteList(constructor.newInstance(RecipeType.CRAFTING, recipes), first, all);
            } catch (ReflectiveOperationException e) {
                throw new GameTestAssertException("FastSuite list unavailable: " + e);
            }
        }

        List<String> answers(Level level) {
            final List<String> out = new ArrayList<>();
            final Item[] inputs = {Items.STONE, Items.DIRT, Items.SAND};
            for (Item input : inputs) {
                final Optional<RecipeHolder<?>> found = first(input, level);
                out.add("first " + input + "=" + found.map(h -> h.id().toString()).orElse("none"));
            }
            for (Item input : inputs) {
                out.add("all " + input + "=" + all(input, level).stream().map(h -> h.id().toString()).toList());
            }
            return out;
        }

        @SuppressWarnings("unchecked")
        Optional<RecipeHolder<?>> first(Item input, Level level) {
            try {
                return (Optional<RecipeHolder<?>>) getRecipeFor.invoke(list, input(input), level);
            } catch (ReflectiveOperationException e) {
                throw new GameTestAssertException("getRecipeFor failed: " + e);
            }
        }

        @SuppressWarnings("unchecked")
        List<RecipeHolder<?>> all(Item input, Level level) {
            try {
                return (List<RecipeHolder<?>>) getRecipesFor.invoke(list, input(input), level);
            } catch (ReflectiveOperationException e) {
                throw new GameTestAssertException("getRecipesFor failed: " + e);
            }
        }

        private static CraftingInput input(Item item) {
            return CraftingInput.of(1, 1, List.of(new ItemStack(item)));
        }
    }

    // ---------------------------------------------------------------- Simply Swords

    private static final ResourceKey<LootTable> DUNGEON = ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.withDefaultNamespace("chests/simple_dungeon"));
    private static final ResourceKey<LootTable> TEMPLE = ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.withDefaultNamespace("chests/desert_pyramid"));

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void pityStackAnswersTheSameInEveryOrder(GameTestHelper helper) {
        final List<String> expected = List.of(
                "null",                                   // Nothing captured.
                "null",                                   // A plain read of a chest slot.
                "null", DUNGEON.location().toString(), "null",     // A fill: the nested read must not take the table.
                "null", TEMPLE.location().toString(), DUNGEON.location().toString(), "null");
        final ModConfigSpec.BooleanValue flag = ArcadiaConfig.SPARE_WORK.simplySwordsReusePityStack;
        helper.assertValueEqual(with(flag, false, () -> {
            clearPityStack();
            return pitySequence();
        }), expected, "pity stack answers, patch off");
        helper.assertValueEqual(with(flag, true, () -> {
            clearPityStack();
            return pitySequence();
        }), expected, "pity stack answers, patch on");

        // Any other thread keeps its own plain thread local, patch on.
        final AtomicReference<List<String>> otherThread = new AtomicReference<>();
        final Thread thread = new Thread(() -> otherThread.set(pitySequence()));
        with(flag, true, () -> {
            PityLootContext.capture(DUNGEON);
            thread.start();
            try {
                thread.join();
            } catch (InterruptedException e) {
                throw new GameTestAssertException("Interrupted");
            }
            return null;
        });
        helper.assertValueEqual(otherThread.get(), expected, "pity stack answers on another thread");
        helper.assertValueEqual(with(flag, true, () -> String.valueOf(PityLootContext.take())), String.valueOf(DUNGEON), "own stack after the other thread ran");
        helper.assertValueEqual(with(flag, true, () -> String.valueOf(PityLootContext.take())), "null", "own stack is empty again");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void pityStackIsKeptOnlyWithThePatchOn(GameTestHelper helper) {
        final ThreadLocal<?> local = pityLocal();
        helper.assertTrue(local instanceof ArcadiaReusableDequeLocal, "PityLootContext should hold the reusable thread local");
        final ModConfigSpec.BooleanValue flag = ArcadiaConfig.SPARE_WORK.simplySwordsReusePityStack;
        helper.assertTrue(with(flag, true, () -> sameDequeAfterARead(local)), "The deque should stay with the patch on");
        helper.assertTrue(!with(flag, false, () -> sameDequeAfterARead(local)), "The deque should be recreated with the patch off");
        helper.succeed();
    }

    /**
     * A table captured and never taken stays at the bottom of the stack: Lootr cancels the unpack of a chest it
     * is about to convert after Simply Swords captured its table, so the stack of a real server is rarely empty.
     * The patch must answer the same on such a stack, and the deque is then kept with or without it.
     */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void pityStackWithALeftoverTableAnswersTheSame(GameTestHelper helper) {
        final ModConfigSpec.BooleanValue flag = ArcadiaConfig.SPARE_WORK.simplySwordsReusePityStack;
        final ThreadLocal<?> local = pityLocal();
        for (boolean patched : new boolean[]{false, true}) {
            final List<String> answers = with(flag, patched, () -> {
                clearPityStack();
                PityLootContext.capture(DUNGEON);
                final Object deque = local.get();
                PityLootContext.capture(null);
                final List<String> out = new ArrayList<>();
                out.add(String.valueOf(tableOf(PityLootContext.take())));
                PityLootContext.capture(TEMPLE);
                out.add(String.valueOf(tableOf(PityLootContext.take())));
                out.add(String.valueOf(deque == local.get()));
                out.add(String.valueOf(tableOf(PityLootContext.take())));
                clearPityStack();
                return out;
            });
            helper.assertValueEqual(answers, List.of("null", TEMPLE.location().toString(), "true", DUNGEON.location().toString()), "stack with a leftover table, patch " + patched);
        }
        helper.succeed();
    }

    /** From the state of a fresh server, whatever an earlier test or a real chest left. */
    private static void clearPityStack() {
        final Object deque = pityLocal().get();
        if (deque instanceof java.util.Deque<?> stack) {
            stack.clear();
        }
    }

    private static boolean sameDequeAfterARead(ThreadLocal<?> local) {
        clearPityStack();
        final Object before = local.get();
        PityLootContext.capture(null);
        PityLootContext.take();
        final boolean same = before == local.get();
        local.remove();
        return same;
    }

    private static ThreadLocal<?> pityLocal() {
        try {
            final Field field = PityLootContext.class.getDeclaredField("CAPTURED_TABLES");
            field.setAccessible(true);
            return (ThreadLocal<?>) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new GameTestAssertException("PityLootContext field unavailable: " + e);
        }
    }

    private static List<String> pitySequence() {
        final List<String> out = new ArrayList<>();
        out.add(String.valueOf(tableOf(PityLootContext.take())));
        PityLootContext.capture(null);
        out.add(String.valueOf(tableOf(PityLootContext.take())));
        PityLootContext.capture(DUNGEON);
        PityLootContext.capture(null);
        out.add(String.valueOf(tableOf(PityLootContext.take())));
        out.add(String.valueOf(tableOf(PityLootContext.take())));
        out.add(String.valueOf(tableOf(PityLootContext.take())));
        PityLootContext.capture(DUNGEON);
        PityLootContext.capture(TEMPLE);
        PityLootContext.capture(null);
        out.add(String.valueOf(tableOf(PityLootContext.take())));
        out.add(String.valueOf(tableOf(PityLootContext.take())));
        out.add(String.valueOf(tableOf(PityLootContext.take())));
        out.add(String.valueOf(tableOf(PityLootContext.take())));
        return out;
    }

    private static ResourceLocation tableOf(ResourceKey<LootTable> key) {
        return key == null ? null : key.location();
    }

    /**
     * A real fill: vanilla's own {@code setItem} during the fill reads the container again, so the stack is
     * nested. The pity roll only happens when the outer read gets its table back, so the player's pity state
     * must change exactly as it does without the patch. Lootr leaves a dropper alone, so this runs in the pack
     * too.
     */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void containerLootAndPityStateAreTheSame(GameTestHelper helper) {
        final BlockPos relative = new BlockPos(0, 1, 0);
        helper.setBlock(relative, Blocks.DROPPER);
        final RandomizableContainerBlockEntity dropper = helper.getBlockEntity(relative);
        final ServerPlayer player = testPlayer(helper.getLevel(), helper.absolutePos(relative).above());
        final ModConfigSpec.BooleanValue flag = ArcadiaConfig.SPARE_WORK.simplySwordsReusePityStack;
        final String freshState = new PlayerPityState().writeNbt().toString();
        boolean pityRolled = false;
        boolean lootGenerated = false;
        try {
            for (long seed = 1; seed <= 64; seed++) {
                final long fixedSeed = seed;
                final List<String> off = with(flag, false, () -> unpack(dropper, player, fixedSeed));
                final List<String> on = with(flag, true, () -> unpack(dropper, player, fixedSeed));
                assertSameLoot(helper, on, off, seed);
                pityRolled |= !on.getFirst().equals(freshState);
                lootGenerated |= on.size() > 1;
            }
        } finally {
            dropper.clearContent();
            helper.setBlock(relative, Blocks.AIR);
            removePlayer(player);
        }
        helper.assertTrue(lootGenerated, "The loot table should have generated items");
        if (PityLootManager.isEligibleChestTable(DUNGEON.location()) && PityLootManager.getConfiguredUniqueChance(DUNGEON.location()) > 0.0F) {
            helper.assertTrue(pityRolled, "The pity roll should have run: the outer read lost its table");
        }
        helper.succeed();
    }

    /** A real chest: same answers with the patch, whether it fills or Lootr takes it over. */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void chestLootAndPityStateAreTheSame(GameTestHelper helper) {
        final BlockPos relative = new BlockPos(0, 1, 0);
        final ServerPlayer player = testPlayer(helper.getLevel(), helper.absolutePos(relative).above());
        final ModConfigSpec.BooleanValue flag = ArcadiaConfig.SPARE_WORK.simplySwordsReusePityStack;
        try {
            for (long seed = 1; seed <= 8; seed++) {
                final long fixedSeed = seed;
                helper.setBlock(relative, Blocks.CHEST);
                final ChestBlockEntity offChest = helper.getBlockEntity(relative);
                final List<String> off = with(flag, false, () -> unpack(offChest, player, fixedSeed));
                offChest.clearContent();
                helper.setBlock(relative, Blocks.AIR);
                helper.setBlock(relative, Blocks.CHEST);
                final ChestBlockEntity onChest = helper.getBlockEntity(relative);
                final List<String> on = with(flag, true, () -> unpack(onChest, player, fixedSeed));
                onChest.clearContent();
                helper.setBlock(relative, Blocks.AIR);
                assertSameLoot(helper, on, off, seed);
            }
        } finally {
            helper.setBlock(relative, Blocks.AIR);
            removePlayer(player);
            with(flag, false, () -> {
                clearPityStack();
                return null;
            });
            with(flag, true, () -> {
                clearPityStack();
                return null;
            });
        }
        helper.succeed();
    }

    /**
     * The pity state first: it is what the patch could lose. In the pack, loot modifiers of other mods add random
     * items whatever the seed, so the items themselves are only compared on the dev runs.
     */
    private static void assertSameLoot(GameTestHelper helper, List<String> on, List<String> off, long seed) {
        helper.assertValueEqual(on.getFirst(), off.getFirst(), "pity state, seed " + seed);
        if (!Boolean.getBoolean(SpareWorkPackRun.PROPERTY)) {
            helper.assertValueEqual(on, off, "container contents, seed " + seed);
        }
    }

    private static List<String> unpack(RandomizableContainerBlockEntity chest, ServerPlayer player, long seed) {
        ((PityStateHolder) (Object) player).simplyswords$setPityState(new PlayerPityState());
        player.getRandom().setSeed(seed);
        chest.setLootTable(null);
        chest.clearContent();
        chest.setLootTable(DUNGEON, seed);
        chest.unpackLootTable(player);
        final List<String> out = new ArrayList<>();
        out.add(((PityStateHolder) (Object) player).simplyswords$getPityState().writeNbt().toString());
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            final ItemStack stack = chest.getItem(slot);
            if (!stack.isEmpty()) {
                out.add(slot + ":" + describe(stack));
            }
        }
        return out;
    }

    private static String describe(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()) + "x" + stack.getCount() + stack.getComponentsPatch();
    }

    // ---------------------------------------------------------------- Structure lookups

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void structureLookupsAnswerTheSameOnALoadedChunk(GameTestHelper helper) {
        final ServerLevel level = helper.getLevel();
        final ModConfigSpec.BooleanValue flag = ArcadiaConfig.SPARE_WORK.structureLookupUsesLoadedChunk;
        try (StructureScene scene = new StructureScene(level)) {
            final long beforeOff = SpareWorkCounters.structureLookupsOnLoadedChunk;
            final Map<String, String> off = with(flag, false, scene::lookups);
            helper.assertValueEqual(SpareWorkCounters.structureLookupsOnLoadedChunk, beforeOff, "shortcuts with the patch off");

            final long beforeOn = SpareWorkCounters.structureLookupsOnLoadedChunk;
            final Map<String, String> on = with(flag, true, scene::lookups);
            helper.assertTrue(SpareWorkCounters.structureLookupsOnLoadedChunk > beforeOn, "The lookups should use the loaded chunk with the patch on");

            helper.assertValueEqual(on, off, "structure lookups on a loaded chunk");
            helper.assertValueEqual(off.get("starts by predicate"), "[real]", "the original finds the start");
            helper.assertValueEqual(off.get("starts by section"), "[real]", "the original finds the start");
            helper.assertValueEqual(off.get("fill"), "[real]", "the original fills the start");
            helper.assertValueEqual(off.get("any"), "true", "the original sees a reference");
            helper.assertValueEqual(off.get("at"), "real", "the original finds the structure at the position");
            helper.assertValueEqual(off.get("with piece"), "real", "the original finds the piece at the position");
            helper.assertValueEqual(off.get("other chunk any"), "false", "the chunk next to it has no reference");
            helper.assertValueEqual(off.get("other structure"), "[]", "another structure has no start");
        }
        helper.succeed();
    }

    /**
     * A chunk that is not fully loaded must still go through the original call: same answer, and it stays
     * below FULL, instead of being loaded or reported as missing.
     */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 200)
    public static void structureLookupOnAChunkNotLoadedGoesThroughTheOriginal(GameTestHelper helper) {
        final ServerLevel level = helper.getLevel();
        final ModConfigSpec.BooleanValue flag = ArcadiaConfig.SPARE_WORK.structureLookupUsesLoadedChunk;
        final ChunkPos far = new ChunkPos(nextSceneChunkX += 64, -4000);
        helper.assertTrue(level.getChunkSource().getChunkNow(far.x, far.z) == null, "The far chunk should not be loaded yet");
        final StructureManager manager = level.structureManager();
        final BlockPos pos = far.getWorldPosition();
        final String off = with(flag, false, () -> manager.hasAnyStructureAt(pos) + " " + referencesOf(manager.getAllStructuresAt(pos)));
        final String on = with(flag, true, () -> manager.hasAnyStructureAt(pos) + " " + referencesOf(manager.getAllStructuresAt(pos)));
        helper.assertValueEqual(on, off, "structure lookups on a chunk not loaded");
        helper.assertTrue(level.getChunkSource().getChunkNow(far.x, far.z) == null, "The lookup must not have loaded the chunk fully");
        helper.succeed();
    }

    /** From another thread the original hands the call to the server thread: {@code getChunkNow} must not shortcut it. */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 200)
    public static void structureLookupFromAnotherThreadAnswersTheSame(GameTestHelper helper) {
        final ServerLevel level = helper.getLevel();
        helper.assertTrue(SpareWorkFlags.structureLookupUsesLoadedChunk(), "The patch should be on");
        final StructureScene scene = new StructureScene(level);
        level.setChunkForced(scene.chunkA.getPos().x, scene.chunkA.getPos().z, true);
        final StructureManager manager = level.structureManager();
        final BlockPos pos = scene.inside;
        final String expected = referencesOf(manager.getAllStructuresAt(pos));
        final CompletableFuture<String> answer = CompletableFuture.supplyAsync(() -> referencesOf(manager.getAllStructuresAt(pos)));
        helper.succeedWhen(() -> {
            helper.assertTrue(answer.isDone(), "Waiting for the other thread");
            helper.assertValueEqual(answer.join(), expected, "structure lookup from another thread");
            level.setChunkForced(scene.chunkA.getPos().x, scene.chunkA.getPos().z, false);
            scene.close();
        });
    }

    private static String referencesOf(Map<Structure, LongSet> references) {
        final TreeMap<String, List<Long>> sorted = new TreeMap<>();
        references.forEach((structure, chunks) -> sorted.put(structure.getClass().getName() + "#" + System.identityHashCode(structure),
                chunks.longStream().sorted().boxed().toList()));
        return sorted.toString();
    }

    /** A structure start in one loaded chunk and a reference to it in the chunk next to it. Restored on close. */
    static final class StructureScene implements AutoCloseable {
        final ServerLevel level;
        final LevelChunk chunkA;
        final LevelChunk chunkB;
        final LevelChunk chunkC;
        final Structure structure;
        final Structure outpost;
        final StructureStart start;
        final BlockPos inside;
        private final Map<Structure, LongSet> savedReferences = new HashMap<>();
        private final Map<Structure, StructureStart> savedStarts;

        StructureScene(ServerLevel level) {
            this(level, level.registryAccess().registryOrThrow(Registries.STRUCTURE).getOrThrow(BuiltinStructures.VILLAGE_PLAINS));
        }

        StructureScene(ServerLevel level, Structure structure) {
            this.level = level;
            final int x = nextSceneChunkX += 8;
            this.chunkA = level.getChunk(x, 4000);
            this.chunkB = level.getChunk(x + 1, 4000);
            this.chunkC = level.getChunk(x + 3, 4000);
            this.structure = structure;
            this.outpost = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getOrThrow(BuiltinStructures.PILLAGER_OUTPOST);
            this.inside = chunkA.getPos().getWorldPosition().offset(4, 80, 4);
            final BoundingBox box = new BoundingBox(inside.getX() - 2, inside.getY() - 2, inside.getZ() - 2, inside.getX() + 2, inside.getY() + 2, inside.getZ() + 2);
            this.start = new StructureStart(structure, chunkB.getPos(), 0, new PiecesContainer(List.of(new TestPiece(box))));
            chunkA.getAllReferences().forEach((key, chunks) -> savedReferences.put(key, new LongOpenHashSet(chunks)));
            this.savedStarts = new HashMap<>(chunkB.getAllStarts());
            chunkB.setStartForStructure(structure, start);
            chunkA.addReferenceForStructure(structure, chunkB.getPos().toLong());
        }

        Map<String, String> lookups() {
            final StructureManager manager = level.structureManager();
            final Map<String, String> out = new TreeMap<>();
            out.put("starts by predicate", describeAll(manager.startsForStructure(chunkA.getPos(), candidate -> candidate == structure)));
            out.put("starts by predicate none", describeAll(manager.startsForStructure(chunkA.getPos(), candidate -> false)));
            out.put("starts by section", describeAll(manager.startsForStructure(SectionPos.of(inside), structure)));
            out.put("other structure", describeAll(manager.startsForStructure(SectionPos.of(inside), outpost)));
            final List<StructureStart> filled = new ArrayList<>();
            manager.fillStartsForStructure(structure, LongSet.of(chunkB.getPos().toLong()), filled::add);
            out.put("fill", describeAll(filled));
            final List<StructureStart> notThere = new ArrayList<>();
            manager.fillStartsForStructure(structure, LongSet.of(chunkC.getPos().toLong()), notThere::add);
            out.put("fill without a start", describeAll(notThere));
            out.put("any", String.valueOf(manager.hasAnyStructureAt(inside)));
            out.put("other chunk any", String.valueOf(manager.hasAnyStructureAt(chunkC.getPos().getWorldPosition())));
            out.put("all", referencesOf(manager.getAllStructuresAt(inside)));
            out.put("other chunk all", referencesOf(manager.getAllStructuresAt(chunkC.getPos().getWorldPosition())));
            out.put("at", describe(manager.getStructureAt(inside, structure)));
            out.put("at outside", describe(manager.getStructureAt(inside.offset(0, 40, 0), structure)));
            out.put("with piece", describe(manager.getStructureWithPieceAt(inside, structure)));
            out.put("with piece outside", describe(manager.getStructureWithPieceAt(inside.offset(0, 40, 0), structure)));
            return out;
        }

        private String describeAll(List<StructureStart> starts) {
            return starts.stream().map(this::describe).toList().toString();
        }

        private String describe(StructureStart found) {
            if (found == start) {
                return "real";
            }
            return found == StructureStart.INVALID_START ? "invalid" : "other " + found.getChunkPos();
        }

        @Override
        public void close() {
            chunkA.setAllReferences(savedReferences);
            chunkB.setAllStarts(savedStarts);
        }
    }

    private static final class TestPiece extends StructurePiece {
        TestPiece(BoundingBox box) {
            super(StructurePieceType.BURIED_TREASURE_PIECE, 0, box);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager manager, ChunkGenerator generator, RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos pivot) {
        }
    }

    // ---------------------------------------------------------------- Crabber's Delight

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 200)
    public static void tackleBoxSearchIsSkippedWhenItCanOnlyFindNothing(GameTestHelper helper) {
        try (TackleBoxScene scene = new TackleBoxScene(helper)) {
            final BlockPos center = scene.origin.offset(8, 100, 8);
            scene.expect("nothing around", center, "none", true);

            final BlockPos chest = scene.place(center.offset(1, 0, 0), Blocks.CHEST);
            scene.expect("a chest in the cube", center, "none", true);
            helper.assertTrue(scene.level.getBlockEntity(chest) instanceof ChestBlockEntity, "The chest keeps its block entity");

            scene.expect("above the world", scene.origin.offset(8, 400, 8), "none", true);
            scene.expect("below the world", scene.origin.offset(8, -100, 8), "none", true);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 200)
    public static void tackleBoxSearchFindsTheSameBox(GameTestHelper helper) {
        try (TackleBoxScene scene = new TackleBoxScene(helper)) {
            final BlockPos center = scene.origin.offset(8, 100, 8);
            scene.box(center.offset(3, 3, 3), Items.APPLE);
            scene.expect("a box at the corner of the cube", center, item(Items.APPLE), false);
            scene.clear();

            // Same chunk as the player: the shortcut leaves it to the original, which does not reach it.
            scene.box(center.offset(4, 0, 0), Items.APPLE);
            scene.expect("a box just outside the cube", center, "none", false);
            scene.clear();

            // Next chunk, which the cube does not touch.
            final BlockPos edge = scene.origin.offset(12, 100, 8);
            scene.box(edge.offset(4, 0, 0), Items.APPLE);
            scene.expect("a box in a chunk the cube does not touch", edge, "none", true);
            scene.clear();

            scene.box(center.offset(2, 0, 0), Items.APPLE);
            scene.box(center.offset(1, 1, 0), Items.BREAD);
            scene.box(center.offset(-3, -3, -3), Items.COAL);
            scene.expect("the closest of three boxes", center, item(Items.BREAD), false);
            scene.clear();

            final BlockPos corner = scene.origin.offset(15, 100, 15);
            scene.box(corner.offset(3, 0, 3), Items.APPLE);
            scene.expect("a box across a chunk corner", corner, item(Items.APPLE), false);
            scene.clear();

            final BlockPos top = new BlockPos(center.getX(), scene.level.getMaxBuildHeight() - 2, center.getZ());
            scene.expect("the top of the world, nothing", top, "none", true);
            scene.box(top.above(), Items.APPLE);
            scene.expect("the top of the world, a box", top, item(Items.APPLE), false);
            scene.clear();

            final BlockPos bottom = new BlockPos(center.getX(), scene.level.getMinBuildHeight() + 1, center.getZ());
            scene.box(bottom.below(), Items.APPLE);
            scene.expect("the bottom of the world, a box", bottom, item(Items.APPLE), false);
        }
        helper.succeed();
    }

    /** Reads of the original can change the chunk: the shortcut must leave those to it. */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 200)
    public static void tackleBoxSearchKeepsWhatTheOriginalChanges(GameTestHelper helper) {
        try (TackleBoxScene scene = new TackleBoxScene(helper)) {
            final BlockPos center = scene.origin.offset(8, 100, 8);
            final BlockPos pos = scene.place(center.offset(1, 0, 0), Blocks.CHEST);
            final LevelChunk chunk = scene.level.getChunkAt(pos);

            for (boolean patched : new boolean[]{false, true}) {
                // A block that should have a block entity and has none: the read creates it.
                chunk.removeBlockEntity(pos);
                helper.assertTrue(chunk.getBlockEntities().get(pos) == null, "The block entity should be missing");
                scene.search(center, patched, "none", false);
                helper.assertTrue(chunk.getBlockEntities().get(pos) instanceof ChestBlockEntity, "The read should create the missing block entity, patch " + patched);

                // A removed block entity: the read drops it and creates a new one.
                final BlockEntity removed = chunk.getBlockEntities().get(pos);
                removed.setRemoved();
                scene.search(center, patched, "none", false);
                final BlockEntity replaced = chunk.getBlockEntities().get(pos);
                helper.assertTrue(replaced != removed && replaced != null && !replaced.isRemoved(), "The read should replace the removed block entity, patch " + patched);

                // A block entity still waiting in the chunk: the read creates it.
                chunk.removeBlockEntity(pos);
                final CompoundTag tag = new CompoundTag();
                tag.putInt("x", pos.getX());
                tag.putInt("y", pos.getY());
                tag.putInt("z", pos.getZ());
                tag.putString("id", "DUMMY");
                chunk.setBlockEntityNbt(tag);
                scene.search(center, patched, "none", false);
                helper.assertTrue(chunk.getBlockEntities().get(pos) instanceof ChestBlockEntity, "The read should promote the pending block entity, patch " + patched);
                helper.assertTrue(chunk.getBlockEntityNbt(pos) == null, "The pending block entity should be gone, patch " + patched);
            }

            // Outside the cube, in a chunk that is read: a pending block entity stays pending in both cases.
            final BlockPos outside = scene.place(center.offset(0, 60, 0), Blocks.CHEST);
            for (boolean patched : new boolean[]{false, true}) {
                chunk.removeBlockEntity(outside);
                scene.search(center, patched, "none", patched);
                helper.assertTrue(chunk.getBlockEntities().get(outside) == null, "A block outside the cube should stay without its block entity, patch " + patched);
            }
        }
        helper.succeed();
    }

    /** The original loads a chunk it reads: with the patch, the same chunk must end up fully loaded. */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 200)
    public static void tackleBoxSearchStillLoadsANeighbourChunk(GameTestHelper helper) {
        final ServerLevel level = helper.getLevel();
        final int chunkX = nextSceneChunkX += 64;
        final LevelChunk chunk = level.getChunk(chunkX, 8000);
        final ChunkPos neighbour = new ChunkPos(chunkX + 1, 8000);
        helper.assertTrue(level.getChunkSource().getChunkNow(neighbour.x, neighbour.z) == null, "The chunk next to it should not be loaded yet");
        final ServerPlayer player = testPlayer(level, chunk.getPos().getWorldPosition().offset(15, 100, 8));
        try {
            final long before = SpareWorkCounters.tackleBoxSearchesSkipped;
            final Object found = with(ArcadiaConfig.SPARE_WORK.crabbersDelightSkipEmptyTackleBoxSearch, true, () -> TackleBoxProximity.find(player));
            helper.assertTrue(found == null, "Nothing to find");
            helper.assertValueEqual(SpareWorkCounters.tackleBoxSearchesSkipped, before, "searches skipped next to a chunk not loaded");
            helper.assertTrue(level.getChunkSource().getChunkNow(neighbour.x, neighbour.z) != null, "The search should load the chunk it reads");
        } finally {
            removePlayer(player);
        }
        helper.succeed();
    }

    /** More block entities than the shortcut walks: the original answers. */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 200)
    public static void tackleBoxSearchLeavesBusyChunksToTheOriginal(GameTestHelper helper) {
        try (TackleBoxScene scene = new TackleBoxScene(helper)) {
            final BlockPos center = scene.origin.offset(8, 100, 8);
            for (int y = 0; y < 2; y++) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        scene.placeQuietly(scene.origin.offset(x, 140 + y * 2, z), Blocks.CHEST);
                    }
                }
            }
            scene.expect("a chunk full of block entities", center, "none", false);
        }
        helper.succeed();
    }

    /** Random boxes and chests around a player crossing chunk borders: same box found, every time. */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 400)
    public static void tackleBoxSearchMatchesTheOriginalOnRandomWorlds(GameTestHelper helper) {
        final Random random = new Random(20261106L);
        final Item[] contents = {Items.APPLE, Items.BREAD, Items.COAL, Items.DIRT};
        int skipped = 0;
        int searched = 0;
        int found = 0;
        try (TackleBoxScene scene = new TackleBoxScene(helper)) {
            for (int round = 0; round < 300; round++) {
                final BlockPos center = scene.origin.offset(random.nextInt(16), 100 + random.nextInt(32), random.nextInt(16));
                final int boxes = random.nextInt(3);
                for (int i = 0; i < boxes; i++) {
                    scene.box(center.offset(random.nextInt(11) - 5, random.nextInt(11) - 5, random.nextInt(11) - 5), contents[i]);
                }
                final int chests = random.nextInt(4);
                for (int i = 0; i < chests; i++) {
                    scene.place(center.offset(random.nextInt(11) - 5, random.nextInt(11) - 5, random.nextInt(11) - 5), Blocks.CHEST);
                }
                final String off = scene.run(center, false).found();
                final TackleBoxOutcome on = scene.run(center, true);
                helper.assertValueEqual(on.found(), off, "tackle box found, round " + round);
                if (on.skipped()) {
                    skipped++;
                    helper.assertValueEqual(on.found(), "none", "a skipped search finds nothing, round " + round);
                } else {
                    searched++;
                }
                if (!off.equals("none")) {
                    found++;
                }
                scene.clear();
            }
        }
        helper.assertTrue(skipped > 0, "Some searches should have been skipped");
        helper.assertTrue(searched > 0, "Some searches should have been left to the original");
        helper.assertTrue(found > 0, "Some searches should have found a box");
        helper.succeed();
    }

    private static String item(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    private record TackleBoxOutcome(String found, boolean skipped) {}

    /** Chunks of its own around a test player. Whatever is placed is removed again. */
    static final class TackleBoxScene implements AutoCloseable {
        final GameTestHelper helper;
        final ServerLevel level;
        final BlockPos origin;
        final ServerPlayer player;
        private final List<BlockPos> placed = new ArrayList<>();

        TackleBoxScene(GameTestHelper helper) {
            this.helper = helper;
            this.level = helper.getLevel();
            final int chunkX = nextSceneChunkX += 8;
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    level.getChunk(chunkX + x, 4000 + z);
                }
            }
            this.origin = new ChunkPos(chunkX, 4000).getWorldPosition();
            this.player = testPlayer(level, origin);
        }

        BlockPos place(BlockPos pos, Block block) {
            level.setBlockAndUpdate(pos, block.defaultBlockState());
            placed.add(pos);
            return pos;
        }

        /** Without updates, for a lot of blocks. */
        void placeQuietly(BlockPos pos, Block block) {
            level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_NONE);
            placed.add(pos);
        }

        void box(BlockPos pos, Item content) {
            place(pos, Objects.requireNonNull(BuiltInRegistries.BLOCK.get(ResourceLocation.parse("crabbersdelight:tackle_box"))));
            if (level.getBlockEntity(pos) instanceof TackleBoxBlockEntity box) {
                box.handler.setStackInSlot(0, new ItemStack(content));
            } else {
                throw new GameTestAssertException("A tackle box should have been placed at " + pos);
            }
        }

        TackleBoxOutcome run(BlockPos center, boolean patched) {
            player.setPos(center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
            return with(ArcadiaConfig.SPARE_WORK.crabbersDelightSkipEmptyTackleBoxSearch, patched, () -> {
                final long before = SpareWorkCounters.tackleBoxSearchesSkipped;
                final TackleBoxProximity.TackleBoxAccess access = TackleBoxProximity.find(player);
                final String found = access == null ? "none" : item(access.getSlot(0).getItem());
                return new TackleBoxOutcome(found, SpareWorkCounters.tackleBoxSearchesSkipped != before);
            });
        }

        void search(BlockPos center, boolean patched, String expectedFound, boolean expectedSkipped) {
            final TackleBoxOutcome outcome = run(center, patched);
            helper.assertValueEqual(outcome.found(), expectedFound, "tackle box found, patch " + patched);
            helper.assertValueEqual(outcome.skipped(), expectedSkipped, "search skipped, patch " + patched);
        }

        /** Off then on, from the same state: same box, and the shortcut only with the patch on. */
        void expect(String name, BlockPos center, String expectedFound, boolean expectedSkipped) {
            final TackleBoxOutcome off = run(center, false);
            final TackleBoxOutcome on = run(center, true);
            helper.assertValueEqual(off.found(), expectedFound, name + ", found by the original");
            helper.assertValueEqual(on.found(), expectedFound, name + ", found with the patch");
            helper.assertTrue(!off.skipped(), name + ": the original should never be skipped with the patch off");
            helper.assertValueEqual(on.skipped(), expectedSkipped, name + ", search skipped");
        }

        void clear() {
            for (BlockPos pos : placed) {
                if (level.getBlockEntity(pos) instanceof TackleBoxBlockEntity box) {
                    box.handler.setStackInSlot(0, ItemStack.EMPTY);
                }
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_NONE);
            }
            placed.clear();
        }

        @Override
        public void close() {
            clear();
            removePlayer(player);
        }
    }

    // ---------------------------------------------------------------- Helpers

    static <T> T with(ModConfigSpec.BooleanValue flag, boolean value, Supplier<T> body) {
        final boolean saved = flag.get();
        flag.set(value);
        SpareWorkFlags.refresh();
        try {
            return body.get();
        } finally {
            flag.set(saved);
            SpareWorkFlags.refresh();
        }
    }

    static ServerPlayer testPlayer(ServerLevel level, BlockPos pos) {
        final ServerPlayer player = new ServerPlayer(level.getServer(), level,
                new GameProfile(UUID.randomUUID(), "sparework-test"), ClientInformation.createDefault());
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        return player;
    }

    static void removePlayer(ServerPlayer player) {
        player.getAdvancements().stopListening();
    }
}
