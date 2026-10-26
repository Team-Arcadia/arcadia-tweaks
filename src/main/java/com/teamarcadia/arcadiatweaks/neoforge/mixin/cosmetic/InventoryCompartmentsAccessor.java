package com.teamarcadia.arcadiatweaks.neoforge.mixin.cosmetic;

import com.teamarcadia.arcadiatweaks.neoforge.cosmetic.CosmeticGate;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;

/**
 * Read access to {@code Inventory.compartments}, the exact lists {@code Inventory.contains} iterates, so the
 * presence snapshot reads the same slots even if another mod adds a compartment. No injector: the
 * {@code contains} behaviour is untouched.
 *
 * @author vyrriox
 */
@Mixin(Inventory.class)
public abstract class InventoryCompartmentsAccessor implements CosmeticGate.CompartmentsView {

    @Shadow
    @Final
    private List<NonNullList<ItemStack>> compartments;

    @Override
    public List<NonNullList<ItemStack>> arcadia$compartments() {
        return compartments;
    }
}
