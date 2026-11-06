package com.teamarcadia.arcadiatweaks.neoforge.mixin.macawsdoors;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mcwdoors.kikoz.objects.GarageDoor;
import com.teamarcadia.arcadiatweaks.common.config.ArcadiaConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Macaw's Doors: {@code GarageDoor.toggleDoor} writes the new state on the top of the door, then calls itself
 * for every door next to it that does not have that state yet. It relies on the write: a level that refuses it
 * leaves the door as it was, so the door next to it, refused too, calls it back, and the two call each other
 * until a {@code StackOverflowError} crashes the game.
 *
 * <p>Bobby's cached chunks refuse every write ({@code FakeChunk.setBlockState} returns null), so a client that
 * clicks a double garage door in a chunk the server has not sent yet runs into this while predicting the click,
 * before the server hears about it (crash of 2026-11-06). The server still applies the real click if it gets one.</p>
 *
 * <p>A door whose write was refused no longer toggles its neighbours. When every write goes through, which is
 * always the case on a server, the check is false on every call and the method does what it did.</p>
 *
 * <p>The same method fills the air below a closing door, down to the first block. Below the world the level
 * answers void air, which is air, for about four billion more positions: closing a door above the void freezes
 * the server until the watchdog stops it. The fill now stops at the bottom of the world.</p>
 *
 * @author vyrriox
 */
@Mixin(GarageDoor.class)
public abstract class GarageDoorRefusedWriteMixin {

    @WrapOperation(
            method = "toggleDoor(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;ZLnet/minecraft/core/Direction;)V",
            at = @At(value = "INVOKE", target = "Lcom/mcwdoors/kikoz/objects/GarageDoor;toggleDoor(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;ZLnet/minecraft/core/Direction;)V"),
            require = 0
    )
    private void arcadia$stopAtARefusedWrite(
            GarageDoor door, Level level, BlockPos adjacent, boolean open, Direction facing,
            Operation<Void> original, @Local(ordinal = 1) BlockPos topPos
    ) {
        if (guardEnabled() && writeRefused(level.getBlockState(topPos), open)) {
            return;
        }
        original.call(door, level, adjacent, open, facing);
    }

    @WrapOperation(
            method = "toggleDoor(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;ZLnet/minecraft/core/Direction;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;isAir()Z"),
            require = 0
    )
    private boolean arcadia$stopAtTheBottomOfTheWorld(
            BlockState below, Operation<Boolean> original, @Local(argsOnly = true) Level level, @Local(ordinal = 2) BlockPos current
    ) {
        return original.call(below) && !(guardEnabled() && level.isOutsideBuildHeight(current));
    }

    @Unique
    private static boolean writeRefused(BlockState top, boolean open) {
        return top.hasProperty(BlockStateProperties.OPEN) && top.getValue(BlockStateProperties.OPEN) != open;
    }

    @Unique
    private static boolean guardEnabled() {
        try {
            return ArcadiaConfig.MACAWS_GARAGE_DOOR_GUARD.get();
        } catch (IllegalStateException notLoadedYet) {
            return true;
        }
    }
}
