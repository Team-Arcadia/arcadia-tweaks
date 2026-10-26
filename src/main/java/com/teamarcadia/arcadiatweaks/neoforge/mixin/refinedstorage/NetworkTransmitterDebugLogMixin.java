package com.teamarcadia.arcadiatweaks.neoforge.mixin.refinedstorage;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.refinedmods.refinedstorage.common.networking.NetworkTransmitterBlockEntity;
import com.teamarcadia.arcadiatweaks.common.modules.refinedstorage.RefinedStorageRuntimeFlags;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * S4 - Drop the "Receiver {} was not found in network for transmitter at {}" debug line.
 *
 * RS2 writes it on every reconnection retry of a network transmitter whose receiver is not in
 * its network. The arguments are plain field reads, so skipping the call changes nothing but
 * logs/debug.log; the retry itself is untouched.
 *
 * @author vyrriox
 */
@Mixin(NetworkTransmitterBlockEntity.class)
public abstract class NetworkTransmitterDebugLogMixin {

    @WrapWithCondition(method = "tryReconnectingWithReceiver",
            at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;debug(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V"))
    private boolean arcadia$keepRetryDebugLine(Logger logger, String message, Object receiver, Object position) {
        return !RefinedStorageRuntimeFlags.quietHotDebugLogs();
    }
}
