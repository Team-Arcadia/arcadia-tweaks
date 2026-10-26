package com.teamarcadia.arcadiatweaks.neoforge.cosmetic;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author vyrriox
 */
final class ItemPresenceTest {

    @Test
    void emptySetContainsNothing() {
        final ItemPresence presence = new ItemPresence(8);
        assertFalse(presence.mayContain(new Object()));
        assertFalse(presence.mayContain(null));
        assertEquals(0, presence.size());
    }

    @Test
    void addedItemsArePresentAndDuplicatesCountOnce() {
        final ItemPresence presence = new ItemPresence(8);
        final Object sword = new Object();
        final Object axe = new Object();
        presence.add(sword);
        presence.add(sword);
        presence.add(axe);
        presence.add(null);
        assertTrue(presence.mayContain(sword));
        assertTrue(presence.mayContain(axe));
        assertFalse(presence.mayContain(new Object()));
        assertEquals(2, presence.size());
    }

    @Test
    void identityNotEquality() {
        final ItemPresence presence = new ItemPresence(4);
        presence.add(new String("item"));
        assertFalse(presence.mayContain("item"), "Items are registry singletons: compare by identity");
    }

    @Test
    void clearForgetsEverythingAndBufferIsReused() {
        final ItemPresence presence = new ItemPresence(64);
        final int capacity = presence.capacity();
        final List<Object> first = objects(41);
        first.forEach(presence::add);
        presence.clear();
        assertEquals(0, presence.size());
        for (Object item : first) {
            assertFalse(presence.mayContain(item), "clear must drop items of the previous snapshot");
        }
        final Object next = new Object();
        presence.add(next);
        assertTrue(presence.mayContain(next));
        assertEquals(capacity, presence.capacity(), "a 41-slot snapshot must not grow a 64-entry buffer");
    }

    @Test
    void growsBeyondExpectedSize() {
        final ItemPresence presence = new ItemPresence(2);
        final List<Object> items = objects(500);
        items.forEach(presence::add);
        assertEquals(500, presence.size());
        for (Object item : items) {
            assertTrue(presence.mayContain(item));
        }
        assertFalse(presence.mayContain(new Object()));
    }

    @Test
    void matchesAReferenceSetOverManyRandomSnapshots() {
        final Random random = new Random(1234);
        final List<Object> universe = objects(200);
        final ItemPresence presence = new ItemPresence(64);
        for (int round = 0; round < 2000; round++) {
            presence.clear();
            final Set<Object> reference = new HashSet<>();
            final int count = random.nextInt(80);
            for (int i = 0; i < count; i++) {
                final Object item = universe.get(random.nextInt(universe.size()));
                presence.add(item);
                reference.add(item);
            }
            assertEquals(reference.size(), presence.size());
            for (Object item : universe) {
                assertEquals(reference.contains(item), presence.mayContain(item));
            }
        }
    }

    @Test
    void stateMarkersAreDistinct() {
        assertFalse(ItemPresence.PENDING == ItemPresence.DISABLED);
        assertFalse(ItemPresence.PENDING.mayContain(new Object()));
    }

    private static List<Object> objects(int count) {
        final List<Object> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(new Object());
        }
        return list;
    }
}
