package com.teamarcadia.arcadiatweaks.neoforge.mixin.simplyswords;

import com.teamarcadia.arcadiatweaks.common.config.ArcadiaConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Simply Swords, bug #328: {@code GloamStainManager.purgeOrphans} walks {@code ServerLevel.getAllEntities()}
 * and discards orphan gloam stain visuals inside that walk. Discarding removes the entity from the level's
 * entity index, the {@code Int2ObjectLinkedOpenHashMap} the walk is reading; once enough entries go, the map
 * shrinks and the iterator reads past the new table ({@code ArrayIndexOutOfBoundsException}), which crashes
 * the server. Server1 crashed this way on a dungeon teleport: the player's chunks stayed loaded but stopped
 * ticking, so their stains were not removed by their own tick and the purge removed them all at once.
 *
 * <p>The discards are collected during the walk and applied once it is over. The same entities are removed,
 * in the same tick.</p>
 *
 * @author vyrriox
 */
@Mixin(targets = "net.sweenus.simplyswords.world.GloamStainManager")
public abstract class GloamStainPurgeMixin {

    @Unique
    private static final List<Entity> arcadia$pendingDiscards = new ArrayList<>();

    @Inject(method = "purgeOrphans", at = @At("HEAD"))
    private static void arcadia$resetPending(ServerLevel level, Set<UUID> keep, CallbackInfo ci) {
        arcadia$pendingDiscards.clear();
    }

    @Redirect(method = "purgeOrphans",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;discard()V"))
    private static void arcadia$deferDiscard(Entity entity) {
        if (ArcadiaConfig.GLOAM_PURGE_DEFERRED_DISCARD.get()) {
            arcadia$pendingDiscards.add(entity);
        } else {
            entity.discard();
        }
    }

    @Inject(method = "purgeOrphans", at = @At("RETURN"))
    private static void arcadia$discardPending(ServerLevel level, Set<UUID> keep, CallbackInfo ci) {
        if (arcadia$pendingDiscards.isEmpty()) {
            return;
        }
        for (Entity entity : arcadia$pendingDiscards) {
            entity.discard();
        }
        arcadia$pendingDiscards.clear();
    }
}
