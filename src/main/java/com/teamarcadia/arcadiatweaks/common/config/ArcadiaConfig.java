package com.teamarcadia.arcadiatweaks.common.config;

import com.teamarcadia.arcadiatweaks.common.modules.botany.BotanyConfig;
import com.teamarcadia.arcadiatweaks.common.modules.mekanism.MekanismConfig;
import com.teamarcadia.arcadiatweaks.common.modules.refinedstorage.RefinedStorageConfig;
import com.teamarcadia.arcadiatweaks.common.modules.waystones.WaystonesConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class ArcadiaConfig {

    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue MODULE_BOTANY_ENABLED;
    public static final ModConfigSpec.BooleanValue MODULE_REFINED_STORAGE_ENABLED;
    public static final ModConfigSpec.BooleanValue MODULE_MEKANISM_ENABLED;
    public static final ModConfigSpec.BooleanValue MODULE_WAYSTONES_ENABLED;

    public static final BotanyConfig BOTANY;
    public static final RefinedStorageConfig REFINED_STORAGE;
    public static final MekanismConfig MEKANISM;
    public static final WaystonesConfig WAYSTONES;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.comment("ArcadiaTweaks - master module toggles").push("modules");
        MODULE_BOTANY_ENABLED = BUILDER
                .comment("Enable the BotanyPots optimization module.")
                .define("botany", true);
        MODULE_REFINED_STORAGE_ENABLED = BUILDER
                .comment("Enable the Refined Storage optimization module.")
                .define("refinedstorage", true);
        MODULE_MEKANISM_ENABLED = BUILDER
                .comment("Enable the Mekanism optimization module.")
                .define("mekanism", true);
        MODULE_WAYSTONES_ENABLED = BUILDER
                .comment("Enable the Waystones fix module.")
                .define("waystones", true);
        BUILDER.pop();

        BOTANY = new BotanyConfig(BUILDER);
        REFINED_STORAGE = new RefinedStorageConfig(BUILDER);
        MEKANISM = new MekanismConfig(BUILDER);
        WAYSTONES = new WaystonesConfig(BUILDER);

        SPEC = BUILDER.build();
    }

    private ArcadiaConfig() {}
}
