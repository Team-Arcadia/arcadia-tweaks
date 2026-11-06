package com.teamarcadia.arcadiatweaks.neoforge.mixin.sparework;

import com.teamarcadia.arcadiatweaks.neoforge.sparework.ArcadiaReusableDequeLocal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Deque;
import java.util.function.Supplier;

/**
 * Simply Swords hooks every {@code RandomizableContainer.unpackLootTable}, which vanilla calls on every read
 * of a chest slot: each call created a deque and a thread local entry, then removed them. The thread local is
 * replaced by one that keeps its deque on the server thread (see {@link ArcadiaReusableDequeLocal}).
 *
 * @author vyrriox
 */
@Pseudo
@Mixin(targets = "net.sweenus.simplyswords.loot.PityLootContext")
public abstract class PityLootContextReuseMixin {

    @Redirect(
            method = "<clinit>",
            at = @At(value = "INVOKE", target = "Ljava/lang/ThreadLocal;withInitial(Ljava/util/function/Supplier;)Ljava/lang/ThreadLocal;"),
            require = 0
    )
    private static ThreadLocal<?> arcadia$reusableStack(Supplier<? extends Deque<Object>> initial) {
        return new ArcadiaReusableDequeLocal<>(initial);
    }
}
