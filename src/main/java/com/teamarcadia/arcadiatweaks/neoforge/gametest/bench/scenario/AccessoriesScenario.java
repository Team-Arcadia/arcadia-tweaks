package com.teamarcadia.arcadiatweaks.neoforge.gametest.bench.scenario;

import com.teamarcadia.arcadiatweaks.common.config.ArcadiaConfig;
import com.teamarcadia.arcadiatweaks.common.modules.accessories.AccessoriesTweaks;
import com.teamarcadia.arcadiatweaks.neoforge.gametest.bench.BenchScenario;
import com.teamarcadia.arcadiatweaks.neoforge.gametest.bench.BenchSwitches;
import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.AccessoriesContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Accessories: {@value #PLAYERS} server players with three accessories equipped and a full survival
 * inventory, {@value #STANDS} armor stands (Accessories gives them its 12 default slots in the dev
 * runtime, a stand-in for the modpack's slotted mobs), and {@value #CONTAINS_PER_TICK}
 * {@code Inventory.contains(new ItemStack(x))} calls per tick spread over the players, standing in
 * for the inventory checks other mods make every tick.
 *
 * <p>The armor stands are real entities and tick with the level, so Accessories' entity tick runs for
 * them from its own {@code EntityTickEvent.Pre} listener. The players are not placed in the world (see
 * {@link BenchPlayers}); the bench fires {@code EntityTickEvent.Pre/Post} for them, which is where
 * Accessories ticks a player, but not the rest of the player tick. The CosmeticWeapons procedures are
 * not run here (their {@code contains} load is the {@code cosmetic} scenario), so this scenario
 * isolates T-A and C1.
 *
 * @author vyrriox
 */
public final class AccessoriesScenario implements BenchScenario {

    private static final int PLAYERS = 20;
    private static final int STANDS = 200;
    private static final int CONTAINS_PER_TICK = 300;
    private static final int GRID = 20;

    /** What other mods look for. EMERALD and TORCH are held by some players (hits); the rest miss. */
    private static final Item[] LOOKED_FOR = {
            Items.DIAMOND, Items.TOTEM_OF_UNDYING, Items.ELYTRA, Items.CLOCK, Items.COMPASS, Items.ENDER_EYE,
            Items.NETHER_STAR, Items.EMERALD, Items.SPYGLASS, Items.TORCH, Items.RECOVERY_COMPASS, Items.HEART_OF_THE_SEA
    };

    private final List<ServerPlayer> players = new ArrayList<>();
    private final List<Entity> stands = new ArrayList<>();
    private final List<BlockPos> floor = new ArrayList<>();
    private final ItemStack[] targets = new ItemStack[LOOKED_FOR.length];
    private int equipped;
    private int playerSlots;
    private int standSlots;
    private long containsHits;
    private long containsCalls;
    private int cursor;

    @Override
    public String id() {
        return "accessories";
    }

    @Override
    public String description() {
        return "Accessories: " + PLAYERS + " server players with 3 accessories equipped (entity tick events fired by the bench), "
                + STANDS + " ticking armor stands with Accessories slots, " + CONTAINS_PER_TICK + " Inventory.contains calls per tick on the players.";
    }

    @Override
    public List<BenchSwitches.Switch> switches() {
        return List.of(
                BenchSwitches.Switch.of("modules.accessories", ArcadiaConfig.MODULE_ACCESSORIES_ENABLED),
                BenchSwitches.Switch.of("accessories.contains_fast_path", ArcadiaConfig.ACCESSORIES.containsFastPath),
                BenchSwitches.Switch.of("accessories.tick_allocation_trimmed", ArcadiaConfig.ACCESSORIES.tickAllocationTrimmed)
        );
    }

    @Override
    public int footprint() {
        return GRID + 4;
    }

    @Override
    public void build(ServerLevel level, BlockPos origin) {
        for (int i = 0; i < LOOKED_FOR.length; i++) {
            targets[i] = new ItemStack(LOOKED_FOR[i]);
        }
        // Stone floor, one stand per block of a 20 x 10 grid.
        final BlockPos corner = origin.offset(-GRID / 2, 0, -GRID / 2);
        for (int x = 0; x < GRID; x++) {
            for (int z = 0; z < GRID; z++) {
                final BlockPos p = corner.offset(x, 0, z);
                level.setBlock(p, Blocks.STONE.defaultBlockState(), 2);
                floor.add(p);
            }
        }
        for (int i = 0; i < STANDS; i++) {
            final ArmorStand stand = EntityType.ARMOR_STAND.create(level);
            if (stand == null) continue;
            stand.moveTo(corner.getX() + (i % GRID) + 0.5, corner.getY() + 1, corner.getZ() + (i / GRID) * 2 + 0.5, 0, 0);
            level.addFreshEntity(stand);
            stands.add(stand);
        }
        for (int i = 0; i < PLAYERS; i++) {
            final ServerPlayer player = BenchPlayers.create(level, "bench_accessories_" + i,
                    Vec3.atBottomCenterOf(origin).add(i % 5, 1, -4 - i / 5));
            BenchPlayers.fillInventory(player, i + 100, Set.of(LOOKED_FOR));
            if (i % 4 == 0) player.getInventory().items.set(9, new ItemStack(Items.EMERALD, 3));
            if (i % 2 == 0) player.getInventory().offhand.set(0, new ItemStack(Items.TORCH, 16));
            final AccessoriesCapability capability = AccessoriesCapability.get(player);
            if (capability != null) {
                for (AccessoriesContainer c : capability.getContainers().values()) {
                    playerSlots += c.getAccessories().getContainerSize();
                }
                final ItemStack[] equip = {new ItemStack(Items.AMETHYST_SHARD), new ItemStack(Items.GOLD_INGOT), new ItemStack(Items.IRON_NUGGET)};
                for (int k = 0; k < equip.length; k++) {
                    if (CosmeticScenario.putAccessory(player, equip[k], k * 3)) equipped++;
                }
            }
            players.add(player);
        }
        if (!stands.isEmpty()) {
            final AccessoriesCapability capability = AccessoriesCapability.get((ArmorStand) stands.get(0));
            if (capability != null) {
                for (AccessoriesContainer c : capability.getContainers().values()) {
                    standSlots += c.getAccessories().getContainerSize();
                }
            }
        }
    }

    @Override
    public void tick(ServerLevel level) {
        for (ServerPlayer player : players) {
            EventHooks.fireEntityTickPre(player);
            EventHooks.fireEntityTickPost(player);
        }
        for (int i = 0; i < CONTAINS_PER_TICK; i++) {
            final ServerPlayer player = players.get(i % players.size());
            if (player.getInventory().contains(targets[(cursor + i) % targets.length])) containsHits++;
            containsCalls++;
        }
        cursor++;
    }

    @Override
    public void beforePhase(ServerLevel level, boolean patchesOn) {
        containsHits = 0;
        containsCalls = 0;
    }

    @Override
    public Map<String, Double> afterPhase(ServerLevel level, boolean patchesOn) {
        final Map<String, Double> out = new LinkedHashMap<>();
        out.put("containsHitRate", containsCalls == 0 ? 0 : (double) containsHits / containsCalls);
        int alive = 0;
        for (Entity e : stands) {
            if (e.isAlive()) alive++;
        }
        out.put("standsAlive", (double) alive);
        return out;
    }

    @Override
    public Map<String, Object> size() {
        final Map<String, Object> m = new LinkedHashMap<>();
        m.put("players", players.size());
        m.put("playerKind", "unplaced ServerPlayer, entity tick events fired by the bench");
        m.put("accessorySlotsPerPlayer", players.isEmpty() ? 0 : playerSlots / players.size());
        m.put("accessoriesEquipped", equipped);
        m.put("armorStands", stands.size());
        m.put("accessorySlotsPerStand", standSlots);
        m.put("containsCallsPerTick", CONTAINS_PER_TICK);
        m.put("containsTargets", LOOKED_FOR.length);
        m.put("containsPatch", AccessoriesTweaks.containsPatchState());
        m.put("tickPatch", AccessoriesTweaks.tickPatchState());
        return m;
    }

    @Override
    public void teardown(ServerLevel level) {
        stands.forEach(Entity::discard);
        stands.clear();
        players.forEach(BenchPlayers::remove);
        players.clear();
        for (BlockPos p : floor) {
            level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
        }
        floor.clear();
    }
}
