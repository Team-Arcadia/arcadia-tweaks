package com.teamarcadia.arcadiatweaks.neoforge.mixin.accessories;

import io.wispforest.accessories.api.components.AccessoryNestContainerContents;
import io.wispforest.accessories.api.slot.SlotReference;
import io.wispforest.accessories.impl.AccessoriesEventHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Private nested-stack change dispatch of {@code AccessoriesEventHandler}, called by the T-A tick copy.
 *
 * @author vyrriox
 */
@Mixin(value = AccessoriesEventHandler.class, remap = false)
public interface AccessoriesEventHandlerInvoker {

    @Invoker("recursiveStackChange")
    static void arcadia$recursiveStackChange(
            SlotReference slotReference,
            AccessoryNestContainerContents lastNestData,
            AccessoryNestContainerContents currentNestData
    ) {
        throw new AssertionError("Mixin invoker not applied");
    }
}
