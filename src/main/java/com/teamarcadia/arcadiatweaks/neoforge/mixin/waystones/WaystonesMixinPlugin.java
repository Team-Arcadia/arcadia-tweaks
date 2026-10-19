package com.teamarcadia.arcadiatweaks.neoforge.mixin.waystones;

import com.teamarcadia.arcadiatweaks.ArcadiaTweaks;
import com.teamarcadia.arcadiatweaks.neoforge.mixin.ArcadiaMixinTargetInspector;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class WaystonesMixinPlugin implements IMixinConfigPlugin {

    private static final String WAYSTONE = "net.blay09.mods.waystones.api.Waystone";
    private static final String WAYSTONE_MANAGER = "net.blay09.mods.waystones.core.WaystoneManagerImpl";
    private static final String FTB_TEAMS_API = "dev.ftb.mods.ftbteams.api.FTBTeamsAPI";
    private static final String FTB_TEAMS_API_INSTANCE = "dev.ftb.mods.ftbteams.api.FTBTeamsAPI$API";
    private static final String FTB_TEAM_MANAGER = "dev.ftb.mods.ftbteams.api.TeamManager";
    private static final Set<String> LOGGED_SKIPS = new HashSet<>();

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        final String simpleMixinName = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        final boolean compatible = switch (simpleMixinName) {
            case "WaystoneIndexManagerMixin" -> ArcadiaMixinTargetInspector.hasMethod(targetClassName, "getTeamTargets",
                    "(Lnet/minecraft/server/level/ServerPlayer;)Ljava/util/Collection;")
                    && waystonesApiCompatible()
                    && ftbTeamsApiCompatible();
            default -> true;
        };
        if (!compatible && LOGGED_SKIPS.add(simpleMixinName)) {
            ArcadiaTweaks.LOGGER.warn("[waystones] Skipping {}: target structure changed in {}, or FTB Teams is not installed.",
                    simpleMixinName, targetClassName);
        }
        return compatible;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    private static boolean waystonesApiCompatible() {
        return ArcadiaMixinTargetInspector.hasMethod(WAYSTONE_MANAGER, "get",
                "(Lnet/minecraft/server/MinecraftServer;)Lnet/blay09/mods/waystones/core/WaystoneManagerImpl;")
                && ArcadiaMixinTargetInspector.hasMethod(WAYSTONE_MANAGER, "getWaystones", "()Ljava/util/stream/Stream;")
                && ArcadiaMixinTargetInspector.hasMethod(WAYSTONE, "getOwnerUid", "()Ljava/util/UUID;")
                && ArcadiaMixinTargetInspector.hasMethod(WAYSTONE, "getVisibility",
                "()Lnet/blay09/mods/waystones/api/WaystoneVisibility;");
    }

    private static boolean ftbTeamsApiCompatible() {
        return ArcadiaMixinTargetInspector.hasMethod(FTB_TEAMS_API, "api",
                "()Ldev/ftb/mods/ftbteams/api/FTBTeamsAPI$API;")
                && ArcadiaMixinTargetInspector.hasMethod(FTB_TEAMS_API_INSTANCE, "isManagerLoaded", "()Z")
                && ArcadiaMixinTargetInspector.hasMethod(FTB_TEAMS_API_INSTANCE, "getManager",
                "()Ldev/ftb/mods/ftbteams/api/TeamManager;")
                && ArcadiaMixinTargetInspector.hasMethod(FTB_TEAM_MANAGER, "arePlayersInSameTeam",
                "(Ljava/util/UUID;Ljava/util/UUID;)Z");
    }
}
