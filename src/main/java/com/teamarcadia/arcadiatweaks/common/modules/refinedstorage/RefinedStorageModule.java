package com.teamarcadia.arcadiatweaks.common.modules.refinedstorage;

import com.teamarcadia.arcadiatweaks.ArcadiaTweaks;
import com.teamarcadia.arcadiatweaks.common.config.ArcadiaConfig;
import com.teamarcadia.arcadiatweaks.common.module.ArcadiaModule;
import com.teamarcadia.arcadiatweaks.common.module.ArcadiaPatchVariant;
import com.teamarcadia.arcadiatweaks.neoforge.refinedstorage.ArcadiaRefinedStoragePatchAudit;

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
        ArcadiaTweaks.LOGGER.info(
                "[refinedstorage] Active strategies: A1={} B1={} B2={} B3={} (idle max {} ticks); patches applied: A1={} B1={} B2={} B3={}",
                ArcadiaConfig.REFINED_STORAGE.autocraftingLiveView.get(),
                ArcadiaConfig.REFINED_STORAGE.importerIdleBackoff.get(),
                ArcadiaConfig.REFINED_STORAGE.exporterIdleBackoff.get(),
                ArcadiaConfig.REFINED_STORAGE.autocraftingSinkBackoff.get(),
                ArcadiaConfig.REFINED_STORAGE.idleBackoffMaxTicks.get(),
                ArcadiaRefinedStoragePatchAudit.liveViewApplied(),
                ArcadiaRefinedStoragePatchAudit.importerBackoffApplied(),
                ArcadiaRefinedStoragePatchAudit.exporterBackoffApplied(),
                ArcadiaRefinedStoragePatchAudit.sinkBackoffApplied()
        );
    }
}
