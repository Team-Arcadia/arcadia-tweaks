package com.teamarcadia.arcadiatweaks.neoforge.mixin;

import com.teamarcadia.arcadiatweaks.ArcadiaTweaks;
import com.teamarcadia.arcadiatweaks.common.config.ArcadiaConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Safety guard - runaway entity movement.
 *
 * Entity.move searches collisions along the whole requested path, block by block. An entity
 * whose velocity became absurd (NaN, infinite, or thousands of blocks per tick) turns that
 * search into a walk of millions of blocks inside one tick, and the server hangs until the
 * watchdog kills it. On Server1 (2026-10-17) Aether Sheepuffs resting on the ground had
 * reached -1284 and -10276 blocks per tick; one of them froze the server for five minutes.
 *
 * Before such a movement runs, the entity's velocity is reset to zero and the move is
 * skipped, so the entity simply stays where it is. Movements under the limit are untouched.
 *
 * @author vyrriox
 */
@Mixin(Entity.class)
public abstract class EntityMoveGuardMixin {

    @Unique
    private static long arcadia$lastLogMillis = 0L;

    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void arcadia$stopRunawayMovement(MoverType type, Vec3 movement, CallbackInfo ci) {
        final double limit = ArcadiaConfig.ENTITY_MOVE_GUARD_LIMIT.get();
        if (Math.abs(movement.x) <= limit && Math.abs(movement.y) <= limit && Math.abs(movement.z) <= limit) {
            return; // also false for NaN, which falls through to the guard below
        }
        if (!ArcadiaConfig.ENTITY_MOVE_GUARD_ENABLED.get()) {
            return;
        }
        final Entity self = (Entity) (Object) this;
        self.setDeltaMovement(Vec3.ZERO);
        ci.cancel();

        final long now = System.currentTimeMillis();
        if (now - arcadia$lastLogMillis > 10_000L) {
            arcadia$lastLogMillis = now;
            ArcadiaTweaks.LOGGER.warn("[EntityMoveGuard] Stopped {} at {} {} {} in {}: movement {} per tick (limit {})",
                    BuiltInRegistries.ENTITY_TYPE.getKey(self.getType()),
                    Math.round(self.getX()), Math.round(self.getY()), Math.round(self.getZ()),
                    self.level().dimension().location(), movement, limit);
        }
    }
}
