package com.teamarcadia.arcadiatweaks.common.modules.refinedstorage;

import com.teamarcadia.arcadiatweaks.ArcadiaTweaks;
import com.teamarcadia.arcadiatweaks.common.config.ArcadiaConfig;
import com.teamarcadia.arcadiatweaks.common.module.ArcadiaModule;
import com.teamarcadia.arcadiatweaks.common.module.ArcadiaPatchVariant;

public final class RefinedStorageModule implements ArcadiaModule {

    @Override
    public String id() {
        return "refinedstorage";
    }

    @Override
    public boolean enabledByConfig() {
        return ArcadiaPatchVariant.hasRefinedStorage() && ArcadiaConfig.MODULE_REFINED_STORAGE_ENABLED.get();
    }

    @Override
    public void onServerStarting() {
        RefinedStorageRuntimeFlags.refresh();
        ArcadiaTweaks.LOGGER.info(
                "[refinedstorage] Active strategies: S1={}(N={}) S2={} S3={}(verify every {} saves) S4={}",
                ArcadiaConfig.REFINED_STORAGE.activenessCheckCoalescing.get(),
                ArcadiaConfig.REFINED_STORAGE.activenessCheckInterval.get(),
                ArcadiaConfig.REFINED_STORAGE.singleComponentLookup.get(),
                ArcadiaConfig.REFINED_STORAGE.storageSaveCache.get(),
                ArcadiaConfig.REFINED_STORAGE.storageSaveCacheVerifyInterval.get(),
                ArcadiaConfig.REFINED_STORAGE.quietHotDebugLogs.get()
        );
    }
}
