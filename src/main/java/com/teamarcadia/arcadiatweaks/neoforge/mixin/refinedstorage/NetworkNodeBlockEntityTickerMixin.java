package com.teamarcadia.arcadiatweaks.neoforge.mixin.refinedstorage;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.refinedmods.refinedstorage.common.detector.DetectorBlockEntity;
import com.refinedmods.refinedstorage.common.support.network.AbstractBaseNetworkNodeContainerBlockEntity;
import com.refinedmods.refinedstorage.common.support.network.NetworkNodeBlockEntityTicker;
import com.teamarcadia.arcadiatweaks.common.config.ArcadiaConfig;
import com.teamarcadia.arcadiatweaks.neoforge.refinedstorage.ArcadiaActivenessDebounce;
import com.teamarcadia.arcadiatweaks.neoforge.refinedstorage.ArcadiaRefinedStorageNodeState;
import com.teamarcadia.arcadiatweaks.neoforge.support.ArcadiaIntervalGate;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * S1 - Coalesce RS2 activeness recalculation.
 *
 * In Refined Storage 2 every network-node block entity runs:
 *   updateActiveness(state, activenessProperty);
 *   doWork();
 * every server tick. Most expensive node work is already throttled by RS or
 * guarded by AbstractNetworkNode.isActive(), but updateActiveness still checks
 * level-loaded state, redstone neighbor power and network energy every tick.
 *
 * This Mixin leaves doWork cadence intact and only skips the activeness
 * recalculation between configured intervals. Each block entity stores its own
 * cooldown, so dense networks are not biased by stable chunk iteration order.
 *
 * updateActiveness applies a pending change through its own debounce counter,
 * which only moves on calls. Around each coalesced call the counter is moved by
 * the ticks the call stands for (see ArcadiaActivenessDebounce), so a change is
 * applied at most N-1 ticks away from plain RS instead of up to 20 * N ticks
 * late. Detectors are never coalesced.
 */
@Mixin(NetworkNodeBlockEntityTicker.class)
public abstract class NetworkNodeBlockEntityTickerMixin {

    @WrapOperation(method = "tick",
            at = @At(value = "INVOKE",
                    target = "Lcom/refinedmods/refinedstorage/common/support/network/AbstractBaseNetworkNodeContainerBlockEntity;updateActiveness(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/properties/BooleanProperty;)V"))
    private void arcadia$coalesceActivenessCheck(
            AbstractBaseNetworkNodeContainerBlockEntity<?> blockEntity,
            BlockState state,
            BooleanProperty activenessProperty,
            Operation<Void> original) {
        final int interval = arcadia$coalescingInterval(blockEntity);
        if (interval <= 1) {
            original.call(blockEntity, state, activenessProperty);
            return;
        }
        final ArcadiaRefinedStorageNodeState nodeState = (ArcadiaRefinedStorageNodeState) blockEntity;
        final ArcadiaIntervalGate.Decision decision = ArcadiaIntervalGate.next(
                nodeState.arcadia$getActivenessCheckCooldown(),
                interval
        );
        nodeState.arcadia$setActivenessCheckCooldown(decision.nextCooldown());
        if (!decision.shouldRun()) {
            return;
        }

        final Level level = blockEntity.getLevel();
        if (level == null || ArcadiaActivenessDebounce.legacyBehaviourForTests()) {
            original.call(blockEntity, state, activenessProperty);
            return;
        }
        final long now = level.getGameTime();
        final int windowTicks = ArcadiaActivenessDebounce.windowTicks(
                now, nodeState.arcadia$getLastActivenessCheckTick(), interval);
        nodeState.arcadia$setLastActivenessCheckTick(now);

        final int counterBefore = nodeState.arcadia$getActivenessChangeTicks();
        final int preCounter = ArcadiaActivenessDebounce.preCounter(counterBefore, windowTicks);
        nodeState.arcadia$setActivenessChangeTicks(preCounter);
        try {
            original.call(blockEntity, state, activenessProperty);
        } finally {
            nodeState.arcadia$setActivenessChangeTicks(ArcadiaActivenessDebounce.postCounter(
                    counterBefore, preCounter, nodeState.arcadia$getActivenessChangeTicks()));
        }
    }

    /** Coalescing interval for this block entity; 1 means the plain RS call every tick. */
    @Unique
    private static int arcadia$coalescingInterval(AbstractBaseNetworkNodeContainerBlockEntity<?> blockEntity) {
        if (blockEntity instanceof DetectorBlockEntity) {
            return 1;
        }
        if (!ArcadiaConfig.MODULE_REFINED_STORAGE_ENABLED.get()
                || !ArcadiaConfig.REFINED_STORAGE.activenessCheckCoalescing.get()) {
            return 1;
        }
        return Math.max(1, ArcadiaConfig.REFINED_STORAGE.activenessCheckInterval.get());
    }
}
