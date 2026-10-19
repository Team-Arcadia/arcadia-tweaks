package com.teamarcadia.arcadiatweaks.common.modules.waystones;

import com.teamarcadia.arcadiatweaks.ArcadiaTweaks;
import com.teamarcadia.arcadiatweaks.common.config.ArcadiaConfig;
import com.teamarcadia.arcadiatweaks.common.module.ArcadiaModule;
import com.teamarcadia.arcadiatweaks.common.module.ArcadiaPatchVariant;

public final class WaystonesModule implements ArcadiaModule {

    @Override
    public String id() {
        return "waystones";
    }

    @Override
    public boolean enabledByConfig() {
        return ArcadiaPatchVariant.hasWaystones() && ArcadiaConfig.MODULE_WAYSTONES_ENABLED.get();
    }

    @Override
    public void onServerStarting() {
        ArcadiaTweaks.LOGGER.info(
                "[waystones] Active strategies: W1={}",
                ArcadiaConfig.WAYSTONES.ftbTeamsTeamVisibility.get()
        );
    }
}
