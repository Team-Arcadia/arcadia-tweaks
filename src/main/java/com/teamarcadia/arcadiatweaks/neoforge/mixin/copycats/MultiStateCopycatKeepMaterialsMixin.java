package com.teamarcadia.arcadiatweaks.neoforge.mixin.copycats;

import com.copycatsplus.copycats.foundation.copycat.multistate.MaterialItemStorage;
import com.teamarcadia.arcadiatweaks.common.config.ArcadiaConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Copycats+: {@code MultiStateCopycatBlockEntity.invalidate()} empties the block entity's material storage.
 * Create calls {@code invalidate()} from {@code setRemoved()}, which runs whenever a chunk is unloaded or
 * resent by the server. Sodium builds chunk meshes on worker threads from a copy of the section that keeps
 * the same block entity objects, so a mesh task started just before the removal still reads that block
 * entity. With its storage emptied, {@code getMaterialItem(property)} returns null and
 * {@code IMultiStateCopycatBlockEntity.isCTEnabled()} throws a NullPointerException, which crashes the
 * client ("Encountered exception while building chunk meshes", copycats:copycat_slab, Arcadia crash reports
 * of 2026-11-02).
 *
 * <p>The storage is left as it is. A removed block entity is dropped by its chunk anyway, so keeping its
 * materials only lets a late reader see the last real values instead of an empty map.</p>
 *
 * @author vyrriox
 */
@Mixin(targets = "com.copycatsplus.copycats.foundation.copycat.multistate.MultiStateCopycatBlockEntity")
public abstract class MultiStateCopycatKeepMaterialsMixin {

    @Redirect(method = "invalidate",
            at = @At(value = "INVOKE",
                    target = "Lcom/copycatsplus/copycats/foundation/copycat/multistate/MaterialItemStorage;clear()V"))
    private void arcadia$keepMaterialsOnRemove(MaterialItemStorage storage) {
        if (!keepMaterials()) {
            storage.clear();
        }
    }

    @Unique
    private static boolean keepMaterials() {
        try {
            return ArcadiaConfig.COPYCATS_KEEP_MATERIALS_ON_REMOVE.get();
        } catch (IllegalStateException notLoadedYet) {
            return true;
        }
    }
}
