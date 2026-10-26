package com.teamarcadia.arcadiatweaks.neoforge.gametest.bench.scenario;

import com.teamarcadia.arcadiatweaks.common.config.ArcadiaConfig;
import com.teamarcadia.arcadiatweaks.common.modules.botany.BotanyConfig;
import com.teamarcadia.arcadiatweaks.neoforge.gametest.bench.BenchScenario;
import com.teamarcadia.arcadiatweaks.neoforge.gametest.bench.BenchSwitches;
import net.darkhax.botanypots.common.impl.block.entity.BotanyPotBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BotanyPots farm: a grid of pots with soil and crops. A third are hopper pots over a full chest
 * (the export always fails: the hopper back-off case), a third are hopper pots over an empty chest
 * (they harvest and export), a third are plain pots.
 *
 * @author vyrriox
 */
public final class BotanyPotsScenario implements BenchScenario {

    private static final int COLS = 36;
    private static final int ROWS = 30;
    private static final ResourceLocation PLAIN = ResourceLocation.fromNamespaceAndPath("botanypots", "terracotta_botany_pot");
    private static final ResourceLocation HOPPER = ResourceLocation.fromNamespaceAndPath("botanypots", "terracotta_hopper_botany_pot");

    private final List<BlockPos> placed = new ArrayList<>();
    private int hopperOverFull;
    private int hopperOverEmpty;
    private int plain;
    private int potsWithEntity;

    @Override
    public String id() {
        return "botanypots";
    }

    @Override
    public String description() {
        return "BotanyPots farm: " + (COLS * ROWS) + " pots with soil and crops; 1/3 hopper pots over full chests, "
                + "1/3 hopper pots over empty chests, 1/3 plain pots.";
    }

    @Override
    public List<BenchSwitches.Switch> switches() {
        final BotanyConfig b = ArcadiaConfig.BOTANY;
        return List.of(
                BenchSwitches.Switch.of("modules.botany", ArcadiaConfig.MODULE_BOTANY_ENABLED),
                BenchSwitches.Switch.of("botany.matches_cache_enabled", b.s1MatchesCache),
                BenchSwitches.Switch.of("botany.tick_coalescing_enabled", b.s2TickCoalescing),
                BenchSwitches.Switch.of("botany.tick_coalescing_n", b.s2CoalesceN),
                BenchSwitches.Switch.of("botany.hopper_backoff_enabled", b.s3HopperBackoff),
                BenchSwitches.Switch.of("botany.hopper_backoff_min_ticks", b.s3HopperBackoffMinTicks),
                BenchSwitches.Switch.of("botany.hopper_backoff_max_ticks", b.s3HopperBackoffMaxTicks),
                BenchSwitches.Switch.of("botany.required_growth_ticks_cache_enabled", b.a1RequiredGrowthTicksCache),
                BenchSwitches.Switch.of("botany.light_flag_downgrade_enabled", b.a2LightFlagDowngrade),
                BenchSwitches.Switch.of("botany.safety_revalidate_period_ticks", b.safetyRevalidatePeriod)
        );
    }

    @Override
    public int footprint() {
        return Math.max(COLS, ROWS) + 2;
    }

    @Override
    public void build(ServerLevel level, BlockPos origin) {
        final Block plainPot = BuiltInRegistries.BLOCK.get(PLAIN);
        final Block hopperPot = BuiltInRegistries.BLOCK.get(HOPPER);
        if (plainPot == Blocks.AIR || hopperPot == Blocks.AIR) {
            throw new IllegalStateException("BotanyPots blocks not found: " + PLAIN + ", " + HOPPER);
        }
        final ItemStack[][] crops = {
                {new ItemStack(Items.DIRT), new ItemStack(Items.WHEAT_SEEDS)},
                {new ItemStack(Items.FARMLAND), new ItemStack(Items.CARROT)},
                {new ItemStack(Items.DIRT), new ItemStack(Items.POTATO)},
                {new ItemStack(Items.FARMLAND), new ItemStack(Items.BEETROOT_SEEDS)},
        };
        int i = 0;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++, i++) {
                final BlockPos chestPos = origin.offset(c - COLS / 2, 0, r - ROWS / 2);
                final BlockPos potPos = chestPos.above();
                final int kind = i % 3;
                if (kind == 2) {
                    level.setBlock(potPos, plainPot.defaultBlockState(), 3);
                    plain++;
                } else {
                    level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3);
                    placed.add(chestPos);
                    if (kind == 0 && level.getBlockEntity(chestPos) instanceof Container chest) {
                        for (int s = 0; s < chest.getContainerSize(); s++) {
                            chest.setItem(s, new ItemStack(Items.COBBLESTONE, 64));
                        }
                        hopperOverFull++;
                    } else {
                        hopperOverEmpty++;
                    }
                    level.setBlock(potPos, hopperPot.defaultBlockState(), 3);
                }
                placed.add(potPos);
                final BlockEntity be = level.getBlockEntity(potPos);
                if (be instanceof BotanyPotBlockEntity pot) {
                    final ItemStack[] crop = crops[(i / 3) % crops.length];
                    pot.setSoilItem(crop[0].copy());
                    pot.setSeed(crop[1].copy());
                    potsWithEntity++;
                }
            }
        }
    }

    @Override
    public Map<String, Object> size() {
        final Map<String, Object> m = new LinkedHashMap<>();
        m.put("pots", COLS * ROWS);
        m.put("potsWithBlockEntity", potsWithEntity);
        m.put("hopperPotsOverFullChest", hopperOverFull);
        m.put("hopperPotsOverEmptyChest", hopperOverEmpty);
        m.put("plainPots", plain);
        m.put("crops", List.of("dirt+wheat", "farmland+carrot", "dirt+potato", "farmland+beetroot"));
        return m;
    }

    @Override
    public void teardown(ServerLevel level) {
        for (BlockPos p : placed) {
            if (level.getBlockEntity(p) instanceof Container c) {
                c.clearContent();
            }
            level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
        }
        placed.clear();
    }
}
