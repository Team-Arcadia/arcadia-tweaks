package com.teamarcadia.arcadiatweaks.neoforge.mixin.refinedstorage;

import com.refinedmods.refinedstorage.api.core.Action;
import com.teamarcadia.arcadiatweaks.neoforge.refinedstorage.ArcadiaVersionedStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * S3 - content version of every RS2 item and fluid disk ({@code PlatformStorage} and its
 * subclass {@code LimitedPlatformStorage}).
 *
 * All content of a disk (resources, amounts, tracked "changed by / at") lives in a delegate
 * chain built for it alone and only written by {@code insert}, {@code extract} and
 * {@code load}; the plugin checks that the class still declares only the known methods. The
 * version is bumped at the head of each of them, before anything changes, on every executed
 * insert or extract even when nothing moves (only a cache miss) and on every load. Simulated
 * calls change nothing and are not counted.
 *
 * The handlers take the Action argument by value: no callback object is allocated on these
 * paths, which run for every item moved through a network.
 *
 * Applied together with {@code StorageRepositoryImplMixin} or not at all.
 *
 * @author vyrriox
 */
@Mixin(targets = "com.refinedmods.refinedstorage.common.storage.PlatformStorage")
public abstract class PlatformStorageMixin implements ArcadiaVersionedStorage {

    @Unique private long arcadia$contentVersion;
    @Unique private long arcadia$encodedVersion = -1L;
    @Unique private Object arcadia$encoding;

    @ModifyVariable(
            method = "insert(Lcom/refinedmods/refinedstorage/api/resource/ResourceKey;JLcom/refinedmods/refinedstorage/api/core/Action;Lcom/refinedmods/refinedstorage/api/storage/Actor;)J",
            at = @At("HEAD"), argsOnly = true, require = 1)
    private Action arcadia$versionOnInsert(Action action) {
        if (action == Action.EXECUTE) {
            arcadia$contentVersion++;
        }
        return action;
    }

    @ModifyVariable(
            method = "extract(Lcom/refinedmods/refinedstorage/api/resource/ResourceKey;JLcom/refinedmods/refinedstorage/api/core/Action;Lcom/refinedmods/refinedstorage/api/storage/Actor;)J",
            at = @At("HEAD"), argsOnly = true, require = 1)
    private Action arcadia$versionOnExtract(Action action) {
        if (action == Action.EXECUTE) {
            arcadia$contentVersion++;
        }
        return action;
    }

    @Inject(
            method = "load(Lcom/refinedmods/refinedstorage/common/storage/StorageCodecs$StorageResource;)V",
            at = @At("HEAD"), require = 1)
    private void arcadia$versionOnLoad(CallbackInfo ci) {
        arcadia$contentVersion++;
    }

    @Override
    @Unique
    public long arcadia$getContentVersion() {
        return arcadia$contentVersion;
    }

    @Override
    @Unique
    public Object arcadia$getCachedEncoding(long contentVersion) {
        return arcadia$encodedVersion == contentVersion ? arcadia$encoding : null;
    }

    @Override
    @Unique
    public void arcadia$setCachedEncoding(long contentVersion, Object encoding) {
        arcadia$encodedVersion = contentVersion;
        arcadia$encoding = encoding;
    }
}
