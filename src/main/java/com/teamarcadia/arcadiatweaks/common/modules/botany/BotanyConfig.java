package com.teamarcadia.arcadiatweaks.common.modules.botany;

import com.teamarcadia.arcadiatweaks.common.config.ArcadiaConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class BotanyConfig {

    public final ModConfigSpec.BooleanValue s1MatchesCache;
    public final ModConfigSpec.BooleanValue s2TickCoalescing;
    public final ModConfigSpec.IntValue    s2CoalesceN;
    public final ModConfigSpec.BooleanValue s3HopperBackoff;
    public final ModConfigSpec.IntValue    s3HopperBackoffMinTicks;
    public final ModConfigSpec.IntValue    s3HopperBackoffMaxTicks;
    public final ModConfigSpec.BooleanValue a1RequiredGrowthTicksCache;
    public final ModConfigSpec.BooleanValue a2LightFlagDowngrade;
    public final ModConfigSpec.IntValue    safetyRevalidatePeriod;

    public BotanyConfig(ModConfigSpec.Builder b) {
        b.comment(
                "BotanyPots optimizations. Each entry is a kill-switch - set false to disable",
                "the corresponding Mixin path while keeping the rest active."
        ).push("botany");

        s1MatchesCache = b
                .comment(
                        "S1 - Memoize Crop/Soil matches() results between slot/state changes.",
                        "Eliminates 2-3 Ingredient.test() calls per pot per tick. ~70-85% CPU cut."
                )
                .define("matches_cache_enabled", true);

        s2TickCoalescing = b
                .comment(
                        "S2 - Tick coalescing. Run the full tick logic once every N game ticks",
                        "instead of every tick. Linear gain. Set N=1 (or false) to disable."
                )
                .define("tick_coalescing_enabled", true);

        s2CoalesceN = b
                .comment("S2 - N value (1=off, 2=fast, 4=balanced, 8=lazy). Above 4 may be perceptible.")
                .defineInRange("tick_coalescing_n", 4, 1, 16);

        s3HopperBackoff = b
                .comment(
                        "S3 - Hopper-pot output backoff. Skip the export attempt for K ticks",
                        "after a failed insertion (downstream full / no inventory below)."
                )
                .define("hopper_backoff_enabled", true);

        s3HopperBackoffMinTicks = b
                .comment("S3 - Minimum backoff window after the first failed insertion.")
                .defineInRange("hopper_backoff_min_ticks", 16, 1, 200);

        s3HopperBackoffMaxTicks = b
                .comment("S3 - Maximum backoff window (exponential cap).")
                .defineInRange("hopper_backoff_max_ticks", 64, 1, 1200);

        a1RequiredGrowthTicksCache = b
                .comment("A1 - Memoize Helpers.getRequiredGrowthTicks per BlockEntity (invalidate on slot change).")
                .define("required_growth_ticks_cache_enabled", true);

        a2LightFlagDowngrade = b
                .comment("A2 - Downgrade sendBlockUpdated flags from 3 to 2 when only inventory changed (no light delta).")
                .define("light_flag_downgrade_enabled", false);

        safetyRevalidatePeriod = b
                .comment("Periodic full revalidation as a safety net for cached invariants (S1, A1). 0 disables.")
                .defineInRange("safety_revalidate_period_ticks", 200, 0, 12000);

        b.pop();
    }

    /**
     * A BotanyPots strategy runs only while both the [modules] botany switch and its own switch
     * are on, so turning the module off in the config or the admin menu stops every strategy.
     */
    public boolean enabled(ModConfigSpec.BooleanValue strategy) {
        return ArcadiaConfig.MODULE_BOTANY_ENABLED.get() && strategy.get();
    }
}
