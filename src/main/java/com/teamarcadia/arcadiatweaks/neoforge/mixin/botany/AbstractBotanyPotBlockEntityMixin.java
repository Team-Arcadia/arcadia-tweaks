package com.teamarcadia.arcadiatweaks.neoforge.mixin.botany;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.teamarcadia.arcadiatweaks.common.config.ArcadiaConfig;
import net.darkhax.botanypots.common.impl.block.entity.AbstractBotanyPotBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A2 - downgrade markUpdated()'s sendBlockUpdated flags from 3 to 2.
 *
 * Vanilla BotanyPots calls
 * {@code level.sendBlockUpdated(pos, sameState, sameState, flags=3)} on every
 * inventory mutation. Flag 3 = UPDATE_CLIENTS | UPDATE_NEIGHBORS, which
 * propagates a neighbor-update wave that can retrigger lighting recalcs on
 * dense farms even though oldState == newState here. Flag 2 = UPDATE_CLIENTS
 * only, which is all we actually need: clients still pull a fresh getUpdateTag
 * to redraw the pot's contents, neighbors are not bothered.
 *
 * Comparator output is routed through a separate
 * {@code serverLevel.updateNeighbourForOutputSignal(pos, block)} call inside
 * {@code updateComparatorLevel} (see {@code BotanyPotBlockEntity}); A2 does
 * not touch that path, so redstone behavior is preserved exactly.
 *
 * Off by default ({@code a2_light_flag_downgrade_enabled = false}). Flip on
 * after profiling if Spark shows lighting recalcs originating from
 * {@code markUpdated} on a dense farm. The gain is workload-dependent and
 * will not show up in tickPot microbenches.
 */
@Mixin(AbstractBotanyPotBlockEntity.class)
public abstract class AbstractBotanyPotBlockEntityMixin {

    @WrapOperation(method = "markUpdated",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/world/level/Level;sendBlockUpdated(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;I)V"))
    private void arcadia$downgradeUpdateFlags(
            Level level, BlockPos pos, BlockState oldState, BlockState newState, int flags,
            Operation<Void> original) {
        if (ArcadiaConfig.BOTANY.enabled(ArcadiaConfig.BOTANY.a2LightFlagDowngrade)) {
            original.call(level, pos, oldState, newState, Block.UPDATE_CLIENTS);
        } else {
            original.call(level, pos, oldState, newState, flags);
        }
    }
}
