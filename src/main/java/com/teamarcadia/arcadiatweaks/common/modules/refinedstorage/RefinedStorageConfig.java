package com.teamarcadia.arcadiatweaks.common.modules.refinedstorage;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class RefinedStorageConfig {

    public final ModConfigSpec.BooleanValue activenessCheckCoalescing;
    public final ModConfigSpec.IntValue activenessCheckInterval;
    public final ModConfigSpec.BooleanValue singleComponentLookup;
    public final ModConfigSpec.BooleanValue storageSaveCache;
    public final ModConfigSpec.IntValue storageSaveCacheVerifyInterval;
    public final ModConfigSpec.BooleanValue quietHotDebugLogs;
    public final ModConfigSpec.BooleanValue autocraftingLiveView;
    public final ModConfigSpec.BooleanValue importerIdleBackoff;
    public final ModConfigSpec.BooleanValue exporterIdleBackoff;
    public final ModConfigSpec.BooleanValue autocraftingSinkBackoff;
    public final ModConfigSpec.IntValue idleBackoffMaxTicks;

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

        autocraftingLiveView = b
                .comment(
                        "A1 - Autocrafting calculations read the network instead of copying it.",
                        "RS2 starts every autocrafting calculation by copying the whole network content, and",
                        "runs at least two calculations each time an exporter, interface or constructor with an",
                        "autocrafting upgrade asks for an item it cannot find. On a network of thousands of",
                        "resources that copy is most of the cost. Calculations on the server thread read the",
                        "network directly instead: nothing can change it while they run, so the results are the",
                        "same. Crafting previews, which run on RS2's own threads, keep the copy."
                )
                .define("autocrafting_live_storage_view_enabled", true);

        importerIdleBackoff = b
                .comment(
                        "B1 - Idle importers try less often.",
                        "An importer whose attempts move nothing (empty source, full network, filtered items) tries",
                        "again after 2 ticks, then 4, 8... up to idle_backoff_max_ticks, and is back to every work",
                        "cycle as soon as something moves. Energy use is unchanged. Without speed upgrades an importer",
                        "already works every 9 ticks and is not slowed down with the default maximum."
                )
                .define("importer_idle_backoff_enabled", true);

        exporterIdleBackoff = b
                .comment(
                        "B2 - Idle exporters try less often.",
                        "Same rule for an exporter whose filters export nothing (destination full, item missing).",
                        "Starting an autocrafting task counts as activity, so an exporter waiting for its craft is",
                        "never slowed down."
                )
                .define("exporter_idle_backoff_enabled", true);

        autocraftingSinkBackoff = b
                .comment(
                        "B3 - A running autocrafting task retries a refusing machine less often.",
                        "A processing step whose machine (or autocrafter lock) refuses the next batch of inputs",
                        "tries again after 2 ticks, then 4, 8... up to idle_backoff_max_ticks, and sends at every",
                        "step again once a batch is accepted."
                )
                .define("autocrafting_sink_backoff_enabled", true);

        idleBackoffMaxTicks = b
                .comment(
                        "B1 to B3 - Longest time between two attempts of an idle node, in ticks. The first item",
                        "after an idle period moves at most this many ticks later than in plain RS."
                )
                .defineInRange("idle_backoff_max_ticks", 10, 2, 40);

        b.pop();
    }
}
