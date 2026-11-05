package com.teamarcadia.arcadiatweaks.neoforge.refinedstorage;

import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.api.resource.list.MutableResourceList;
import com.refinedmods.refinedstorage.api.resource.list.MutableResourceListImpl;
import com.refinedmods.refinedstorage.api.storage.root.RootStorage;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A1 - Read-only view of a network's storage, used as the base of an autocrafting calculation.
 *
 * <p>Refined Storage starts every calculation by copying the whole network content into a new list
 * ({@code CraftingState.of}), then only ever reads that copy through a lazy copy-on-write list, one
 * resource at a time. This view answers the same reads straight from the network. It is only used
 * for calculations that run on the server thread, during which nothing changes the network, so every
 * read returns what the copy would have held.</p>
 *
 * @author vyrriox
 */
public final class ArcadiaLiveNetworkList implements MutableResourceList {

    /** Views created so far, read by the GameTests to prove the view is used. Server thread only. */
    private static long created;

    private final RootStorage network;

    public ArcadiaLiveNetworkList(RootStorage network) {
        this.network = network;
        created++;
    }

    public static long created() {
        return created;
    }

    @Override
    public long get(ResourceKey resource) {
        return network.get(resource);
    }

    @Override
    public boolean contains(ResourceKey resource) {
        return network.contains(resource);
    }

    @Override
    public Collection<ResourceAmount> copyState() {
        return List.copyOf(network.getAll());
    }

    @Override
    public Set<ResourceKey> getAll() {
        final Set<ResourceKey> all = new HashSet<>();
        for (ResourceAmount amount : network.getAll()) {
            all.add(amount.resource());
        }
        return all;
    }

    @Override
    public boolean isEmpty() {
        return network.getAll().isEmpty();
    }

    @Override
    public MutableResourceList copy() {
        final MutableResourceList copy = MutableResourceListImpl.create();
        network.getAll().forEach(copy::add);
        return copy;
    }

    @Override
    public OperationResult add(ResourceKey resource, long amount) {
        throw new UnsupportedOperationException("read-only view of the network storage");
    }

    @Override
    public OperationResult remove(ResourceKey resource, long amount) {
        throw new UnsupportedOperationException("read-only view of the network storage");
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("read-only view of the network storage");
    }
}
