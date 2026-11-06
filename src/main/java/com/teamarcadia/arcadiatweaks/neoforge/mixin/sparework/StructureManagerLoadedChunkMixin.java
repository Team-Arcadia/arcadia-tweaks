package com.teamarcadia.arcadiatweaks.neoforge.mixin.sparework;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.teamarcadia.arcadiatweaks.common.modules.sparework.SpareWorkFlags;
import com.teamarcadia.arcadiatweaks.neoforge.sparework.SpareWorkCounters;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Structure lookups ask the level for a chunk at status {@code STRUCTURE_REFERENCES} or {@code STRUCTURE_STARTS}.
 * For a chunk that is already fully loaded, that misses the chunk cache (it only holds the {@code FULL} entry
 * most of the time), adds a one tick ticket weaker than the chunk's own tickets, and returns an
 * {@code ImposterProtoChunk} whose structure methods all read the wrapped {@code LevelChunk}. The caller only
 * reads those structure methods, so the {@code LevelChunk} gives the same answer directly.
 *
 * <p>{@code getChunkNow} is null for a chunk that is not fully loaded and on any other thread: those calls,
 * and the world generation regions, go through the original call.</p>
 *
 * @author vyrriox
 */
@Mixin(StructureManager.class)
public abstract class StructureManagerLoadedChunkMixin {

    @WrapOperation(
            method = {
                    "startsForStructure(Lnet/minecraft/world/level/ChunkPos;Ljava/util/function/Predicate;)Ljava/util/List;",
                    "startsForStructure(Lnet/minecraft/core/SectionPos;Lnet/minecraft/world/level/levelgen/structure/Structure;)Ljava/util/List;",
                    "fillStartsForStructure(Lnet/minecraft/world/level/levelgen/structure/Structure;Lit/unimi/dsi/fastutil/longs/LongSet;Ljava/util/function/Consumer;)V",
                    "hasAnyStructureAt(Lnet/minecraft/core/BlockPos;)Z",
                    "getAllStructuresAt(Lnet/minecraft/core/BlockPos;)Ljava/util/Map;"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/LevelAccessor;getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;)Lnet/minecraft/world/level/chunk/ChunkAccess;"),
            require = 0
    )
    private ChunkAccess arcadia$useLoadedChunk(LevelAccessor level, int x, int z, ChunkStatus status, Operation<ChunkAccess> original) {
        if (SpareWorkFlags.structureLookupUsesLoadedChunk() && level instanceof ServerLevel serverLevel) {
            final LevelChunk loaded = serverLevel.getChunkSource().getChunkNow(x, z);
            if (loaded != null) {
                SpareWorkCounters.structureLookupsOnLoadedChunk++;
                return loaded;
            }
        }
        return original.call(level, x, z, status);
    }
}
