package com.teamarcadia.arcadiatweaks.neoforge.mixin.accessories;

import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;

/**
 * C1 anchor. Adds nothing: it only gives {@code AccessoriesMixinPlugin.postApply} a hook on
 * {@code Inventory} after Accessories (priority 1000) has merged its {@code contains} handlers, where
 * {@code AccessoriesContainsSurgery} rewrites the {@code contains(ItemStack)} one.
 *
 * @author vyrriox
 */
@Mixin(value = Inventory.class, priority = 2000)
public abstract class AccessoriesInventoryAnchorMixin {
}
