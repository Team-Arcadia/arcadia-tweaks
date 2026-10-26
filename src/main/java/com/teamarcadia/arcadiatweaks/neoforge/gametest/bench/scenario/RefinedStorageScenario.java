package com.teamarcadia.arcadiatweaks.neoforge.gametest.bench.scenario;

import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.network.impl.node.AbstractNetworkNode;
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.api.storage.Actor;
import com.refinedmods.refinedstorage.common.api.RefinedStorageApi;
import com.refinedmods.refinedstorage.common.api.storage.SerializableStorage;
import com.refinedmods.refinedstorage.common.api.storage.StorageContainerItem;
import com.refinedmods.refinedstorage.common.api.storage.StorageRepository;
import com.refinedmods.refinedstorage.common.exporter.AbstractExporterBlockEntity;
import com.refinedmods.refinedstorage.common.importer.AbstractImporterBlockEntity;
import com.refinedmods.refinedstorage.common.storage.AbstractDiskContainerBlockEntity;
import com.refinedmods.refinedstorage.common.support.direction.DefaultDirectionType;
import com.refinedmods.refinedstorage.common.support.network.AbstractBaseNetworkNodeContainerBlockEntity;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;
import com.teamarcadia.arcadiatweaks.common.config.ArcadiaConfig;
import com.teamarcadia.arcadiatweaks.neoforge.gametest.bench.BenchScenario;
import com.teamarcadia.arcadiatweaks.neoforge.gametest.bench.BenchSwitches;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Refined Storage 2: a creative-controller network with a long cable bus, two disk drives full of
 * 64k disks holding thousands of distinct resources, importer/exporter pairs that move cobblestone
 * in a loop (network to chest to network) every tick, and external storages on full chests.
 *
 * <p>Every Refined Storage switch of ArcadiaTweaks is switched (S1 to S4). After each phase the
 * Refined Storage saved data ({@code refinedstorage_storages}) is saved {@value #SAVE_REPEATS} times
 * the way an autosave does and timed ({@code extra.rsSave*}, see {@link RsSaveTimer}); the network
 * moves items every tick, so the loop disks changed between saves. The storage-heavy save is the
 * {@code refinedstorage_save} scenario.
 *
 * @author vyrriox
 */
public final class RefinedStorageScenario implements BenchScenario {

    private static final int PAIRS = 24;
    private static final int EXTERNAL = 12;
    private static final int PAIR_SPACING = 3;
    private static final int BUS_HEIGHT = 3;
    private static final int SAVE_REPEATS = 3;

    private final List<BlockPos> placed = new ArrayList<>();
    private final List<AbstractBaseNetworkNodeContainerBlockEntity<?>> nodes = new ArrayList<>();
    private final List<BlockPos> loopChests = new ArrayList<>();
    private int cables;
    private int disks;
    private int resources;
    private long storedItems;
    private int nodesOnNetwork;
    private String setupWarning = "";
    private BlockPos exporterProbe;
    private final List<PendingDisk> pendingDisks = new ArrayList<>();
    private final List<UUID> diskIds = new ArrayList<>();
    private int activeAtStart;
    private BlockPos importerProbe;

    @Override
    public String id() {
        return "refinedstorage";
    }

    @Override
    public String description() {
        return "Refined Storage 2: creative controller, cable bus, 2 disk drives of 64k disks, " + PAIRS
                + " exporter/importer pairs looping items every tick, " + EXTERNAL + " external storages; timed RS saved-data save per phase.";
    }

    @Override
    public List<BenchSwitches.Switch> switches() {
        return List.of(
                BenchSwitches.Switch.of("modules.refinedstorage", ArcadiaConfig.MODULE_REFINED_STORAGE_ENABLED),
                BenchSwitches.Switch.of("refinedstorage.activeness_check_coalescing_enabled", ArcadiaConfig.REFINED_STORAGE.activenessCheckCoalescing),
                BenchSwitches.Switch.of("refinedstorage.activeness_check_interval_ticks", ArcadiaConfig.REFINED_STORAGE.activenessCheckInterval),
                BenchSwitches.Switch.of("refinedstorage.single_component_lookup_enabled", ArcadiaConfig.REFINED_STORAGE.singleComponentLookup),
                BenchSwitches.Switch.of("refinedstorage.storage_save_cache_enabled", ArcadiaConfig.REFINED_STORAGE.storageSaveCache),
                BenchSwitches.Switch.of("refinedstorage.storage_save_cache_verify_interval", ArcadiaConfig.REFINED_STORAGE.storageSaveCacheVerifyInterval),
                BenchSwitches.Switch.of("refinedstorage.quiet_hot_debug_logs_enabled", ArcadiaConfig.REFINED_STORAGE.quietHotDebugLogs)
        );
    }

    @Override
    public int footprint() {
        return (PAIRS + EXTERNAL) * PAIR_SPACING / 2 + 16;
    }

    @Override
    public void build(ServerLevel level, BlockPos center) {
        final int length = (PAIRS + EXTERNAL + 2) * PAIR_SPACING + 8;
        final BlockPos origin = center.offset(-length / 2, 0, 0);
        final Block controller = block("creative_controller");
        final Block cable = block("cable");
        final Block diskDrive = block("disk_drive");
        final Block importer = block("importer");
        final Block exporter = block("exporter");
        final Block external = block("external_storage");
        final Item disk64k = item("64k_storage_disk");
        final Item speed = item("speed_upgrade");

        // Bus at y+1 along x, with extra cable columns on top (realistic cable count).
        place(level, origin.offset(0, 1, 0), controller.defaultBlockState());
        for (int x = 1; x < length; x++) {
            for (int h = 1; h <= BUS_HEIGHT; h++) {
                place(level, origin.offset(x, h, 0), cable.defaultBlockState());
                cables++;
            }
        }
        // Two disk drives under the bus (their front face, north by default, does not connect).
        final List<AbstractDiskContainerBlockEntity> drives = new ArrayList<>();
        for (int d = 0; d < 2; d++) {
            final BlockPos pos = origin.offset(2 + d * 2, 0, 0);
            place(level, pos, diskDrive.defaultBlockState());
            if (level.getBlockEntity(pos) instanceof AbstractDiskContainerBlockEntity drive) {
                drives.add(drive);
            }
        }
        // Exporter -> chest -> importer loops. The chest sits under the bus, the exporter north of
        // it (fed by a cable above), the importer south of it.
        int x = 8;
        for (int p = 0; p < PAIRS; p++, x += PAIR_SPACING) {
            final BlockPos chest = origin.offset(x, 0, 0);
            place(level, chest, Blocks.CHEST.defaultBlockState());
            loopChests.add(chest);
            place(level, origin.offset(x, 1, -1), cable.defaultBlockState());
            place(level, origin.offset(x, 1, 1), cable.defaultBlockState());
            cables += 2;
            final BlockPos exp = origin.offset(x, 0, -1);
            place(level, exp, directed(exporter, Direction.SOUTH));
            final BlockPos imp = origin.offset(x, 0, 1);
            place(level, imp, directed(importer, Direction.NORTH));
            if (level.getBlockEntity(exp) instanceof AbstractExporterBlockEntity e) {
                for (int u = 0; u < 4; u++) e.addUpgrade(new ItemStack(speed));
                setExporterFilter(e, List.of(ItemResource.ofItemStack(new ItemStack(Items.COBBLESTONE))));
            }
            if (level.getBlockEntity(imp) instanceof AbstractImporterBlockEntity i) {
                for (int u = 0; u < 4; u++) i.addUpgrade(new ItemStack(speed));
            }
        }
        // External storages on full chests.
        for (int e = 0; e < EXTERNAL; e++, x += PAIR_SPACING) {
            final BlockPos chest = origin.offset(x, 0, 0);
            place(level, chest, Blocks.CHEST.defaultBlockState());
            if (level.getBlockEntity(chest) instanceof Container c) {
                for (int s = 0; s < c.getContainerSize(); s++) {
                    final Item it = BuiltInRegistries.ITEM.byId(1 + (e * 27 + s) % (BuiltInRegistries.ITEM.size() - 1));
                    c.setItem(s, new ItemStack(it, it.getDefaultMaxStackSize()));
                }
            }
            place(level, origin.offset(x, 1, -1), cable.defaultBlockState());
            cables++;
            place(level, origin.offset(x, 0, -1), directed(external, Direction.SOUTH));
        }
        // Two probes that prove items really move: an exporter alone into an empty chest and an
        // importer alone on a chest refilled before each phase (see beforePhase / afterPhase).
        exporterProbe = origin.offset(x, 0, 0);
        place(level, exporterProbe, Blocks.CHEST.defaultBlockState());
        place(level, origin.offset(x, 1, -1), cable.defaultBlockState());
        place(level, origin.offset(x, 0, -1), directed(exporter, Direction.SOUTH));
        if (level.getBlockEntity(origin.offset(x, 0, -1)) instanceof AbstractExporterBlockEntity e) {
            for (int u = 0; u < 4; u++) e.addUpgrade(new ItemStack(speed));
            setExporterFilter(e, List.of(ItemResource.ofItemStack(new ItemStack(Items.COBBLESTONE))));
        }
        x += PAIR_SPACING;
        importerProbe = origin.offset(x, 0, 0);
        place(level, importerProbe, Blocks.CHEST.defaultBlockState());
        place(level, origin.offset(x, 1, 1), cable.defaultBlockState());
        place(level, origin.offset(x, 0, 1), directed(importer, Direction.NORTH));
        if (level.getBlockEntity(origin.offset(x, 0, 1)) instanceof AbstractImporterBlockEntity i) {
            for (int u = 0; u < 4; u++) i.addUpgrade(new ItemStack(speed));
        }
        cables += 2;

        // Disks: created through the item's own init path (a player inventory tick), then filled
        // with many distinct resources so the saved data is the size of a real base.
        final FakePlayer player = FakePlayerFactory.getMinecraft(level);
        final StorageRepository repo = RefinedStorageApi.INSTANCE.getStorageRepository(level);
        final List<ItemStack> variety = varietyStacks(level);
        resources = variety.size();
        final List<SerializableStorage> storages = new ArrayList<>();
        for (AbstractDiskContainerBlockEntity drive : drives) {
            final int slots = drive.getDiskInventory().getContainerSize();
            for (int s = 0; s < slots; s++) {
                final ItemStack diskStack = new ItemStack(disk64k);
                diskStack.getItem().inventoryTick(diskStack, level, player, 0, false);
                if (diskStack.getItem() instanceof StorageContainerItem sci) {
                    sci.resolve(repo, diskStack).ifPresent(storages::add);
                }
                final UUID id = diskStack.get(com.refinedmods.refinedstorage.common.content.DataComponents.INSTANCE.getStorageReference());
                if (id != null) diskIds.add(id);
                // Inserted once the drive is on the network (see ready()): a disk put into a drive
                // that has no network yet is ignored by Refined Storage until the next disk change.
                pendingDisks.add(new PendingDisk(drive.getBlockPos(), s, diskStack));
                disks++;
            }
        }
        if (storages.isEmpty()) {
            throw new IllegalStateException("no Refined Storage disk storage could be created");
        }
        // Plenty of cobblestone for the loops, then every resource once per disk round robin.
        for (SerializableStorage st : storages) {
            storedItems += st.insert(ItemResource.ofItemStack(new ItemStack(Items.COBBLESTONE)), 4_000, Action.EXECUTE, Actor.EMPTY);
        }
        for (int i = 0; i < variety.size(); i++) {
            final SerializableStorage st = storages.get(i % storages.size());
            storedItems += st.insert(ItemResource.ofItemStack(variety.get(i)), 1 + (i % 50), Action.EXECUTE, Actor.EMPTY);
        }
        repo.markAsChanged();
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
        // nodes.get(0) is the controller: every node must be on the controller's network.
        final var network = nodes.isEmpty() ? null : nodes.get(0).getNetworkForItem();
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
        // Wait until every node is active and the disks are part of the network storage, so both
        // phases start from the same working network.
        int active = 0;
        for (AbstractBaseNetworkNodeContainerBlockEntity<?> n : nodes) {
            if (isNodeActive(n)) active++;
        }
        activeAtStart = active;
        final long stored = network == null ? 0 : network.getComponent(StorageNetworkComponent.class).getStored();
        return (active == nodes.size() && stored >= storedItems) || ticksSinceBuild > 1500;
    }

    private record PendingDisk(BlockPos drive, int slot, ItemStack stack) {}

    @Override
    public Map<String, Double> afterPhase(ServerLevel level, boolean patchesOn) {
        final Map<String, Double> out = new LinkedHashMap<>();
        final StorageRepository repo = RefinedStorageApi.INSTANCE.getStorageRepository(level);
        out.putAll(RsSaveTimer.time(level, "rsSave", SAVE_REPEATS, s -> repo.markAsChanged()));
        int loopItems = 0;
        for (BlockPos p : loopChests) {
            if (level.getBlockEntity(p) instanceof Container c) {
                for (int s = 0; s < c.getContainerSize(); s++) loopItems += c.getItem(s).getCount();
            }
        }
        out.put("itemsInLoopChests", (double) loopItems);
        int active = 0;
        final Map<String, int[]> byType = new java.util.TreeMap<>();
        for (AbstractBaseNetworkNodeContainerBlockEntity<?> n : nodes) {
            final boolean a = isNodeActive(n);
            if (a) active++;
            final int[] c = byType.computeIfAbsent(n.getClass().getSimpleName(), k -> new int[2]);
            c[0] += a ? 1 : 0;
            c[1]++;
        }
        final StringBuilder sb = new StringBuilder();
        byType.forEach((k, v) -> sb.append(k).append('=').append(v[0]).append('/').append(v[1]).append(' '));
        com.teamarcadia.arcadiatweaks.ArcadiaTweaks.LOGGER.info("[bench] RS active nodes by type: {}", sb);
        out.put("activeNodes", (double) active);
        final var network = nodes.isEmpty() ? null : nodes.get(0).getNetworkForItem();
        if (network != null) {
            final StorageNetworkComponent storage = network.getComponent(StorageNetworkComponent.class);
            out.put("networkStoredItems", (double) storage.getStored());
            out.put("networkResources", (double) storage.getAll().size());
        }
        out.put("exporterProbeItemsExported", (double) countItems(level, exporterProbe));
        out.put("importerProbeItemsImported", (double) (PROBE_FILL - countItems(level, importerProbe)));
        return out;
    }

    private static final int PROBE_FILL = 27 * 64;

    @Override
    public void beforePhase(ServerLevel level, boolean patchesOn) {
        if (level.getBlockEntity(exporterProbe) instanceof Container c) {
            c.clearContent();
        }
        if (level.getBlockEntity(importerProbe) instanceof Container c) {
            for (int s = 0; s < c.getContainerSize(); s++) c.setItem(s, new ItemStack(Items.SAND, 64));
        }
    }

    private static boolean isNodeActive(AbstractBaseNetworkNodeContainerBlockEntity<?> be) {
        try {
            for (Class<?> c = be.getClass(); c != null; c = c.getSuperclass()) {
                for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                    if (f.getName().equals("mainNetworkNode")) {
                        f.setAccessible(true);
                        return f.get(be) instanceof AbstractNetworkNode node && node.isActive();
                    }
                }
            }
            return false;
        } catch (ReflectiveOperationException | RuntimeException e) {
            com.teamarcadia.arcadiatweaks.ArcadiaTweaks.LOGGER.warn("[bench] cannot read RS node activeness", e);
            return false;
        }
    }

    private static int countItems(ServerLevel level, BlockPos pos) {
        int n = 0;
        if (pos != null && level.getBlockEntity(pos) instanceof Container c) {
            for (int s = 0; s < c.getContainerSize(); s++) n += c.getItem(s).getCount();
        }
        return n;
    }

    @Override
    public Map<String, Object> size() {
        final Map<String, Object> m = new LinkedHashMap<>();
        m.put("controller", "creative_controller");
        m.put("cables", cables);
        m.put("diskDrives", 2);
        m.put("disks64k", disks);
        m.put("distinctResources", resources + 1);
        m.put("storedItems", storedItems);
        m.put("exporterImporterPairs", PAIRS);
        m.put("speedUpgradesPerNode", 4);
        m.put("externalStorages", EXTERNAL);
        m.put("networkNodes", nodes.size());
        m.put("nodesOnNetwork", nodesOnNetwork);
        m.put("activeNodesAtStart", activeAtStart);
        if (!setupWarning.isEmpty()) m.put("warning", setupWarning);
        return m;
    }

    @Override
    public void teardown(ServerLevel level) {
        for (int i = placed.size() - 1; i >= 0; i--) {
            final BlockPos p = placed.get(i);
            final BlockEntity be = level.getBlockEntity(p);
            if (be instanceof AbstractDiskContainerBlockEntity drive) {
                drive.getDiskInventory().clearContent();
            } else if (be instanceof Container c) {
                c.clearContent();
            }
            level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
        }
        placed.clear();
        nodes.clear();
        // Drop the disk storages from the saved data so repeated runs start from the same size.
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

    /** The exporter filter setter is package-private; the GUI path needs a player menu. */
    private void setExporterFilter(AbstractExporterBlockEntity exporter, List<ResourceKey> filters) {
        try {
            final Method m = AbstractExporterBlockEntity.class.getDeclaredMethod("setFilters", List.class);
            m.setAccessible(true);
            m.invoke(exporter, filters);
        } catch (ReflectiveOperationException | RuntimeException e) {
            setupWarning = "exporter filter not set: " + e;
        }
    }

    static List<ItemStack> varietyStacks(ServerLevel level) {
        final List<ItemStack> out = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR || item == Items.COBBLESTONE) continue;
            out.add(new ItemStack(item));
        }
        // Distinct component variants, as a real base holds: enchanted books and potions.
        level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).holders().forEach(h -> {
            final Holder<Enchantment> ench = h;
            for (int lvl = 1; lvl <= ench.value().getMaxLevel(); lvl++) {
                out.add(EnchantedBookItem.createForEnchantment(new EnchantmentInstance(ench, lvl)));
            }
        });
        BuiltInRegistries.POTION.holders().forEach(potion -> {
            for (Item it : List.of(Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION, Items.TIPPED_ARROW)) {
                final ItemStack s = new ItemStack(it);
                s.set(DataComponents.POTION_CONTENTS, new PotionContents(potion));
                out.add(s);
            }
        });
        return out;
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
