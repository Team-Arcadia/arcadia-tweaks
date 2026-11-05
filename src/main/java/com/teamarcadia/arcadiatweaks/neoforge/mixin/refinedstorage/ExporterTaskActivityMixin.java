package com.teamarcadia.arcadiatweaks.neoforge.mixin.refinedstorage;

import com.refinedmods.refinedstorage.api.network.node.exporter.ExporterTransferStrategy;
import com.teamarcadia.arcadiatweaks.neoforge.refinedstorage.ArcadiaExporterActivity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * B2 - Reports each exporter filter result to {@link ArcadiaExporterActivity}: exported and
 * autocrafting started count as activity. Reads the result only; the filter runs unchanged.
 *
 * @author vyrriox
 */
@Mixin(targets = "com.refinedmods.refinedstorage.api.network.impl.node.exporter.ExporterNetworkNode$ExporterTask")
public abstract class ExporterTaskActivityMixin {

    @Shadow private ExporterTransferStrategy.Result lastResult;

    @Inject(method = "run", at = @At("RETURN"))
    private void arcadia$reportActivity(CallbackInfoReturnable<Boolean> cir) {
        if (lastResult == ExporterTransferStrategy.Result.EXPORTED
                || lastResult == ExporterTransferStrategy.Result.AUTOCRAFTING_STARTED) {
            ArcadiaExporterActivity.markActive();
        }
    }
}
