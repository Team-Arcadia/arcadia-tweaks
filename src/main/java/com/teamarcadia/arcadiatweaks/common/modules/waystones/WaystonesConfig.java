package com.teamarcadia.arcadiatweaks.common.modules.waystones;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class WaystonesConfig {

    public final ModConfigSpec.BooleanValue ftbTeamsTeamVisibility;

    public WaystonesConfig(ModConfigSpec.Builder b) {
        b.comment(
                "Waystones fixes. Each entry is a kill-switch - set false to disable",
                "the corresponding Mixin path while keeping the rest active."
        ).push("waystones");

        ftbTeamsTeamVisibility = b
                .comment(
                        "W1 - Resolve \"Visible to Team\" waystones through FTB Teams.",
                        "Waystones resolves TEAM visibility from the owner's vanilla scoreboard team. Any mod that",
                        "puts players into scoreboard teams for another reason (tab list sorting, name colours)",
                        "then shares TEAM waystones with everyone in that scoreboard team.",
                        "When enabled, a TEAM waystone is only offered to players in the owner's FTB Teams party.",
                        "Allies are not included. A player without a party only sees their own TEAM waystones.",
                        "Without FTB Teams the Mixin is skipped and Waystones keeps its own behaviour."
                )
                .define("team_visibility_uses_ftb_teams", true);

        b.pop();
    }
}
