package com.teamarcadia.arcadiatweaks.neoforge.mixin.sparework;

import com.teamarcadia.arcadiatweaks.common.modules.sparework.SpareWorkFlags;
import com.teamarcadia.arcadiatweaks.neoforge.sparework.ArcadiaTackleBoxSearch;
import com.teamarcadia.arcadiatweaks.neoforge.sparework.SpareWorkCounters;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Crabber's Delight reads the 343 positions around every player, every tick, to find a placed tackle box.
 * The search is skipped when {@link ArcadiaTackleBoxSearch} proves it finds nothing and changes nothing.
 *
 * @author vyrriox
 */
@Pseudo
@Mixin(targets = "alabaster.crabbersdelight.common.utils.TackleBoxProximity")
public abstract class TackleBoxProximitySkipMixin {

    @Inject(
            method = "findClosestPlaced(Lnet/minecraft/world/entity/player/Player;)Lalabaster/crabbersdelight/common/utils/TackleBoxProximity$TackleBoxAccess;",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private static void arcadia$skipEmptySearch(Player player, CallbackInfoReturnable<Object> result) {
        if (SpareWorkFlags.crabbersDelightSkipEmptyTackleBoxSearch() && ArcadiaTackleBoxSearch.provablyEmpty(player)) {
            SpareWorkCounters.tackleBoxSearchesSkipped++;
            result.setReturnValue(null);
        }
    }
}
