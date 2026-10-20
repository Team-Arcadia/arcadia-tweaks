package com.teamarcadia.arcadiatweaks.neoforge.mixin.simplyswords;

import com.teamarcadia.arcadiatweaks.common.modules.simplyswords.RunicForgeLock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.sweenus.simplyswords.api.AwakeningApi;
import net.sweenus.simplyswords.api.AwakeningFormRegistry;
import net.sweenus.simplyswords.screen.RunicForgeScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Runic Forge, bug #321: no free tablets from a weapon whose awakening level is only implicit.
 * See {@link RunicForgeLock}.
 *
 * - extractWeaponContents: lock the implicit level first, then hand out only the tablets above
 *   the lock, and leave the weapon at its locked level instead of 0 while it is edited.
 * - buildConfiguredPreview: the weapon gets lock + tablets placed, for its level and for the
 *   awakening form it resolves to.
 *
 * @author vyrriox
 */
@Mixin(RunicForgeScreenHandler.class)
public abstract class RunicForgeScreenHandlerMixin {

    @Shadow
    private ItemStack originalStack;

    @Inject(method = "extractWeaponContents", at = @At("HEAD"))
    private void arcadia$lockImplicitLevel(ItemStack stack, CallbackInfo ci) {
        if (RunicForgeLock.enabled()) {
            RunicForgeLock.lockImplicitLevel(stack, this.originalStack);
        }
    }

    @Redirect(method = "extractWeaponContents", at = @At(value = "INVOKE",
            target = "Lnet/sweenus/simplyswords/api/AwakeningApi;getLevel(Lnet/minecraft/world/item/ItemStack;)I"))
    private int arcadia$extractOnlyUnlockedTablets(ItemStack stack) {
        final int level = AwakeningApi.getLevel(stack);
        return RunicForgeLock.enabled() ? Math.max(0, level - RunicForgeLock.get(stack)) : level;
    }

    /**
     * While it sits in the forge the weapon is reset to level 0, its tablets being in the slots. A
     * locked level has no tablet to give back, so the weapon keeps it: a weapon taken out without
     * being rebuilt (number key swap) does not lose its implicit power.
     */
    @Redirect(method = "extractWeaponContents", at = @At(value = "INVOKE",
            target = "Lnet/sweenus/simplyswords/api/AwakeningApi;setLevel(Lnet/minecraft/world/item/ItemStack;I)V"))
    private void arcadia$keepLockedLevelWhileEditing(ItemStack stack, int level) {
        AwakeningApi.setLevel(stack, arcadia$withLock(stack, level));
    }

    @Redirect(method = "buildConfiguredPreview", at = @At(value = "INVOKE",
            target = "Lnet/sweenus/simplyswords/api/AwakeningApi;setLevel(Lnet/minecraft/world/item/ItemStack;I)V"))
    private void arcadia$setLevelWithLock(ItemStack stack, int tablets) {
        AwakeningApi.setLevel(stack, arcadia$withLock(stack, tablets));
    }

    @Redirect(method = "buildConfiguredPreview", at = @At(value = "INVOKE",
            target = "Lnet/sweenus/simplyswords/api/AwakeningFormRegistry;resolvePreview(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;IILnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack arcadia$resolveWithLock(ItemStack preview, ItemStack original, int originalLevel, int tablets,
                                              ServerPlayer player, BlockPos forgePos) {
        return AwakeningFormRegistry.resolvePreview(preview, original, originalLevel, arcadia$withLock(preview, tablets), player, forgePos);
    }

    private static int arcadia$withLock(ItemStack stack, int tablets) {
        if (!RunicForgeLock.enabled()) {
            return tablets;
        }
        return Math.min(RunicForgeLock.MAX_LEVEL, tablets + RunicForgeLock.get(stack));
    }
}
