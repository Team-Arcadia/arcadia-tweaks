package com.teamarcadia.arcadiatweaks.neoforge.mixin.simplyswords;

import com.teamarcadia.arcadiatweaks.common.modules.simplyswords.RunicForgeLock;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Runic Forge, bug #321: the tablet slots covered by a weapon's locked level refuse tablets, so a
 * weapon cannot go above level 8 and no tablet placed there is lost when the weapon is built.
 *
 * @author vyrriox
 */
@Mixin(targets = "net.sweenus.simplyswords.screen.RunicForgeScreenHandler$TabletSlot")
public abstract class RunicForgeTabletSlotMixin extends Slot {

    private static final int WEAPON_SLOT = 0;
    private static final int TABLET_START = 3;

    private RunicForgeTabletSlotMixin(Container container, int slot, int x, int y) {
        super(container, slot, x, y);
    }

    @Inject(method = "mayPlace", at = @At("RETURN"), cancellable = true)
    private void arcadia$keepLockedSlotsClosed(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() || !RunicForgeLock.enabled()) {
            return;
        }
        final int lock = RunicForgeLock.get(this.container.getItem(WEAPON_SLOT));
        if (lock > 0 && this.getContainerSlot() - TABLET_START >= RunicForgeLock.MAX_LEVEL - lock) {
            cir.setReturnValue(false);
        }
    }
}
