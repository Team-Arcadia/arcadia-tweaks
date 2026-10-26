package com.teamarcadia.arcadiatweaks.neoforge.refinedstorage;

import java.util.Map;

/**
 * S2 - single-lookup form of RS2 {@code ComponentMap.getComponent}.
 *
 * Plain RS: {@code if (!map.containsKey(type)) throw ...; return map.get(type);}. Here: one
 * {@code get}, and {@code containsKey} only when the value is null, so a key mapped to null
 * still returns null and an absent key still throws the same exception with the same message.
 *
 * @author vyrriox
 */
public final class ArcadiaComponentLookup {

    private ArcadiaComponentLookup() {}

    @SuppressWarnings("unchecked")
    public static <I> I get(Map<?, ?> map, Object componentType) {
        final Object value = map.get(componentType);
        if (value == null && !map.containsKey(componentType)) {
            throw new IllegalArgumentException("Component not present: " + componentType);
        }
        return (I) value;
    }
}
