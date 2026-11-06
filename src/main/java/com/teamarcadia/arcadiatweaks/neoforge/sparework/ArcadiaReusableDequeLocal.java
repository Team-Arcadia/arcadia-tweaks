package com.teamarcadia.arcadiatweaks.neoforge.sparework;

import com.teamarcadia.arcadiatweaks.common.modules.sparework.SpareWorkFlags;

import java.util.Deque;
import java.util.function.Supplier;

/**
 * Replaces the thread local of Simply Swords' {@code PityLootContext}: on the server thread the deque stays
 * instead of being created and removed around every read of a chest slot.
 *
 * <p>Same behaviour: {@code take()} only calls {@link #remove()} on an empty deque (pinned bytecode), so
 * "no value for this thread" and "an empty deque kept for this thread" answer every later call the same way.
 * Other threads, and the server thread with the switch off, use the plain thread local. The switch only
 * changes at the start of a tick, when no chest is being unpacked on the server thread.</p>
 *
 * @author vyrriox
 */
public final class ArcadiaReusableDequeLocal<E> extends ThreadLocal<Deque<E>> {

    private final Supplier<? extends Deque<E>> initial;
    /** Only touched on the server thread. */
    private Deque<E> serverThreadDeque;

    public ArcadiaReusableDequeLocal(Supplier<? extends Deque<E>> initial) {
        this.initial = initial;
    }

    @Override
    protected Deque<E> initialValue() {
        return initial.get();
    }

    @Override
    public Deque<E> get() {
        if (!reuseHere()) {
            return super.get();
        }
        Deque<E> deque = serverThreadDeque;
        if (deque == null) {
            deque = initial.get();
            serverThreadDeque = deque;
        }
        return deque;
    }

    @Override
    public void remove() {
        if (!reuseHere()) {
            super.remove();
            return;
        }
        final Deque<E> deque = serverThreadDeque;
        if (deque != null) {
            deque.clear();
        }
    }

    private static boolean reuseHere() {
        return SpareWorkFlags.simplySwordsReusePityStack() && SpareWorkFlags.onServerThread();
    }
}
