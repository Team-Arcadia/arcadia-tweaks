package com.teamarcadia.arcadiatweaks.neoforge.refinedstorage;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.teamarcadia.arcadiatweaks.common.config.ArcadiaConfig;
import net.minecraft.nbt.EndTag;
import net.minecraft.resources.RegistryOps;

/**
 * S3 - wraps the per-disk codec of RS2 {@code StorageRepositoryImpl} (the value codec of its
 * {@code ErrorHandlingMapCodec}). Decoding is passed through unchanged; encoding goes through
 * {@link ArcadiaStorageSaveCache}.
 *
 * @author vyrriox
 */
public final class ArcadiaCachingStorageCodec<V> implements Codec<V> {

    private final Codec<V> delegate;

    public ArcadiaCachingStorageCodec(Codec<V> delegate) {
        this.delegate = delegate;
    }

    @Override
    public <T> DataResult<Pair<V, T>> decode(DynamicOps<T> ops, T input) {
        return delegate.decode(ops, input);
    }

    @Override
    public <T> DataResult<T> encode(V input, DynamicOps<T> ops, T prefix) {
        if (!(input instanceof ArcadiaVersionedStorage)) {
            return delegate.encode(input, ops, prefix);
        }
        return ArcadiaStorageSaveCache.encode(delegate, input, ops, prefix, enabled(), isNbtSaveContext(ops, prefix));
    }

    /** RS saves with {@code provider.createSerializationContext(NbtOps.INSTANCE)} and an empty prefix. */
    private static <T> boolean isNbtSaveContext(DynamicOps<T> ops, T prefix) {
        final T empty = ops.empty();
        return ops instanceof RegistryOps<?> && empty == EndTag.INSTANCE && prefix == empty;
    }

    private static boolean enabled() {
        try {
            return ArcadiaConfig.MODULE_REFINED_STORAGE_ENABLED.get()
                    && ArcadiaConfig.REFINED_STORAGE.storageSaveCache.get();
        } catch (IllegalStateException | NullPointerException configNotLoaded) {
            return false;
        }
    }

    @Override
    public String toString() {
        return delegate.toString();
    }
}
