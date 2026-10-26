package com.teamarcadia.arcadiatweaks.neoforge.refinedstorage;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The single lookup gives what RS2's {@code containsKey}-then-{@code get} gives, on the same
 * unmodifiable HashMap view RS uses.
 *
 * @author vyrriox
 */
final class ArcadiaComponentLookupTest {

    private static Object plainRs(Map<Class<?>, Object> map, Class<?> type) {
        if (!map.containsKey(type)) {
            throw new IllegalArgumentException("Component not present: " + type);
        }
        return map.get(type);
    }

    private static Map<Class<?>, Object> components() {
        final Map<Class<?>, Object> backing = new HashMap<>();
        backing.put(String.class, "energy");
        backing.put(Integer.class, 42);
        backing.put(Long.class, null);
        return Collections.unmodifiableMap(backing);
    }

    @Test
    void presentComponentReturnsTheSameObject() {
        final Map<Class<?>, Object> map = components();
        assertSame(plainRs(map, String.class), ArcadiaComponentLookup.get(map, String.class));
        assertSame(plainRs(map, Integer.class), ArcadiaComponentLookup.get(map, Integer.class));
    }

    @Test
    void keyMappedToNullReturnsNull() {
        final Map<Class<?>, Object> map = components();
        assertNull(plainRs(map, Long.class));
        assertNull(ArcadiaComponentLookup.get(map, Long.class));
    }

    @Test
    void missingComponentThrowsTheSameException() {
        final Map<Class<?>, Object> map = components();
        final IllegalArgumentException plain = assertThrows(IllegalArgumentException.class, () -> plainRs(map, Double.class));
        final IllegalArgumentException single = assertThrows(IllegalArgumentException.class,
                () -> ArcadiaComponentLookup.get(map, Double.class));
        assertEquals(plain.getMessage(), single.getMessage());

        final IllegalArgumentException plainNull = assertThrows(IllegalArgumentException.class, () -> plainRs(map, null));
        final IllegalArgumentException singleNull = assertThrows(IllegalArgumentException.class,
                () -> ArcadiaComponentLookup.get(map, null));
        assertEquals(plainNull.getMessage(), singleNull.getMessage());
    }
}
