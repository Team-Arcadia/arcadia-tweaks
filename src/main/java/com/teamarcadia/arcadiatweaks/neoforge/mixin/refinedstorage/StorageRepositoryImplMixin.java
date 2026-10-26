package com.teamarcadia.arcadiatweaks.neoforge.mixin.refinedstorage;

import com.mojang.serialization.Codec;
import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.storage.Actor;
import com.refinedmods.refinedstorage.common.api.storage.SerializableStorage;
import com.refinedmods.refinedstorage.common.storage.StorageRepositoryImpl;
import com.refinedmods.refinedstorage.common.storage.StorageTypes;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;
import com.teamarcadia.arcadiatweaks.common.config.ArcadiaConfig;
import com.teamarcadia.arcadiatweaks.neoforge.refinedstorage.ArcadiaCachingStorageCodec;
import com.teamarcadia.arcadiatweaks.neoforge.refinedstorage.ArcadiaStorageSaveCache;
import com.teamarcadia.arcadiatweaks.neoforge.refinedstorage.ArcadiaVersionedStorage;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * S3 - Reuse the saved form of unchanged RS2 disks.
 *
 * {@code StorageRepositoryImpl} is the saved data holding every disk of the world. It is
 * dirtied by any executed insert or extract on any disk, and its save then encodes every disk.
 * The value codec of its map codec is wrapped in {@link ArcadiaCachingStorageCodec}, which
 * reuses the encoded form of a disk whose content version did not move since it was made.
 *
 * At the head of every save the verification counter moves on, and on the first save of the
 * session a self-check proves the version tracking of {@code PlatformStorageMixin} is live in
 * this runtime (an executed insert and extract on a scratch disk must move the version). If it
 * is not, the cache stays off.
 *
 * Applied together with {@code PlatformStorageMixin} or not at all.
 *
 * @author vyrriox
 */
@Mixin(StorageRepositoryImpl.class)
public abstract class StorageRepositoryImplMixin {

    @Unique private static boolean arcadia$selfCheckDone;

    @ModifyArg(method = "createCodec",
            at = @At(value = "INVOKE",
                    target = "Lcom/refinedmods/refinedstorage/common/storage/ErrorHandlingMapCodec;<init>(Lcom/mojang/serialization/Codec;Lcom/mojang/serialization/Codec;)V"),
            index = 1, require = 1)
    private static Codec<?> arcadia$wrapStorageCodec(Codec<?> storageCodec) {
        return new ArcadiaCachingStorageCodec<>(storageCodec);
    }

    @Inject(method = "save(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/nbt/CompoundTag;",
            at = @At("HEAD"), require = 1)
    private void arcadia$onSaveStart(CompoundTag tag, HolderLookup.Provider registries, CallbackInfoReturnable<CompoundTag> cir) {
        int verifyInterval = 0;
        boolean enabled = false;
        try {
            enabled = ArcadiaConfig.MODULE_REFINED_STORAGE_ENABLED.get()
                    && ArcadiaConfig.REFINED_STORAGE.storageSaveCache.get();
            verifyInterval = ArcadiaConfig.REFINED_STORAGE.storageSaveCacheVerifyInterval.get();
        } catch (IllegalStateException | NullPointerException configNotLoaded) {
            // Config not loaded: the codec reads the switch as off too.
        }
        ArcadiaStorageSaveCache.onSaveStart(verifyInterval);
        if (enabled && !arcadia$selfCheckDone) {
            arcadia$selfCheckDone = true;
            arcadia$selfCheckVersionTracking();
        }
    }

    @Unique
    private static void arcadia$selfCheckVersionTracking() {
        try {
            final SerializableStorage scratch = StorageTypes.ITEM.create(1000L, () -> { });
            if (!(scratch instanceof ArcadiaVersionedStorage versioned)) {
                ArcadiaStorageSaveCache.disableForSession("item disks carry no content version (Mixin not applied).");
                return;
            }
            final ItemResource stone = new ItemResource(Items.STONE);
            final long start = versioned.arcadia$getContentVersion();
            scratch.insert(stone, 10L, Action.EXECUTE, Actor.EMPTY);
            final long afterInsert = versioned.arcadia$getContentVersion();
            scratch.extract(stone, 4L, Action.EXECUTE, Actor.EMPTY);
            final long afterExtract = versioned.arcadia$getContentVersion();
            if (afterInsert <= start || afterExtract <= afterInsert || scratch.getStored() != 6L) {
                ArcadiaStorageSaveCache.disableForSession("insert/extract do not move the disk content version.");
            }
        } catch (RuntimeException | LinkageError e) {
            ArcadiaStorageSaveCache.disableForSession("version tracking self-check failed: " + e);
        }
    }
}
