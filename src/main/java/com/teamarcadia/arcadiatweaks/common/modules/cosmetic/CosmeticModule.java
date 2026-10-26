package com.teamarcadia.arcadiatweaks.common.modules.cosmetic;

import com.teamarcadia.arcadiatweaks.ArcadiaTweaks;
import com.teamarcadia.arcadiatweaks.common.config.ArcadiaConfig;
import com.teamarcadia.arcadiatweaks.common.module.ArcadiaModule;
import com.teamarcadia.arcadiatweaks.common.module.ArcadiaPatchVariant;
import com.teamarcadia.arcadiatweaks.neoforge.cosmetic.CosmeticGate;

/**
 * CosmeticWeapons and CosmeticArmours module. The gate classes it starts are only shipped in jars that
 * carry the cosmetic Mixin config, which {@link ArcadiaPatchVariant#hasCosmetic()} checks first.
 *
 * @author vyrriox
 */
public final class CosmeticModule implements ArcadiaModule {

    @Override
    public String id() {
        return "cosmetic";
    }

    @Override
    public boolean enabledByConfig() {
        return ArcadiaPatchVariant.hasCosmetic() && ArcadiaConfig.MODULE_COSMETIC_ENABLED.get();
    }

    @Override
    public void onServerStarting() {
        CosmeticGate.runStartupAudit();
        ArcadiaTweaks.LOGGER.info(
                "[cosmetic] Active strategies: presenceGate={} (startup audit passed={})",
                ArcadiaConfig.COSMETIC.containsPresenceGate.get(),
                CosmeticGate.auditPassed()
        );
    }
}
