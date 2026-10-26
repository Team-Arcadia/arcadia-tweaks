package com.teamarcadia.arcadiatweaks.neoforge.gametest.bench;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.teamarcadia.arcadiatweaks.neoforge.gametest.bench.scenario.BotanyPotsScenario;
import com.teamarcadia.arcadiatweaks.neoforge.gametest.bench.scenario.EntityMoveGuardScenario;
import com.teamarcadia.arcadiatweaks.neoforge.gametest.bench.scenario.MekanismScenario;
import com.teamarcadia.arcadiatweaks.neoforge.gametest.bench.scenario.RefinedStorageScenario;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * The scenario registry. One entry per scenario: its id, the mods it needs, and a factory. The
 * factory is only called when the mods are loaded, so a scenario class may reference its mod's
 * classes directly.
 *
 * <p>Extension points not implemented yet (their mods are not part of the dev runtime):
 * <ul>
 *   <li>{@code cosmetic}: fake players whose player tick event is fired manually while they hold
 *   CosmeticWeapons / CosmeticArmours items, OFF/ON on the cosmetic gate switch.</li>
 *   <li>{@code accessories}: players and mobs with Accessories slots.</li>
 * </ul>
 * To add one: write the class in {@code scenario/}, then add an {@link Entry} below with its mod ids.
 *
 * <p>{@code -Darcadia.bench.scenarios=id1,id2} runs only the listed scenarios.
 *
 * @author vyrriox
 */
public final class BenchScenarios {

    record Entry(String id, List<String> requiredMods, Supplier<BenchScenario> factory) {}

    private static final List<Entry> ENTRIES = List.of(
            new Entry("botanypots", List.of("botanypots"), () -> new BotanyPotsScenario()),
            new Entry("refinedstorage", List.of("refinedstorage"), () -> new RefinedStorageScenario()),
            new Entry("mekanism", List.of("mekanism"), () -> new MekanismScenario()),
            new Entry("entity_move_guard", List.of(), () -> new EntityMoveGuardScenario())
    );

    private BenchScenarios() {}

    static List<BenchScenario> create(JsonArray skipped) {
        final String filter = System.getProperty("arcadia.bench.scenarios", "").trim();
        final Set<String> only = filter.isEmpty() ? Set.of()
                : Arrays.stream(filter.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
        final List<BenchScenario> out = new ArrayList<>();
        for (Entry e : ENTRIES) {
            if (!only.isEmpty() && !only.contains(e.id())) {
                continue;
            }
            final List<String> missing = e.requiredMods().stream().filter(m -> !ModList.get().isLoaded(m)).toList();
            if (!missing.isEmpty()) {
                final JsonObject s = new JsonObject();
                s.addProperty("id", e.id());
                s.addProperty("reason", "mods not loaded: " + missing);
                skipped.add(s);
                continue;
            }
            out.add(e.factory().get());
        }
        return out;
    }
}
