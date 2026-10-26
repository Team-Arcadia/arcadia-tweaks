package com.teamarcadia.arcadiatweaks.neoforge.gametest;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.mojang.authlib.GameProfile;
import com.teamarcadia.arcadiatweaks.ArcadiaTweaks;
import com.teamarcadia.arcadiatweaks.common.config.ArcadiaConfig;
import com.teamarcadia.arcadiatweaks.common.modules.accessories.AccessoriesTweaks;
import com.teamarcadia.arcadiatweaks.neoforge.accessories.ArcadiaAccessoriesContains;
import com.teamarcadia.arcadiatweaks.neoforge.accessories.ArcadiaAccessoriesTick;
import com.teamarcadia.arcadiatweaks.neoforge.mixin.accessories.AccessoriesHolderImplAccessor;
import io.wispforest.accessories.api.AccessoriesAPI;
import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.AccessoriesContainer;
import io.wispforest.accessories.api.Accessory;
import io.wispforest.accessories.api.attributes.AccessoryAttributeBuilder;
import io.wispforest.accessories.api.events.AccessoryChangeCallback;
import io.wispforest.accessories.api.events.ContainersChangeCallback;
import io.wispforest.accessories.api.slot.SlotReference;
import io.wispforest.accessories.impl.AccessoriesContainerImpl;
import io.wispforest.accessories.impl.AccessoriesEventHandler;
import io.wispforest.accessories.impl.AccessoriesHolderImpl;
import io.wispforest.accessories.impl.ExpandedSimpleContainer;
import io.wispforest.accessories.neoforge.AccessoriesForge;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;

/**
 * Differential GameTests of the Accessories optimizations: every scenario runs once with the switch
 * off (stock Accessories) and once with it on, on fresh but identical entities, and the two logs of
 * everything observable must be equal: {@code contains} answers, accessory callbacks
 * ({@code tick}/{@code onEquip}/{@code onUnequip}), {@code AccessoryChangeCallback} and
 * {@code ContainersChangeCallback} events, sync packets sent to the player, attribute modifiers,
 * raw container state (items, previous items, sizes, pending updates), invalid stacks, dropped and
 * given items, and the holder attachment. Negative controls inject a fault in the optimized path and
 * check that the same comparison catches it.
 *
 * <p>Timed microbenchmarks run only with {@code -ParcadiaBench} (system property {@code arcadia.bench}).
 *
 * @author vyrriox
 */
@GameTestHolder(ArcadiaTweaks.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AccessoriesGameTests {

    private static final String TEMPLATE = "empty";
    private static final String BATCH = ArcadiaTweaks.MOD_ID + "_accessories";
    private static final ResourceLocation TEST_MODIFIER = ResourceLocation.fromNamespaceAndPath(ArcadiaTweaks.MOD_ID, "test_accessory");
    private static final ResourceLocation RESIZE_MODIFIER = ResourceLocation.fromNamespaceAndPath(ArcadiaTweaks.MOD_ID, "test_resize");

    /** Log of the scenario being recorded; only events about {@link Recorder#subject} are kept. */
    private static Recorder active;
    private static Runnable midTickAction;
    private static boolean listenersRegistered;

    private AccessoriesGameTests() {}

    // ---------------------------------------------------------------- C1: contains(ItemStack)

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 40)
    public static void containsPatchIsInstalled(GameTestHelper helper) {
        helper.assertValueEqual(AccessoriesTweaks.containsPatchState(), "applied", "C1 surgery state");
        helper.assertValueEqual(AccessoriesTweaks.tickPatchState(), "applied", "T-A mixin state");
        helper.succeed();
    }

    /** Same answers with C1 on and off, for every place an item can be; accessory-only stays false as upstream. */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 40)
    public static void containsAnswersAreIdentical(GameTestHelper helper) {
        setup();
        final List<String> off = withSwitch(ArcadiaConfig.ACCESSORIES.containsFastPath, false, () -> containsMatrix(helper));
        final List<String> on = withSwitch(ArcadiaConfig.ACCESSORIES.containsFastPath, true, () -> containsMatrix(helper));
        assertSameLog(helper, "contains matrix", off, on);
        helper.assertTrue(off.contains("answer diamond_in_inventory=true"), "Vanilla hit should be true: " + off);
        helper.assertTrue(off.contains("answer amethyst_in_accessory=false"), "Accessory-only item stays false upstream: " + off);
        helper.assertTrue(off.contains("answer empty=false"), "Empty target should be false: " + off);

        // Negative control: a rewrite that "fixes" the answer is caught by the same comparison.
        final List<String> broken = withFault(2, 0,
                () -> withSwitch(ArcadiaConfig.ACCESSORIES.containsFastPath, true, () -> containsMatrix(helper)));
        helper.assertTrue(firstDifference(off, broken) >= 0, "A rewrite that changes the answer must be detected");
        helper.succeed();
    }

    /** contains() applies a pending resize, a holder reload and creates the holder exactly as upstream does. */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 40)
    public static void containsSideEffectsAreIdentical(GameTestHelper helper) {
        setup();
        final List<String> off = withSwitch(ArcadiaConfig.ACCESSORIES.containsFastPath, false, () -> containsSideEffects(helper));
        final List<String> on = withSwitch(ArcadiaConfig.ACCESSORIES.containsFastPath, true, () -> containsSideEffects(helper));
        assertSameLog(helper, "contains side effects", off, on);
        helper.assertTrue(off.stream().anyMatch(line -> line.contains("ring:size=1")),
                "contains() should have applied the pending ring resize upstream: " + off);

        // Negative control: skipping the per-container update leaves the resize pending and is caught.
        final List<String> broken = withFault(1, 0,
                () -> withSwitch(ArcadiaConfig.ACCESSORIES.containsFastPath, true, () -> containsSideEffects(helper)));
        helper.assertTrue(firstDifference(off, broken) >= 0, "A rewrite that skips the container update must be detected");
        helper.succeed();
    }

    private static List<String> containsMatrix(GameTestHelper helper) {
        final ServerPlayer player = recordingPlayer(helper, "matrix");
        final Recorder recorder = new Recorder(player);
        active = recorder;
        try {
            final AccessoriesCapability capability = AccessoriesCapability.get(player);
            player.getInventory().setItem(0, new ItemStack(Items.DIAMOND));
            final ExpandedSimpleContainer ring = capability.getContainers().get("ring").getAccessories();
            ring.setItem(0, new ItemStack(Items.AMETHYST_SHARD));
            final ItemStack named = new ItemStack(Items.EMERALD);
            named.set(DataComponents.CUSTOM_NAME, Component.literal("Named"));
            ring.setItem(1, named);
            capability.getContainers().get("ring").getCosmeticAccessories().setItem(0, new ItemStack(Items.GOLD_INGOT));

            final ItemStack namedDiamond = new ItemStack(Items.DIAMOND);
            namedDiamond.set(DataComponents.CUSTOM_NAME, Component.literal("Other"));
            final Inventory inventory = player.getInventory();
            recorder.log("answer diamond_in_inventory=" + inventory.contains(new ItemStack(Items.DIAMOND)));
            recorder.log("answer diamond_other_components=" + inventory.contains(namedDiamond));
            recorder.log("answer amethyst_in_accessory=" + inventory.contains(new ItemStack(Items.AMETHYST_SHARD)));
            recorder.log("answer emerald_plain=" + inventory.contains(new ItemStack(Items.EMERALD)));
            recorder.log("answer emerald_named=" + inventory.contains(named.copy()));
            recorder.log("answer gold_in_cosmetic=" + inventory.contains(new ItemStack(Items.GOLD_INGOT)));
            recorder.log("answer empty=" + inventory.contains(ItemStack.EMPTY));
            recorder.log("answer stone_nowhere=" + inventory.contains(new ItemStack(Items.STONE)));
            recorder.log("answer tag_amethyst=" + inventory.contains(net.minecraft.tags.ItemTags.TRIM_MATERIALS));
            recorder.log("state " + snapshot(player));
            return recorder.lines;
        } finally {
            active = null;
        }
    }

    private static List<String> containsSideEffects(GameTestHelper helper) {
        final ServerPlayer player = recordingPlayer(helper, "effects");
        final Recorder recorder = new Recorder(player);
        active = recorder;
        try {
            final Inventory inventory = player.getInventory();
            // A player whose holder does not exist yet: contains() creates and validates it.
            recorder.log("holder before=" + player.hasData(AccessoriesForge.HOLDER_ATTACHMENT_TYPE));
            recorder.log("answer first=" + inventory.contains(new ItemStack(Items.STONE)));
            recorder.log("state " + snapshot(player));

            // A pending shrink of the ring container with an item in the slot that goes away.
            final AccessoriesCapability capability = AccessoriesCapability.get(player);
            capability.getContainers().get("ring").getAccessories().setItem(1, new ItemStack(Items.AMETHYST_SHARD, 2));
            capability.addTransientSlotModifiers(resize(-1));
            recorder.log("state before contains " + snapshot(player));
            recorder.log("answer resize=" + inventory.contains(new ItemStack(Items.STONE)));
            recorder.log("state after contains " + snapshot(player));

            // A holder flagged as freshly loaded from NBT: contains() resets it.
            final AccessoriesHolderImpl holder = (AccessoriesHolderImpl) player.getData(AccessoriesForge.HOLDER_ATTACHMENT_TYPE);
            ((AccessoriesHolderImplAccessor) (Object) holder).arcadia$setLoadedFromTag(true);
            recorder.log("answer reload=" + inventory.contains(new ItemStack(Items.STONE)));
            recorder.log("state after reload " + snapshot(player));
            capability.removeSlotModifiers(resize(-1));
            recorder.log("answer restore=" + inventory.contains(new ItemStack(Items.STONE)));
            recorder.log("state after restore " + snapshot(player));
            return recorder.lines;
        } finally {
            active = null;
        }
    }

    // ---------------------------------------------------------------- T-A: entity tick

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 40)
    public static void tickIsIdenticalForPlayers(GameTestHelper helper) {
        setup();
        tickDifferential(helper, "player", h -> recordingPlayer(h, "tick"));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 40)
    public static void tickIsIdenticalForSlottedMobs(GameTestHelper helper) {
        setup();
        // Armor stands carry the 12 default Accessories slots, like the slotted mobs of the modpack.
        tickDifferential(helper, "armor_stand", h -> spawnApart(h, EntityType.ARMOR_STAND));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 40)
    public static void tickIsIdenticalForEntitiesWithoutSlots(GameTestHelper helper) {
        setup();
        tickDifferential(helper, "zombie", h -> spawnApart(h, EntityType.ZOMBIE));
        tickDifferential(helper, "cow", h -> spawnApart(h, EntityType.COW));
        helper.succeed();
    }

    private static void tickDifferential(GameTestHelper helper, String name, Function<GameTestHelper, LivingEntity> factory) {
        final List<String> off = withSwitch(ArcadiaConfig.ACCESSORIES.tickAllocationTrimmed, false,
                () -> tickScenario(helper, factory));
        final List<String> on = withSwitch(ArcadiaConfig.ACCESSORIES.tickAllocationTrimmed, true,
                () -> tickScenario(helper, factory));
        assertSameLog(helper, "tick scenario " + name, off, on);
        helper.assertTrue(off.stream().anyMatch(line -> line.startsWith("snapshot")), "Scenario should have run: " + name);
        if (!name.equals("zombie") && !name.equals("cow")) {
            helper.assertTrue(off.stream().anyMatch(line -> line.contains("REPLACEMENT")), "Scenario should hit an equip change: " + off);
            helper.assertTrue(off.stream().anyMatch(line -> line.contains("MUTATION")), "Scenario should hit an in-place change: " + off);
            // Negative control: dropping cosmetic change detection in the copy is caught.
            final List<String> broken = withFault(0, 1, () -> withSwitch(ArcadiaConfig.ACCESSORIES.tickAllocationTrimmed, true,
                    () -> tickScenario(helper, factory)));
            helper.assertTrue(firstDifference(off, broken) >= 0, "A tick copy that skips cosmetics must be detected (" + name + ")");
        } else {
            helper.assertTrue(off.stream().anyMatch(line -> line.contains("holder=true")),
                    "The holder attachment should be created on an entity without slots: " + off);
        }
    }

    /** Scripted sequence covering every branch of the per-slot change detection. */
    private static List<String> tickScenario(GameTestHelper helper, Function<GameTestHelper, LivingEntity> factory) {
        final LivingEntity subject = factory.apply(helper);
        final Recorder recorder = new Recorder(subject);
        active = recorder;
        try {
            tick(recorder);
            tick(recorder);
            final AccessoriesCapability capability = AccessoriesCapability.get(subject);
            if (capability == null) {
                recorder.log("no capability");
                tick(recorder);
                return recorder.lines;
            }
            final AccessoriesContainer ringContainer = capability.getContainers().get("ring");
            final String otherName = capability.getContainers().keySet().stream().filter(n -> !n.equals("ring")).sorted().findFirst().orElseThrow();
            final ExpandedSimpleContainer ring = ringContainer.getAccessories();

            step(recorder, "equip test accessory", () -> ring.setItem(0, new ItemStack(Items.AMETHYST_SHARD)));
            tick(recorder);
            step(recorder, "replace with another item", () -> ring.setItem(0, new ItemStack(Items.EMERALD)));
            step(recorder, "same item, new components", () -> {
                final ItemStack named = new ItemStack(Items.EMERALD);
                named.set(DataComponents.CUSTOM_NAME, Component.literal("A"));
                ring.setItem(0, named);
            });
            step(recorder, "count change in place", () -> ring.getItem(0).grow(1));
            step(recorder, "component change in place", () -> ring.getItem(0).set(DataComponents.CUSTOM_NAME, Component.literal("B")));
            step(recorder, "removeItem flags the slot", () -> ring.removeItem(0, 1));
            step(recorder, "cosmetic change", () -> ringContainer.getCosmeticAccessories().setItem(0, new ItemStack(Items.GOLD_INGOT)));
            step(recorder, "test accessory with modifiers", () -> ring.setItem(0, new ItemStack(Items.AMETHYST_SHARD, 3)));
            step(recorder, "stack grows in place", () -> ring.getItem(0).grow(1));
            step(recorder, "removal", () -> ring.setItem(0, ItemStack.EMPTY));
            step(recorder, "cosmetic removal", () -> ringContainer.getCosmeticAccessories().setItem(0, ItemStack.EMPTY));
            step(recorder, "item in the slot a resize removes", () -> ring.setItem(1, new ItemStack(Items.AMETHYST_SHARD, 2)));
            step(recorder, "shrink ring by one", () -> capability.addTransientSlotModifiers(resize(-1)));
            tick(recorder);
            step(recorder, "grow ring back", () -> capability.removeSlotModifiers(resize(-1)));
            step(recorder, "dying: changes are not detected", () -> {
                subject.setHealth(0.0F);
                ringContainer.getAccessories().setItem(0, new ItemStack(Items.DIAMOND));
            });
            step(recorder, "alive again", () -> subject.setHealth(subject.getMaxHealth()));
            step(recorder, "API call from another listener mid-tick", () -> {
                midTickAction = () -> {
                    capability.getContainers().get(otherName).getAccessories().setItem(0, new ItemStack(Items.AMETHYST_SHARD));
                    capability.addTransientSlotModifiers(resize(1));
                };
                ringContainer.getAccessories().setItem(0, new ItemStack(Items.AMETHYST_SHARD));
            });
            tick(recorder);
            step(recorder, "clear everything", () -> {
                midTickAction = null;
                capability.removeSlotModifiers(resize(1));
                for (AccessoriesContainer container : capability.getContainers().values()) {
                    for (int i = 0; i < container.getAccessories().getContainerSize(); i++) {
                        container.getAccessories().setItem(i, ItemStack.EMPTY);
                        container.getCosmeticAccessories().setItem(i, ItemStack.EMPTY);
                    }
                }
            });
            tick(recorder);
            return recorder.lines;
        } finally {
            midTickAction = null;
            active = null;
            if (!(subject instanceof ServerPlayer)) {
                subject.discard();
            }
        }
    }

    private static void step(Recorder recorder, String name, Runnable action) {
        recorder.log("step " + name);
        action.run();
        tick(recorder);
    }

    private static void tick(Recorder recorder) {
        AccessoriesEventHandler.onLivingEntityTick(recorder.subject);
        recorder.log("snapshot " + snapshot(recorder.subject));
    }

    // ---------------------------------------------------------------- Microbenchmarks

    @GameTest(template = TEMPLATE, batch = BATCH + "_bench", timeoutTicks = 200)
    public static void benchContainsFastPath(GameTestHelper helper) {
        if (!Boolean.getBoolean("arcadia.bench")) {
            helper.succeed();
            return;
        }
        setup();
        final ServerPlayer player = recordingPlayer(helper, "bench");
        final AccessoriesCapability capability = AccessoriesCapability.get(player);
        capability.getContainers().get("ring").getAccessories().setItem(0, new ItemStack(Items.AMETHYST_SHARD));
        capability.getContainers().get("ring").getAccessories().setItem(1, new ItemStack(Items.EMERALD));
        capability.getContainers().get("hat").getAccessories().setItem(0, new ItemStack(Items.GOLD_INGOT));
        AccessoriesEventHandler.onLivingEntityTick(player);
        final Inventory inventory = player.getInventory();
        final ItemStack target = new ItemStack(Items.DIAMOND);
        final long[] sink = new long[1];
        final Runnable op = () -> sink[0] += inventory.contains(target) ? 1 : 0;
        final double[] off = new double[2];
        final double[] on = new double[2];
        bench(op, ArcadiaConfig.ACCESSORIES.containsFastPath, 20_000, 200_000, off, on);
        logBench("c1_contains_player_12_slots_3_filled", off, on, sink[0],
                "Inventory.contains(new diamond) on a FakePlayer with the dev runtime's default Accessories slots, 3 filled, empty vanilla inventory (vanilla miss, Accessories handler runs).");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH + "_bench", timeoutTicks = 200)
    public static void benchTickAllocationTrimmed(GameTestHelper helper) {
        if (!Boolean.getBoolean("arcadia.bench")) {
            helper.succeed();
            return;
        }
        setup();
        final List<LivingEntity> stands = new ArrayList<>();
        final List<LivingEntity> zombies = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            stands.add(helper.spawn(EntityType.ARMOR_STAND, new Vec3(1.5 + (i % 20), 2, 1.5 + (i / 20))));
            zombies.add(helper.spawn(EntityType.ZOMBIE, new Vec3(1.5 + (i % 20), 2, 30.5 + (i / 20))));
        }
        final ServerPlayer player = recordingPlayer(helper, "bench_tick");
        final AccessoriesCapability capability = AccessoriesCapability.get(player);
        capability.getContainers().get("ring").getAccessories().setItem(0, new ItemStack(Items.AMETHYST_SHARD));
        capability.getContainers().get("ring").getAccessories().setItem(1, new ItemStack(Items.EMERALD));
        capability.getContainers().get("hat").getAccessories().setItem(0, new ItemStack(Items.GOLD_INGOT));
        try {
            final double[] off = new double[2];
            final double[] on = new double[2];
            bench(() -> AccessoriesEventHandler.onLivingEntityTick(player),
                    ArcadiaConfig.ACCESSORIES.tickAllocationTrimmed, 20_000, 200_000, off, on);
            logBench("ta_tick_player_12_slots_3_filled", off, on, 0,
                    "AccessoriesEventHandler.onLivingEntityTick on the same FakePlayer, no change between ticks.");
            final int[] index = new int[1];
            bench(() -> AccessoriesEventHandler.onLivingEntityTick(stands.get(index[0]++ % 500)),
                    ArcadiaConfig.ACCESSORIES.tickAllocationTrimmed, 20_000, 200_000, off, on);
            logBench("ta_tick_armor_stand_12_empty_slots", off, on, 0,
                    "Per entity tick, round-robin over 500 armor stands with the default empty slots (stand-in for the pack's slotted mobs).");
            bench(() -> AccessoriesEventHandler.onLivingEntityTick(zombies.get(index[0]++ % 500)),
                    ArcadiaConfig.ACCESSORIES.tickAllocationTrimmed, 20_000, 200_000, off, on);
            logBench("ta_tick_zombie_no_slots", off, on, 0,
                    "Per entity tick over 500 zombies without Accessories slots in dev: T-A changes nothing there.");
        } finally {
            stands.forEach(Entity::discard);
            zombies.forEach(Entity::discard);
        }
        helper.succeed();
    }

    /** Alternates off/on three times, keeps the best ns/op and its allocation per op for each mode. */
    private static void bench(Runnable op, ModConfigSpec.BooleanValue toggle, int warmup, int iterations, double[] off, double[] on) {
        off[0] = on[0] = Double.MAX_VALUE;
        for (int round = 0; round < 3; round++) {
            for (boolean enabled : new boolean[]{false, true}) {
                final double[] result = withSwitch(toggle, enabled, () -> {
                    for (int i = 0; i < warmup; i++) {
                        op.run();
                    }
                    final long bytesBefore = allocatedBytes();
                    final long start = System.nanoTime();
                    for (int i = 0; i < iterations; i++) {
                        op.run();
                    }
                    final long elapsed = System.nanoTime() - start;
                    final long bytes = allocatedBytes() - bytesBefore;
                    return new double[]{(double) elapsed / iterations, bytesBefore < 0 ? -1 : (double) bytes / iterations};
                });
                final double[] target = enabled ? on : off;
                if (result[0] < target[0]) {
                    target[0] = result[0];
                    target[1] = result[1];
                }
            }
        }
    }

    private static void logBench(String name, double[] off, double[] on, long sink, String what) {
        MicroBenchResults.record("accessories", name, "ns/op", off[0], on[0], what
                + " GameTest-hosted, same JVM, warm-up 20k then 200k timed, off/on alternated 3 rounds, best per mode."
                + String.format(java.util.Locale.ROOT, " Allocation %.0f -> %.0f B/op.", off[1], on[1]));
        ArcadiaTweaks.LOGGER.info("[accessories-bench] name={} unit=ns/op before={} after={} allocBefore={} allocAfter={} sink={}",
                name, String.format("%.1f", off[0]), String.format("%.1f", on[0]),
                String.format("%.1f", off[1]), String.format("%.1f", on[1]), sink);
    }

    private static long allocatedBytes() {
        try {
            final java.lang.management.ThreadMXBean bean = ManagementFactory.getThreadMXBean();
            if (bean instanceof com.sun.management.ThreadMXBean sun) {
                return sun.getCurrentThreadAllocatedBytes();
            }
        } catch (Throwable ignored) {
            // Allocation counters are optional.
        }
        return -1;
    }

    // ---------------------------------------------------------------- Infrastructure

    private static void setup() {
        AccessoriesAPI.registerAccessory(Items.AMETHYST_SHARD, new TestAccessory());
        if (!listenersRegistered) {
            listenersRegistered = true;
            AccessoryChangeCallback.EVENT.register((previous, current, reference, change) -> {
                final Recorder recorder = active;
                if (recorder != null && reference.entity() == recorder.subject) {
                    recorder.log("change " + slot(reference) + " " + desc(previous) + " -> " + desc(current) + " " + change);
                }
            });
            ContainersChangeCallback.EVENT.register((entity, capability, updated) -> {
                final Recorder recorder = active;
                if (recorder != null && entity == recorder.subject && !updated.isEmpty()) {
                    final TreeMap<String, Boolean> sorted = new TreeMap<>();
                    updated.forEach((container, resized) -> sorted.put(container.getSlotName(), resized));
                    recorder.log("containers changed " + sorted);
                }
            });
        }
    }

    private static <T> T withSwitch(ModConfigSpec.BooleanValue toggle, boolean enabled, java.util.function.Supplier<T> body) {
        final boolean previous = toggle.get();
        final boolean previousModule = ArcadiaConfig.MODULE_ACCESSORIES_ENABLED.get();
        toggle.set(enabled);
        ArcadiaConfig.MODULE_ACCESSORIES_ENABLED.set(true);
        if (toggle.get() != enabled) {
            throw new IllegalStateException("Config switch did not take effect");
        }
        try {
            return body.get();
        } finally {
            toggle.set(previous);
            ArcadiaConfig.MODULE_ACCESSORIES_ENABLED.set(previousModule);
        }
    }

    private static <T> T withFault(int containsFault, int tickFault, java.util.function.Supplier<T> body) {
        final int previousContains = ArcadiaAccessoriesContains.faultForTest;
        final int previousTick = ArcadiaAccessoriesTick.faultForTest;
        ArcadiaAccessoriesContains.faultForTest = containsFault;
        ArcadiaAccessoriesTick.faultForTest = tickFault;
        try {
            return body.get();
        } finally {
            ArcadiaAccessoriesContains.faultForTest = previousContains;
            ArcadiaAccessoriesTick.faultForTest = previousTick;
        }
    }

    private static Multimap<String, AttributeModifier> resize(int amount) {
        final Multimap<String, AttributeModifier> modifiers = HashMultimap.create();
        modifiers.put("ring", new AttributeModifier(RESIZE_MODIFIER, amount, AttributeModifier.Operation.ADD_VALUE));
        return modifiers;
    }

    private static int spawnOffset;

    private static LivingEntity spawnApart(GameTestHelper helper, EntityType<? extends LivingEntity> type) {
        spawnOffset = (spawnOffset + 6) % 60;
        return helper.spawn(type, new Vec3(1.5 + spawnOffset, 2, 1.5));
    }

    /** A fake player whose outgoing packets are recorded instead of dropped. */
    private static ServerPlayer recordingPlayer(GameTestHelper helper, String name) {
        final ServerLevel level = helper.getLevel();
        final FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "arcadia_acc_" + name));
        player.connection = new RecordingListener(level.getServer(), player);
        return player;
    }

    private static void assertSameLog(GameTestHelper helper, String what, List<String> off, List<String> on) {
        final int index = firstDifference(off, on);
        if (index >= 0) {
            final String expected = index < off.size() ? off.get(index) : "<end>";
            final String actual = index < on.size() ? on.get(index) : "<end>";
            helper.fail(what + " differs at line " + index + "\n  off: " + expected + "\n  on:  " + actual);
        }
        ArcadiaTweaks.LOGGER.info("[accessories-test] {}: {} identical lines with the switch off and on", what, off.size());
        if (Boolean.getBoolean("arcadia.bench")) {
            for (String line : off) {
                ArcadiaTweaks.LOGGER.info("[accessories-test] {} | {}", what, line);
            }
        }
    }

    private static int firstDifference(List<String> a, List<String> b) {
        final int n = Math.min(a.size(), b.size());
        for (int i = 0; i < n; i++) {
            if (!a.get(i).equals(b.get(i))) {
                return i;
            }
        }
        return a.size() == b.size() ? -1 : n;
    }

    private static String snapshot(LivingEntity entity) {
        final StringBuilder out = new StringBuilder();
        final boolean hasHolder = entity.hasData(AccessoriesForge.HOLDER_ATTACHMENT_TYPE);
        out.append("holder=").append(hasHolder).append(" dead=").append(entity.isDeadOrDying());
        if (hasHolder) {
            final AccessoriesHolderImpl holder = (AccessoriesHolderImpl) entity.getData(AccessoriesForge.HOLDER_ATTACHMENT_TYPE);
            out.append(" loadedFromTag=").append(((AccessoriesHolderImplAccessor) (Object) holder).arcadia$isLoadedFromTag());
            out.append(" invalid=").append(holder.invalidStacks.stream().map(AccessoriesGameTests::desc).toList());
            final TreeMap<String, AccessoriesContainer> containers =
                    new TreeMap<>(((AccessoriesHolderImplAccessor) (Object) holder).arcadia$getAllSlotContainers());
            for (Map.Entry<String, AccessoriesContainer> entry : containers.entrySet()) {
                final AccessoriesContainer container = entry.getValue();
                final ExpandedSimpleContainer accessories = raw(container, "accessories");
                final ExpandedSimpleContainer cosmetics = raw(container, "cosmeticAccessories");
                out.append(' ').append(entry.getKey()).append(":size=").append(accessories.getContainerSize())
                        .append(",pending=").append(container.hasChanged())
                        .append(",capOk=").append(container.capability().entity() == entity);
                for (int i = 0; i < accessories.getContainerSize(); i++) {
                    final ItemStack item = accessories.getItem(i);
                    final ItemStack previous = accessories.getPreviousItem(i);
                    final ItemStack cosmetic = i < cosmetics.getContainerSize() ? cosmetics.getItem(i) : ItemStack.EMPTY;
                    final ItemStack cosmeticPrevious = i < cosmetics.getContainerSize() ? cosmetics.getPreviousItem(i) : ItemStack.EMPTY;
                    if (!item.isEmpty() || !previous.isEmpty() || !cosmetic.isEmpty() || !cosmeticPrevious.isEmpty()) {
                        out.append(",[").append(i).append(' ').append(desc(item)).append('|').append(desc(previous))
                                .append('|').append(desc(cosmetic)).append('|').append(desc(cosmeticPrevious)).append(']');
                    }
                }
            }
        }
        final AttributeInstance armor = entity.getAttribute(Attributes.ARMOR);
        if (armor != null) {
            out.append(" armor=").append(armor.getModifiers().stream()
                    .sorted(Comparator.comparing(m -> m.id().toString()))
                    .map(m -> m.id() + "=" + m.amount())
                    .toList());
        }
        if (entity instanceof ServerPlayer player) {
            final List<String> items = new ArrayList<>();
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                if (!player.getInventory().getItem(i).isEmpty()) {
                    items.add(i + ":" + desc(player.getInventory().getItem(i)));
                }
            }
            out.append(" inventory=").append(items);
        } else if (entity.level() != null && !entity.isRemoved()) {
            out.append(" drops=").append(entity.level().getEntitiesOfClass(ItemEntity.class, entity.getBoundingBox().inflate(1.5)).stream()
                    .map(item -> desc(item.getItem())).sorted().toList());
        }
        return out.toString();
    }

    private static ExpandedSimpleContainer raw(AccessoriesContainer container, String field) {
        try {
            final Field f = AccessoriesContainerImpl.class.getDeclaredField(field);
            f.setAccessible(true);
            return (ExpandedSimpleContainer) f.get(container);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot read " + field, e);
        }
    }

    private static String slot(SlotReference reference) {
        return reference.slotName() + "/" + reference.slot();
    }

    static String desc(ItemStack stack) {
        if (stack.isEmpty()) {
            return "empty";
        }
        return stack.getCount() + "x" + BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath()
                + (stack.getComponentsPatch().isEmpty() ? "" : stack.getComponentsPatch().toString());
    }

    private static String describePacket(Packet<?> packet) {
        if (packet instanceof ClientboundCustomPayloadPacket custom) {
            final Object payload = custom.payload();
            final Object inner = findRecord(payload, 0);
            return "payload " + describeRecord(inner != null ? inner : payload);
        }
        return "packet " + packet.getClass().getSimpleName();
    }

    /** Finds the Accessories message inside owo's payload wrapper. */
    private static Object findRecord(Object value, int depth) {
        if (value == null || depth > 3) {
            return null;
        }
        if (value.getClass().getName().startsWith("io.wispforest.accessories.networking")) {
            return value;
        }
        if (value instanceof Record record) {
            for (RecordComponent component : record.getClass().getRecordComponents()) {
                try {
                    final Object found = findRecord(component.getAccessor().invoke(record), depth + 1);
                    if (found != null) {
                        return found;
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Not readable: fall back to the class name.
                }
            }
        }
        return null;
    }

    private static String describeRecord(Object value) {
        if (value instanceof io.wispforest.accessories.networking.client.SyncContainerData sync) {
            return "SyncContainerData updated=" + new TreeMap<>(sync.updatedContainers()).keySet()
                    + " tags=" + new TreeMap<>(sync.updatedContainers()).values().stream().map(c -> c.compoundTag().toString()).toList()
                    + " dirty=" + describeStacks(sync.dirtyStacks())
                    + " cosmetic=" + describeStacks(sync.dirtyCosmeticStacks());
        }
        return value.getClass().getSimpleName();
    }

    private static String describeStacks(Map<String, ItemStack> stacks) {
        final TreeMap<String, String> sorted = new TreeMap<>();
        stacks.forEach((key, stack) -> sorted.put(key, desc(stack)));
        return sorted.toString();
    }

    private static final class Recorder {
        final LivingEntity subject;
        final List<String> lines = new ArrayList<>();

        Recorder(LivingEntity subject) {
            this.subject = subject;
        }

        void log(String line) {
            lines.add(line);
        }
    }

    /** Logs its callbacks for the recorded entity and adds an armor modifier scaled by the stack count. */
    private static final class TestAccessory implements Accessory {

        @Override
        public void tick(ItemStack stack, SlotReference reference) {
            record(reference, "tick " + desc(stack));
            final Runnable action = midTickAction;
            if (action != null && reference.slotName().equals("ring") && reference.slot() == 0 && active != null
                    && reference.entity() == active.subject) {
                midTickAction = null;
                action.run();
            }
        }

        @Override
        public void onEquip(ItemStack stack, SlotReference reference) {
            record(reference, "onEquip " + desc(stack));
        }

        @Override
        public void onUnequip(ItemStack stack, SlotReference reference) {
            record(reference, "onUnequip " + desc(stack));
        }

        @Override
        public void getDynamicModifiers(ItemStack stack, SlotReference reference, AccessoryAttributeBuilder builder) {
            builder.addExclusive(Attributes.ARMOR, TEST_MODIFIER, stack.getCount(), AttributeModifier.Operation.ADD_VALUE);
        }

        private static void record(SlotReference reference, String what) {
            final Recorder recorder = active;
            if (recorder != null && reference.entity() == recorder.subject) {
                recorder.log("accessory " + slot(reference) + " " + what);
            }
        }
    }

    private static final class RecordingConnection extends Connection {
        RecordingConnection() {
            super(PacketFlow.SERVERBOUND);
        }

        @Override
        public void setListenerForServerboundHandshake(PacketListener listener) {
        }
    }

    /** Like NeoForge's fake player connection, but keeps what the server sends to the player. */
    private static final class RecordingListener extends ServerGamePacketListenerImpl {
        private final ServerPlayer owner;

        RecordingListener(MinecraftServer server, ServerPlayer player) {
            super(server, new RecordingConnection(), player, CommonListenerCookie.createInitial(player.getGameProfile(), false));
            this.owner = player;
        }

        @Override
        public void tick() {
        }

        @Override
        public void send(Packet<?> packet) {
            capture(packet);
        }

        @Override
        public void send(Packet<?> packet, PacketSendListener listener) {
            capture(packet);
        }

        private void capture(Packet<?> packet) {
            final Recorder recorder = active;
            if (recorder != null && recorder.subject == owner) {
                recorder.log("sent " + describePacket(packet));
            }
        }
    }
}
