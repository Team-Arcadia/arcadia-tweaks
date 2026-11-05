package com.teamarcadia.arcadiatweaks.neoforge.mixin.refinedstorage;

import com.refinedmods.refinedstorage.api.network.impl.node.AbstractNetworkNode;
import com.refinedmods.refinedstorage.api.network.impl.node.exporter.ExporterNetworkNode;
import com.refinedmods.refinedstorage.api.network.node.SchedulingMode;
import com.teamarcadia.arcadiatweaks.common.modules.refinedstorage.RefinedStorageRuntimeFlags;
import com.teamarcadia.arcadiatweaks.neoforge.refinedstorage.ArcadiaExporterActivity;
import com.teamarcadia.arcadiatweaks.neoforge.refinedstorage.ArcadiaIdleBackoff;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * B2 - An exporter whose filters export nothing tries less often (see {@link ArcadiaIdleBackoff}).
 *
 * <p>A work cycle counts as active when one of its filters exported or started an autocrafting
 * task ({@link ArcadiaExporterActivity}). Only the filter attempts are skipped: the node still draws
 * its energy every work cycle. Any change of filters, scheduling mode or transfer strategy brings it
 * back to every cycle.</p>
 *
 * @author vyrriox
 */
@Mixin(ExporterNetworkNode.class)
public abstract class ExporterIdleBackoffMixin extends AbstractNetworkNode {

    @Unique
    private final ArcadiaIdleBackoff arcadia$backoff = new ArcadiaIdleBackoff();

    @Redirect(method = "doWork",
            at = @At(value = "INVOKE",
                    target = "Lcom/refinedmods/refinedstorage/api/network/node/SchedulingMode;execute(Ljava/util/List;)V"))
    private void arcadia$executeUnlessIdle(SchedulingMode mode, List<? extends SchedulingMode.ScheduledTask> tasks) {
        if (!RefinedStorageRuntimeFlags.exporterIdleBackoff()) {
            arcadia$backoff.reset();
            mode.execute(tasks);
            return;
        }
        final long now = RefinedStorageRuntimeFlags.serverTicks();
        if (!arcadia$backoff.shouldAttempt(now, network)) {
            return;
        }
        final boolean outerActive = ArcadiaExporterActivity.begin();
        try {
            mode.execute(tasks);
        } finally {
            final boolean active = ArcadiaExporterActivity.end(outerActive);
            arcadia$backoff.record(now, active, RefinedStorageRuntimeFlags.idleBackoffMaxTicks());
        }
    }

    @Inject(method = {"setTransferStrategy", "setFilters", "setSchedulingMode"}, at = @At("TAIL"))
    private void arcadia$resetBackoff(CallbackInfo ci) {
        arcadia$backoff.reset();
    }
}
