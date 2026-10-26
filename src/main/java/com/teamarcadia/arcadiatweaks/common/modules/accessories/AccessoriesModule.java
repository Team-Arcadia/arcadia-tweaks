package com.teamarcadia.arcadiatweaks.common.modules.accessories;

import com.teamarcadia.arcadiatweaks.ArcadiaTweaks;
import com.teamarcadia.arcadiatweaks.common.config.ArcadiaConfig;
import com.teamarcadia.arcadiatweaks.common.module.ArcadiaModule;
import com.teamarcadia.arcadiatweaks.common.module.ArcadiaPatchVariant;

/**
 * Accessories (wisp-forest) optimizations: C1 (dead scan in {@code Inventory.contains(ItemStack)})
 * and T-A (allocation-trimmed entity tick).
 *
 * @author vyrriox
 */
public final class AccessoriesModule implements ArcadiaModule {

    @Override
    public String id() {
        return "accessories";
    }

    @Override
    public boolean enabledByConfig() {
        return ArcadiaPatchVariant.hasAccessories() && ArcadiaConfig.MODULE_ACCESSORIES_ENABLED.get();
    }

    @Override
    public void onServerStarting() {
        ArcadiaTweaks.LOGGER.info(
                "[accessories] Active strategies: C1={} (patch {}), T-A={} (patch {})",
                ArcadiaConfig.ACCESSORIES.containsFastPath.get(),
                AccessoriesTweaks.containsPatchState(),
                ArcadiaConfig.ACCESSORIES.tickAllocationTrimmed.get(),
                AccessoriesTweaks.tickPatchState()
        );
    }
}
