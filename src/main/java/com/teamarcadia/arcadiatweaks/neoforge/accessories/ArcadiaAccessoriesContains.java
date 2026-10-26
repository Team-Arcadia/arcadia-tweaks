package com.teamarcadia.arcadiatweaks.neoforge.accessories;

import com.teamarcadia.arcadiatweaks.common.modules.accessories.AccessoriesTweaks;
import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.AccessoriesContainer;
import io.wispforest.accessories.impl.AccessoriesCapabilityImpl;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * C1: replacement for the body of Accessories' {@code Inventory.contains(ItemStack)} extension,
 * called first thing in the merged handler by {@link AccessoriesContainsSurgery}.
 *
 * <p>Accessories 1.1.0-beta.54 runs, after a vanilla miss:
 * <pre>
 * capability = AccessoriesCapability.get(player);
 * if (capability != null &amp;&amp; capability.isEquipped(s -> s.isEmpty() &amp;&amp; isSameItemSameComponents(s, stack)))
 *     cir.setReturnValue(true);
 * </pre>
 * {@code isEquipped} ends in {@code AccessoriesCapabilityImpl.getFirstEquipped}, which only tests the
 * predicate on non-empty stacks, so it is {@code !s.isEmpty() && s.isEmpty() && ...}: always false.
 * The scan never sets a value. What it does change is done by four calls, kept here with the same
 * receivers in the same order: {@code AccessoriesCapability.get} (holder validation),
 * {@code getHolder()} (start of {@code getFirstEquipped}), {@code getContainers()} (re-points
 * container capabilities) and {@code getAccessories()} per container (applies a pending resize).
 * Only pure work is dropped: the predicate objects, the slot iterator, slot references, accessory
 * lookups and nested stack reads.
 *
 * @author vyrriox
 */
public final class ArcadiaAccessoriesContains {

    /**
     * Test-only fault injection, 0 in normal play. 1 skips the per-container update, 2 answers true
     * for an item worn in an accessory slot. The differential GameTests set it to prove they catch a
     * wrong rewrite.
     */
    public static volatile int faultForTest = 0;

    private ArcadiaAccessoriesContains() {}

    /**
     * @return true when this call fully replaced Accessories' handler body, false to run that body.
     */
    public static boolean handle(Inventory inventory, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!AccessoriesTweaks.containsFastPathEnabled()) {
            return false;
        }
        final Player player = inventory.player;
        if (player.level().isClientSide()) {
            return false;
        }
        final AccessoriesCapability capability = AccessoriesCapability.get(player);
        if (capability == null) {
            return true;
        }
        if (capability.getClass() != AccessoriesCapabilityImpl.class) {
            // Not the implementation the proof is about: Accessories' own logic, verbatim.
            if (capability.isEquipped(stack1 -> stack1.isEmpty() && ItemStack.isSameItemSameComponents(stack1, stack))) {
                cir.setReturnValue(true);
            }
            return true;
        }
        if (faultForTest != 0) {
            return faulty(capability, stack, cir);
        }
        capability.getHolder();
        for (AccessoriesContainer container : capability.getContainers().values()) {
            container.getAccessories();
        }
        return true;
    }

    private static boolean faulty(AccessoriesCapability capability, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (faultForTest == 2) {
            if (capability.isEquipped(stack1 -> ItemStack.isSameItemSameComponents(stack1, stack))) {
                cir.setReturnValue(true);
            }
            return true;
        }
        capability.getHolder();
        capability.getContainers();
        return true;
    }
}
