package com.teamarcadia.arcadiatweaks.neoforge.mixin.accessories;

import com.teamarcadia.arcadiatweaks.common.modules.accessories.AccessoriesTweaks;
import com.teamarcadia.arcadiatweaks.neoforge.accessories.ArcadiaAccessoriesTick;
import io.wispforest.accessories.impl.AccessoriesEventHandler;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * T-A: on the server, runs {@link ArcadiaAccessoriesTick#tick} instead of Accessories' per-entity tick
 * when the switch is on. Off, or on a client level, Accessories' own method runs.
 *
 * @author vyrriox
 */
@Mixin(value = AccessoriesEventHandler.class, remap = false)
public abstract class AccessoriesEventHandlerTickMixin {

    @Inject(method = "onLivingEntityTick(Lnet/minecraft/world/entity/LivingEntity;)V", at = @At("HEAD"), cancellable = true)
    private static void arcadia$allocationTrimmedTick(LivingEntity entity, CallbackInfo ci) {
        if (AccessoriesTweaks.tickAllocationTrimmedEnabled() && !entity.level().isClientSide()) {
            ArcadiaAccessoriesTick.tick(entity);
            ci.cancel();
        }
    }
}
