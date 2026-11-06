package com.teamarcadia.arcadiatweaks.neoforge.gametest;

import alabaster.crabbersdelight.common.utils.TackleBoxProximity;
import com.teamarcadia.arcadiatweaks.ArcadiaTweaks;
import com.teamarcadia.arcadiatweaks.common.config.ArcadiaConfig;
import com.teamarcadia.arcadiatweaks.neoforge.sparework.SpareWorkCounters;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * The spare work patches against the real mods of the pack, where they are installed: every recipe type the
 * pack registers, and Twilight Forest's spawn lists. A mod that is missing makes its test pass with nothing to
 * compare, so the dev runs only exercise the harness; {@code -Darcadia.packTests=true} (see
 * {@link SpareWorkPackRun}) runs them on a copy of the server pack and also logs what each patch saves.
 *
 * @author vyrriox
 */
@GameTestHolder(ArcadiaTweaks.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SpareWorkPackGameTests {

    private static final String TEMPLATE = "empty";
    private static final String BATCH = ArcadiaTweaks.MOD_ID;
    private static final int RECIPE_SAMPLES = 8;

    private SpareWorkPackGameTests() {}

    // ---------------------------------------------------------------- Every recipe type of the pack

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 400)
    public static void everyRecipeTypeAnswersTheSame(GameTestHelper helper) {
        final ServerLevel level = helper.getLevel();
        final RecipeManager manager = level.getRecipeManager();
        final ModConfigSpec.BooleanValue flag = ArcadiaConfig.SPARE_WORK.fastSuiteSkipEmptyParallelLookup;
        final TreeMap<String, Long> skippedByType = new TreeMap<>();
        int types = 0;
        int answers = 0;
        for (RecipeType<?> type : BuiltInRegistries.RECIPE_TYPE) {
            final List<RecipeInput> inputs = inputsFor(type, manager);
            final List<String> off = SpareWorkGameTests.with(flag, false, () -> answers(manager, type, inputs, helper));
            final long before = SpareWorkCounters.fastSuiteLookupsSkipped;
            final List<String> on = SpareWorkGameTests.with(flag, true, () -> answers(manager, type, inputs, helper));
            final long skipped = SpareWorkCounters.fastSuiteLookupsSkipped - before;
            helper.assertValueEqual(on, off, "recipe answers of " + BuiltInRegistries.RECIPE_TYPE.getKey(type));
            types++;
            answers += on.size();
            if (skipped > 0) {
                skippedByType.put(String.valueOf(BuiltInRegistries.RECIPE_TYPE.getKey(type)), skipped);
            }
        }
        ArcadiaTweaks.LOGGER.info("[sparework-pack] recipes: {} types, {} answers compared, skipped lookups by type: {}", types, answers, skippedByType);
        if (ModList.get().isLoaded("create")) {
            helper.assertTrue(!skippedByType.isEmpty(), "Create's recipe types should have used the shortcut");
        }
        helper.succeed();
    }

    private static List<String> answers(RecipeManager manager, RecipeType<?> type, List<RecipeInput> inputs, GameTestHelper helper) {
        final List<String> out = new ArrayList<>();
        for (RecipeInput input : inputs) {
            out.add("first " + lookup(() -> first(manager, type, input, helper)));
            out.add("all " + lookup(() -> all(manager, type, input, helper)));
        }
        return out;
    }

    /** A recipe type can refuse an input of the wrong kind: the failure must be the same too. */
    private static String lookup(java.util.function.Supplier<String> call) {
        try {
            return call.get();
        } catch (RuntimeException | LinkageError failure) {
            Throwable root = failure;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            return "throws " + root.getClass().getName();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String first(RecipeManager manager, RecipeType<?> type, RecipeInput input, GameTestHelper helper) {
        final java.util.Optional<RecipeHolder<?>> found = manager.getRecipeFor((RecipeType) type, input, helper.getLevel());
        return found.map(holder -> holder.id().toString()).orElse("none");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String all(RecipeManager manager, RecipeType<?> type, RecipeInput input, GameTestHelper helper) {
        final List<RecipeHolder<?>> found = manager.getRecipesFor((RecipeType) type, input, helper.getLevel());
        return found.stream().map(holder -> holder.id().toString()).toList().toString();
    }

    /** Something no recipe takes, and the first ingredient of a few recipes of the type. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<RecipeInput> inputsFor(RecipeType<?> type, RecipeManager manager) {
        final List<RecipeInput> inputs = new ArrayList<>();
        inputs.add(inputOf(type, new ItemStack(Items.BARRIER)));
        final List<RecipeHolder<?>> recipes = new ArrayList<>((java.util.Collection) manager.getAllRecipesFor((RecipeType) type));
        for (int i = 0; i < recipes.size() && inputs.size() <= RECIPE_SAMPLES; i += Math.max(1, recipes.size() / RECIPE_SAMPLES)) {
            final ItemStack sample = firstIngredient(recipes.get(i).value());
            if (!sample.isEmpty()) {
                inputs.add(inputOf(type, sample));
            }
        }
        return inputs;
    }

    private static ItemStack firstIngredient(Recipe<?> recipe) {
        try {
            for (Ingredient ingredient : recipe.getIngredients()) {
                final ItemStack[] stacks = ingredient.getItems();
                if (stacks.length > 0) {
                    return stacks[0].copy();
                }
            }
        } catch (RuntimeException unusualRecipe) {
            return ItemStack.EMPTY;
        }
        return ItemStack.EMPTY;
    }

    private static RecipeInput inputOf(RecipeType<?> type, ItemStack stack) {
        if (type == RecipeType.CRAFTING) {
            return CraftingInput.of(1, 1, List.of(stack));
        }
        if (type == RecipeType.SMITHING) {
            return new SmithingRecipeInput(stack, stack, stack);
        }
        return new SingleRecipeInput(stack);
    }

    // ---------------------------------------------------------------- Twilight Forest

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 400)
    public static void twilightForestSpawnListsAnswerTheSame(GameTestHelper helper) {
        if (!ModList.get().isLoaded("twilightforest")) {
            helper.succeed();
            return;
        }
        final ServerLevel level = helper.getLevel();
        final ModConfigSpec.BooleanValue flag = ArcadiaConfig.SPARE_WORK.structureLookupUsesLoadedChunk;
        final Method gather = twilightForestGather();
        final Class<?> landmark = twilightForestLandmark();
        int compared = 0;
        int landmarks = 0;
        for (Structure structure : level.registryAccess().registryOrThrow(Registries.STRUCTURE)) {
            if (!landmark.isInstance(structure)) {
                continue;
            }
            landmarks++;
            try (SpareWorkGameTests.StructureScene scene = new SpareWorkGameTests.StructureScene(level, structure)) {
                final StructureManager manager = level.structureManager();
                final List<BlockPos> positions = List.of(scene.inside, scene.inside.above(40), scene.chunkC.getPos().getWorldPosition());
                for (MobCategory category : MobCategory.values()) {
                    for (BlockPos pos : positions) {
                        final String off = SpareWorkGameTests.with(flag, false, () -> spawnList(gather, manager, category, pos));
                        final long before = SpareWorkCounters.structureLookupsOnLoadedChunk;
                        final String on = SpareWorkGameTests.with(flag, true, () -> spawnList(gather, manager, category, pos));
                        helper.assertTrue(SpareWorkCounters.structureLookupsOnLoadedChunk > before, "The spawn list lookup should use the loaded chunk");
                        helper.assertValueEqual(on, off, "spawn list of " + structure + " for " + category + " at " + pos);
                        compared++;
                    }
                }
            }
        }
        ArcadiaTweaks.LOGGER.info("[sparework-pack] Twilight Forest: {} landmarks, {} spawn lists compared", landmarks, compared);
        helper.assertTrue(landmarks > 0, "Twilight Forest should register landmark structures");
        helper.succeed();
    }

    private static Method twilightForestGather() {
        try {
            return Class.forName("twilightforest.events.EntityEvents")
                    .getMethod("gatherPotentialSpawns", StructureManager.class, MobCategory.class, BlockPos.class);
        } catch (ReflectiveOperationException e) {
            throw new GameTestAssertException("Twilight Forest entry point unavailable: " + e);
        }
    }

    private static Class<?> twilightForestLandmark() {
        try {
            return Class.forName("twilightforest.world.components.structures.util.ControlledSpawns");
        } catch (ReflectiveOperationException e) {
            throw new GameTestAssertException("Twilight Forest landmark type unavailable: " + e);
        }
    }

    @SuppressWarnings("unchecked")
    private static String spawnList(Method gather, StructureManager manager, MobCategory category, BlockPos pos) {
        try {
            final List<MobSpawnSettings.SpawnerData> list = (List<MobSpawnSettings.SpawnerData>) gather.invoke(null, manager, category, pos);
            if (list == null) {
                return "null";
            }
            return list.stream().map(data -> BuiltInRegistries.ENTITY_TYPE.getKey(data.type) + " w" + data.getWeight().asInt() + " " + data.minCount + "-" + data.maxCount).toList().toString();
        } catch (InvocationTargetException e) {
            return "throws " + e.getCause().getClass().getName();
        } catch (IllegalAccessException e) {
            throw new GameTestAssertException("Twilight Forest entry point refused: " + e);
        }
    }

    // ---------------------------------------------------------------- What each patch saves

    /** Only on a copy of the server pack: the numbers mean nothing on the small dev runs. */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 400)
    public static void measureWhatEachPatchSaves(GameTestHelper helper) {
        if (!Boolean.getBoolean(SpareWorkPackRun.PROPERTY)) {
            helper.succeed();
            return;
        }
        final ServerLevel level = helper.getLevel();
        try (SpareWorkGameTests.TackleBoxScene scene = new SpareWorkGameTests.TackleBoxScene(helper)) {
            final BlockPos center = scene.origin.offset(8, 100, 8);
            final Player player = scene.player;
            player.setPos(center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
            final ModConfigSpec.BooleanValue tackleBox = ArcadiaConfig.SPARE_WORK.crabbersDelightSkipEmptyTackleBoxSearch;
            measure("tackle box search, nothing around", 2_000, tackleBox, () -> TackleBoxProximity.find(player));
            scene.place(center.offset(1, 0, 0), Blocks.CHEST);
            scene.place(center.offset(-2, 1, 0), Blocks.CHEST);
            measure("tackle box search, 2 chests around", 2_000, tackleBox, () -> TackleBoxProximity.find(player));
            scene.clear();

            // A real read of a chest slot, with no loot table: what vanilla does on every slot access.
            final ChestBlockEntity chest = (ChestBlockEntity) scene.level.getBlockEntity(scene.place(center, Blocks.CHEST));
            measure("chest slot read", 500_000, ArcadiaConfig.SPARE_WORK.simplySwordsReusePityStack, () -> chest.getItem(0));
            scene.clear();
        }
        for (String name : List.of("create:milling", "create:crushing", "create:splashing", "create:mixing")) {
            final ResourceLocation id = ResourceLocation.parse(name);
            if (BuiltInRegistries.RECIPE_TYPE.containsKey(id)) {
                final RecipeType<?> type = BuiltInRegistries.RECIPE_TYPE.get(id);
                final RecipeManager manager = level.getRecipeManager();
                final RecipeInput input = new SingleRecipeInput(new ItemStack(Items.BARRIER));
                measure("recipe lookup without a match, " + name, 2_000, ArcadiaConfig.SPARE_WORK.fastSuiteSkipEmptyParallelLookup,
                        () -> lookup(() -> first(manager, type, input, helper)));
            }
        }
        helper.succeed();
    }

    /**
     * Spawning walks the chunks around a player once per tick, and Lithium only adds the chunk ticket on the first
     * access of a chunk in a tick, so this one is measured over many ticks, one walk per tick.
     */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 1200)
    public static void measureStructureLookupsOverTicks(GameTestHelper helper) {
        if (!Boolean.getBoolean(SpareWorkPackRun.PROPERTY)) {
            helper.succeed();
            return;
        }
        final ServerLevel level = helper.getLevel();
        final ModConfigSpec.BooleanValue flag = ArcadiaConfig.SPARE_WORK.structureLookupUsesLoadedChunk;
        final StructureManager structures = level.structureManager();
        final List<ChunkPos> chunks = new ArrayList<>();
        final int base = 6000;
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                level.setChunkForced(base + x, base + z, true);
                level.getChunk(base + x, base + z);
                chunks.add(new ChunkPos(base + x, base + z));
            }
        }
        java.util.Collections.shuffle(chunks, new java.util.Random(20261106L));
        final long[] nanos = new long[2];
        final long[] calls = new long[2];
        final int[] ticks = {0};
        helper.onEachTick(() -> {
            final int tick = ticks[0]++;
            final int mode = (tick / 10) % 2;
            final long spent = SpareWorkGameTests.with(flag, mode == 1, () -> {
                final long start = System.nanoTime();
                for (ChunkPos chunk : chunks) {
                    structures.startsForStructure(chunk, structure -> false);
                }
                return System.nanoTime() - start;
            });
            if (tick >= 200) {
                nanos[mode] += spent;
                calls[mode] += chunks.size();
            }
            if (tick == 1000) {
                ArcadiaTweaks.LOGGER.info("[sparework-bench] structure lookup, first access of a chunk in a tick: {} ns without the patch, {} ns with it",
                        nanos[0] / calls[0], nanos[1] / calls[1]);
                for (ChunkPos chunk : chunks) {
                    level.setChunkForced(chunk.x, chunk.z, false);
                }
                helper.succeed();
            }
        });
    }

    private static void measure(String name, int iterations, ModConfigSpec.BooleanValue flag, Runnable body) {
        double off = Double.MAX_VALUE;
        double on = Double.MAX_VALUE;
        for (int round = 0; round < 7; round++) {
            off = Math.min(off, nanosPerCall(iterations, flag, false, body));
            on = Math.min(on, nanosPerCall(iterations, flag, true, body));
        }
        ArcadiaTweaks.LOGGER.info("[sparework-bench] {}: {} ns without the patch, {} ns with it", name, Math.round(off), Math.round(on));
    }

    private static double nanosPerCall(int iterations, ModConfigSpec.BooleanValue flag, boolean value, Runnable body) {
        return SpareWorkGameTests.with(flag, value, () -> {
            final long start = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                body.run();
            }
            return (System.nanoTime() - start) / (double) iterations;
        });
    }
}
