package com.teamarcadia.arcadiatweaks.neoforge.cosmetic;

import java.util.Arrays;

/**
 * Identity set of the items present in the slots {@code Inventory.contains} reads, taken once per
 * procedure call by {@link CosmeticGate}. Open addressing with a generation stamp, so clearing is one
 * increment and a capture allocates nothing once the table has grown to the inventory size.
 *
 * Keys are compared by identity (an {@code Item} is a registry singleton). The class holds no Minecraft
 * type so the set logic can be unit tested with plain objects.
 *
 * Two shared instances are not sets but states of the per-call gate: {@link #PENDING} (first lookup was
 * false, snapshot not taken yet) and {@link #DISABLED} (gate inactive for this call).
 *
 * @author vyrriox
 */
public final class ItemPresence {

    /** First {@code contains} of the call ran and returned false; the snapshot is taken on the next call. */
    public static final ItemPresence PENDING = new ItemPresence(1);
    /** Gate inactive for the rest of this call: every lookup goes to the original. */
    public static final ItemPresence DISABLED = new ItemPresence(1);

    private Object[] keys;
    private int[] stamps;
    private int mask;
    private int size;
    private int generation = 1;

    public ItemPresence(int expectedSize) {
        int capacity = 16;
        while (capacity < expectedSize * 2) {
            capacity <<= 1;
        }
        keys = new Object[capacity];
        stamps = new int[capacity];
        mask = capacity - 1;
    }

    /** Empties the set without touching the table. */
    public void clear() {
        size = 0;
        generation++;
        if (generation == 0) {
            Arrays.fill(stamps, 0);
            Arrays.fill(keys, null);
            generation = 1;
        }
    }

    public void add(Object item) {
        if (item == null) {
            return;
        }
        int index = slot(item);
        while (stamps[index] == generation) {
            if (keys[index] == item) {
                return;
            }
            index = (index + 1) & mask;
        }
        keys[index] = item;
        stamps[index] = generation;
        size++;
        if (size * 2 > keys.length) {
            grow();
        }
    }

    /** True when {@code item} was added since the last {@link #clear()}. */
    public boolean mayContain(Object item) {
        if (item == null) {
            return false;
        }
        int index = slot(item);
        while (stamps[index] == generation) {
            if (keys[index] == item) {
                return true;
            }
            index = (index + 1) & mask;
        }
        return false;
    }

    public int size() {
        return size;
    }

    int capacity() {
        return keys.length;
    }

    private int slot(Object item) {
        final int hash = System.identityHashCode(item);
        return (hash ^ (hash >>> 16)) * 0x9E3779B9 >>> 7 & mask;
    }

    private void grow() {
        final Object[] oldKeys = keys;
        final int[] oldStamps = stamps;
        final int oldGeneration = generation;
        keys = new Object[oldKeys.length << 1];
        stamps = new int[oldKeys.length << 1];
        mask = keys.length - 1;
        generation = 1;
        size = 0;
        for (int i = 0; i < oldKeys.length; i++) {
            if (oldStamps[i] == oldGeneration) {
                add(oldKeys[i]);
            }
        }
    }
}
