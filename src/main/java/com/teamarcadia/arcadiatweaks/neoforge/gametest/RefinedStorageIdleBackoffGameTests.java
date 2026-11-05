package com.teamarcadia.arcadiatweaks.neoforge.gametest;

import com.refinedmods.refinedstorage.api.autocrafting.calculation.CancellationToken;
import com.refinedmods.refinedstorage.api.network.Network;
import com.refinedmods.refinedstorage.api.network.autocrafting.AutocraftingNetworkComponent;
import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.api.storage.Actor;
import com.refinedmods.refinedstorage.common.autocrafting.ProcessingPatternState;
import com.refinedmods.refinedstorage.common.autocrafting.autocrafter.AutocrafterBlockEntity;
import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternGridBlockEntity;
import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternType;
import com.refinedmods.refinedstorage.common.content.DataComponents;
import com.refinedmods.refinedstorage.common.exporter.AbstractExporterBlockEntity;
import com.refinedmods.refinedstorage.common.importer.AbstractImporterBlockEntity;
import com.refinedmods.refinedstorage.common.support.direction.DefaultDirectionType;
import com.refinedmods.refinedstorage.common.support.network.AbstractBaseNetworkNodeContainerBlockEntity;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;
import com.teamarcadia.arcadiatweaks.ArcadiaTweaks;
import com.teamarcadia.arcadiatweaks.common.config.ArcadiaConfig;
import com.teamarcadia.arcadiatweaks.common.modules.refinedstorage.RefinedStorageRuntimeFlags;
import com.teamarcadia.arcadiatweaks.neoforge.refinedstorage.ArcadiaIdleBackoff;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * In-game checks of the Refined Storage idle backoff (B1 importers, B2 exporters, B3 machines refused
 * by a running autocrafting task).
 *
 * <p>Each test builds a small network (creative controller, an external storage on a chest as the
 * network's storage, the node under test facing a chest), lets the node fail for a while, then makes
 * the next transfer possible and counts the ticks until it happens. Plain Refined Storage (patch off)
 * takes one or two ticks; with the patch the node has slowed down to its maximum and must still move
 * within that maximum. The patch must also really have slowed the node before the change.</p>
 *
 * @author vyrriox
 */
@GameTestHolder(ArcadiaTweaks.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RefinedStorageIdleBackoffGameTests {

    private static final String AREA = "rs_area";
    private static final String BATCH = ArcadiaTweaks.MOD_ID + "_rs_backoff";

    private static final BlockPos STORE = new BlockPos(1, 1, 3);
    private static final BlockPos EXTERNAL = new BlockPos(2, 1, 3);
    private static final BlockPos CONTROLLER = new BlockPos(3, 1, 3);
    private static final BlockPos NODE = new BlockPos(4, 1, 3);
    private static final BlockPos TARGET = new BlockPos(5, 1, 3);

    private static final int IDLE_TICKS = 120;
    private static final int GIVE_UP = 80;
    private static final int PLAIN_RS_SLACK = 2;
    private static final int PHASE_GAP = 10;

    private RefinedStorageIdleBackoffGameTests() {}

    /** One node type under test: how to build it, how to make it succeed, how to see that it did. */
    private record Case(String name, Block node, ModConfigSpec.BooleanValue patch, int nodeSlack,
                        Consumer<GameTestHelper> setup, Consumer<GameTestHelper> ready, Consumer<GameTestHelper> unblock,
                        Predicate<GameTestHelper> moved, BackoffHolder holder) {}

    /** Finds the object the patch added its backoff state to, after the idle period. */
    @FunctionalInterface
    private interface BackoffHolder {
        Object find(GameTestHelper helper) throws ReflectiveOperationException;
    }

    private static final BackoffHolder NODE_HOLDER = h -> mainNode(h.getBlockEntity(NODE));

    /** The processing pattern of the autocrafter's running task. */
    private static final BackoffHolder TASK_PATTERN_HOLDER = h -> {
        final Object provider = mainNode(h.getBlockEntity(NODE));
        final List<?> tasks = (List<?>) provider.getClass().getMethod("getTasks").invoke(provider);
        if (tasks.isEmpty()) throw new GameTestAssertException("no running task");
        final Field patterns = tasks.get(0).getClass().getDeclaredField("patterns");
        patterns.setAccessible(true);
        return ((java.util.Map<?, ?>) patterns.get(tasks.get(0))).values().iterator().next();
    };

    /** Tick of each phase at which the network is formed and {@link Case#ready} runs. */
    private static final int READY_TICK = 40;

    private static Block rsBlock(String path) {
        final Block b = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("refinedstorage", path));
        if (b == Blocks.AIR) throw new GameTestAssertException("missing block refinedstorage:" + path);
        return b;
    }

    private static Item rsItem(String path) {
        final Item i = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("refinedstorage", path));
        if (i == Items.AIR) throw new GameTestAssertException("missing item refinedstorage:" + path);
        return i;
    }

    private static BlockState facing(Block block, Direction direction) {
        return block.defaultBlockState().setValue(DefaultDirectionType.FACE_CLICKED.getProperty(), direction);
    }

    private static Container chest(GameTestHelper helper, BlockPos pos) {
        if (helper.getBlockEntity(pos) instanceof Container c) return c;
        throw new GameTestAssertException("no chest at " + pos);
    }

    private static void fill(Container c, Item item) {
        for (int s = 0; s < c.getContainerSize(); s++) c.setItem(s, new ItemStack(item, item.getDefaultMaxStackSize()));
    }

    private static int count(Container c, Item item) {
        int n = 0;
        for (int s = 0; s < c.getContainerSize(); s++) {
            if (c.getItem(s).is(item)) n += c.getItem(s).getCount();
        }
        return n;
    }

    private static void speedUpgrades(GameTestHelper helper, int amount) {
        final Item speed = rsItem("speed_upgrade");
        final Object be = helper.getBlockEntity(NODE);
        boolean added = true;
        for (int i = 0; i < amount; i++) {
            if (be instanceof AbstractImporterBlockEntity importer) added &= importer.addUpgrade(new ItemStack(speed));
            else if (be instanceof AbstractExporterBlockEntity exporter) added &= exporter.addUpgrade(new ItemStack(speed));
            else if (be instanceof AutocrafterBlockEntity crafter) added &= crafter.addUpgrade(new ItemStack(speed));
        }
        if (!added) throw new GameTestAssertException("speed upgrades not added");
    }

    private static Network network(GameTestHelper helper) {
        if (helper.getBlockEntity(CONTROLLER) instanceof AbstractBaseNetworkNodeContainerBlockEntity<?> c) {
            return c.getNetworkForItem();
        }
        return null;
    }

    /** The backoff state the patch added to the node under test (found by type), or null. */
    private static ArcadiaIdleBackoff backoffOf(Object holder) throws ReflectiveOperationException {
        for (Class<?> c = holder.getClass(); c != null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getType() == ArcadiaIdleBackoff.class) {
                    f.setAccessible(true);
                    return (ArcadiaIdleBackoff) f.get(holder);
                }
            }
        }
        return null;
    }

    private static Object mainNode(Object blockEntity) throws ReflectiveOperationException {
        for (Class<?> c = blockEntity.getClass(); c != null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getName().equals("mainNetworkNode")) {
                    f.setAccessible(true);
                    return f.get(blockEntity);
                }
            }
        }
        throw new GameTestAssertException("no mainNetworkNode on " + blockEntity);
    }

    /**
     * Runs the case twice on fresh blocks, patch off then on, and checks the delays. Returns through
     * {@code helper.succeed()}.
     */
    private static void run(GameTestHelper helper, Case c) {
        final boolean originalModule = ArcadiaConfig.MODULE_REFINED_STORAGE_ENABLED.get();
        final boolean originalPatch = c.patch().get();
        final int max = ArcadiaConfig.REFINED_STORAGE.idleBackoffMaxTicks.get();
        final Runnable restore = () -> {
            ArcadiaConfig.MODULE_REFINED_STORAGE_ENABLED.set(originalModule);
            c.patch().set(originalPatch);
            RefinedStorageRuntimeFlags.refresh();
        };
        final boolean[] modes = {false, true};
        final int[] delays = {-1, -1};
        final int[] gaps = {-1, -1};
        final int[] clock = {0};
        final int[] phase = {0};
        final int[] start = {0};
        helper.onEachTick(() -> {
            try {
                if (phase[0] >= modes.length) return;
                final int p = clock[0]++ - start[0];
                if (p < 0) return;
                if (p == 0) {
                    ArcadiaConfig.MODULE_REFINED_STORAGE_ENABLED.set(true);
                    c.patch().set(modes[phase[0]]);
                    RefinedStorageRuntimeFlags.refresh();
                    helper.setBlock(STORE, Blocks.CHEST);
                    helper.setBlock(TARGET, Blocks.CHEST);
                    helper.setBlock(EXTERNAL, facing(rsBlock("external_storage"), Direction.WEST));
                    helper.setBlock(CONTROLLER, rsBlock("creative_controller"));
                    helper.setBlock(NODE, facing(c.node(), Direction.EAST));
                    c.setup().accept(helper);
                    return;
                }
                if (p == 20 && network(helper) == null) {
                    throw new GameTestAssertException(c.name() + ": no network");
                }
                if (p == READY_TICK) {
                    c.ready().accept(helper);
                    return;
                }
                if (p == IDLE_TICKS) {
                    final ArcadiaIdleBackoff backoff = backoffOf(c.holder().find(helper));
                    gaps[phase[0]] = backoff == null ? -2 : backoff.gap();
                    if (c.moved().test(helper)) {
                        throw new GameTestAssertException(c.name() + ": moved before it was possible");
                    }
                    c.unblock().accept(helper);
                    return;
                }
                if (p > IDLE_TICKS && delays[phase[0]] < 0 && c.moved().test(helper)) {
                    delays[phase[0]] = p - IDLE_TICKS;
                }
                if (p > IDLE_TICKS && (delays[phase[0]] >= 0 || p - IDLE_TICKS > GIVE_UP)) {
                    for (BlockPos pos : List.of(NODE, CONTROLLER, EXTERNAL, TARGET, STORE)) {
                        if (helper.getBlockEntity(pos) instanceof Container chest) chest.clearContent();
                        helper.setBlock(pos, Blocks.AIR);
                    }
                    phase[0]++;
                    start[0] = clock[0] + PHASE_GAP;
                    if (phase[0] == modes.length) {
                        restore.run();
                        ArcadiaTweaks.LOGGER.info("[gametest] {}: delay after the idle period plain RS {} ticks, with the backoff {} ticks (gap {}, max {})",
                                c.name(), delays[0], delays[1], gaps[1], max);
                        helper.assertTrue(delays[0] >= 0 && delays[0] <= c.nodeSlack(),
                                c.name() + ": plain RS took " + delays[0] + " ticks");
                        helper.assertTrue(delays[1] >= 0 && delays[1] <= max + c.nodeSlack(),
                                c.name() + ": with the backoff it took " + delays[1] + " ticks, max " + max);
                        helper.assertValueEqual(gaps[0], 0, c.name() + ": no backoff with the patch off");
                        helper.assertTrue(gaps[1] >= max - max / 4 && gaps[1] <= max,
                                c.name() + ": idle node slowed down near the maximum, gap " + gaps[1]);
                        helper.succeed();
                    }
                }
            } catch (ReflectiveOperationException e) {
                restore.run();
                throw new GameTestAssertException(c.name() + ": " + e);
            } catch (RuntimeException e) {
                restore.run();
                throw e;
            }
        });
    }

    /** B2: an exporter (4 speed upgrades) facing a full chest; one slot is freed after the idle period. */
    @GameTest(template = AREA, batch = BATCH, timeoutTicks = 600)
    public static void rsIdleExporterExportsWithinTheMaximum(GameTestHelper helper) {
        run(helper, new Case("exporter", rsBlock("exporter"), ArcadiaConfig.REFINED_STORAGE.exporterIdleBackoff, PLAIN_RS_SLACK,
                h -> {
                    fill(chest(h, STORE), Items.COBBLESTONE);
                    fill(chest(h, TARGET), Items.DIRT);
                    speedUpgrades(h, 4);
                    setExporterFilters((AbstractExporterBlockEntity) h.getBlockEntity(NODE),
                            List.of(ItemResource.ofItemStack(new ItemStack(Items.COBBLESTONE))));
                },
                h -> {},
                h -> chest(h, TARGET).setItem(0, ItemStack.EMPTY),
                h -> count(chest(h, TARGET), Items.COBBLESTONE) > 0,
                NODE_HOLDER));
    }

    /** B1: an importer (4 speed upgrades) facing an empty chest; one cobblestone is put in after the idle period. */
    @GameTest(template = AREA, batch = BATCH, timeoutTicks = 600)
    public static void rsIdleImporterImportsWithinTheMaximum(GameTestHelper helper) {
        run(helper, new Case("importer", rsBlock("importer"), ArcadiaConfig.REFINED_STORAGE.importerIdleBackoff, PLAIN_RS_SLACK,
                h -> speedUpgrades(h, 4),
                h -> {},
                h -> chest(h, TARGET).setItem(0, new ItemStack(Items.COBBLESTONE)),
                h -> count(chest(h, TARGET), Items.COBBLESTONE) == 0 && count(chest(h, STORE), Items.COBBLESTONE) > 0,
                NODE_HOLDER));
    }

    /**
     * B3: an autocrafter (4 speed upgrades) runs a processing task into a full chest; one slot is freed
     * after the idle period. Its steps come every 2 ticks, so plain RS may take up to 2 ticks more.
     */
    @GameTest(template = AREA, batch = BATCH, timeoutTicks = 600)
    public static void rsRefusingMachineGetsInputsWithinTheMaximum(GameTestHelper helper) {
        run(helper, new Case("autocrafting machine", rsBlock("autocrafter"), ArcadiaConfig.REFINED_STORAGE.autocraftingSinkBackoff, PLAIN_RS_SLACK + 2,
                h -> {
                    fill(chest(h, STORE), Items.COBBLESTONE);
                    fill(chest(h, TARGET), Items.DIRT);
                    speedUpgrades(h, 4);
                    insertPattern(h, Items.COBBLESTONE, Items.STONE);
                },
                h -> startTask(h, Items.STONE, 3),
                h -> chest(h, TARGET).setItem(0, ItemStack.EMPTY),
                h -> count(chest(h, TARGET), Items.COBBLESTONE) > 0,
                TASK_PATTERN_HOLDER));
    }

    private static void startTask(GameTestHelper helper, Item output, long amount) {
        final Network network = network(helper);
        if (network == null) throw new GameTestAssertException("no network for the task");
        final Optional<?> id = network.getComponent(AutocraftingNetworkComponent.class)
                .startTask(ItemResource.ofItemStack(new ItemStack(output)), amount, Actor.EMPTY, false, CancellationToken.NONE);
        if (id.isEmpty()) throw new GameTestAssertException("autocrafting task not started");
    }

    private static void insertPattern(GameTestHelper helper, Item input, Item output) {
        final ItemStack pattern = PatternGridBlockEntity.createPatternStack(PatternType.PROCESSING);
        pattern.set(DataComponents.INSTANCE.getProcessingPatternState(), new ProcessingPatternState(
                List.of(Optional.of(new ProcessingPatternState.ProcessingIngredient(
                        new ResourceAmount(ItemResource.ofItemStack(new ItemStack(input)), 1), List.of()))),
                List.of(Optional.of(new ResourceAmount(ItemResource.ofItemStack(new ItemStack(output)), 1)))));
        try {
            final Method getter = AutocrafterBlockEntity.class.getDeclaredMethod("getPatternContainer");
            getter.setAccessible(true);
            ((Container) getter.invoke(helper.getBlockEntity(NODE))).setItem(0, pattern);
        } catch (ReflectiveOperationException e) {
            throw new GameTestAssertException("pattern not inserted: " + e);
        }
    }

    private static void setExporterFilters(AbstractExporterBlockEntity exporter, List<ResourceKey> filters) {
        try {
            final Method m = AbstractExporterBlockEntity.class.getDeclaredMethod("setFilters", List.class);
            m.setAccessible(true);
            m.invoke(exporter, new ArrayList<>(filters));
        } catch (ReflectiveOperationException e) {
            throw new GameTestAssertException("exporter filter not set: " + e);
        }
    }
}
