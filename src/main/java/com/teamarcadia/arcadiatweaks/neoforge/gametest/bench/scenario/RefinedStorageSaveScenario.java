package com.teamarcadia.arcadiatweaks.neoforge.gametest.bench.scenario;

import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.storage.Actor;
import com.refinedmods.refinedstorage.common.api.RefinedStorageApi;
import com.refinedmods.refinedstorage.common.api.storage.SerializableStorage;
import com.refinedmods.refinedstorage.common.api.storage.StorageContainerItem;
import com.refinedmods.refinedstorage.common.api.storage.StorageRepository;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;
import com.teamarcadia.arcadiatweaks.common.config.ArcadiaConfig;
import com.teamarcadia.arcadiatweaks.neoforge.gametest.bench.BenchScenario;
import com.teamarcadia.arcadiatweaks.neoforge.gametest.bench.BenchSwitches;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Refined Storage saved data of a storage-heavy world: {@value #DISKS} 64k item disks holding
 * {@value #TYPES_PER_DISK} item types each (items and their component variants: enchanted books,
 * potions), as in a large base. The disks live in the world's storage repository (the saved data an
 * autosave writes) without drives: the save encodes every disk of the repository whether it sits in a
 * drive or not, and nothing ticks here.
 *
 * <p>After every measured phase {@value #SAVES} saves are timed; before each, {@value #CHANGED_PER_SAVE}
 * random disks get an executed insert and extract (content unchanged, version moved), so a few disks
 * changed between saves as between two autosaves of a running base. See {@link RsSaveTimer} for what
 * is timed. The ON phase keeps the shipped verification interval: every Nth save of the session
 * re-encodes every cached disk and compares, and that save is part of the mean.
 *
 * @author vyrriox
 */
public final class RefinedStorageSaveScenario implements BenchScenario {

    private static final int DISKS = 200;
    private static final int TYPES_PER_DISK = 300;
    private static final int SAVES = 10;
    private static final int CHANGED_PER_SAVE = 3;

    private final List<SerializableStorage> storages = new ArrayList<>();
    private final List<UUID> diskIds = new ArrayList<>();
    private final List<ItemResource> variety = new ArrayList<>();
    private long storedItems;
    private int phase;

    @Override
    public String id() {
        return "refinedstorage_save";
    }

    @Override
    public String description() {
        return "Refined Storage saved data: " + DISKS + " 64k item disks x " + TYPES_PER_DISK + " item types; per phase " + SAVES
                + " timed saves, " + CHANGED_PER_SAVE + " disks changed before each (encode + NeoForge copy on the server thread, compression timed apart).";
    }

    @Override
    public List<BenchSwitches.Switch> switches() {
        return List.of(
                BenchSwitches.Switch.of("modules.refinedstorage", ArcadiaConfig.MODULE_REFINED_STORAGE_ENABLED),
                BenchSwitches.Switch.of("refinedstorage.storage_save_cache_enabled", ArcadiaConfig.REFINED_STORAGE.storageSaveCache),
                BenchSwitches.Switch.of("refinedstorage.storage_save_cache_verify_interval", ArcadiaConfig.REFINED_STORAGE.storageSaveCacheVerifyInterval)
        );
    }

    @Override
    public int footprint() {
        return 4;
    }

    @Override
    public void build(ServerLevel level, BlockPos origin) {
        final Item disk64k = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("refinedstorage", "64k_storage_disk"));
        if (disk64k == Items.AIR) throw new IllegalStateException("missing item refinedstorage:64k_storage_disk");
        for (ItemStack s : RefinedStorageScenario.varietyStacks(level)) {
            variety.add(ItemResource.ofItemStack(s));
        }
        final FakePlayer player = FakePlayerFactory.getMinecraft(level);
        final StorageRepository repo = RefinedStorageApi.INSTANCE.getStorageRepository(level);
        for (int d = 0; d < DISKS; d++) {
            final ItemStack diskStack = new ItemStack(disk64k);
            diskStack.getItem().inventoryTick(diskStack, level, player, 0, false);
            if (diskStack.getItem() instanceof StorageContainerItem sci) {
                sci.resolve(repo, diskStack).ifPresent(storages::add);
            }
            final UUID id = diskStack.get(com.refinedmods.refinedstorage.common.content.DataComponents.INSTANCE.getStorageReference());
            if (id != null) diskIds.add(id);
        }
        if (storages.size() != DISKS) {
            throw new IllegalStateException("only " + storages.size() + " of " + DISKS + " Refined Storage disks could be created");
        }
        // Each disk holds a window of the variety list starting at its own offset, so the item types
        // overlap between disks as in a real base.
        for (int d = 0; d < DISKS; d++) {
            final SerializableStorage st = storages.get(d);
            final int start = (d * 137) % variety.size();
            for (int t = 0; t < TYPES_PER_DISK; t++) {
                storedItems += st.insert(variety.get((start + t) % variety.size()), 1 + ((d + t) % 50), Action.EXECUTE, Actor.EMPTY);
            }
        }
        repo.markAsChanged();
    }

    @Override
    public Map<String, Double> afterPhase(ServerLevel level, boolean patchesOn) {
        final Random random = new Random(0xD15C + phase++);
        final StorageRepository repo = RefinedStorageApi.INSTANCE.getStorageRepository(level);
        return RsSaveTimer.time(level, "rsSave", SAVES, s -> {
            for (int c = 0; c < CHANGED_PER_SAVE; c++) {
                final SerializableStorage st = storages.get(random.nextInt(storages.size()));
                final ItemResource item = variety.get(random.nextInt(variety.size()));
                st.insert(item, 1, Action.EXECUTE, Actor.EMPTY);
                st.extract(item, 1, Action.EXECUTE, Actor.EMPTY);
            }
            repo.markAsChanged();
        });
    }

    @Override
    public Map<String, Object> size() {
        final Map<String, Object> m = new LinkedHashMap<>();
        m.put("disks64k", storages.size());
        m.put("itemTypesPerDisk", TYPES_PER_DISK);
        m.put("distinctResourcesAvailable", variety.size());
        m.put("diskEntries", (long) storages.size() * TYPES_PER_DISK);
        m.put("storedItems", storedItems);
        m.put("savesPerPhase", SAVES);
        m.put("disksChangedBeforeEachSave", CHANGED_PER_SAVE);
        return m;
    }

    @Override
    public void teardown(ServerLevel level) {
        final StorageRepository repo = RefinedStorageApi.INSTANCE.getStorageRepository(level);
        diskIds.forEach(repo::remove);
        diskIds.clear();
        storages.clear();
    }
}
