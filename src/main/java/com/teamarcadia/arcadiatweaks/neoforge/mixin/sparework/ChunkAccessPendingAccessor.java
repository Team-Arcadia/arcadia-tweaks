package com.teamarcadia.arcadiatweaks.neoforge.mixin.sparework;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * Block entities of a chunk still waiting to be created, which a read of their position would create.
 *
 * @author vyrriox
 */
@Mixin(ChunkAccess.class)
public interface ChunkAccessPendingAccessor {

    @Accessor("pendingBlockEntities")
    Map<BlockPos, CompoundTag> arcadia$pendingBlockEntities();
}
