package com.teamarcadia.arcadiatweaks.neoforge.mixin.client;

import com.teamarcadia.arcadiatweaks.neoforge.client.ClientBlockEntityThreadGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client only. Block entity reads and writes on a client chunk from a thread other than the render thread
 * no longer modify the chunk's maps from that thread. See {@link ClientBlockEntityThreadGuard}.
 *
 * @author vyrriox
 */
@Mixin(LevelChunk.class)
public abstract class LevelChunkThreadGuardMixin {

    @Inject(method = "getBlockEntity(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/chunk/LevelChunk$EntityCreationType;)Lnet/minecraft/world/level/block/entity/BlockEntity;",
            at = @At("HEAD"),
            cancellable = true)
    private void arcadia$readOnlyOffThread(BlockPos pos, LevelChunk.EntityCreationType creationType,
                                           CallbackInfoReturnable<BlockEntity> cir) {
        final LevelChunk self = (LevelChunk) (Object) this;
        if (ClientBlockEntityThreadGuard.isOffThreadAccess(self)) {
            cir.setReturnValue(ClientBlockEntityThreadGuard.readOnly(self, pos));
        }
    }

    @Inject(method = "setBlockEntity(Lnet/minecraft/world/level/block/entity/BlockEntity;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void arcadia$deferSetOffThread(BlockEntity blockEntity, CallbackInfo ci) {
        final LevelChunk self = (LevelChunk) (Object) this;
        if (ClientBlockEntityThreadGuard.isOffThreadAccess(self)) {
            ClientBlockEntityThreadGuard.deferToRenderThread("setBlockEntity", self, blockEntity.getBlockPos(),
                    () -> self.setBlockEntity(blockEntity));
            ci.cancel();
        }
    }

    @Inject(method = "removeBlockEntity(Lnet/minecraft/core/BlockPos;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void arcadia$deferRemoveOffThread(BlockPos pos, CallbackInfo ci) {
        final LevelChunk self = (LevelChunk) (Object) this;
        if (ClientBlockEntityThreadGuard.isOffThreadAccess(self)) {
            final BlockPos immutablePos = pos.immutable();
            ClientBlockEntityThreadGuard.deferToRenderThread("removeBlockEntity", self, immutablePos,
                    () -> self.removeBlockEntity(immutablePos));
            ci.cancel();
        }
    }
}
