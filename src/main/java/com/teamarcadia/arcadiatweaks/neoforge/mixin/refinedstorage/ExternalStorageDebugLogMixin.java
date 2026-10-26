package com.teamarcadia.arcadiatweaks.neoforge.mixin.refinedstorage;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.refinedmods.refinedstorage.common.storage.externalstorage.AbstractExternalStorageBlockEntity;
import com.teamarcadia.arcadiatweaks.common.modules.refinedstorage.RefinedStorageRuntimeFlags;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * S4 - Drop the "External storage @ {} has changed!" debug line.
 *
 * RS2 writes it from {@code doWork} each time an external storage scan sees a change. With
 * DEBUG enabled for mod loggers (NeoForge writes logs/debug.log), the synchronous appender
 * write showed up in the server tick profile. The argument is a plain field read, so skipping
 * the call changes nothing but the log file.
 *
 * @author vyrriox
 */
@Mixin(AbstractExternalStorageBlockEntity.class)
public abstract class ExternalStorageDebugLogMixin {

    @WrapWithCondition(method = "doWork",
            at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;debug(Ljava/lang/String;Ljava/lang/Object;)V"))
    private boolean arcadia$keepChangedDebugLine(Logger logger, String message, Object position) {
        return !RefinedStorageRuntimeFlags.quietHotDebugLogs();
    }
}
