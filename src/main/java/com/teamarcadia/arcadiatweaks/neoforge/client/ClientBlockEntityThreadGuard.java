package com.teamarcadia.arcadiatweaks.neoforge.client;

import com.teamarcadia.arcadiatweaks.ArcadiaTweaks;
import com.teamarcadia.arcadiatweaks.common.config.ArcadiaConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Keeps the block entity maps of client chunks on the render thread.
 *
 * <p>On a server, {@code Level.getBlockEntity} returns null to any thread other than the level's own. On a
 * client there is no such check: a call from any thread goes to {@code LevelChunk.getBlockEntity} with
 * {@code IMMEDIATE} creation, which removes a removed block entity from the chunk map, promotes a pending one,
 * or creates and registers a missing one. Those writes go to the chunk's {@code blockEntities}
 * ({@code Object2ObjectOpenHashMap}) and {@code tickersInLevel} ({@code HashMap}), which are not thread-safe.
 * When another thread writes them while the render thread does the same (Sodium cloning a section, NeoForge
 * refreshing model data), the render thread crashes with a {@code ConcurrentModificationException} in
 * {@code LevelChunk.updateBlockEntityTicker} or an {@code ArrayIndexOutOfBoundsException} in the map's
 * {@code rehash} (Arcadia crash reports of 2026-11-03).</p>
 *
 * <p>For a chunk the client level actually holds, a call from another thread now only reads: it gets the
 * block entity already in the map, or null. Writes asked from another thread
 * ({@code setBlockEntity}, {@code removeBlockEntity}) are handed to the render thread. The first calls are
 * logged with their stack trace so the mod making them can be identified.</p>
 *
 * @author vyrriox
 */
public final class ClientBlockEntityThreadGuard {

    private static final int LOGGED_CALLS = 5;
    private static final AtomicInteger OFF_THREAD_CALLS = new AtomicInteger();

    private ClientBlockEntityThreadGuard() {}

    /**
     * True when {@code chunk} is a chunk of the client level and the current thread is not the render thread.
     * Chunks the level does not hold (a copy another mod is building on its own thread) are left alone.
     */
    public static boolean isOffThreadAccess(LevelChunk chunk) {
        final Level level = chunk.getLevel();
        if (!(level instanceof ClientLevel clientLevel)) {
            return false;
        }
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.isSameThread() || !enabled()) {
            return false;
        }
        return isHeldByLevel(clientLevel, chunk);
    }

    /** The block entity already stored at {@code pos}, without creating, promoting or removing anything. */
    public static BlockEntity readOnly(LevelChunk chunk, BlockPos pos) {
        report("getBlockEntity", chunk, pos);
        try {
            final BlockEntity blockEntity = chunk.getBlockEntities().get(pos);
            return blockEntity == null || blockEntity.isRemoved() ? null : blockEntity;
        } catch (RuntimeException concurrentRead) {
            // The render thread is resizing the map right now; this caller gets nothing rather than a crash.
            return null;
        }
    }

    /** Runs a chunk write on the render thread, if the chunk is still the one the level holds by then. */
    public static void deferToRenderThread(String operation, LevelChunk chunk, BlockPos pos, Runnable write) {
        report(operation, chunk, pos);
        Minecraft.getInstance().execute(() -> {
            if (chunk.getLevel() instanceof ClientLevel clientLevel && isHeldByLevel(clientLevel, chunk)) {
                write.run();
            }
        });
    }

    private static boolean isHeldByLevel(ClientLevel level, LevelChunk chunk) {
        final ChunkPos pos = chunk.getPos();
        try {
            return level.getChunkSource().getChunk(pos.x, pos.z, ChunkStatus.FULL, false) == chunk;
        } catch (RuntimeException concurrentRead) {
            // Cannot tell: treat the chunk as shared, which is the safe side.
            return true;
        }
    }

    private static boolean enabled() {
        try {
            return ArcadiaConfig.CLIENT_BLOCK_ENTITY_THREAD_GUARD.get();
        } catch (IllegalStateException notLoadedYet) {
            return true;
        }
    }

    private static void report(String operation, LevelChunk chunk, BlockPos pos) {
        final int count = OFF_THREAD_CALLS.incrementAndGet();
        if (count > LOGGED_CALLS) {
            return;
        }
        final String suffix = count == LOGGED_CALLS ? " Further calls are handled the same way and not logged." : "";
        ArcadiaTweaks.LOGGER.warn(
                "[client] {} on client chunk {} at {} from thread '{}', outside the render thread. Handled safely "
                        + "(read only or moved to the render thread). The stack trace shows the calling mod.{}",
                operation, chunk.getPos(), pos.toShortString(), Thread.currentThread().getName(), suffix,
                new Throwable("off-thread block entity access"));
    }
}
