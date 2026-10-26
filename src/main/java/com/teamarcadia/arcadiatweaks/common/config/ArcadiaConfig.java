package com.teamarcadia.arcadiatweaks.common.config;

import com.teamarcadia.arcadiatweaks.common.modules.accessories.AccessoriesConfig;
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
    public static final ModConfigSpec.BooleanValue MODULE_ACCESSORIES_ENABLED;

    public static final ModConfigSpec.BooleanValue ENTITY_MOVE_GUARD_ENABLED;
    public static final ModConfigSpec.DoubleValue ENTITY_MOVE_GUARD_LIMIT;
    public static final ModConfigSpec.BooleanValue RUNIC_FORGE_LOCK_ENABLED;

    public static final BotanyConfig BOTANY;
    public static final RefinedStorageConfig REFINED_STORAGE;
    public static final MekanismConfig MEKANISM;
    public static final WaystonesConfig WAYSTONES;
    public static final AccessoriesConfig ACCESSORIES;

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
        MODULE_ACCESSORIES_ENABLED = BUILDER
                .comment("Enable the Accessories (wisp-forest) optimization module.")
                .define("accessories", true);
        BUILDER.pop();

        BUILDER.comment("Server safety guards").push("safety");
        ENTITY_MOVE_GUARD_ENABLED = BUILDER
                .comment("Stop an entity whose movement for one tick is NaN, infinite or above the limit.",
                        "Such a movement makes the collision search walk the whole path block by block and",
                        "freezes the server until the watchdog kills it (Server1, 2026-10-17, an Aether Sheepuff",
                        "moving -10276 blocks per tick).")
                .define("entityMoveGuard", true);
        ENTITY_MOVE_GUARD_LIMIT = BUILDER
                .comment("Largest movement per tick on one axis, in blocks. Nothing legitimate comes close:",
                        "an elytra with rockets moves about 3 blocks per tick.")
                .defineInRange("entityMoveGuardLimit", 100.0, 10.0, 100000.0);
        RUNIC_FORGE_LOCK_ENABLED = BUILDER
                .comment("Simply Swords Runic Forge: a unique weapon that never got an awakening level counts as",
                        "fully awakened (8 tablets), and the forge turned those 8 implicit tablets into real",
                        "items (bug #321). The implicit level is locked on the weapon instead: the forge gives",
                        "no tablet for it, the weapon keeps its power, and only the free tablet slots accept one.")
                .define("runicForgeImplicitLevelLock", true);
        BUILDER.pop();

        BOTANY = new BotanyConfig(BUILDER);
        REFINED_STORAGE = new RefinedStorageConfig(BUILDER);
        MEKANISM = new MekanismConfig(BUILDER);
        WAYSTONES = new WaystonesConfig(BUILDER);
        ACCESSORIES = new AccessoriesConfig(BUILDER);

        SPEC = BUILDER.build();
    }

    private ArcadiaConfig() {}
}
