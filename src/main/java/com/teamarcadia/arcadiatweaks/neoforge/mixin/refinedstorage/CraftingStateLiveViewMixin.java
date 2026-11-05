package com.teamarcadia.arcadiatweaks.neoforge.mixin.refinedstorage;

import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.api.resource.list.LazyCopyMutableResourceList;
import com.refinedmods.refinedstorage.api.resource.list.MutableResourceList;
import com.refinedmods.refinedstorage.api.storage.root.RootStorage;
import com.teamarcadia.arcadiatweaks.common.modules.refinedstorage.RefinedStorageRuntimeFlags;
import com.teamarcadia.arcadiatweaks.neoforge.refinedstorage.ArcadiaLiveNetworkList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Collection;
import java.util.List;

/**
 * A1 - An autocrafting calculation no longer starts by copying the whole network.
 *
 * <p>{@code CraftingState.of(rootStorage)} copies every resource of the network into a new list,
 * then wraps it in a lazy copy-on-write list that the calculation reads one resource at a time. With
 * thousands of resources this copy is most of the cost of a calculation, and Refined Storage runs one
 * per pattern of the requested item, twice or more for every exporter, interface or constructor
 * filter that asks to craft a missing item. When the calculation runs on the server thread, nothing
 * can change the network while it runs, so the lazy list can read the network directly
 * ({@link ArcadiaLiveNetworkList}): same values, no copy.</p>
 *
 * <p>Calculations started for the crafting preview run on Refined Storage's own executor threads;
 * they keep the copy, because the server thread may change the network while they run.</p>
 *
 * @author vyrriox
 */
@Mixin(targets = "com.refinedmods.refinedstorage.api.autocrafting.calculation.CraftingState")
public abstract class CraftingStateLiveViewMixin {

    /** The network of the calculation being set up on the server thread, between the two calls below. */
    @Unique
    private static RootStorage arcadia$liveNetwork;

    @Redirect(method = "of",
            at = @At(value = "INVOKE",
                    target = "Lcom/refinedmods/refinedstorage/api/storage/root/RootStorage;getAll()Ljava/util/Collection;"))
    private static Collection<ResourceAmount> arcadia$skipNetworkCopy(RootStorage network) {
        if (RefinedStorageRuntimeFlags.autocraftingLiveView() && RefinedStorageRuntimeFlags.onServerThread()) {
            arcadia$liveNetwork = network;
            return List.of();
        }
        return network.getAll();
    }

    @Redirect(method = "of",
            at = @At(value = "INVOKE",
                    target = "Lcom/refinedmods/refinedstorage/api/resource/list/LazyCopyMutableResourceList;create(Lcom/refinedmods/refinedstorage/api/resource/list/MutableResourceList;)Lcom/refinedmods/refinedstorage/api/resource/list/LazyCopyMutableResourceList;"))
    private static LazyCopyMutableResourceList arcadia$readNetworkLive(MutableResourceList copy) {
        if (RefinedStorageRuntimeFlags.onServerThread()) {
            final RootStorage network = arcadia$liveNetwork;
            if (network != null) {
                arcadia$liveNetwork = null;
                return LazyCopyMutableResourceList.create(new ArcadiaLiveNetworkList(network));
            }
        }
        return LazyCopyMutableResourceList.create(copy);
    }
}
