package com.teamarcadia.arcadiatweaks.neoforge.gametest;

import com.refinedmods.refinedstorage.api.autocrafting.Pattern;
import com.refinedmods.refinedstorage.api.autocrafting.PatternBuilder;
import com.refinedmods.refinedstorage.api.autocrafting.PatternRepositoryImpl;
import com.refinedmods.refinedstorage.api.autocrafting.PatternType;
import com.refinedmods.refinedstorage.api.autocrafting.calculation.CancellationToken;
import com.refinedmods.refinedstorage.api.autocrafting.calculation.CraftingCalculator;
import com.refinedmods.refinedstorage.api.autocrafting.calculation.CraftingCalculatorImpl;
import com.refinedmods.refinedstorage.api.autocrafting.craftability.IsCraftableCraftingCalculatorListener;
import com.refinedmods.refinedstorage.api.autocrafting.preview.PreviewCraftingCalculatorListener;
import com.refinedmods.refinedstorage.api.autocrafting.task.TaskPlanCraftingCalculatorListener;
import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.api.storage.Actor;
import com.refinedmods.refinedstorage.api.storage.StorageImpl;
import com.refinedmods.refinedstorage.api.storage.root.RootStorageImpl;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;
import com.teamarcadia.arcadiatweaks.ArcadiaTweaks;
import com.teamarcadia.arcadiatweaks.common.config.ArcadiaConfig;
import com.teamarcadia.arcadiatweaks.common.modules.refinedstorage.RefinedStorageRuntimeFlags;
import com.teamarcadia.arcadiatweaks.neoforge.refinedstorage.ArcadiaLiveNetworkList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

/**
 * In-game checks of the Refined Storage 2 autocrafting patches (A1 and up).
 *
 * <p>The checks are differential: the same random pattern graphs and network contents are calculated
 * with the patch off (plain Refined Storage) and on, and every result must be equal.</p>
 *
 * @author vyrriox
 */
@GameTestHolder(ArcadiaTweaks.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RefinedStorageAutocraftingGameTests {

    private static final String EMPTY = "empty";
    private static final String BATCH = ArcadiaTweaks.MOD_ID + "_rs_autocrafting";
    private static final long[] SEEDS = {1L, 2L, 3L, 42L, 1234L};
    private static final long[] AMOUNTS = {1L, 3L, 64L, 1000L};

    private RefinedStorageAutocraftingGameTests() {}

    /** A random network: a pattern repository and a root storage with a disk holding thousands of resources. */
    private record Network(PatternRepositoryImpl patterns, RootStorageImpl storage, List<ResourceKey> craftables) {}

    private static ResourceKey resource(Item item) {
        return ItemResource.ofItemStack(new ItemStack(item));
    }

    /**
     * About 80 craftable resources, each with one or two patterns of one to three ingredients. An
     * ingredient takes one or two alternative inputs, mostly raw resources or lower craftables (a few
     * point upwards, which makes cycles). Some patterns have a second output or a byproduct, some are
     * external. The disk holds random amounts of the raw resources and of some craftables, plus every
     * other item of the runtime once, so the network is as large as a real one.
     */
    private static Network network(long seed) {
        final Random random = new Random(seed);
        final List<ResourceKey> pool = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item != Items.AIR) pool.add(resource(item));
        }
        final List<ResourceKey> raw = pool.subList(0, 200);
        final List<ResourceKey> craftables = pool.subList(200, 280);
        final PatternRepositoryImpl patterns = new PatternRepositoryImpl();
        for (int c = 0; c < craftables.size(); c++) {
            final int alternatives = 1 + random.nextInt(2);
            for (int a = 0; a < alternatives; a++) {
                final boolean external = random.nextInt(4) == 0;
                final PatternBuilder builder = PatternBuilder.pattern(external ? PatternType.EXTERNAL : PatternType.INTERNAL);
                final int ingredients = 1 + random.nextInt(3);
                for (int i = 0; i < ingredients; i++) {
                    final PatternBuilder.IngredientBuilder ingredient = builder.ingredient(1 + random.nextInt(4));
                    final int inputs = 1 + random.nextInt(2);
                    for (int k = 0; k < inputs; k++) {
                        ingredient.input(pick(random, raw, craftables, c));
                    }
                    ingredient.end();
                }
                builder.output(craftables.get(c), 1 + random.nextInt(3));
                if (random.nextInt(5) == 0) {
                    builder.output(raw.get(random.nextInt(raw.size())), 1);
                }
                if (!external && random.nextInt(5) == 0) {
                    builder.byproduct(raw.get(random.nextInt(raw.size())), 1);
                }
                final Pattern pattern = builder.build();
                patterns.add(pattern, random.nextInt(3));
            }
        }
        final StorageImpl disk = new StorageImpl();
        final RootStorageImpl storage = new RootStorageImpl();
        storage.addSource(disk);
        for (ResourceKey r : raw) {
            final int amount = random.nextInt(6) == 0 ? 0 : random.nextInt(200);
            if (amount > 0) storage.insert(r, amount, Action.EXECUTE, Actor.EMPTY);
        }
        for (ResourceKey r : craftables) {
            if (random.nextInt(4) == 0) storage.insert(r, 1 + random.nextInt(20), Action.EXECUTE, Actor.EMPTY);
        }
        for (ResourceKey r : pool.subList(280, pool.size())) {
            storage.insert(r, 1 + random.nextInt(64), Action.EXECUTE, Actor.EMPTY);
        }
        return new Network(patterns, storage, craftables);
    }

    /** Mostly a raw resource or a lower craftable; one time in twelve a higher craftable (a cycle may form). */
    private static ResourceKey pick(Random random, List<ResourceKey> raw, List<ResourceKey> craftables, int current) {
        if (random.nextInt(12) == 0) {
            return craftables.get(random.nextInt(craftables.size()));
        }
        if (current > 0 && random.nextInt(3) == 0) {
            return craftables.get(random.nextInt(current));
        }
        return raw.get(random.nextInt(raw.size()));
    }

    /** Every calculation Refined Storage offers, for every craftable and amount, compared with equals. */
    private static List<Object> calculateAll(Network network) {
        final CraftingCalculator calculator = new CraftingCalculatorImpl(network.patterns(), network.storage());
        final List<Object> results = new ArrayList<>();
        for (ResourceKey resource : network.craftables()) {
            for (long amount : AMOUNTS) {
                results.add(outcome(() -> TaskPlanCraftingCalculatorListener.calculatePlan(calculator, resource, amount, CancellationToken.NONE)));
                results.add(outcome(() -> PreviewCraftingCalculatorListener.calculatePreview(calculator, resource, amount, CancellationToken.NONE)));
            }
            results.add(outcome(() -> IsCraftableCraftingCalculatorListener.binarySearchMaxAmount(calculator, resource, CancellationToken.NONE)));
        }
        return results;
    }

    private static Object outcome(Supplier<?> calculation) {
        try {
            return calculation.get();
        } catch (RuntimeException e) {
            return "exception " + e.getClass().getName();
        }
    }

    /**
     * A1: the same random networks give the same task plans, previews and craftable amounts with the
     * live view off and on, and the live view is really used when on (and never when off).
     */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 200)
    public static void rsAutocraftingLiveViewGivesThePlainResults(GameTestHelper helper) {
        final boolean original = ArcadiaConfig.REFINED_STORAGE.autocraftingLiveView.get();
        final boolean originalModule = ArcadiaConfig.MODULE_REFINED_STORAGE_ENABLED.get();
        try {
            ArcadiaConfig.MODULE_REFINED_STORAGE_ENABLED.set(true);
            long plainNanos = 0;
            long liveNanos = 0;
            int compared = 0;
            for (long seed : SEEDS) {
                final Network network = network(seed);

                ArcadiaConfig.REFINED_STORAGE.autocraftingLiveView.set(false);
                RefinedStorageRuntimeFlags.refresh();
                final long viewsBefore = ArcadiaLiveNetworkList.created();
                long start = System.nanoTime();
                final List<Object> plain = calculateAll(network);
                plainNanos += System.nanoTime() - start;
                helper.assertValueEqual(ArcadiaLiveNetworkList.created(), viewsBefore, "no live view with A1 off (seed " + seed + ")");

                ArcadiaConfig.REFINED_STORAGE.autocraftingLiveView.set(true);
                RefinedStorageRuntimeFlags.refresh();
                start = System.nanoTime();
                final List<Object> live = calculateAll(network);
                liveNanos += System.nanoTime() - start;
                helper.assertTrue(ArcadiaLiveNetworkList.created() > viewsBefore, "live view used with A1 on (seed " + seed + ")");

                for (int i = 0; i < plain.size(); i++) {
                    helper.assertValueEqual(live.get(i), plain.get(i), "result " + i + " of seed " + seed);
                }
                compared += plain.size();
            }
            ArcadiaTweaks.LOGGER.info("[gametest] A1 compared {} autocrafting results: plain {} ms, live view {} ms",
                    compared, plainNanos / 1_000_000, liveNanos / 1_000_000);
        } finally {
            ArcadiaConfig.REFINED_STORAGE.autocraftingLiveView.set(original);
            ArcadiaConfig.MODULE_REFINED_STORAGE_ENABLED.set(originalModule);
            RefinedStorageRuntimeFlags.refresh();
        }
        helper.succeed();
    }
}
