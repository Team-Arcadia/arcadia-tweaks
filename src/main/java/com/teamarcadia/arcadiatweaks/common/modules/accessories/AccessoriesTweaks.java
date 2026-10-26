package com.teamarcadia.arcadiatweaks.common.modules.accessories;

import com.teamarcadia.arcadiatweaks.common.config.ArcadiaConfig;

/**
 * Runtime switches and patch state of the Accessories module. Holds no Accessories type, so it is
 * safe to load in every jar variant and without Accessories installed.
 *
 * @author vyrriox
 */
public final class AccessoriesTweaks {

    /** Patch state written by the Mixin plugin: "not applied" until a patch is installed. */
    private static volatile String containsPatchState = "not applied";
    private static volatile String tickPatchState = "not applied";

    private AccessoriesTweaks() {}

    /** C1 switch, read on every {@code Inventory.contains(ItemStack)} call on the server. */
    public static boolean containsFastPathEnabled() {
        return ArcadiaConfig.MODULE_ACCESSORIES_ENABLED.get() && ArcadiaConfig.ACCESSORIES.containsFastPath.get();
    }

    /** T-A switch, read once per living entity tick on the server. */
    public static boolean tickAllocationTrimmedEnabled() {
        return ArcadiaConfig.MODULE_ACCESSORIES_ENABLED.get() && ArcadiaConfig.ACCESSORIES.tickAllocationTrimmed.get();
    }

    public static String containsPatchState() {
        return containsPatchState;
    }

    public static String tickPatchState() {
        return tickPatchState;
    }

    public static void setContainsPatchState(String state) {
        containsPatchState = state;
    }

    public static void setTickPatchState(String state) {
        tickPatchState = state;
    }
}
