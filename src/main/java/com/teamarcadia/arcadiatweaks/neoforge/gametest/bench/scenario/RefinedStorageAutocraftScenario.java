package com.teamarcadia.arcadiatweaks.neoforge.gametest.bench.scenario;

import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.network.Network;
import com.refinedmods.refinedstorage.api.network.autocrafting.AutocraftingNetworkComponent;
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent;
import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.api.storage.Actor;
import com.refinedmods.refinedstorage.common.api.RefinedStorageApi;
import com.refinedmods.refinedstorage.common.api.storage.SerializableStorage;
import com.refinedmods.refinedstorage.common.api.storage.StorageContainerItem;
import com.refinedmods.refinedstorage.common.api.storage.StorageRepository;
import com.refinedmods.refinedstorage.common.autocrafting.ProcessingPatternState;
import com.refinedmods.refinedstorage.common.autocrafting.autocrafter.AutocrafterBlockEntity;
import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternGridBlockEntity;
import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternType;
import com.refinedmods.refinedstorage.common.content.DataComponents;
import com.refinedmods.refinedstorage.common.exporter.AbstractExporterBlockEntity;
import com.refinedmods.refinedstorage.common.storage.AbstractDiskContainerBlockEntity;
import com.refinedmods.refinedstorage.common.support.direction.DefaultDirectionType;
import com.refinedmods.refinedstorage.common.support.network.AbstractBaseNetworkNodeContainerBlockEntity;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;
import com.teamarcadia.arcadiatweaks.ArcadiaTweaks;
import com.teamarcadia.arcadiatweaks.common.config.ArcadiaConfig;
import com.teamarcadia.arcadiatweaks.neoforge.gametest.bench.BenchScenario;
import com.teamarcadia.arcadiatweaks.neoforge.gametest.bench.BenchSwitches;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Refined Storage 2 autocrafting on demand: exporters with an autocrafting upgrade ask for items the
 * network does not hold and cannot craft, which is what a base does when a machine is fed by an
 * exporter and one ingredient runs out.
 *
 * <p>The network holds thousands of distinct resources (every item, enchanted book and potion of the
 * runtime) on 64k disks. Five autocrafters carry {@value #OUTPUTS} processing patterns: each output
 * needs one shared intermediate, which needs an ingredient the network never has. {@value #EXPORTERS}
 * exporters, each with 3 speed upgrades and an autocrafting upgrade (an exporter has 4 upgrade slots), have 9 of those outputs as
 * filters and face a chest. Every work cycle, every filter finds nothing, asks the network to craft it
 * and gets "missing resources" back.
 *
 * @author vyrriox
 */
public final class RefinedStorageAutocraftScenario implements BenchScenario {

    private static final int OUTPUTS = 36;
    private static final int EXPORTERS = 16;
    private static final int FILTERS = 9;
    private static final int SPACING = 3;
    private static final int BUS_HEIGHT = 2;
    private static final int PATTERN_SLOTS = 9;

    private final List<BlockPos> placed = new ArrayList<>();
    private final List<AbstractBaseNetworkNodeContainerBlockEntity<?>> nodes = new ArrayList<>();
    private final List<BlockPos> autocrafters = new ArrayList<>();
    private final List<BlockPos> targetChests = new ArrayList<>();
    private final List<PendingDisk> pendingDisks = new ArrayList<>();
    private final List<UUID> diskIds = new ArrayList<>();
    private final List<ItemStack> patterns = new ArrayList<>();
    private boolean patternsInserted;
    private int resources;
    private long storedItems;
    private int nodesOnNetwork;
    private String setupWarning = "";

    @Override
    public String id() {
        return "refinedstorage_autocraft";
    }

    @Override
    public String description() {
        return "Refined Storage 2 autocrafting on demand: " + EXPORTERS + " exporters (3 speed + autocrafting upgrade, "
                + FILTERS + " filters each) ask a network of thousands of resources for " + OUTPUTS
                + " patterned items whose ingredient is missing.";
    }

    @Override
    public List<BenchSwitches.Switch> switches() {
        return List.of(
                BenchSwitches.Switch.of("modules.refinedstorage", ArcadiaConfig.MODULE_REFINED_STORAGE_ENABLED),
                BenchSwitches.Switch.of("refinedstorage.activeness_check_coalescing_enabled", ArcadiaConfig.REFINED_STORAGE.activenessCheckCoalescing),
                BenchSwitches.Switch.of("refinedstorage.activeness_check_interval_ticks", ArcadiaConfig.REFINED_STORAGE.activenessCheckInterval),
                BenchSwitches.Switch.of("refinedstorage.single_component_lookup_enabled", ArcadiaConfig.REFINED_STORAGE.singleComponentLookup),
                BenchSwitches.Switch.of("refinedstorage.quiet_hot_debug_logs_enabled", ArcadiaConfig.REFINED_STORAGE.quietHotDebugLogs),
                BenchSwitches.Switch.of("refinedstorage.autocrafting_live_storage_view_enabled", ArcadiaConfig.REFINED_STORAGE.autocraftingLiveView)
        );
    }

    @Override
    public int footprint() {
        return (EXPORTERS + 8) * SPACING / 2 + 16;
    }

    @Override
    public void build(ServerLevel level, BlockPos center) {
        final int crafters = (OUTPUTS + 1 + PATTERN_SLOTS - 1) / PATTERN_SLOTS;
        final int length = (EXPORTERS + crafters + 4) * SPACING + 8;
        final BlockPos origin = center.offset(-length / 2, 0, 0);
        final Block controller = block("creative_controller");
        final Block cable = block("cable");
        final Block diskDrive = block("disk_drive");
        final Block exporter = block("exporter");
        final Block autocrafter = block("autocrafter");
        final Item disk64k = item("64k_storage_disk");
        final Item speed = item("speed_upgrade");
        final Item autocraftingUpgrade = item("autocrafting_upgrade");

        place(level, origin.offset(0, 1, 0), controller.defaultBlockState());
        for (int x = 1; x < length; x++) {
            for (int h = 1; h <= BUS_HEIGHT; h++) {
                place(level, origin.offset(x, h, 0), cable.defaultBlockState());
            }
        }
        final List<AbstractDiskContainerBlockEntity<?>> drives = new ArrayList<>();
        for (int d = 0; d < 2; d++) {
            final BlockPos pos = origin.offset(2 + d * 2, 0, 0);
            place(level, pos, diskDrive.defaultBlockState());
            if (level.getBlockEntity(pos) instanceof AbstractDiskContainerBlockEntity<?> drive) {
                drives.add(drive);
            }
        }
        int x = 8;
        for (int c = 0; c < crafters; c++, x += SPACING) {
            final BlockPos pos = origin.offset(x, 0, 0);
            place(level, pos, autocrafter.defaultBlockState());
            autocrafters.add(pos);
        }

        // Items the network never holds: outputs, the shared intermediate and the missing ingredient.
        final List<Item> outputs = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (BuiltInRegistries.ITEM.getKey(item).getPath().endsWith("_spawn_egg") && outputs.size() < OUTPUTS) {
                outputs.add(item);
            }
        }
        if (outputs.size() < OUTPUTS) {
            throw new IllegalStateException("only " + outputs.size() + " spawn eggs for " + OUTPUTS + " outputs");
        }
        final Item intermediate = Items.COMMAND_BLOCK;
        final Item missing = Items.BARRIER;
        for (Item output : outputs) {
            patterns.add(processingPattern(intermediate, output));
        }
        patterns.add(processingPattern(missing, intermediate));

        for (int e = 0; e < EXPORTERS; e++, x += SPACING) {
            final BlockPos chest = origin.offset(x, 0, 0);
            place(level, chest, Blocks.CHEST.defaultBlockState());
            targetChests.add(chest);
            place(level, origin.offset(x, 1, -1), cable.defaultBlockState());
            final BlockPos exp = origin.offset(x, 0, -1);
            place(level, exp, directed(exporter, Direction.SOUTH));
            if (level.getBlockEntity(exp) instanceof AbstractExporterBlockEntity be) {
                for (int u = 0; u < 3; u++) be.addUpgrade(new ItemStack(speed));
                be.addUpgrade(new ItemStack(autocraftingUpgrade));
                final List<ResourceKey> filters = new ArrayList<>();
                for (int k = 0; k < FILTERS; k++) {
                    filters.add(ItemResource.ofItemStack(new ItemStack(outputs.get((e * FILTERS + k) % OUTPUTS))));
                }
                setExporterFilter(be, filters);
            }
        }

        final Set<Item> reserved = new HashSet<>(outputs);
        reserved.add(intermediate);
        reserved.add(missing);
        final FakePlayer player = FakePlayerFactory.getMinecraft(level);
        final StorageRepository repo = RefinedStorageApi.INSTANCE.getStorageRepository(level);
        final List<ItemStack> variety = RefinedStorageScenario.varietyStacks(level).stream()
                .filter(s -> !reserved.contains(s.getItem()))
                .toList();
        resources = variety.size();
        final List<SerializableStorage> storages = new ArrayList<>();
        for (AbstractDiskContainerBlockEntity<?> drive : drives) {
            final int slots = drive.getDiskInventory().getContainerSize();
            for (int s = 0; s < slots; s++) {
                final ItemStack diskStack = new ItemStack(disk64k);
                diskStack.getItem().inventoryTick(diskStack, level, player, 0, false);
                if (diskStack.getItem() instanceof StorageContainerItem sci) {
                    sci.resolve(repo, diskStack).ifPresent(storages::add);
                }
                final UUID id = diskStack.get(DataComponents.INSTANCE.getStorageReference());
                if (id != null) diskIds.add(id);
                pendingDisks.add(new PendingDisk(drive.getBlockPos(), s, diskStack));
            }
        }
        if (storages.isEmpty()) {
            throw new IllegalStateException("no Refined Storage disk storage could be created");
        }
        for (int i = 0; i < variety.size(); i++) {
            final SerializableStorage st = storages.get(i % storages.size());
            storedItems += st.insert(ItemResource.ofItemStack(variety.get(i)), 1 + (i % 50), Action.EXECUTE, Actor.EMPTY);
        }
        repo.markAsChanged();
    }

    private static ItemStack processingPattern(Item input, Item output) {
        final ItemStack stack = PatternGridBlockEntity.createPatternStack(PatternType.PROCESSING);
        final ProcessingPatternState state = new ProcessingPatternState(
                List.of(Optional.of(new ProcessingPatternState.ProcessingIngredient(
                        new ResourceAmount(ItemResource.ofItemStack(new ItemStack(input)), 1), List.of()))),
                List.of(Optional.of(new ResourceAmount(ItemResource.ofItemStack(new ItemStack(output)), 1))));
        stack.set(DataComponents.INSTANCE.getProcessingPatternState(), state);
        return stack;
    }

    @Override
    public boolean ready(ServerLevel level, int ticksSinceBuild) {
        if (ticksSinceBuild < 40) return false;
        nodes.clear();
        nodesOnNetwork = 0;
        for (BlockPos p : placed) {
            if (level.getBlockEntity(p) instanceof AbstractBaseNetworkNodeContainerBlockEntity<?> n) {
                nodes.add(n);
            }
        }
        final Network network = network();
        for (AbstractBaseNetworkNodeContainerBlockEntity<?> n : nodes) {
            if (network != null && n.getNetworkForItem() == network) nodesOnNetwork++;
        }
        if (nodesOnNetwork < nodes.size() && ticksSinceBuild <= 200) return false;
        if (!pendingDisks.isEmpty()) {
            for (PendingDisk d : pendingDisks) {
                if (level.getBlockEntity(d.drive()) instanceof AbstractDiskContainerBlockEntity<?> drive) {
                    drive.getDiskInventory().setItem(d.slot(), d.stack());
                }
            }
            pendingDisks.clear();
            return false;
        }
        if (!patternsInserted) {
            insertPatterns(level);
            patternsInserted = true;
            return false;
        }
        final long stored = network == null ? 0 : network.getComponent(StorageNetworkComponent.class).getStored();
        final int knownPatterns = network == null ? 0 : network.getComponent(AutocraftingNetworkComponent.class).getPatterns().size();
        return (stored >= storedItems && knownPatterns >= patterns.size()) || ticksSinceBuild > 1500;
    }

    private void insertPatterns(ServerLevel level) {
        try {
            final Method getter = AutocrafterBlockEntity.class.getDeclaredMethod("getPatternContainer");
            getter.setAccessible(true);
            int next = 0;
            for (BlockPos pos : autocrafters) {
                if (!(level.getBlockEntity(pos) instanceof AutocrafterBlockEntity crafter)) continue;
                final Container container = (Container) getter.invoke(crafter);
                for (int slot = 0; slot < container.getContainerSize() && next < patterns.size(); slot++) {
                    container.setItem(slot, patterns.get(next++));
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            setupWarning = "patterns not inserted: " + e;
            ArcadiaTweaks.LOGGER.warn("[bench] {}", setupWarning, e);
        }
    }

    private record PendingDisk(BlockPos drive, int slot, ItemStack stack) {}

    private Network network() {
        return nodes.isEmpty() ? null : nodes.get(0).getNetworkForItem();
    }

    @Override
    public Map<String, Double> afterPhase(ServerLevel level, boolean patchesOn) {
        final Map<String, Double> out = new LinkedHashMap<>();
        final Network network = network();
        if (network != null) {
            final AutocraftingNetworkComponent autocrafting = network.getComponent(AutocraftingNetworkComponent.class);
            out.put("runningTasks", (double) autocrafting.getStatuses().size());
            out.put("networkPatterns", (double) autocrafting.getPatterns().size());
            out.put("networkResources", (double) network.getComponent(StorageNetworkComponent.class).getAll().size());
        }
        int exported = 0;
        for (BlockPos p : targetChests) {
            if (level.getBlockEntity(p) instanceof Container c) {
                for (int s = 0; s < c.getContainerSize(); s++) exported += c.getItem(s).getCount();
            }
        }
        out.put("itemsExported", (double) exported);
        lastResults(level).forEach((k, v) -> out.put("lastResult." + k, (double) v));
        return out;
    }

    /** How the exporters' filters ended their last work cycle, counted by result. */
    private Map<String, Integer> lastResults(ServerLevel level) {
        final Map<String, Integer> counts = new java.util.TreeMap<>();
        for (AbstractBaseNetworkNodeContainerBlockEntity<?> n : nodes) {
            if (!(n instanceof AbstractExporterBlockEntity)) continue;
            try {
                java.lang.reflect.Field f = null;
                for (Class<?> c = n.getClass(); c != null && f == null; c = c.getSuperclass()) {
                    for (java.lang.reflect.Field d : c.getDeclaredFields()) {
                        if (d.getName().equals("mainNetworkNode")) f = d;
                    }
                }
                if (f == null) continue;
                f.setAccessible(true);
                final var node = (com.refinedmods.refinedstorage.api.network.impl.node.exporter.ExporterNetworkNode) f.get(n);
                counts.merge(node.isActive() ? "active" : "inactive", 1, Integer::sum);
                for (int i = 0; i < FILTERS; i++) {
                    final var r = node.getLastResult(i);
                    counts.merge(r == null ? "none" : r.name(), 1, Integer::sum);
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                counts.merge("error", 1, Integer::sum);
            }
        }
        return counts;
    }

    @Override
    public Map<String, Object> size() {
        final Map<String, Object> m = new LinkedHashMap<>();
        m.put("controller", "creative_controller");
        m.put("distinctResources", resources);
        m.put("storedItems", storedItems);
        m.put("autocrafters", autocrafters.size());
        m.put("patterns", patterns.size());
        m.put("exportersWithAutocraftingUpgrade", EXPORTERS);
        m.put("filtersPerExporter", FILTERS);
        m.put("speedUpgradesPerExporter", 3);
        m.put("networkNodes", nodes.size());
        m.put("nodesOnNetwork", nodesOnNetwork);
        if (!setupWarning.isEmpty()) m.put("warning", setupWarning);
        return m;
    }

    @Override
    public void teardown(ServerLevel level) {
        for (int i = placed.size() - 1; i >= 0; i--) {
            final BlockPos p = placed.get(i);
            final BlockEntity be = level.getBlockEntity(p);
            if (be instanceof AbstractDiskContainerBlockEntity<?> drive) {
                drive.getDiskInventory().clearContent();
            } else if (be instanceof Container c) {
                c.clearContent();
            }
            level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
        }
        placed.clear();
        nodes.clear();
        final StorageRepository repo = RefinedStorageApi.INSTANCE.getStorageRepository(level);
        diskIds.forEach(repo::remove);
        diskIds.clear();
    }

    private void place(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, 3);
        placed.add(pos);
    }

    private static BlockState directed(Block block, Direction facing) {
        return block.defaultBlockState().setValue(DefaultDirectionType.FACE_CLICKED.getProperty(), facing);
    }

    private void setExporterFilter(AbstractExporterBlockEntity exporter, List<ResourceKey> filters) {
        try {
            final Method m = AbstractExporterBlockEntity.class.getDeclaredMethod("setFilters", List.class);
            m.setAccessible(true);
            m.invoke(exporter, filters);
        } catch (ReflectiveOperationException | RuntimeException e) {
            setupWarning = "exporter filter not set: " + e;
        }
    }

    private static Block block(String path) {
        final ResourceLocation id = ResourceLocation.fromNamespaceAndPath("refinedstorage", path);
        final Block b = BuiltInRegistries.BLOCK.get(id);
        if (b == Blocks.AIR) throw new IllegalStateException("missing block " + id);
        return b;
    }

    private static Item item(String path) {
        final ResourceLocation id = ResourceLocation.fromNamespaceAndPath("refinedstorage", path);
        final Item i = BuiltInRegistries.ITEM.get(id);
        if (i == Items.AIR) throw new IllegalStateException("missing item " + id);
        return i;
    }
}
