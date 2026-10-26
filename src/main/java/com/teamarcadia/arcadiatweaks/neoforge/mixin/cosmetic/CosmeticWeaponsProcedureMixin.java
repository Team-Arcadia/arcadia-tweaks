package com.teamarcadia.arcadiatweaks.neoforge.mixin.cosmetic;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.teamarcadia.arcadiatweaks.neoforge.cosmetic.CosmeticGate;
import com.teamarcadia.arcadiatweaks.neoforge.cosmetic.ItemPresence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * CosmeticWeapons advancement procedures that test two items or more: item-presence gate on their
 * {@code Inventory.contains} chain (see {@link CosmeticGate}). Each target is checked on its own by
 * {@link CosmeticMixinPlugin}; single-item procedures are not targeted (nothing to skip).
 *
 * @author vyrriox
 */
@Pseudo
@Mixin(targets = {
        "cosmeticweaponsmod.procedures.DiamondTierWeaponAdvProdProcedure",
        "cosmeticweaponsmod.procedures.GildedNetheriteTierWeaponAdvProdProcedure",
        "cosmeticweaponsmod.procedures.GoldTierWeaponAdvProdProcedure",
        "cosmeticweaponsmod.procedures.IronTierWeaponAdvProdProcedure",
        "cosmeticweaponsmod.procedures.NetheriteTierWeaponAdvProdProcedure",
        "cosmeticweaponsmod.procedures.PrismarineWeaponAdvProdProcedure",
        "cosmeticweaponsmod.procedures.RoseGoldWeaponAdvProdProcedure",
        "cosmeticweaponsmod.procedures.StoneTierWeaponAdvProdProcedure",
        "cosmeticweaponsmod.procedures.WoodTierWeaponAdvProdProcedure"
})
public abstract class CosmeticWeaponsProcedureMixin {

    @WrapOperation(
            method = "execute(Lnet/neoforged/bus/api/Event;Lnet/minecraft/world/entity/Entity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;contains(Lnet/minecraft/world/item/ItemStack;)Z"),
            require = 0
    )
    private static boolean arcadia$presenceGate(
            Inventory inventory,
            ItemStack target,
            Operation<Boolean> original,
            @Share("arcadia$presence") LocalRef<ItemPresence> presence
    ) {
        return CosmeticGate.contains(inventory, target, original, presence);
    }
}
