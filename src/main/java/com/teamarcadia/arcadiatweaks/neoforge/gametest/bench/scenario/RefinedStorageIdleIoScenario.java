package com.teamarcadia.arcadiatweaks.neoforge.gametest.bench.scenario;

import com.refinedmods.refinedstorage.api.autocrafting.calculation.CancellationToken;
import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.network.Network;
import com.refinedmods.refinedstorage.api.network.autocrafting.AutocraftingNetworkComponent;
import com.refinedmods.refinedstorage.api.network.impl.node.exporter.ExporterNetworkNode;
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
import com.refinedmods.refinedstorage.common.importer.AbstractImporterBlockEntity;
import com.refinedmods.refinedstorage.common.storage.AbstractDiskContainerBlockEntity;
import com.refinedmods.refinedstorage.common.support.direction.DefaultDirectionType;
import com.refinedmods.refinedstorage.common.support.network.AbstractBaseNetworkNodeContainerBlockEntity;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;
import com.teamarcadia.arcadiatweaks.ArcadiaTweaks;
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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Refined Storage 2 nodes that wait, as most nodes of a base do most of the time: exporters into full
 * machines, exporters asking for items the network does not have, importers on empty outputs and
 * autocrafting tasks whose machine is full. Every node has 4 speed upgrades (one attempt per tick).
 *
 * <p>The network holds thousands of resources on 64k disks plus {@value #EXTERNAL} external storages
 * on full chests, so a lookup goes through every kind of storage. Per kind: {@value #PER_KIND}
 * exporters with 9 missing filters, {@value #PER_KIND} exporters of cobblestone into chests full of
 * dirt, {@value #PER_KIND} importers on empty chests, and {@value #CRAFTERS} autocrafters running a
 * processing task into a chest full of dirt.</p>
 *
 * @author vyrriox
 */
public final class RefinedStorageIdleIoScenario implements BenchScenario {

    private static final int PER_KIND = 24;
    private static final int CRAFTERS = 12;
    private static final int EXTERNAL = 8;
    private static final int FILTERS = 9;
    private static final int SPACING = 2;
    private static final int BUS_HEIGHT = 2;

    private final List<BlockPos> placed = new ArrayList<>();
    private final List<AbstractBaseNetworkNodeContainerBlockEntity<?>> nodes = new ArrayList<>();
    private final List<BlockPos> crafters = new ArrayList<>();
    private final List<BlockPos> fullTargets = new ArrayList<>();
    private final List<BlockPos> emptySources = new ArrayList<>();
    private final List<PendingDisk> pendingDisks = new ArrayList<>();
    private final List<UUID> diskIds = new ArrayList<>();
    private final List<Item> missing = new ArrayList<>();
    private final List<Item> outputs = new ArrayList<>();
    private int stage;
    private int resources;
    private long storedItems;
    private int nodesOnNetwork;
    private int tasksStarted;
    private String setupWarning = "";

    @Override
    public String id() {
        return "refinedstorage_idle_io";
    }

    @Override
    public String description() {
        return "Refined Storage 2 waiting nodes, 4 speed upgrades each: " + PER_KIND + " exporters with missing filters, "
                + PER_KIND + " exporters into full chests, " + PER_KIND + " importers on empty chests and " + CRAFTERS
                + " autocrafters whose task waits for a full chest, on a network of thousands of resources and "
                + EXTERNAL + " external storages.";
    }

    @Override
    public List<BenchSwitches.Switch> switches() {
        return RefinedStorageScenario.allSwitches();
    }

    @Override
    public int footprint() {
        return (PER_KIND * 3 + CRAFTERS + EXTERNAL + 8) * SPACING / 2 + 16;
    }

    @Override
    public void build(ServerLevel level, BlockPos center) {
        final int length = (PER_KIND * 3 + CRAFTERS + EXTERNAL + 8) * SPACING + 8;
        final BlockPos origin = center.offset(-length / 2, 0, 0);
        final Block cable = block("cable");
        final Block exporter = block("exporter");
        final Block importer = block("importer");
        final Block autocrafter = block("autocrafter");
        final Block external = block("external_storage");
        final Item speed = item("speed_upgrade");

        place(level, origin.offset(0, 1, 0), block("creative_controller").defaultBlockState());
        for (int x = 1; x < length; x++) {
            for (int h = 1; h <= BUS_HEIGHT; h++) {
                place(level, origin.offset(x, h, 0), cable.defaultBlockState());
            }
        }
        final List<AbstractDiskContainerBlockEntity<?>> drives = new ArrayList<>();
        for (int d = 0; d < 2; d++) {
            final BlockPos pos = origin.offset(2 + d * 2, 0, 0);
            place(level, pos, block("disk_drive").defaultBlockState());
            if (level.getBlockEntity(pos) instanceof AbstractDiskContainerBlockEntity<?> drive) drives.add(drive);
        }
        // Items the network never holds: the exporters' missing filters, then the autocrafters' outputs.
        for (Item item : BuiltInRegistries.ITEM) {
            if (!BuiltInRegistries.ITEM.getKey(item).getPath().endsWith("_spawn_egg")) continue;
            if (missing.size() < FILTERS) missing.add(item);
            else if (outputs.size() < CRAFTERS) outputs.add(item);
        }
        if (outputs.size() < CRAFTERS) throw new IllegalStateException("not enough spawn eggs for the scenario");
        final List<ResourceKey> missingFilters = missing.stream().map(i -> (ResourceKey) ItemResource.ofItemStack(new ItemStack(i))).toList();

        int x = 8;
        // External storages on full chests, under the bus.
        for (int e = 0; e < EXTERNAL; e++, x += SPACING) {
            final BlockPos chest = origin.offset(x, 0, -2);
            place(level, chest, Blocks.CHEST.defaultBlockState());
            if (level.getBlockEntity(chest) instanceof Container c) {
                for (int s = 0; s < c.getContainerSize(); s++) {
                    final Item it = BuiltInRegistries.ITEM.byId(1 + (e * 27 + s) % (BuiltInRegistries.ITEM.size() - 1));
                    if (!missing.contains(it) && !outputs.contains(it)) c.setItem(s, new ItemStack(it, it.getDefaultMaxStackSize()));
                }
            }
            place(level, origin.offset(x, 1, -1), cable.defaultBlockState());
            place(level, origin.offset(x, 0, -1), directed(external, Direction.NORTH));
        }
        for (int i = 0; i < PER_KIND; i++, x += SPACING) {
            // Exporter with 9 missing filters into an empty chest (north side).
            final BlockPos missingExp = origin.offset(x, 0, -1);
            place(level, origin.offset(x, 1, -1), cable.defaultBlockState());
            place(level, missingExp, directed(exporter, Direction.NORTH));
            place(level, origin.offset(x, 0, -2), Blocks.CHEST.defaultBlockState());
            if (level.getBlockEntity(missingExp) instanceof AbstractExporterBlockEntity be) {
                upgrade(be::addUpgrade, speed, 4);
                setExporterFilters(be, missingFilters);
            }
            // Exporter of cobblestone into a chest full of dirt (south side).
            final BlockPos fullExp = origin.offset(x, 0, 1);
            place(level, origin.offset(x, 1, 1), cable.defaultBlockState());
            place(level, fullExp, directed(exporter, Direction.SOUTH));
            final BlockPos full = origin.offset(x, 0, 2);
            place(level, full, Blocks.CHEST.defaultBlockState());
            fill(level, full, Items.DIRT);
            fullTargets.add(full);
            if (level.getBlockEntity(fullExp) instanceof AbstractExporterBlockEntity be) {
                upgrade(be::addUpgrade, speed, 4);
                setExporterFilters(be, List.of(ItemResource.ofItemStack(new ItemStack(Items.COBBLESTONE))));
            }
        }
        for (int i = 0; i < PER_KIND; i++, x += SPACING) {
            // Importer on an empty chest (north side).
            final BlockPos imp = origin.offset(x, 0, -1);
            place(level, origin.offset(x, 1, -1), cable.defaultBlockState());
            place(level, imp, directed(importer, Direction.NORTH));
            final BlockPos source = origin.offset(x, 0, -2);
            place(level, source, Blocks.CHEST.defaultBlockState());
            emptySources.add(source);
            if (level.getBlockEntity(imp) instanceof AbstractImporterBlockEntity be) {
                upgrade(be::addUpgrade, speed, 4);
            }
        }
        for (int i = 0; i < CRAFTERS; i++, x += SPACING) {
            // Autocrafter pushing into a chest full of dirt (south side).
            final BlockPos crafter = origin.offset(x, 0, 1);
            place(level, origin.offset(x, 1, 1), cable.defaultBlockState());
            place(level, crafter, directed(autocrafter, Direction.SOUTH));
            final BlockPos machine = origin.offset(x, 0, 2);
            place(level, machine, Blocks.CHEST.defaultBlockState());
            fill(level, machine, Items.DIRT);
            fullTargets.add(machine);
            crafters.add(crafter);
            if (level.getBlockEntity(crafter) instanceof AutocrafterBlockEntity be) {
                upgrade(be::addUpgrade, speed, 4);
            }
        }

        // Disks with every resource of the runtime (except the missing filters) and plenty of cobblestone.
        final Set<Item> reserved = new HashSet<>(missing);
        reserved.addAll(outputs);
        final FakePlayer player = FakePlayerFactory.getMinecraft(level);
        final StorageRepository repo = RefinedStorageApi.INSTANCE.getStorageRepository(level);
        final List<ItemStack> variety = RefinedStorageScenario.varietyStacks(level).stream()
                .filter(s -> !reserved.contains(s.getItem())).toList();
        resources = variety.size();
        final List<SerializableStorage> storages = new ArrayList<>();
        for (AbstractDiskContainerBlockEntity<?> drive : drives) {
            for (int s = 0; s < drive.getDiskInventory().getContainerSize(); s++) {
                final ItemStack diskStack = new ItemStack(item("64k_storage_disk"));
                diskStack.getItem().inventoryTick(diskStack, level, player, 0, false);
                if (diskStack.getItem() instanceof StorageContainerItem sci) sci.resolve(repo, diskStack).ifPresent(storages::add);
                final UUID id = diskStack.get(DataComponents.INSTANCE.getStorageReference());
                if (id != null) diskIds.add(id);
                pendingDisks.add(new PendingDisk(drive.getBlockPos(), s, diskStack));
            }
        }
        if (storages.isEmpty()) throw new IllegalStateException("no Refined Storage disk storage could be created");
        for (SerializableStorage st : storages) {
            storedItems += st.insert(ItemResource.ofItemStack(new ItemStack(Items.COBBLESTONE)), 4_000, Action.EXECUTE, Actor.EMPTY);
        }
        for (int i = 0; i < variety.size(); i++) {
            storedItems += storages.get(i % storages.size()).insert(ItemResource.ofItemStack(variety.get(i)), 1 + (i % 50), Action.EXECUTE, Actor.EMPTY);
        }
        repo.markAsChanged();
    }

    @Override
    public boolean ready(ServerLevel level, int ticksSinceBuild) {
        if (ticksSinceBuild < 40) return false;
        nodes.clear();
        nodesOnNetwork = 0;
        for (BlockPos p : placed) {
            if (level.getBlockEntity(p) instanceof AbstractBaseNetworkNodeContainerBlockEntity<?> n) nodes.add(n);
        }
        final Network network = network();
        for (AbstractBaseNetworkNodeContainerBlockEntity<?> n : nodes) {
            if (network != null && n.getNetworkForItem() == network) nodesOnNetwork++;
        }
        if (nodesOnNetwork < nodes.size() && ticksSinceBuild <= 200) return false;
        if (network == null) return ticksSinceBuild > 1500;
        switch (stage) {
            case 0 -> {
                for (PendingDisk d : pendingDisks) {
                    if (level.getBlockEntity(d.drive()) instanceof AbstractDiskContainerBlockEntity<?> drive) {
                        drive.getDiskInventory().setItem(d.slot(), d.stack());
                    }
                }
                pendingDisks.clear();
                insertPatterns(level);
                stage = 1;
                return false;
            }
            case 1 -> {
                final int patterns = network.getComponent(AutocraftingNetworkComponent.class).getPatterns().size();
                if (patterns < CRAFTERS || network.getComponent(StorageNetworkComponent.class).getStored() < storedItems) {
                    return ticksSinceBuild > 1500;
                }
                // One processing task per autocrafter, each waiting for its full chest.
                final AutocraftingNetworkComponent autocrafting = network.getComponent(AutocraftingNetworkComponent.class);
                for (int i = 0; i < CRAFTERS; i++) {
                    final ResourceKey output = ItemResource.ofItemStack(new ItemStack(outputs.get(i)));
                    if (autocrafting.startTask(output, 64, Actor.EMPTY, false, CancellationToken.NONE).isPresent()) tasksStarted++;
                }
                stage = 2;
                return false;
            }
            default -> {
                return true;
            }
        }
    }

    /** Autocrafter i gets a processing pattern: 1 cobblestone into output i, an item the network does not hold. */
    private void insertPatterns(ServerLevel level) {
        try {
            final Method getter = AutocrafterBlockEntity.class.getDeclaredMethod("getPatternContainer");
            getter.setAccessible(true);
            for (int i = 0; i < crafters.size(); i++) {
                if (!(level.getBlockEntity(crafters.get(i)) instanceof AutocrafterBlockEntity crafter)) continue;
                final ItemStack pattern = PatternGridBlockEntity.createPatternStack(PatternType.PROCESSING);
                pattern.set(DataComponents.INSTANCE.getProcessingPatternState(), new ProcessingPatternState(
                        List.of(Optional.of(new ProcessingPatternState.ProcessingIngredient(
                                new ResourceAmount(ItemResource.ofItemStack(new ItemStack(Items.COBBLESTONE)), 1), List.of()))),
                        List.of(Optional.of(new ResourceAmount(ItemResource.ofItemStack(new ItemStack(outputs.get(i))), 1)))));
                ((Container) getter.invoke(crafter)).setItem(0, pattern);
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
    public void beforePhase(ServerLevel level, boolean patchesOn) {
        // Same start for both phases: the full chests are full, the sources are empty.
        for (BlockPos p : fullTargets) fill(level, p, Items.DIRT);
        for (BlockPos p : emptySources) {
            if (level.getBlockEntity(p) instanceof Container c) c.clearContent();
        }
    }

    @Override
    public Map<String, Double> afterPhase(ServerLevel level, boolean patchesOn) {
        final Map<String, Double> out = new LinkedHashMap<>();
        final Network network = network();
        if (network != null) {
            out.put("runningTasks", (double) network.getComponent(AutocraftingNetworkComponent.class).getStatuses().size());
            out.put("networkResources", (double) network.getComponent(StorageNetworkComponent.class).getAll().size());
        }
        int moved = 0;
        for (BlockPos p : fullTargets) {
            if (level.getBlockEntity(p) instanceof Container c) {
                for (int s = 0; s < c.getContainerSize(); s++) {
                    if (!c.getItem(s).is(Items.DIRT)) moved += c.getItem(s).getCount();
                }
            }
        }
        out.put("itemsIntoFullChests", (double) moved);
        final Map<String, Integer> results = new TreeMap<>();
        for (AbstractBaseNetworkNodeContainerBlockEntity<?> n : nodes) {
            if (!(n instanceof AbstractExporterBlockEntity)) continue;
            try {
                if (mainNode(n) instanceof ExporterNetworkNode node) {
                    for (int i = 0; i < FILTERS; i++) {
                        try {
                            final var r = node.getLastResult(i);
                            results.merge(r == null ? "none" : r.name(), 1, Integer::sum);
                        } catch (IndexOutOfBoundsException fewerFilters) {
                            break;
                        }
                    }
                }
            } catch (ReflectiveOperationException e) {
                results.merge("error", 1, Integer::sum);
            }
        }
        results.forEach((k, v) -> out.put("lastResult." + k, (double) v));
        return out;
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
        return null;
    }

    @Override
    public Map<String, Object> size() {
        final Map<String, Object> m = new LinkedHashMap<>();
        m.put("controller", "creative_controller");
        m.put("distinctResources", resources);
        m.put("storedItems", storedItems);
        m.put("externalStoragesOnFullChests", EXTERNAL);
        m.put("exportersWithMissingFilters", PER_KIND);
        m.put("exportersIntoFullChests", PER_KIND);
        m.put("importersOnEmptyChests", PER_KIND);
        m.put("autocraftersWaitingForFullChest", CRAFTERS);
        m.put("tasksStarted", tasksStarted);
        m.put("speedUpgradesPerNode", 4);
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
            if (be instanceof AbstractDiskContainerBlockEntity<?> drive) drive.getDiskInventory().clearContent();
            else if (be instanceof Container c) c.clearContent();
            level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
        }
        placed.clear();
        nodes.clear();
        final StorageRepository repo = RefinedStorageApi.INSTANCE.getStorageRepository(level);
        diskIds.forEach(repo::remove);
        diskIds.clear();
    }

    private interface UpgradeSlot {
        boolean add(ItemStack stack);
    }

    private void upgrade(UpgradeSlot slot, Item upgrade, int amount) {
        for (int i = 0; i < amount; i++) {
            if (!slot.add(new ItemStack(upgrade))) setupWarning = "upgrade not added";
        }
    }

    private static void fill(ServerLevel level, BlockPos pos, Item item) {
        if (level.getBlockEntity(pos) instanceof Container c) {
            for (int s = 0; s < c.getContainerSize(); s++) c.setItem(s, new ItemStack(item, item.getDefaultMaxStackSize()));
        }
    }

    private void place(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, 3);
        placed.add(pos);
    }

    private static BlockState directed(Block block, Direction facing) {
        return block.defaultBlockState().setValue(DefaultDirectionType.FACE_CLICKED.getProperty(), facing);
    }

    private void setExporterFilters(AbstractExporterBlockEntity exporter, List<ResourceKey> filters) {
        try {
            final Method m = AbstractExporterBlockEntity.class.getDeclaredMethod("setFilters", List.class);
            m.setAccessible(true);
            m.invoke(exporter, new ArrayList<>(filters));
        } catch (ReflectiveOperationException | RuntimeException e) {
            setupWarning = "exporter filter not set: " + e;
        }
    }

    private static Block block(String path) {
        final Block b = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("refinedstorage", path));
        if (b == Blocks.AIR) throw new IllegalStateException("missing block refinedstorage:" + path);
        return b;
    }

    private static Item item(String path) {
        final Item i = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("refinedstorage", path));
        if (i == Items.AIR) throw new IllegalStateException("missing item refinedstorage:" + path);
        return i;
    }
}
