package com.teamarcadia.arcadiatweaks.neoforge.gametest.bench.scenario;

import com.refinedmods.refinedstorage.common.api.RefinedStorageApi;
import com.refinedmods.refinedstorage.common.api.storage.StorageRepository;
import com.teamarcadia.arcadiatweaks.neoforge.refinedstorage.ArcadiaStorageSaveCache;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Times saves of the Refined Storage saved data ({@code refinedstorage_storages}) the way
 * {@code SavedData.save(File, Provider)} runs them in NeoForge 21.1: on the server thread the data is
 * encoded ({@code save(CompoundTag, Provider)} plus the data version) and the tag is copied; the
 * copy is then compressed and written on the IO pool. Here the compression is done in memory on the
 * server thread and timed apart; nothing is written to disk.
 *
 * <p>Keys ({@code prefix} + name), means over the timed saves unless stated:
 * {@code EncodeMs}, {@code CopyMs}, {@code CompressMs}, {@code ServerThreadMs} (encode + copy: what an
 * autosave costs the tick), {@code ServerThreadMedianMs}, {@code ServerThreadMaxMs}, {@code TotalMs}
 * (encode + copy + compress), {@code Kb} (compressed size), {@code Saves}, and the save cache counters
 * of these saves: {@code CacheHits}, {@code CacheMisses}, {@code CacheVerifications}.
 *
 * @author vyrriox
 */
final class RsSaveTimer {

    /** Something done before each timed save, e.g. changing a few disks. */
    interface BeforeSave {
        void run(int saveIndex);
    }

    private RsSaveTimer() {}

    static Map<String, Double> time(ServerLevel level, String prefix, int saves, BeforeSave before) {
        final Map<String, Double> out = new LinkedHashMap<>();
        final StorageRepository repo = RefinedStorageApi.INSTANCE.getStorageRepository(level);
        if (!(repo instanceof SavedData data)) {
            return out;
        }
        final double[] encode = new double[saves];
        final double[] copy = new double[saves];
        final double[] compress = new double[saves];
        final double[] serverThread = new double[saves];
        long bytes = 0;
        final long hits0 = ArcadiaStorageSaveCache.hits();
        final long misses0 = ArcadiaStorageSaveCache.misses();
        final long verify0 = ArcadiaStorageSaveCache.verifications();
        for (int s = 0; s < saves; s++) {
            before.run(s);
            final long t0 = System.nanoTime();
            final CompoundTag tag = new CompoundTag();
            tag.put("data", data.save(new CompoundTag(), level.registryAccess()));
            NbtUtils.addCurrentDataVersion(tag);
            final long t1 = System.nanoTime();
            final CompoundTag copied = tag.copy();
            final long t2 = System.nanoTime();
            final ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try {
                NbtIo.writeCompressed(copied, bos);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            final long t3 = System.nanoTime();
            encode[s] = (t1 - t0) / 1e6;
            copy[s] = (t2 - t1) / 1e6;
            compress[s] = (t3 - t2) / 1e6;
            serverThread[s] = (t2 - t0) / 1e6;
            bytes = bos.size();
        }
        out.put(prefix + "EncodeMs", mean(encode));
        out.put(prefix + "CopyMs", mean(copy));
        out.put(prefix + "CompressMs", mean(compress));
        out.put(prefix + "ServerThreadMs", mean(serverThread));
        final double[] sorted = serverThread.clone();
        Arrays.sort(sorted);
        out.put(prefix + "ServerThreadMedianMs", sorted.length % 2 == 1 ? sorted[sorted.length / 2]
                : (sorted[sorted.length / 2 - 1] + sorted[sorted.length / 2]) / 2);
        out.put(prefix + "ServerThreadMaxMs", sorted[sorted.length - 1]);
        out.put(prefix + "TotalMs", mean(serverThread) + mean(compress));
        out.put(prefix + "Kb", bytes / 1024.0);
        out.put(prefix + "Saves", (double) saves);
        out.put(prefix + "CacheHits", (double) (ArcadiaStorageSaveCache.hits() - hits0));
        out.put(prefix + "CacheMisses", (double) (ArcadiaStorageSaveCache.misses() - misses0));
        out.put(prefix + "CacheVerifications", (double) (ArcadiaStorageSaveCache.verifications() - verify0));
        return out;
    }

    private static double mean(double[] v) {
        double s = 0;
        for (double d : v) s += d;
        return v.length == 0 ? 0 : s / v.length;
    }
}
