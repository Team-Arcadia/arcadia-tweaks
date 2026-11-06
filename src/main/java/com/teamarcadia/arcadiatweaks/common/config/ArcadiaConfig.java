package com.teamarcadia.arcadiatweaks.common.config;

import com.teamarcadia.arcadiatweaks.common.modules.accessories.AccessoriesConfig;
import com.teamarcadia.arcadiatweaks.common.modules.botany.BotanyConfig;
import com.teamarcadia.arcadiatweaks.common.modules.cosmetic.CosmeticConfig;
import com.teamarcadia.arcadiatweaks.common.modules.mekanism.MekanismConfig;
import com.teamarcadia.arcadiatweaks.common.modules.refinedstorage.RefinedStorageConfig;
import com.teamarcadia.arcadiatweaks.common.modules.sparework.SpareWorkConfig;
import com.teamarcadia.arcadiatweaks.common.modules.waystones.WaystonesConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class ArcadiaConfig {

    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue MODULE_BOTANY_ENABLED;
    public static final ModConfigSpec.BooleanValue MODULE_REFINED_STORAGE_ENABLED;
    public static final ModConfigSpec.BooleanValue MODULE_MEKANISM_ENABLED;
    public static final ModConfigSpec.BooleanValue MODULE_WAYSTONES_ENABLED;
    public static final ModConfigSpec.BooleanValue MODULE_COSMETIC_ENABLED;
    public static final ModConfigSpec.BooleanValue MODULE_ACCESSORIES_ENABLED;
    public static final ModConfigSpec.BooleanValue MODULE_SPARE_WORK_ENABLED;

    public static final ModConfigSpec.BooleanValue ENTITY_MOVE_GUARD_ENABLED;
    public static final ModConfigSpec.DoubleValue ENTITY_MOVE_GUARD_LIMIT;
    public static final ModConfigSpec.BooleanValue RUNIC_FORGE_LOCK_ENABLED;
    public static final ModConfigSpec.BooleanValue GLOAM_PURGE_DEFERRED_DISCARD;
    public static final ModConfigSpec.BooleanValue COPYCATS_KEEP_MATERIALS_ON_REMOVE;
    public static final ModConfigSpec.BooleanValue CLIENT_BLOCK_ENTITY_THREAD_GUARD;
    public static final ModConfigSpec.BooleanValue MACAWS_GARAGE_DOOR_GUARD;

    public static final BotanyConfig BOTANY;
    public static final RefinedStorageConfig REFINED_STORAGE;
    public static final MekanismConfig MEKANISM;
    public static final WaystonesConfig WAYSTONES;
    public static final CosmeticConfig COSMETIC;
    public static final AccessoriesConfig ACCESSORIES;
    public static final SpareWorkConfig SPARE_WORK;

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
        MODULE_COSMETIC_ENABLED = BUILDER
                .comment("Enable the CosmeticWeapons / CosmeticArmours optimization module.")
                .define("cosmetic", true);
        MODULE_ACCESSORIES_ENABLED = BUILDER
                .comment("Enable the Accessories (wisp-forest) optimization module.")
                .define("accessories", true);
        MODULE_SPARE_WORK_ENABLED = BUILDER
                .comment("Enable the spare work module: work other mods and the game repeat for nothing, skipped",
                        "with the same results (FastSuite, Simply Swords, Crabber's Delight, structure lookups).")
                .define("sparework", true);
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
        GLOAM_PURGE_DEFERRED_DISCARD = BUILDER
                .comment("Simply Swords: the gloam stain purge discarded orphan stain visuals while walking the",
                        "level's entity index, which shrinks under it and crashes the server (bug #328, Server1,",
                        "a dungeon teleport). The stains are collected during the walk and discarded after it.")
                .define("gloamStainPurgeDeferredDiscard", true);
        COPYCATS_KEEP_MATERIALS_ON_REMOVE = BUILDER
                .comment("Copycats+: a removed multi-state copycat block entity emptied its material storage while",
                        "Sodium could still be building a chunk mesh with it, which crashed the client with a",
                        "NullPointerException in IMultiStateCopycatBlockEntity.isCTEnabled (2026-11-02). The",
                        "materials are kept on removal.")
                .define("copycatsKeepMaterialsOnRemove", true);
        CLIENT_BLOCK_ENTITY_THREAD_GUARD = BUILDER
                .comment("Client only, read from each player's own config. A block entity read or write on a client",
                        "chunk from a thread other than the render thread could change the chunk's block entity",
                        "maps under the render thread, which crashed the client (ConcurrentModificationException",
                        "in LevelChunk.updateBlockEntityTicker, ArrayIndexOutOfBoundsException in",
                        "Object2ObjectOpenHashMap.rehash, 2026-11-03). Such reads now only read and such writes run",
                        "on the render thread. The first five are logged with the calling mod's stack trace.")
                .define("clientBlockEntityThreadGuard", true);
        MACAWS_GARAGE_DOOR_GUARD = BUILDER
                .comment("Macaw's Doors: a garage door toggles the doors next to it, which toggle the doors next to them,",
                        "until they all have the new state. A write the level refuses never gets there, so two doors side",
                        "by side called each other until a StackOverflowError crashed the game (2026-11-06, a client clicking",
                        "a double garage door in a Bobby cached chunk, which refuses every write). A door whose write was",
                        "refused no longer toggles its neighbours. Read from each side's own config. Without a refused",
                        "write nothing changes. Turns itself off, with a warning, on a Macaw's Doors build other than 1.1.5.")
                .define("macawsGarageDoorGuard", true);
        BUILDER.pop();

        BOTANY = new BotanyConfig(BUILDER);
        REFINED_STORAGE = new RefinedStorageConfig(BUILDER);
        MEKANISM = new MekanismConfig(BUILDER);
        WAYSTONES = new WaystonesConfig(BUILDER);
        COSMETIC = new CosmeticConfig(BUILDER);
        ACCESSORIES = new AccessoriesConfig(BUILDER);
        SPARE_WORK = new SpareWorkConfig(BUILDER);

        SPEC = BUILDER.build();
    }

    private ArcadiaConfig() {}
}
