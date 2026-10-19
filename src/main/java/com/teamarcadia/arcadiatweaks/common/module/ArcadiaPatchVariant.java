package com.teamarcadia.arcadiatweaks.common.module;

import com.teamarcadia.arcadiatweaks.ArcadiaTweaks;

public final class ArcadiaPatchVariant {

    private static final String BOTANY_MIXIN_CONFIG = ArcadiaTweaks.MOD_ID + ".botany.mixins.json";
    private static final String REFINED_STORAGE_MIXIN_CONFIG = ArcadiaTweaks.MOD_ID + ".refinedstorage.mixins.json";
    private static final String MEKANISM_MIXIN_CONFIG = ArcadiaTweaks.MOD_ID + ".mekanism.mixins.json";
    private static final String WAYSTONES_MIXIN_CONFIG = ArcadiaTweaks.MOD_ID + ".waystones.mixins.json";

    private static final boolean BOTANY_PRESENT = hasResource(BOTANY_MIXIN_CONFIG);
    private static final boolean REFINED_STORAGE_PRESENT = hasResource(REFINED_STORAGE_MIXIN_CONFIG);
    private static final boolean MEKANISM_PRESENT = hasResource(MEKANISM_MIXIN_CONFIG);
    private static final boolean WAYSTONES_PRESENT = hasResource(WAYSTONES_MIXIN_CONFIG);
    private static final int PATCH_COUNT = (BOTANY_PRESENT ? 1 : 0)
            + (REFINED_STORAGE_PRESENT ? 1 : 0)
            + (MEKANISM_PRESENT ? 1 : 0)
            + (WAYSTONES_PRESENT ? 1 : 0);

    private ArcadiaPatchVariant() {}

    public static boolean hasBotany() {
        return BOTANY_PRESENT;
    }

    public static boolean hasRefinedStorage() {
        return REFINED_STORAGE_PRESENT;
    }

    public static boolean hasMekanism() {
        return MEKANISM_PRESENT;
    }

    public static boolean hasWaystones() {
        return WAYSTONES_PRESENT;
    }

    public static String commandRoot() {
        if (PATCH_COUNT == 1) {
            if (BOTANY_PRESENT) {
                return "botanypatch";
            }
            if (REFINED_STORAGE_PRESENT) {
                return "refinedstoragepatch";
            }
            if (MEKANISM_PRESENT) {
                return "mekanismpatch";
            }
            if (WAYSTONES_PRESENT) {
                return "waystonespatch";
            }
        }
        return ArcadiaTweaks.MOD_ID;
    }

    public static String menuTitle() {
        if (PATCH_COUNT == 1) {
            if (BOTANY_PRESENT) {
                return "BotanyPatch Admin";
            }
            if (REFINED_STORAGE_PRESENT) {
                return "RefinedStoragePatch Admin";
            }
            if (MEKANISM_PRESENT) {
                return "MekanismPatch Admin";
            }
            if (WAYSTONES_PRESENT) {
                return "WaystonesPatch Admin";
            }
        }
        return "ArcadiaTweaks Admin";
    }

    private static boolean hasResource(String name) {
        final ClassLoader loader = ArcadiaPatchVariant.class.getClassLoader();
        return loader.getResource(name) != null;
    }
}
