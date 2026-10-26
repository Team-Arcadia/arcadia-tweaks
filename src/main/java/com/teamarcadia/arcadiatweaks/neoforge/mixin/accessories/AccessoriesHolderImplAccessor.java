package com.teamarcadia.arcadiatweaks.neoforge.mixin.accessories;

import io.wispforest.accessories.api.AccessoriesContainer;
import io.wispforest.accessories.impl.AccessoriesHolderImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;

/**
 * Protected members of {@code AccessoriesHolderImpl} read by the T-A tick copy and the GameTests.
 *
 * @author vyrriox
 */
@Mixin(value = AccessoriesHolderImpl.class, remap = false)
public interface AccessoriesHolderImplAccessor {

    @Accessor("loadedFromTag")
    boolean arcadia$isLoadedFromTag();

    @Accessor("loadedFromTag")
    void arcadia$setLoadedFromTag(boolean loadedFromTag);

    @Invoker("getAllSlotContainers")
    Map<String, AccessoriesContainer> arcadia$getAllSlotContainers();
}
