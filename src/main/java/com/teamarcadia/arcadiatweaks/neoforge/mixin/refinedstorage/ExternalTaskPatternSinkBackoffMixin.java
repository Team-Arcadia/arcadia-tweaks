package com.teamarcadia.arcadiatweaks.neoforge.mixin.refinedstorage;

import com.refinedmods.refinedstorage.api.autocrafting.task.ExternalPatternSinkProvider;
import com.refinedmods.refinedstorage.api.resource.list.MutableResourceList;
import com.teamarcadia.arcadiatweaks.common.modules.refinedstorage.RefinedStorageRuntimeFlags;
import com.teamarcadia.arcadiatweaks.neoforge.refinedstorage.ArcadiaIdleBackoff;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * B3 - A running autocrafting task retries a refusing machine less often (see {@link ArcadiaIdleBackoff}).
 *
 * <p>Each step of a processing (external) pattern with batches left to send simulates taking one
 * batch of inputs from the task and simulates inserting it into every machine of the pattern, until
 * one accepts. While the machine is busy, full or locked, that happens again at every step and is
 * refused again. Here a refused attempt makes the next one wait 2 ticks, then 4, 8... up to the
 * configured maximum; an accepted batch brings the pattern back to every step.</p>
 *
 * <p>A skipped attempt returns "not accepted" without touching anything, exactly what a refused
 * attempt returns: the pattern's state, its status and the received outputs are handled by the rest
 * of the step as usual.</p>
 *
 * @author vyrriox
 */
@Mixin(targets = "com.refinedmods.refinedstorage.api.autocrafting.task.ExternalTaskPattern")
public abstract class ExternalTaskPatternSinkBackoffMixin {

    @Unique
    private final ArcadiaIdleBackoff arcadia$backoff = new ArcadiaIdleBackoff();

    /** Tick of the attempt in progress, between the two injections below; MIN_VALUE when none. */
    @Unique
    private long arcadia$attemptTick = Long.MIN_VALUE;

    @Inject(method = "acceptsIterationInputs", at = @At("HEAD"), cancellable = true)
    private void arcadia$skipWhileRefused(MutableResourceList internalStorage, ExternalPatternSinkProvider sinkProvider,
                                          CallbackInfoReturnable<Boolean> cir) {
        if (!RefinedStorageRuntimeFlags.autocraftingSinkBackoff()) {
            arcadia$backoff.reset();
            arcadia$attemptTick = Long.MIN_VALUE;
            return;
        }
        final long now = RefinedStorageRuntimeFlags.serverTicks();
        if (!arcadia$backoff.shouldAttempt(now, sinkProvider)) {
            cir.setReturnValue(false);
            return;
        }
        arcadia$attemptTick = now;
    }

    @Inject(method = "acceptsIterationInputs", at = @At("RETURN"))
    private void arcadia$recordAttempt(MutableResourceList internalStorage, ExternalPatternSinkProvider sinkProvider,
                                       CallbackInfoReturnable<Boolean> cir) {
        if (arcadia$attemptTick != Long.MIN_VALUE && RefinedStorageRuntimeFlags.autocraftingSinkBackoff()) {
            arcadia$backoff.record(arcadia$attemptTick, cir.getReturnValueZ(), RefinedStorageRuntimeFlags.idleBackoffMaxTicks());
        }
        arcadia$attemptTick = Long.MIN_VALUE;
    }
}
