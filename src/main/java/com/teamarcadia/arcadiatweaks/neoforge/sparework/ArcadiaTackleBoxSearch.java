package com.teamarcadia.arcadiatweaks.neoforge.sparework;

import alabaster.crabbersdelight.common.block.entity.TackleBoxBlockEntity;
import com.teamarcadia.arcadiatweaks.neoforge.mixin.sparework.ChunkAccessPendingAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.Map;
import java.util.function.Predicate;

/**
 * Decides whether Crabber's Delight's {@code TackleBoxProximity.findClosestPlaced} can be skipped.
 *
 * <p>The original reads {@code level.getBlockEntity} at the 343 positions around the player and returns null
 * unless one holds a tackle box. Each read can also change the chunk: it loads the chunk, promotes a pending
 * block entity, drops a removed one, or creates a block entity that a block should have but does not. So the
 * search is only skipped when every chunk it would read is fully loaded and none of that can happen:</p>
 * <ul>
 *     <li>no pending block entity in the chunk;</li>
 *     <li>no tackle box and no removed block entity in the chunk (wherever it is: the original decides);</li>
 *     <li>no position of the cube whose block needs a block entity without having one.</li>
 * </ul>
 * <p>The answer of the original is then null and it changes nothing. Anything else runs the original.</p>
 *
 * @author vyrriox
 */
public final class ArcadiaTackleBoxSearch {

    /** The radius of the original, pinned with its bytecode. */
    private static final int RADIUS = 3;
    /** Above this the walk over the chunk's block entities could cost as much as the original search. */
    private static final int MAX_BLOCK_ENTITIES = 256;
    private static final Predicate<BlockState> HAS_BLOCK_ENTITY = BlockBehaviour.BlockStateBase::hasBlockEntity;

    private ArcadiaTackleBoxSearch() {}

    /** True when the original search would return null and change nothing. Never true on another thread. */
    public static boolean provablyEmpty(Player player) {
        // A debug world does not read block states from the chunk sections.
        if (!(player.level() instanceof ServerLevel level) || level.isDebug()) {
            return false;
        }
        final BlockPos center = player.blockPosition();
        final int minX = center.getX() - RADIUS;
        final int maxX = center.getX() + RADIUS;
        final int minZ = center.getZ() - RADIUS;
        final int maxZ = center.getZ() + RADIUS;
        final int minY = Math.max(center.getY() - RADIUS, level.getMinBuildHeight());
        final int maxY = Math.min(center.getY() + RADIUS, level.getMaxBuildHeight() - 1);
        final ServerChunkCache chunks = level.getChunkSource();
        for (int chunkX = SectionPos.blockToSectionCoord(minX); chunkX <= SectionPos.blockToSectionCoord(maxX); chunkX++) {
            for (int chunkZ = SectionPos.blockToSectionCoord(minZ); chunkZ <= SectionPos.blockToSectionCoord(maxZ); chunkZ++) {
                // Null when the chunk is not fully loaded and on any thread but the server's.
                final LevelChunk chunk = chunks.getChunkNow(chunkX, chunkZ);
                if (chunk == null
                        || !blockEntitiesAreSettled(chunk)
                        || blockEntityMissing(chunk, minX, minY, minZ, maxX, maxY, maxZ)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean blockEntitiesAreSettled(LevelChunk chunk) {
        if (!(chunk instanceof ChunkAccessPendingAccessor accessor) || !accessor.arcadia$pendingBlockEntities().isEmpty()) {
            return false;
        }
        final Map<BlockPos, BlockEntity> blockEntities = chunk.getBlockEntities();
        if (blockEntities.size() > MAX_BLOCK_ENTITIES) {
            return false;
        }
        for (BlockEntity blockEntity : blockEntities.values()) {
            if (blockEntity instanceof TackleBoxBlockEntity || blockEntity.isRemoved()) {
                return false;
            }
        }
        return true;
    }

    /** True when a block of the cube inside this chunk needs a block entity and has none: the original creates it. */
    private static boolean blockEntityMissing(LevelChunk chunk, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        final Map<BlockPos, BlockEntity> blockEntities = chunk.getBlockEntities();
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        final int chunkMinX = chunk.getPos().getMinBlockX();
        final int chunkMinZ = chunk.getPos().getMinBlockZ();
        final int x0 = Math.max(minX, chunkMinX);
        final int x1 = Math.min(maxX, chunkMinX + 15);
        final int z0 = Math.max(minZ, chunkMinZ);
        final int z1 = Math.min(maxZ, chunkMinZ + 15);
        for (int sectionY = SectionPos.blockToSectionCoord(minY); sectionY <= SectionPos.blockToSectionCoord(maxY); sectionY++) {
            final LevelChunkSection section = chunk.getSection(chunk.getSectionIndexFromSectionY(sectionY));
            // The palette holds every state of the section, so a miss rules out the whole section.
            if (!section.maybeHas(HAS_BLOCK_ENTITY)) {
                continue;
            }
            final int sectionMinY = SectionPos.sectionToBlockCoord(sectionY);
            final int y0 = Math.max(minY, sectionMinY);
            final int y1 = Math.min(maxY, sectionMinY + 15);
            // Most positions repeat one state: comparing it is cheaper than asking it again.
            BlockState plain = null;
            for (int x = x0; x <= x1; x++) {
                for (int y = y0; y <= y1; y++) {
                    for (int z = z0; z <= z1; z++) {
                        final BlockState state = section.getBlockState(x & 15, y & 15, z & 15);
                        if (state == plain) {
                            continue;
                        }
                        if (!state.hasBlockEntity()) {
                            plain = state;
                        } else if (blockEntities.get(cursor.set(x, y, z)) == null) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}
