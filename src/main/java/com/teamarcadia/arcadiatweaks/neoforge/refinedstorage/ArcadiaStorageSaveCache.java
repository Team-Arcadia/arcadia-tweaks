package com.teamarcadia.arcadiatweaks.neoforge.refinedstorage;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Encoder;
import com.teamarcadia.arcadiatweaks.ArcadiaTweaks;

import java.util.Objects;
import java.util.Optional;

/**
 * S3 - reuse of the encoded form of unchanged RS2 disks when the storage repository is saved.
 *
 * Every rule here falls back to the plain encoder:
 * <ul>
 *   <li>only values carrying {@link ArcadiaVersionedStorage} (RS {@code PlatformStorage} and
 *       {@code LimitedPlatformStorage}) and only the NBT save context are cached;</li>
 *   <li>a cached result is reused only when the disk content version is the one it was made
 *       at, and only successful results are stored;</li>
 *   <li>every {@code verifyInterval}th save encodes the cached disks again: any difference logs
 *       one error, writes the fresh result and turns the cache off for the rest of the session;</li>
 *   <li>any exception thrown by the cache code is logged once, turns the cache off and the
 *       plain encoder result is used.</li>
 * </ul>
 * Single-threaded by contract: RS saves and mutates disks on the server thread.
 *
 * @author vyrriox
 */
public final class ArcadiaStorageSaveCache {

    private static volatile boolean disabledForSession;
    private static long saveCount;
    private static boolean verifyCurrentSave;
    private static long hits;
    private static long misses;
    private static long verifications;

    private ArcadiaStorageSaveCache() {}

    /** Called at the start of every repository save. */
    public static void onSaveStart(int verifyInterval) {
        saveCount++;
        verifyCurrentSave = verifyInterval > 0 && saveCount % verifyInterval == 0;
    }

    /**
     * Encodes {@code input} with {@code delegate}, or returns the cached result of an unchanged
     * disk.
     *
     * @param enabled          config switch (module and S3)
     * @param cacheableContext true for the NBT save context: registry ops over NBT and an empty
     *                         prefix, which is how RS's map codec encodes every disk
     */
    @SuppressWarnings("unchecked")
    public static <V, T> DataResult<T> encode(
            Encoder<V> delegate,
            V input,
            DynamicOps<T> ops,
            T prefix,
            boolean enabled,
            boolean cacheableContext
    ) {
        if (!enabled || disabledForSession || !cacheableContext || !(input instanceof ArcadiaVersionedStorage storage)) {
            return delegate.encode(input, ops, prefix);
        }

        long version = 0L;
        DataResult<T> cached = null;
        boolean usable;
        try {
            version = storage.arcadia$getContentVersion();
            cached = (DataResult<T>) storage.arcadia$getCachedEncoding(version);
            usable = true;
        } catch (RuntimeException e) {
            fail(e);
            usable = false;
        }

        final boolean verify = verifyCurrentSave;
        if (usable && cached != null && !verify) {
            hits++;
            return cached;
        }

        final DataResult<T> fresh = delegate.encode(input, ops, prefix);
        if (!usable) {
            return fresh;
        }
        try {
            if (cached != null) {
                verifications++;
                if (!sameResult(cached, fresh)) {
                    mismatch(storage);
                    return fresh;
                }
            } else {
                misses++;
            }
            if (isCleanSuccess(fresh)) {
                storage.arcadia$setCachedEncoding(version, fresh);
            }
        } catch (RuntimeException e) {
            fail(e);
        }
        return fresh;
    }

    private static boolean isCleanSuccess(DataResult<?> result) {
        return result.isSuccess() && result.result().isPresent();
    }

    private static boolean sameResult(DataResult<?> cached, DataResult<?> fresh) {
        final Optional<?> cachedValue = cached.result();
        final Optional<?> freshValue = fresh.result();
        return fresh.isSuccess() && cachedValue.isPresent() && freshValue.isPresent()
                && Objects.equals(cachedValue.get(), freshValue.get());
    }

    private static void mismatch(Object storage) {
        if (!disabledForSession) {
            disabledForSession = true;
            ArcadiaTweaks.LOGGER.error(
                    "[refinedstorage] Storage save cache: the cached encoding of a {} differs from a fresh encoding."
                            + " The fresh data was written and the cache is off until the next restart."
                            + " Please report this with the Refined Storage version and its addons.",
                    storage.getClass().getSimpleName());
        }
    }

    private static void fail(RuntimeException e) {
        if (!disabledForSession) {
            disabledForSession = true;
            ArcadiaTweaks.LOGGER.error(
                    "[refinedstorage] Storage save cache failed; plain RS encoding is used until the next restart.", e);
        }
    }

    public static boolean isDisabledForSession() {
        return disabledForSession;
    }

    /** Turns the cache off for the session, with a reason in the log. Used by the runtime self-check. */
    public static void disableForSession(String reason) {
        if (!disabledForSession) {
            disabledForSession = true;
            ArcadiaTweaks.LOGGER.error("[refinedstorage] Storage save cache turned off: {}", reason);
        }
    }

    public static long hits() {
        return hits;
    }

    public static long misses() {
        return misses;
    }

    public static long verifications() {
        return verifications;
    }

    /** Test support: clears the session state and the counters. */
    public static void resetForTests() {
        disabledForSession = false;
        saveCount = 0L;
        verifyCurrentSave = false;
        hits = 0L;
        misses = 0L;
        verifications = 0L;
    }
}
