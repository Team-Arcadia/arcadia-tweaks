package com.teamarcadia.arcadiatweaks.neoforge.gametest;

import com.mojang.serialization.DataResult;
import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.core.component.ComponentMap;
import com.refinedmods.refinedstorage.api.network.impl.node.AbstractNetworkNode;
import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.api.storage.Actor;
import com.refinedmods.refinedstorage.api.storage.tracked.TrackedResource;
import com.refinedmods.refinedstorage.api.storage.tracked.TrackedStorage;
import com.refinedmods.refinedstorage.common.api.storage.PlayerActor;
import com.refinedmods.refinedstorage.common.api.storage.SerializableStorage;
import com.refinedmods.refinedstorage.common.storage.StorageRepositoryImpl;
import com.refinedmods.refinedstorage.common.storage.StorageTypes;
import com.refinedmods.refinedstorage.common.support.RedstoneMode;
import com.refinedmods.refinedstorage.common.support.network.AbstractBaseNetworkNodeContainerBlockEntity;
import com.refinedmods.refinedstorage.common.support.resource.FluidResource;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;
import com.teamarcadia.arcadiatweaks.ArcadiaTweaks;
import com.teamarcadia.arcadiatweaks.common.config.ArcadiaConfig;
import com.teamarcadia.arcadiatweaks.common.modules.refinedstorage.RefinedStorageRuntimeFlags;
import com.teamarcadia.arcadiatweaks.neoforge.refinedstorage.ArcadiaActivenessDebounce;
import com.teamarcadia.arcadiatweaks.neoforge.refinedstorage.ArcadiaCachingStorageCodec;
import com.teamarcadia.arcadiatweaks.neoforge.refinedstorage.ArcadiaStorageSaveCache;
import com.teamarcadia.arcadiatweaks.neoforge.refinedstorage.ArcadiaVersionedStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * In-game checks of the Refined Storage 2 patches S1 (activeness timing), S2 (component
 * lookup), S3 (storage save cache) and S4 (debug log lines), plus opt-in microbenchmarks
 * ({@code ./gradlew runGameTestServer -ParcadiaBench}).
 *
 * Tests that change config values for longer than one tick run in their own batch, so no
 * other test runs while the value is changed.
 *
 * @author vyrriox
 */
@GameTestHolder(ArcadiaTweaks.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RefinedStorageGameTests {

    private static final String AREA = "rs_area";
    private static final String EMPTY = "empty";
    private static final String BATCH = ArcadiaTweaks.MOD_ID + "_rs";
    private static final String ACTIVENESS_BATCH = ArcadiaTweaks.MOD_ID + "_rs_activeness";
    private static final String BENCH_BATCH = ArcadiaTweaks.MOD_ID + "_rs_bench";

    private static final BlockPos CONTROLLER = new BlockPos(2, 2, 3);
    private static final BlockPos IMPORTER = new BlockPos(3, 2, 3);
    private static final BlockPos REDSTONE = new BlockPos(4, 2, 3);
    private static final int RS_DEBOUNCE_SLACK = 19;

    private RefinedStorageGameTests() {}

    // ------------------------------------------------------------------ S1

    private enum ActivenessMode {
        PLAIN_RS, COALESCED, COALESCED_OLD
    }

    /** One run of the activeness timeline on fresh blocks. */
    private static final class ActivenessPhase {
        final ActivenessMode mode;
        final int[] toggles;
        final int length;
        final int[] delays;
        int awaiting = -1;
        boolean expectedActive;

        ActivenessPhase(ActivenessMode mode, int[] toggles, int length) {
            this.mode = mode;
            this.toggles = toggles;
            this.length = length;
            this.delays = new int[toggles.length];
            Arrays.fill(delays, -1);
        }
    }

    /**
     * An importer on a creative controller, redstone mode HIGH, with a redstone block placed and
     * removed on a fixed schedule. The tick each change is applied on (node active or not) is
     * recorded with plain RS (S1 off), with S1 and the debounce fix, and with S1 as it was before
     * the fix. With the fix every change is applied within 19 ticks of plain RS; before it, the
     * second change already came hundreds of ticks late.
     */
    @GameTest(template = AREA, batch = ACTIVENESS_BATCH, timeoutTicks = 2400)
    public static void rsActivenessChangesKeepPlainRsTiming(GameTestHelper helper) {
        final boolean originalModule = ArcadiaConfig.MODULE_REFINED_STORAGE_ENABLED.get();
        final boolean originalCoalescing = ArcadiaConfig.REFINED_STORAGE.activenessCheckCoalescing.get();
        final int originalInterval = ArcadiaConfig.REFINED_STORAGE.activenessCheckInterval.get();
        final Runnable restore = () -> {
            ArcadiaConfig.MODULE_REFINED_STORAGE_ENABLED.set(originalModule);
            ArcadiaConfig.REFINED_STORAGE.activenessCheckCoalescing.set(originalCoalescing);
            ArcadiaConfig.REFINED_STORAGE.activenessCheckInterval.set(originalInterval);
            ArcadiaActivenessDebounce.setLegacyBehaviourForTests(false);
        };

        final int[] schedule = {30, 95, 170, 263, 334, 421};
        final List<ActivenessPhase> phases = List.of(
                new ActivenessPhase(ActivenessMode.PLAIN_RS, schedule, 520),
                new ActivenessPhase(ActivenessMode.COALESCED, schedule, 520),
                new ActivenessPhase(ActivenessMode.COALESCED_OLD, new int[] {30, 95}, 600)
        );
        final int gap = 10;
        final int[] clock = {0};
        final int[] phaseIndex = {0};
        final int[] phaseStart = {0};

        helper.onEachTick(() -> {
            try {
                if (phaseIndex[0] >= phases.size()) {
                    return;
                }
                final ActivenessPhase phase = phases.get(phaseIndex[0]);
                final int p = clock[0] - phaseStart[0];
                clock[0]++;
                if (p == 0) {
                    configureActivenessMode(phase.mode);
                    helper.setBlock(CONTROLLER, rsBlock("creative_controller"));
                    helper.setBlock(IMPORTER, rsBlock("importer"));
                    final BlockEntity importer = helper.getBlockEntity(IMPORTER);
                    if (!(importer instanceof AbstractBaseNetworkNodeContainerBlockEntity<?> node)) {
                        throw new GameTestAssertException("No RS importer block entity at " + IMPORTER);
                    }
                    node.setRedstoneMode(RedstoneMode.HIGH);
                    return;
                }
                if (p < 0) {
                    return;
                }
                final AbstractNetworkNode node = importerNode(helper);
                if (p == 20 && node.getNetwork() == null) {
                    throw new GameTestAssertException("The importer did not join the controller network");
                }
                for (int i = 0; i < phase.toggles.length; i++) {
                    if (phase.toggles[i] == p) {
                        phase.expectedActive = (i % 2) == 0;
                        helper.setBlock(REDSTONE, phase.expectedActive ? Blocks.REDSTONE_BLOCK : Blocks.AIR);
                        phase.awaiting = i;
                    }
                }
                if (phase.awaiting >= 0 && node.isActive() == phase.expectedActive) {
                    phase.delays[phase.awaiting] = p - phase.toggles[phase.awaiting];
                    phase.awaiting = -1;
                }
                if (p == phase.length) {
                    if (phase.awaiting >= 0) {
                        throw new GameTestAssertException(phase.mode + ": change " + phase.awaiting + " never applied");
                    }
                    helper.setBlock(REDSTONE, Blocks.AIR);
                    helper.setBlock(IMPORTER, Blocks.AIR);
                    helper.setBlock(CONTROLLER, Blocks.AIR);
                    phaseIndex[0]++;
                    phaseStart[0] = clock[0] + gap;
                    if (phaseIndex[0] == phases.size()) {
                        restore.run();
                        assertActivenessTimings(helper, phases);
                        helper.succeed();
                    }
                }
            } catch (RuntimeException e) {
                restore.run();
                throw e;
            }
        });
    }

    private static void configureActivenessMode(ActivenessMode mode) {
        ArcadiaConfig.MODULE_REFINED_STORAGE_ENABLED.set(true);
        ArcadiaConfig.REFINED_STORAGE.activenessCheckInterval.set(20);
        ArcadiaConfig.REFINED_STORAGE.activenessCheckCoalescing.set(mode != ActivenessMode.PLAIN_RS);
        ArcadiaActivenessDebounce.setLegacyBehaviourForTests(mode == ActivenessMode.COALESCED_OLD);
    }

    private static void assertActivenessTimings(GameTestHelper helper, List<ActivenessPhase> phases) {
        final ActivenessPhase plain = phases.get(0);
        final ActivenessPhase fixed = phases.get(1);
        final ActivenessPhase old = phases.get(2);
        ArcadiaTweaks.LOGGER.info("[gametest] RS activeness delays (ticks after each change): plain RS {}, S1 fixed {}, S1 before the fix {}",
                Arrays.toString(plain.delays), Arrays.toString(fixed.delays), Arrays.toString(old.delays));
        for (int i = 0; i < plain.delays.length; i++) {
            // The delay is observed one tick after the block entity ticks, so plain RS reads 1..20.
            helper.assertTrue(plain.delays[i] >= 0 && plain.delays[i] <= RS_DEBOUNCE_SLACK + 1,
                    "plain RS applies change " + i + " within its 20-tick debounce, got " + plain.delays[i]);
            helper.assertTrue(Math.abs(fixed.delays[i] - plain.delays[i]) <= RS_DEBOUNCE_SLACK,
                    "S1 applies change " + i + " within 19 ticks of plain RS: plain " + plain.delays[i] + ", S1 " + fixed.delays[i]);
        }
        helper.assertTrue(old.delays[1] - plain.delays[1] > RS_DEBOUNCE_SLACK,
                "S1 before the fix should be measurably late on the second change: plain " + plain.delays[1] + ", old " + old.delays[1]);
    }

    private static AbstractNetworkNode importerNode(GameTestHelper helper) {
        final BlockEntity blockEntity = helper.getBlockEntity(IMPORTER);
        try {
            Class<?> type = blockEntity.getClass();
            while (type != null) {
                for (Field field : type.getDeclaredFields()) {
                    if (field.getName().equals("mainNetworkNode")) {
                        field.setAccessible(true);
                        return (AbstractNetworkNode) field.get(blockEntity);
                    }
                }
                type = type.getSuperclass();
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot read the RS importer node", e);
        }
        throw new IllegalStateException("RS block entity has no mainNetworkNode field");
    }

    private static Block rsBlock(String path) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("refinedstorage", path));
    }

    // ------------------------------------------------------------------ S2

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 20)
    public static void rsSingleComponentLookupMatchesPlainLookup(GameTestHelper helper) {
        final boolean original = ArcadiaConfig.REFINED_STORAGE.singleComponentLookup.get();
        final boolean originalModule = ArcadiaConfig.MODULE_REFINED_STORAGE_ENABLED.get();
        try {
            final Map<Class<?>, Object> backing = new HashMap<>();
            backing.put(String.class, "energy");
            backing.put(Integer.class, 7);
            backing.put(Long.class, null);
            final ComponentMap<Object> components = new ComponentMap<>(backing);

            ArcadiaConfig.MODULE_REFINED_STORAGE_ENABLED.set(true);
            final Map<Boolean, List<String>> outcomes = new LinkedHashMap<>();
            for (boolean single : new boolean[] {false, true}) {
                ArcadiaConfig.REFINED_STORAGE.singleComponentLookup.set(single);
                RefinedStorageRuntimeFlags.refresh();
                helper.assertValueEqual(RefinedStorageRuntimeFlags.singleComponentLookup(), single, "runtime flag follows config");
                final List<String> outcome = new ArrayList<>();
                for (Class<?> type : List.of(String.class, Integer.class, Long.class, Double.class)) {
                    try {
                        outcome.add(String.valueOf(components.getComponent(type)));
                    } catch (IllegalArgumentException e) {
                        outcome.add(e.getClass().getName() + ": " + e.getMessage());
                    }
                }
                outcomes.put(single, outcome);
            }
            helper.assertValueEqual(outcomes.get(true), outcomes.get(false), "single lookup gives the plain RS results");
            helper.assertTrue(outcomes.get(true).get(3).contains("Component not present: class java.lang.Double"),
                    "missing component message unchanged");
        } finally {
            ArcadiaConfig.REFINED_STORAGE.singleComponentLookup.set(original);
            ArcadiaConfig.MODULE_REFINED_STORAGE_ENABLED.set(originalModule);
            RefinedStorageRuntimeFlags.refresh();
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ S3

    /** Saves the repository with the cache on or off and returns the tag. */
    private static CompoundTag save(StorageRepositoryImpl repository, HolderLookup.Provider registries, boolean cache) {
        ArcadiaConfig.REFINED_STORAGE.storageSaveCache.set(cache);
        return repository.save(new CompoundTag(), registries);
    }

    private static byte[] bytes(CompoundTag tag) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (DataOutputStream data = new DataOutputStream(out)) {
            NbtIo.write(tag, data);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return out.toByteArray();
    }

    private record Disk(UUID id, SerializableStorage storage, boolean fluid) {}

    private static List<Disk> createDisks(StorageRepositoryImpl repository, Random random, int count) {
        final List<Disk> disks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            final boolean fluid = i % 4 == 3;
            final Long capacity = i % 3 == 0 ? null : 1_000_000L + random.nextInt(1_000_000);
            final SerializableStorage storage = (fluid ? StorageTypes.FLUID : StorageTypes.ITEM)
                    .create(capacity, repository::markAsChanged);
            final UUID id = new UUID(0x5A5AL, i);
            repository.set(id, storage);
            disks.add(new Disk(id, storage, fluid));
        }
        return disks;
    }

    private static List<ResourceKey> itemPool() {
        final List<ResourceKey> pool = new ArrayList<>();
        for (Item item : List.of(Items.STONE, Items.DIRT, Items.OAK_LOG, Items.IRON_INGOT, Items.DIAMOND,
                Items.REDSTONE, Items.COBBLESTONE, Items.GLASS, Items.SAND, Items.GOLD_INGOT)) {
            pool.add(new ItemResource(item));
        }
        pool.add(new ItemResource(Items.DIAMOND_SWORD, DataComponentPatch.builder()
                .set(DataComponents.CUSTOM_NAME, Component.literal("Named sword")).build()));
        pool.add(new ItemResource(Items.PAPER, DataComponentPatch.builder()
                .set(DataComponents.MAX_STACK_SIZE, 16).build()));
        return pool;
    }

    private static List<ResourceKey> fluidPool() {
        return List.of(new FluidResource(Fluids.WATER), new FluidResource(Fluids.LAVA));
    }

    private static Actor randomActor(Random random) {
        return switch (random.nextInt(3)) {
            case 0 -> Actor.EMPTY;
            case 1 -> new PlayerActor("Tester" + random.nextInt(3));
            default -> new PlayerActor("Builder");
        };
    }

    /** Random executed inserts and extracts (and some simulated ones) on a few random disks. */
    private static void mutateSome(List<Disk> disks, Random random, List<ResourceKey> items, List<ResourceKey> fluids, int maxDisks) {
        final int touched = 1 + random.nextInt(maxDisks);
        for (int n = 0; n < touched; n++) {
            final Disk disk = disks.get(random.nextInt(disks.size()));
            final List<ResourceKey> pool = disk.fluid() ? fluids : items;
            final ResourceKey resource = pool.get(random.nextInt(pool.size()));
            final long amount = 1 + random.nextInt(disk.fluid() ? 5000 : 200);
            final Action action = random.nextInt(5) == 0 ? Action.SIMULATE : Action.EXECUTE;
            if (random.nextInt(3) == 0) {
                disk.storage().extract(resource, amount, action, randomActor(random));
            } else {
                disk.storage().insert(resource, amount, action, randomActor(random));
            }
        }
    }

    /**
     * Several item and fluid disks, many rounds of random inserts and extracts on a few of them
     * (most disks untouched between saves): each save with the cache on is byte-identical to a
     * save with the cache off, the cache is actually used, and the saved data loads back to the
     * same content, tracked "changed by / at" included.
     */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 20)
    public static void rsStorageSaveCacheWritesIdenticalData(GameTestHelper helper) {
        final StorageCacheConfig config = StorageCacheConfig.capture();
        try {
            config.apply(true, 0);
            ArcadiaStorageSaveCache.resetForTests();
            final HolderLookup.Provider registries = helper.getLevel().registryAccess();
            final Random random = new Random(321L);
            final List<ResourceKey> items = itemPool();
            final List<ResourceKey> fluids = fluidPool();
            final StorageRepositoryImpl repository = new StorageRepositoryImpl();
            final List<Disk> disks = createDisks(repository, random, 8);

            helper.assertTrue(disks.get(0).storage() instanceof ArcadiaVersionedStorage, "item disks carry a content version");
            helper.assertTrue(disks.get(3).storage() instanceof ArcadiaVersionedStorage, "fluid disks carry a content version");
            helper.assertTrue(isCodecWrapped(repository), "the repository disk codec is wrapped");

            for (int i = 0; i < 40; i++) {
                mutateSome(disks, random, items, fluids, 3);
            }
            CompoundTag last = null;
            for (int round = 0; round < 60; round++) {
                if (round % 5 != 4) {
                    mutateSome(disks, random, items, fluids, 2);
                }
                final CompoundTag cached = save(repository, registries, true);
                final CompoundTag cachedAgain = save(repository, registries, true);
                final CompoundTag plain = save(repository, registries, false);
                helper.assertTrue(Arrays.equals(bytes(cached), bytes(plain)),
                        "round " + round + ": cache on and off write the same bytes");
                helper.assertTrue(Arrays.equals(bytes(cachedAgain), bytes(plain)),
                        "round " + round + ": second cached save writes the same bytes");
                last = cached;
            }
            helper.assertTrue(ArcadiaStorageSaveCache.hits() > 0, "the cache was used, hits=" + ArcadiaStorageSaveCache.hits());
            helper.assertTrue(ArcadiaStorageSaveCache.misses() > 0, "changed disks were encoded again");
            helper.assertFalse(ArcadiaStorageSaveCache.isDisabledForSession(), "no mismatch or failure");

            // Round trip: load what the cached save wrote and compare every disk.
            final StorageRepositoryImpl loaded = new StorageRepositoryImpl(last, registries);
            for (Disk disk : disks) {
                final SerializableStorage copy = loaded.get(disk.id())
                        .orElseThrow(() -> new GameTestAssertException("disk " + disk.id() + " missing after load"));
                assertSameContent(helper, disk.storage(), copy);
            }
            // The loaded repository keeps working with the cache: mutate and compare again.
            final List<Disk> loadedDisks = new ArrayList<>();
            for (Disk disk : disks) {
                loadedDisks.add(new Disk(disk.id(), loaded.get(disk.id()).orElseThrow(), disk.fluid()));
            }
            for (int round = 0; round < 20; round++) {
                mutateSome(loadedDisks, random, items, fluids, 2);
                helper.assertTrue(Arrays.equals(bytes(save(loaded, registries, true)), bytes(save(loaded, registries, false))),
                        "loaded round " + round + ": cache on and off write the same bytes");
            }
        } finally {
            config.restore();
            ArcadiaStorageSaveCache.resetForTests();
        }
        helper.succeed();
    }

    /**
     * Safety net: a cached encoding that no longer matches the disk (forced here) is caught by
     * the verification save, which writes the fresh data and turns the cache off.
     */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 20)
    public static void rsStorageSaveCacheVerificationCatchesStaleData(GameTestHelper helper) {
        final StorageCacheConfig config = StorageCacheConfig.capture();
        try {
            config.apply(true, 0);
            ArcadiaStorageSaveCache.resetForTests();
            final HolderLookup.Provider registries = helper.getLevel().registryAccess();
            final Random random = new Random(7L);
            final StorageRepositoryImpl repository = new StorageRepositoryImpl();
            final List<Disk> disks = createDisks(repository, random, 3);
            for (int i = 0; i < 20; i++) {
                mutateSome(disks, random, itemPool(), fluidPool(), 3);
            }
            save(repository, registries, true);
            final CompoundTag truth = save(repository, registries, false);

            final ArcadiaVersionedStorage versioned = (ArcadiaVersionedStorage) disks.get(1).storage();
            versioned.arcadia$setCachedEncoding(versioned.arcadia$getContentVersion(), DataResult.success(new CompoundTag()));
            helper.assertFalse(Arrays.equals(bytes(save(repository, registries, true)), bytes(truth)),
                    "the forced stale entry is used without verification");

            config.apply(true, 1);
            final CompoundTag verified = save(repository, registries, true);
            helper.assertTrue(Arrays.equals(bytes(verified), bytes(truth)), "the verification save writes the fresh data");
            helper.assertTrue(ArcadiaStorageSaveCache.isDisabledForSession(), "the mismatch turns the cache off");
            helper.assertTrue(Arrays.equals(bytes(save(repository, registries, true)), bytes(truth)),
                    "with the cache off every save is fresh");
        } finally {
            config.restore();
            ArcadiaStorageSaveCache.resetForTests();
        }
        helper.succeed();
    }

    private static void assertSameContent(GameTestHelper helper, SerializableStorage expected, SerializableStorage actual) {
        helper.assertValueEqual(actual.getStored(), expected.getStored(), "stored amount");
        helper.assertValueEqual(amounts(actual.getAll()), amounts(expected.getAll()), "resources and amounts");
        // RS saves "changed by / at" only for resources still stored, so compare those.
        for (ResourceKey resource : amounts(expected.getAll()).keySet()) {
            final String expectedTracked = tracked(expected, resource);
            final String actualTracked = tracked(actual, resource);
            helper.assertValueEqual(actualTracked, expectedTracked, "tracked change of " + resource);
        }
    }

    private static Map<ResourceKey, Long> amounts(Collection<ResourceAmount> all) {
        final Map<ResourceKey, Long> map = new LinkedHashMap<>();
        for (ResourceAmount amount : all) {
            map.put(amount.resource(), amount.amount());
        }
        return map;
    }

    private static String tracked(SerializableStorage storage, ResourceKey resource) {
        return ((TrackedStorage) storage).findTrackedResourceByActorType(resource, PlayerActor.class)
                .map((TrackedResource t) -> t.getSourceName() + "@" + t.getTime())
                .orElse("none");
    }

    private static boolean isCodecWrapped(StorageRepositoryImpl repository) {
        try {
            final Field codecField = StorageRepositoryImpl.class.getDeclaredField("codec");
            codecField.setAccessible(true);
            final Object mapCodec = codecField.get(repository);
            final Method elementCodec = mapCodec.getClass().getDeclaredMethod("elementCodec");
            elementCodec.setAccessible(true);
            return elementCodec.invoke(mapCodec) instanceof ArcadiaCachingStorageCodec<?>;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot inspect the RS storage repository codec", e);
        }
    }

    private record StorageCacheConfig(boolean module, boolean cache, int verify) {
        static StorageCacheConfig capture() {
            return new StorageCacheConfig(
                    ArcadiaConfig.MODULE_REFINED_STORAGE_ENABLED.get(),
                    ArcadiaConfig.REFINED_STORAGE.storageSaveCache.get(),
                    ArcadiaConfig.REFINED_STORAGE.storageSaveCacheVerifyInterval.get());
        }

        void apply(boolean cacheOn, int verifyInterval) {
            ArcadiaConfig.MODULE_REFINED_STORAGE_ENABLED.set(true);
            ArcadiaConfig.REFINED_STORAGE.storageSaveCache.set(cacheOn);
            ArcadiaConfig.REFINED_STORAGE.storageSaveCacheVerifyInterval.set(verifyInterval);
        }

        void restore() {
            ArcadiaConfig.MODULE_REFINED_STORAGE_ENABLED.set(module);
            ArcadiaConfig.REFINED_STORAGE.storageSaveCache.set(cache);
            ArcadiaConfig.REFINED_STORAGE.storageSaveCacheVerifyInterval.set(verify);
        }
    }

    // ------------------------------------------------------------------ S4

    /**
     * S4 only removes two log calls: the handlers are in place and the runtime flag follows the
     * config. The calls take plain field reads as arguments, so nothing else can change.
     */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 20)
    public static void rsQuietDebugLogsAreWiredToTheConfig(GameTestHelper helper) {
        helper.assertTrue(hasMergedHandler(
                "com.refinedmods.refinedstorage.common.storage.externalstorage.AbstractExternalStorageBlockEntity",
                "arcadia$keepChangedDebugLine"), "external storage log handler is merged");
        helper.assertTrue(hasMergedHandler(
                "com.refinedmods.refinedstorage.common.networking.NetworkTransmitterBlockEntity",
                "arcadia$keepRetryDebugLine"), "transmitter log handler is merged");
        final boolean original = ArcadiaConfig.REFINED_STORAGE.quietHotDebugLogs.get();
        final boolean originalModule = ArcadiaConfig.MODULE_REFINED_STORAGE_ENABLED.get();
        try {
            ArcadiaConfig.MODULE_REFINED_STORAGE_ENABLED.set(true);
            ArcadiaConfig.REFINED_STORAGE.quietHotDebugLogs.set(false);
            RefinedStorageRuntimeFlags.refresh();
            helper.assertFalse(RefinedStorageRuntimeFlags.quietHotDebugLogs(), "switch off keeps the log lines");
            ArcadiaConfig.REFINED_STORAGE.quietHotDebugLogs.set(true);
            RefinedStorageRuntimeFlags.refresh();
            helper.assertTrue(RefinedStorageRuntimeFlags.quietHotDebugLogs(), "switch on drops the log lines");
            ArcadiaConfig.MODULE_REFINED_STORAGE_ENABLED.set(false);
            RefinedStorageRuntimeFlags.refresh();
            helper.assertFalse(RefinedStorageRuntimeFlags.quietHotDebugLogs(), "module off keeps the log lines");
        } finally {
            ArcadiaConfig.REFINED_STORAGE.quietHotDebugLogs.set(original);
            ArcadiaConfig.MODULE_REFINED_STORAGE_ENABLED.set(originalModule);
            RefinedStorageRuntimeFlags.refresh();
        }
        helper.succeed();
    }

    private static boolean hasMergedHandler(String targetClassName, String handlerName) {
        try {
            for (Method method : Class.forName(targetClassName).getDeclaredMethods()) {
                if (method.getName().contains(handlerName)) {
                    return true;
                }
            }
            return false;
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Unable to inspect runtime class " + targetClassName, e);
        }
    }

    // ------------------------------------------------------------------ benchmarks

    /**
     * Microbenchmarks of S2 and S3, switch off then on, same JVM, warm-up first. Only run with
     * {@code -ParcadiaBench}; results go to the log and to arcadia-bench/refinedstorage.json in
     * the game directory.
     */
    @GameTest(template = EMPTY, batch = BENCH_BATCH, timeoutTicks = 20)
    public static void rsBenchmarks(GameTestHelper helper) {
        if (!Boolean.getBoolean("arcadia.bench")) {
            helper.succeed();
            return;
        }
        final List<String> entries = new ArrayList<>();
        entries.add(benchComponentLookup());
        entries.add(benchStorageSave(helper.getLevel().registryAccess()));
        final String json = "[\n  " + String.join(",\n  ", entries) + "\n]\n";
        ArcadiaTweaks.LOGGER.info("[bench] Refined Storage results:\n{}", json);
        try {
            final Path out = FMLPaths.GAMEDIR.get().resolve("arcadia-bench").resolve("refinedstorage.json");
            Files.createDirectories(out.getParent());
            Files.writeString(out, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot write the benchmark results", e);
        }
        helper.succeed();
    }

    private static String benchEntry(String name, String unit, double before, double after, String notes) {
        return String.format(Locale.ROOT,
                "{\"name\": \"%s\", \"unit\": \"%s\", \"before\": %.4f, \"after\": %.4f, \"notes\": \"%s\"}",
                name, unit, before, after, notes);
    }

    private static String benchComponentLookup() {
        final boolean original = ArcadiaConfig.REFINED_STORAGE.singleComponentLookup.get();
        final boolean originalModule = ArcadiaConfig.MODULE_REFINED_STORAGE_ENABLED.get();
        try {
            ArcadiaConfig.MODULE_REFINED_STORAGE_ENABLED.set(true);
            final Map<Class<?>, Object> backing = new HashMap<>();
            final List<Class<?>> keys = List.of(String.class, Integer.class, Long.class, Double.class,
                    Float.class, Short.class, Byte.class);
            for (Class<?> key : keys) {
                backing.put(key, key.getSimpleName());
            }
            final ComponentMap<Object> components = new ComponentMap<>(backing);
            final Class<?>[] order = keys.toArray(new Class<?>[0]);
            final int calls = 20_000_000;
            final double[] off = new double[7];
            final double[] on = new double[7];
            long sink = 0;
            for (int warm = 0; warm < 3; warm++) {
                for (boolean single : new boolean[] {false, true}) {
                    setSingleLookup(single);
                    sink += lookupLoop(components, order, calls / 4);
                }
            }
            for (int round = 0; round < off.length; round++) {
                setSingleLookup(false);
                long start = System.nanoTime();
                sink += lookupLoop(components, order, calls);
                off[round] = (System.nanoTime() - start) / (double) calls;
                setSingleLookup(true);
                start = System.nanoTime();
                sink += lookupLoop(components, order, calls);
                on[round] = (System.nanoTime() - start) / (double) calls;
            }
            return benchEntry("rs_component_lookup", "ns/call", median(off), median(on),
                    "ComponentMap.getComponent over a 7-entry map, 20M calls x 7 rounds, median; before = S2 off"
                            + " (plain containsKey+get behind the S2 flag read), after = S2 on; sink " + (sink & 1));
        } finally {
            ArcadiaConfig.REFINED_STORAGE.singleComponentLookup.set(original);
            ArcadiaConfig.MODULE_REFINED_STORAGE_ENABLED.set(originalModule);
            RefinedStorageRuntimeFlags.refresh();
        }
    }

    private static void setSingleLookup(boolean single) {
        ArcadiaConfig.REFINED_STORAGE.singleComponentLookup.set(single);
        RefinedStorageRuntimeFlags.refresh();
    }

    private static long lookupLoop(ComponentMap<Object> components, Class<?>[] order, int calls) {
        long sink = 0;
        for (int i = 0; i < calls; i++) {
            final Object value = components.getComponent(order[i % order.length]);
            sink += value.hashCode();
        }
        return sink;
    }

    private static String benchStorageSave(HolderLookup.Provider registries) {
        final StorageCacheConfig config = StorageCacheConfig.capture();
        try {
            config.apply(true, 0);
            ArcadiaStorageSaveCache.resetForTests();
            final Random random = new Random(99L);
            final List<ResourceKey> pool = new ArrayList<>();
            for (Item item : BuiltInRegistries.ITEM) {
                if (item != Items.AIR) {
                    pool.add(new ItemResource(item));
                }
                if (pool.size() == 500) {
                    break;
                }
            }
            final int diskCount = 200;
            final int dirtyPerSave = 10;
            final StorageRepositoryImpl repository = new StorageRepositoryImpl();
            final List<SerializableStorage> disks = new ArrayList<>();
            for (int i = 0; i < diskCount; i++) {
                final SerializableStorage disk = StorageTypes.ITEM.create(null, repository::markAsChanged);
                repository.set(new UUID(0xBE7CL, i), disk);
                for (ResourceKey resource : pool) {
                    disk.insert(resource, 1 + random.nextInt(10_000), Action.EXECUTE, Actor.EMPTY);
                }
                disks.add(disk);
            }
            final Runnable mutate = () -> {
                for (int n = 0; n < dirtyPerSave; n++) {
                    final SerializableStorage disk = disks.get(random.nextInt(disks.size()));
                    disk.insert(pool.get(random.nextInt(pool.size())), 1 + random.nextInt(64), Action.EXECUTE,
                            new PlayerActor("Bench"));
                }
            };
            for (int warm = 0; warm < 3; warm++) {
                mutate.run();
                save(repository, registries, false);
                save(repository, registries, true);
            }
            final double[] off = new double[9];
            final double[] on = new double[9];
            for (int round = 0; round < off.length; round++) {
                mutate.run();
                long start = System.nanoTime();
                save(repository, registries, false);
                off[round] = (System.nanoTime() - start) / 1_000_000.0;
                mutate.run();
                start = System.nanoTime();
                save(repository, registries, true);
                on[round] = (System.nanoTime() - start) / 1_000_000.0;
            }
            return benchEntry("rs_storage_save", "ms/save", median(off), median(on),
                    "StorageRepositoryImpl.save (RS codec encode only, not the NeoForge copy or the disk write),"
                            + " 200 item disks x 500 item types, 10 executed inserts on random disks before each save"
                            + " (<= 5% of disks dirty), 9 rounds median; before = S3 off, after = S3 on with"
                            + " verification off; hits " + ArcadiaStorageSaveCache.hits()
                            + ", misses " + ArcadiaStorageSaveCache.misses());
        } finally {
            config.restore();
            ArcadiaStorageSaveCache.resetForTests();
        }
    }

    private static double median(double[] values) {
        final double[] sorted = values.clone();
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }
}
