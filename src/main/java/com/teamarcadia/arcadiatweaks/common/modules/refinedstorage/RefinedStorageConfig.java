package com.teamarcadia.arcadiatweaks.common.modules.refinedstorage;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class RefinedStorageConfig {

    public final ModConfigSpec.BooleanValue activenessCheckCoalescing;
    public final ModConfigSpec.IntValue activenessCheckInterval;
    public final ModConfigSpec.BooleanValue singleComponentLookup;
    public final ModConfigSpec.BooleanValue storageSaveCache;
    public final ModConfigSpec.IntValue storageSaveCacheVerifyInterval;
    public final ModConfigSpec.BooleanValue quietHotDebugLogs;

    public RefinedStorageConfig(ModConfigSpec.Builder b) {
        b.comment(
                "Refined Storage optimizations. Each entry is a kill-switch - set false to disable",
                "the corresponding Mixin path while keeping the rest active."
        ).push("refinedstorage");

        activenessCheckCoalescing = b
                .comment(
                        "S1 - Coalesce network-node activeness checks.",
                        "RS2 recalculates level-loaded, redstone and network-energy state every block-entity tick.",
                        "This keeps node work cadence unchanged and only runs that activeness check every N ticks.",
                        "RS2 applies a pending change through its own 20-tick debounce; the skipped ticks are",
                        "added to that debounce, so a change (redstone, energy, network) is applied at most N-1",
                        "ticks earlier or later than in plain RS. Detectors are never coalesced."
                )
                .define("activeness_check_coalescing_enabled", true);

        activenessCheckInterval = b
                .comment("S1 - Interval in ticks for activeness recalculation. 1 disables coalescing.")
                .defineInRange("activeness_check_interval_ticks", 20, 1, 200);

        singleComponentLookup = b
                .comment(
                        "S2 - Single hash lookup in RS network component access.",
                        "RS2 looks every network component up twice (containsKey then get), on every node work",
                        "cycle (energy) and in storage, graph and autocrafting code. This does one get and only",
                        "checks containsKey when the value is null. Same results, same exception for a missing",
                        "component."
                )
                .define("single_component_lookup_enabled", true);

        storageSaveCache = b
                .comment(
                        "S3 - Reuse the saved form of unchanged storage disks.",
                        "Every autosave RS2 re-encodes every disk of the world as soon as one item moved.",
                        "This keeps the encoded form of each disk and reuses it when the disk has not been",
                        "inserted into or extracted from since the last save. Only the encoding time changes:",
                        "the saved data is the same. A disk changed in any way is encoded again."
                )
                .define("storage_save_cache_enabled", true);

        storageSaveCacheVerifyInterval = b
                .comment(
                        "S3 - Safety net: every Nth save, encode the cached disks again and compare.",
                        "On any difference one error is logged, the fresh data is written and the cache is",
                        "turned off until the next restart. 0 never verifies."
                )
                .defineInRange("storage_save_cache_verify_interval", 20, 0, 10000);

        quietHotDebugLogs = b
                .comment(
                        "S4 - Drop two RS2 debug log lines written from the tick.",
                        "'External storage @ ... has changed!' (each external storage scan that sees a change)",
                        "and 'Receiver ... was not found in network for transmitter' (each transmitter retry).",
                        "Only logs/debug.log changes; nothing in the world does."
                )
                .define("quiet_hot_debug_logs_enabled", true);

        b.pop();
    }
}
