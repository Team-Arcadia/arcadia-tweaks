package com.teamarcadia.arcadiatweaks.neoforge.gametest;

import com.mojang.authlib.GameProfile;
import com.teamarcadia.arcadiatweaks.ArcadiaTweaks;
import com.teamarcadia.arcadiatweaks.common.module.ArcadiaPatchVariant;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.data.PartyTeam;
import dev.ftb.mods.ftbteams.data.TeamManagerImpl;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.api.WaystoneOrigin;
import net.blay09.mods.waystones.api.WaystoneTypes;
import net.blay09.mods.waystones.api.WaystoneVisibility;
import net.blay09.mods.waystones.core.WaystoneImpl;
import net.blay09.mods.waystones.core.WaystoneIndexManager;
import net.blay09.mods.waystones.core.WaystoneManagerImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.UUID;

/**
 * In-game check of the Waystones TEAM visibility fix ({@code WaystoneIndexManagerMixin}).
 *
 * Three fake players share one vanilla scoreboard team, the situation that leaked TEAM
 * waystones on Arcadia (the tab list buckets players into scoreboard teams). Only the owner and
 * the owner's FTB Teams party member must be offered the owner's TEAM waystone; the outsider
 * shares the scoreboard team but not the party, and must not see it.
 *
 * @author vyrriox
 */
@GameTestHolder(ArcadiaTweaks.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WaystoneTeamVisibilityGameTests {

    private static final String TEMPLATE = "empty";
    private static final String BATCH = ArcadiaTweaks.MOD_ID;

    private WaystoneTeamVisibilityGameTests() {}

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void waystonesTeamWaystoneFollowsFtbParty(GameTestHelper helper) {
        if (!ArcadiaPatchVariant.hasWaystones()) {
            helper.succeed();
            return;
        }
        final ServerLevel level = helper.getLevel();
        final MinecraftServer server = level.getServer();
        helper.assertTrue(FTBTeamsAPI.api().isManagerLoaded(), "FTB Teams manager should be loaded on the test server");

        final String suffix = UUID.randomUUID().toString().substring(0, 6);
        final ServerPlayer owner = fakePlayer(level, "arcws_o_" + suffix);
        final ServerPlayer member = fakePlayer(level, "arcws_m_" + suffix);
        final ServerPlayer outsider = fakePlayer(level, "arcws_x_" + suffix);

        final Scoreboard scoreboard = server.getScoreboard();
        final PlayerTeam bucket = scoreboard.addPlayerTeam("arcws_" + suffix);
        for (ServerPlayer player : new ServerPlayer[] {owner, member, outsider}) {
            scoreboard.addPlayerToTeam(player.getScoreboardName(), bucket);
        }

        final TeamManagerImpl teams = (TeamManagerImpl) FTBTeamsAPI.api().getManager();
        PartyTeam party = null;
        final WaystoneImpl waystone = new WaystoneImpl(WaystoneTypes.WAYSTONE, UUID.randomUUID(), level.dimension(),
                helper.absolutePos(BlockPos.ZERO), WaystoneOrigin.PLAYER, owner.getUUID());
        try {
            for (ServerPlayer player : new ServerPlayer[] {owner, member, outsider}) {
                registerWithFtbTeams(teams, player);
            }
            party = teams.createParty(owner, "arcws_party_" + suffix);
            party.join(member);
            helper.assertTrue(teams.arePlayersInSameTeam(owner.getUUID(), member.getUUID()),
                    "Member should share the owner's FTB party");
            helper.assertFalse(teams.arePlayersInSameTeam(owner.getUUID(), outsider.getUUID()),
                    "Outsider should not share the owner's FTB party");

            waystone.setName(Component.literal("Arcadia team test " + suffix));
            waystone.setOwnerUsername(owner.getScoreboardName());
            waystone.setVisibility(WaystoneVisibility.TEAM);
            WaystoneManagerImpl.get(server).addWaystone(waystone);
            WaystoneIndexManager.visibilityChanged(server, waystone, WaystoneVisibility.ACTIVATION);

            // Precondition: Waystones' own index offers the waystone to the whole scoreboard
            // team, outsider included. That is the leak the mixin has to override.
            helper.assertTrue(scoreboardIndexOffers(bucket.getName(), waystone.getWaystoneUid()),
                    "Waystones' scoreboard index should list the TEAM waystone under the shared scoreboard team");

            helper.assertTrue(offers(owner, waystone), "Owner should be offered their TEAM waystone");
            helper.assertTrue(offers(member, waystone), "FTB party member should be offered the owner's TEAM waystone");
            helper.assertFalse(offers(outsider, waystone),
                    "Player sharing only the scoreboard team should not be offered the owner's TEAM waystone");
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new IllegalStateException("FTB Teams refused to set up the test party: " + e.getMessage(), e);
        } finally {
            WaystoneManagerImpl.get(server).removeWaystone(waystone);
            WaystoneIndexManager.waystoneRemoved(waystone);
            scoreboard.removePlayerTeam(bucket);
            if (party != null) {
                leaveQuietly(party, member.getUUID());
                leaveQuietly(party, owner.getUUID());
            }
        }
        helper.succeed();
    }

    private static ServerPlayer fakePlayer(ServerLevel level, String name) {
        return FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), name));
    }

    /**
     * Creates the FTB personal team of a fake player, as FTB Teams does on login. Login ends by
     * sending the team data to the player; a FakePlayer has no network channel, so that last
     * step throws once the team is already registered. Any other failure is rethrown.
     */
    private static void registerWithFtbTeams(TeamManagerImpl teams, ServerPlayer player) {
        try {
            teams.playerLoggedIn(player, player.getUUID(), player.getScoreboardName());
        } catch (NullPointerException e) {
            if (teams.getPlayerTeamForPlayerID(player.getUUID()).isEmpty()) {
                throw e;
            }
        }
    }

    private static boolean offers(ServerPlayer player, Waystone waystone) {
        final Collection<Waystone> targets = WaystoneIndexManager.getTargets(player);
        return targets.stream().anyMatch(target -> target.getWaystoneUid().equals(waystone.getWaystoneUid()));
    }

    @SuppressWarnings("unchecked")
    private static boolean scoreboardIndexOffers(String teamName, UUID waystoneUid) {
        try {
            final Field field = WaystoneIndexManager.class.getDeclaredField("waystonesByTeamName");
            field.setAccessible(true);
            final com.google.common.collect.SetMultimap<String, UUID> index =
                    (com.google.common.collect.SetMultimap<String, UUID>) field.get(null);
            return index.containsEntry(teamName, waystoneUid);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to read the Waystones team index", e);
        }
    }

    private static void leaveQuietly(PartyTeam party, UUID player) {
        try {
            party.leave(player);
        } catch (Exception e) {
            ArcadiaTweaks.LOGGER.debug("GameTest party cleanup: {} could not leave: {}", player, e.getMessage());
        }
    }
}
