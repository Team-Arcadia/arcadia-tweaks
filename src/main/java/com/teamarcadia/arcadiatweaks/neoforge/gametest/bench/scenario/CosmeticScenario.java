package com.teamarcadia.arcadiatweaks.neoforge.gametest.bench.scenario;

import com.teamarcadia.arcadiatweaks.ArcadiaTweaks;
import com.teamarcadia.arcadiatweaks.common.config.ArcadiaConfig;
import com.teamarcadia.arcadiatweaks.neoforge.cosmetic.CosmeticGate;
import com.teamarcadia.arcadiatweaks.neoforge.gametest.CosmeticPresenceGateGameTests;
import com.teamarcadia.arcadiatweaks.neoforge.gametest.CosmeticPresenceGateGameTests.Procedure;
import com.teamarcadia.arcadiatweaks.neoforge.gametest.bench.BenchScenario;
import com.teamarcadia.arcadiatweaks.neoforge.gametest.bench.BenchSwitches;
import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.AccessoriesContainer;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CosmeticWeapons / CosmeticArmours: {@value #PLAYERS} server players with a full survival inventory,
 * and every tick the NeoForge player tick events (Pre and Post) are fired for each of them, so every
 * {@code *ProdProcedure.onPlayerTick} of both mods runs as it does for a real player, together with
 * every other player tick listener of the dev runtime.
 *
 * <p>The players are real {@code ServerPlayer}s that are not placed in the world (see
 * {@link BenchPlayers}); only their tick events are fired, not the rest of the player tick (movement,
 * food, vanilla inventory tick). Every {@value #HOLDER_EVERY}th player holds one target item of a
 * gated procedure; in both mods holding any one item of a procedure's list completes its advancement,
 * so, as for a real player, that advancement is granted by the procedure itself on a tick fired at
 * build time, before any measurement. The other players hold nothing the procedures look for and have
 * none of the cosmetic advancements done.
 *
 * <p>OFF is the gate's own kill switch ({@code cosmetic.contains_presence_gate=false}): the Mixin stays
 * applied and passes every call to the original, as in production with the switch off. The module
 * switch is only read when the Mixins are applied at startup, so it is not switched here.
 *
 * <p>The {@code driven} series is the time spent inside the fired events.
 *
 * @author vyrriox
 */
public final class CosmeticScenario implements BenchScenario {

    private static final int PLAYERS = 20;
    private static final int HOLDER_EVERY = 5;

    private final List<ServerPlayer> players = new ArrayList<>();
    private final List<ResourceLocation> advancements = new ArrayList<>();
    private final List<String> heldTargets = new ArrayList<>();
    private int curioStacks;
    private int accessoryStacks;
    private int procedures;
    private int gatedProcedures;
    private int doneAtBuild;

    @Override
    public String id() {
        return "cosmetic";
    }

    @Override
    public String description() {
        return "CosmeticWeapons + CosmeticArmours: " + PLAYERS + " server players (36 mixed stacks, armour, offhand, 2 Curios and 2 Accessories items,"
                + " 1 in " + HOLDER_EVERY + " holding one target item, whose advancement is then done, the others with no cosmetic advancement done),"
                + " player tick events fired every tick;"
                + " OFF = presence gate kill switch.";
    }

    @Override
    public List<BenchSwitches.Switch> switches() {
        return List.of(BenchSwitches.Switch.of("cosmetic.contains_presence_gate", ArcadiaConfig.COSMETIC.containsPresenceGate));
    }

    @Override
    public int footprint() {
        return 8;
    }

    @Override
    public void build(ServerLevel level, BlockPos origin) {
        final List<Procedure> all = CosmeticPresenceGateGameTests.procedures();
        procedures = all.size();
        final Set<Item> chainItems = new HashSet<>();
        final Set<Item> singleItems = new HashSet<>();
        for (Procedure p : all) {
            chainItems.addAll(p.items());
            if (p.items().size() == 1) singleItems.addAll(p.items());
            advancements.add(p.advancement());
            if (p.gated()) gatedProcedures++;
        }
        // One target item per gated procedure, never one that is also the only item of another one.
        final List<Item> holdable = new ArrayList<>();
        final Set<Item> seen = new LinkedHashSet<>();
        for (Procedure p : all) {
            if (!p.gated() || p.items().size() < 2) continue;
            for (Item item : p.items()) {
                if (!singleItems.contains(item) && seen.add(item)) {
                    holdable.add(item);
                    break;
                }
            }
        }
        final Item[] small = {Items.EMERALD, Items.GOLD_NUGGET, Items.IRON_NUGGET, Items.AMETHYST_SHARD, Items.QUARTZ};
        for (int i = 0; i < PLAYERS; i++) {
            final ServerPlayer player = BenchPlayers.create(level, "bench_cosmetic_" + i,
                    Vec3.atBottomCenterOf(origin).add(i % 5, 1, i / 5));
            BenchPlayers.fillInventory(player, i, chainItems);
            for (int c = 0; c < 2; c++) {
                if (putCurio(player, new ItemStack(small[(i + c) % small.length]), c)) curioStacks++;
                if (putAccessory(player, new ItemStack(small[(i + c + 2) % small.length]), c)) accessoryStacks++;
            }
            if (i % HOLDER_EVERY == 0 && !holdable.isEmpty()) {
                final Item target = holdable.get((i / HOLDER_EVERY) % holdable.size());
                player.getInventory().items.set(0, new ItemStack(target));
                heldTargets.add(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(target).toString());
                // The procedure grants the advancement of the held item, as it would for a real player.
                EventHooks.firePlayerTickPre(player);
                EventHooks.firePlayerTickPost(player);
            }
            players.add(player);
        }
        doneAtBuild = countDone(level);
    }

    @Override
    public void tick(ServerLevel level) {
        for (ServerPlayer player : players) {
            EventHooks.firePlayerTickPre(player);
            EventHooks.firePlayerTickPost(player);
        }
    }

    @Override
    public Map<String, Double> afterPhase(ServerLevel level, boolean patchesOn) {
        final Map<String, Double> out = new LinkedHashMap<>();
        out.put("cosmeticAdvancementsDone", (double) countDone(level));
        out.put("gateSkippedLookupsTotal", (double) CosmeticGate.skippedLookups());
        return out;
    }

    private int countDone(ServerLevel level) {
        int done = 0;
        for (ServerPlayer player : players) {
            done += countDone(level, player);
        }
        return done;
    }

    private int countDone(ServerLevel level, ServerPlayer player) {
        int done = 0;
        for (ResourceLocation id : advancements) {
            final AdvancementHolder holder = level.getServer().getAdvancements().get(id);
            if (holder != null && player.getAdvancements().getOrStartProgress(holder).isDone()) done++;
        }
        return done;
    }

    @Override
    public Map<String, Object> size() {
        final Map<String, Object> m = new LinkedHashMap<>();
        m.put("players", players.size());
        m.put("playerKind", "unplaced ServerPlayer, player tick events fired by the bench");
        m.put("mainStacksPerPlayer", 36);
        m.put("armourPiecesPerPlayer", 4);
        m.put("offhandPerPlayer", 1);
        m.put("curioStacks", curioStacks);
        m.put("accessoryStacks", accessoryStacks);
        m.put("playersHoldingOneTargetItem", heldTargets.size());
        m.put("heldTargetItems", heldTargets);
        m.put("cosmeticAdvancementsDoneBeforeMeasuring", doneAtBuild);
        m.put("containsProcedures", procedures);
        m.put("gatedProcedures", gatedProcedures);
        m.put("gateAuditPassed", CosmeticGate.auditPassed());
        if (!CosmeticGate.auditPassed()) m.put("gateAuditFailure", CosmeticGate.auditFailure());
        return m;
    }

    @Override
    public void teardown(ServerLevel level) {
        final int done = countDone(level);
        if (done != doneAtBuild) {
            ArcadiaTweaks.LOGGER.warn("[bench] cosmetic: {} cosmetic advancements done at the end, {} before measuring", done, doneAtBuild);
        }
        players.forEach(BenchPlayers::remove);
        players.clear();
    }

    private static boolean putCurio(ServerPlayer player, ItemStack stack, int nth) {
        final ICuriosItemHandler handler = CuriosApi.getCuriosInventory(player).orElse(null);
        if (handler == null) return false;
        if (handler.getCurios().values().stream().noneMatch(stacks -> stacks.getSlots() > 0)) {
            handler.addTransientSlotModifier("ring", ResourceLocation.fromNamespaceAndPath(ArcadiaTweaks.MOD_ID, "bench"),
                    2, AttributeModifier.Operation.ADD_VALUE);
        }
        int index = 0;
        for (ICurioStacksHandler stacks : handler.getCurios().values()) {
            for (int i = 0; i < stacks.getSlots(); i++) {
                if (index++ == nth) {
                    stacks.getStacks().setStackInSlot(i, stack);
                    return true;
                }
            }
        }
        return false;
    }

    static boolean putAccessory(ServerPlayer player, ItemStack stack, int nth) {
        final AccessoriesCapability accessories = AccessoriesCapability.get(player);
        if (accessories == null) return false;
        int index = 0;
        for (AccessoriesContainer container : accessories.getContainers().values()) {
            final Container items = container.getAccessories();
            for (int i = 0; i < items.getContainerSize(); i++) {
                if (index++ == nth) {
                    items.setItem(i, stack);
                    return true;
                }
            }
        }
        return false;
    }
}
