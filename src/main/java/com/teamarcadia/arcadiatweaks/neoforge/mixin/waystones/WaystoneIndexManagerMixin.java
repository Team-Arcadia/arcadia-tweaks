package com.teamarcadia.arcadiatweaks.neoforge.mixin.waystones;

import com.teamarcadia.arcadiatweaks.common.config.ArcadiaConfig;
import com.teamarcadia.arcadiatweaks.neoforge.waystones.ArcadiaWaystoneTeamVisibility;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.TeamManager;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.api.WaystoneVisibility;
import net.blay09.mods.waystones.core.WaystoneIndexManager;
import net.blay09.mods.waystones.core.WaystoneManagerImpl;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * W1 - Resolve "Visible to Team" waystones through FTB Teams.
 *
 * Waystones looks up the owner's vanilla scoreboard team by username and offers
 * the waystone to every player of that scoreboard team. When another mod buckets
 * players into scoreboard teams for its own purpose (on Arcadia, the tab list
 * sorts players by permission group), a TEAM waystone reaches everyone sharing
 * the owner's bucket and shows up in their teleport list.
 *
 * This replaces only the read path that builds a player's TEAM targets: a TEAM
 * waystone is offered to its owner and to players in the owner's FTB Teams party.
 * The scoreboard index kept by Waystones is left untouched.
 */
@Mixin(WaystoneIndexManager.class)
public abstract class WaystoneIndexManagerMixin {

    @Inject(method = "getTeamTargets", at = @At("HEAD"), cancellable = true)
    private static void arcadia$resolveTeamTargetsWithFtbTeams(
            ServerPlayer player,
            CallbackInfoReturnable<Collection<Waystone>> cir) {
        if (!ArcadiaConfig.MODULE_WAYSTONES_ENABLED.get()
                || !ArcadiaConfig.WAYSTONES.ftbTeamsTeamVisibility.get()) {
            return;
        }
        final MinecraftServer server = player.getServer();
        if (server == null || !FTBTeamsAPI.api().isManagerLoaded()) {
            // Fail closed: without team data, never fall back to the scoreboard lookup.
            cir.setReturnValue(List.of());
            return;
        }
        final TeamManager teams = FTBTeamsAPI.api().getManager();
        final UUID viewer = player.getUUID();
        cir.setReturnValue(WaystoneManagerImpl.get(server).getWaystones()
                .filter(waystone -> waystone.getVisibility() == WaystoneVisibility.TEAM)
                .filter(waystone -> ArcadiaWaystoneTeamVisibility.isTeamTarget(
                        viewer, waystone.getOwnerUid(), teams::arePlayersInSameTeam))
                .toList());
    }
}
