package com.teamarcadia.arcadiatweaks.neoforge.gametest.bench;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runtime switching of ArcadiaTweaks config values for the A/B phases. Every patch reads its
 * switch on each call, so a {@code set} takes effect on the next tick without a restart. Values are
 * changed in memory only (never saved), and {@link #restore} puts back what was loaded.
 *
 * @author vyrriox
 */
public final class BenchSwitches {

    /** One config value with a readable name for the report. */
    public record Switch(String name, ModConfigSpec.ConfigValue<?> value) {

        public static Switch of(String name, ModConfigSpec.ConfigValue<?> value) {
            return new Switch(name, value);
        }

        boolean isBoolean() {
            return value.getDefault() instanceof Boolean;
        }
    }

    private final Map<Switch, Object> original = new LinkedHashMap<>();

    /** Records the current value of every switch so it can be restored later. */
    public void snapshot(List<Switch> switches) {
        for (Switch s : switches) {
            original.putIfAbsent(s, s.value().get());
        }
    }

    /** Phase OFF: every boolean switch false. Numeric tuning values are left at their default. */
    public static void applyOff(List<Switch> switches) {
        for (Switch s : switches) {
            if (s.isBoolean()) {
                set(s, Boolean.FALSE);
            } else {
                set(s, s.value().getDefault());
            }
        }
    }

    /** Phase ON: every switch at its shipped default. */
    public static void applyOn(List<Switch> switches) {
        for (Switch s : switches) {
            set(s, s.value().getDefault());
        }
    }

    /** The current values, for the JSON record of what each phase ran with. */
    public static Map<String, Object> describe(List<Switch> switches) {
        final Map<String, Object> out = new LinkedHashMap<>();
        for (Switch s : switches) {
            out.put(s.name(), s.value().get());
        }
        return out;
    }

    public void restore() {
        for (Map.Entry<Switch, Object> e : new ArrayList<>(original.entrySet())) {
            set(e.getKey(), e.getValue());
        }
        original.clear();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void set(Switch s, Object value) {
        ((ModConfigSpec.ConfigValue) s.value()).set(value);
    }
}
