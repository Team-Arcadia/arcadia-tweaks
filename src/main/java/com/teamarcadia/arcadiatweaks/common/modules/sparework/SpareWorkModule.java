package com.teamarcadia.arcadiatweaks.common.modules.sparework;

import com.teamarcadia.arcadiatweaks.ArcadiaTweaks;
import com.teamarcadia.arcadiatweaks.common.config.ArcadiaConfig;
import com.teamarcadia.arcadiatweaks.common.module.ArcadiaModule;
import com.teamarcadia.arcadiatweaks.common.module.ArcadiaPatchVariant;
import com.teamarcadia.arcadiatweaks.neoforge.sparework.SpareWorkPatchAudit;

/**
 * Spare work module. The classes it starts are only shipped in jars that carry its Mixin config, which
 * {@link ArcadiaPatchVariant#hasSpareWork()} checks first.
 *
 * @author vyrriox
 */
public final class SpareWorkModule implements ArcadiaModule {

    @Override
    public String id() {
        return "sparework";
    }

    @Override
    public boolean enabledByConfig() {
        return ArcadiaPatchVariant.hasSpareWork() && ArcadiaConfig.MODULE_SPARE_WORK_ENABLED.get();
    }

    @Override
    public void onServerStarting() {
        SpareWorkFlags.refresh();
        ArcadiaTweaks.LOGGER.info(
                "[sparework] Active strategies: fastsuite={} simplyswords={} structures={} crabbersdelight={}; patches applied: fastsuite={} simplyswords={} structures={} crabbersdelight={}",
                ArcadiaConfig.SPARE_WORK.fastSuiteSkipEmptyParallelLookup.get(),
                ArcadiaConfig.SPARE_WORK.simplySwordsReusePityStack.get(),
                ArcadiaConfig.SPARE_WORK.structureLookupUsesLoadedChunk.get(),
                ArcadiaConfig.SPARE_WORK.crabbersDelightSkipEmptyTackleBoxSearch.get(),
                SpareWorkPatchAudit.fastSuiteApplied(),
                SpareWorkPatchAudit.simplySwordsApplied(),
                SpareWorkPatchAudit.structuresApplied(),
                SpareWorkPatchAudit.crabbersDelightApplied()
        );
    }
}
