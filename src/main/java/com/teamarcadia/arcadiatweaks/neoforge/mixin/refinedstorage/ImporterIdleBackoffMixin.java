package com.teamarcadia.arcadiatweaks.neoforge.mixin.refinedstorage;

import com.refinedmods.refinedstorage.api.network.Network;
import com.refinedmods.refinedstorage.api.network.impl.node.importer.ImporterNetworkNode;
import com.refinedmods.refinedstorage.api.network.node.importer.ImporterTransferStrategy;
import com.refinedmods.refinedstorage.api.resource.filter.Filter;
import com.refinedmods.refinedstorage.api.storage.Actor;
import com.teamarcadia.arcadiatweaks.common.modules.refinedstorage.RefinedStorageRuntimeFlags;
import com.teamarcadia.arcadiatweaks.neoforge.refinedstorage.ArcadiaIdleBackoff;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * B1 - An importer whose attempts move nothing tries less often (see {@link ArcadiaIdleBackoff}).
 *
 * <p>Only the transfer attempt is skipped: the node still draws its energy every work cycle, as in
 * plain Refined Storage. Any change of filters or transfer strategy (upgrades, facing) brings it back
 * to every cycle.</p>
 *
 * @author vyrriox
 */
@Mixin(ImporterNetworkNode.class)
public abstract class ImporterIdleBackoffMixin {

    @Unique
    private final ArcadiaIdleBackoff arcadia$backoff = new ArcadiaIdleBackoff();

    @Redirect(method = "doWork",
            at = @At(value = "INVOKE",
                    target = "Lcom/refinedmods/refinedstorage/api/network/node/importer/ImporterTransferStrategy;transfer(Lcom/refinedmods/refinedstorage/api/resource/filter/Filter;Lcom/refinedmods/refinedstorage/api/storage/Actor;Lcom/refinedmods/refinedstorage/api/network/Network;)Z"))
    private boolean arcadia$transferUnlessIdle(ImporterTransferStrategy strategy, Filter filter, Actor actor, Network network) {
        if (!RefinedStorageRuntimeFlags.importerIdleBackoff()) {
            arcadia$backoff.reset();
            return strategy.transfer(filter, actor, network);
        }
        final long now = RefinedStorageRuntimeFlags.serverTicks();
        if (!arcadia$backoff.shouldAttempt(now, network)) {
            return false;
        }
        final boolean moved = strategy.transfer(filter, actor, network);
        arcadia$backoff.record(now, moved, RefinedStorageRuntimeFlags.idleBackoffMaxTicks());
        return moved;
    }

    @Inject(method = {"setTransferStrategy", "setFilters", "setFilterMode", "setNormalizer"}, at = @At("TAIL"))
    private void arcadia$resetBackoff(CallbackInfo ci) {
        arcadia$backoff.reset();
    }
}
