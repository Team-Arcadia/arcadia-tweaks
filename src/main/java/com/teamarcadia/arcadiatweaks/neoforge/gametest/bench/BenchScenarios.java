package com.teamarcadia.arcadiatweaks.neoforge.gametest.bench;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.teamarcadia.arcadiatweaks.neoforge.gametest.bench.scenario.AccessoriesScenario;
import com.teamarcadia.arcadiatweaks.neoforge.gametest.bench.scenario.AllScenario;
import com.teamarcadia.arcadiatweaks.neoforge.gametest.bench.scenario.BotanyPotsScenario;
import com.teamarcadia.arcadiatweaks.neoforge.gametest.bench.scenario.CosmeticScenario;
import com.teamarcadia.arcadiatweaks.neoforge.gametest.bench.scenario.EntityMoveGuardScenario;
import com.teamarcadia.arcadiatweaks.neoforge.gametest.bench.scenario.MekanismScenario;
import com.teamarcadia.arcadiatweaks.neoforge.gametest.bench.scenario.RefinedStorageSaveScenario;
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
 * <p>To add one: write the class in {@code scenario/}, then add an {@link Entry} below with its mod
 * ids. The last scenario, {@code all}, runs every other loaded scenario at once in one world.
 *
 * <p>{@code -Darcadia.bench.scenarios=id1,id2} runs only the listed scenarios.
 *
 * @author vyrriox
 */
public final class BenchScenarios {

    record Entry(String id, List<String> requiredMods, Supplier<BenchScenario> factory) {}

    private static final List<Entry> PARTS = List.of(
            new Entry("botanypots", List.of("botanypots"), () -> new BotanyPotsScenario()),
            new Entry("refinedstorage", List.of("refinedstorage"), () -> new RefinedStorageScenario()),
            new Entry("refinedstorage_save", List.of("refinedstorage"), () -> new RefinedStorageSaveScenario()),
            new Entry("mekanism", List.of("mekanism"), () -> new MekanismScenario()),
            new Entry("entity_move_guard", List.of(), () -> new EntityMoveGuardScenario()),
            new Entry("cosmetic", List.of("cosmeticweaponsmod", "cosmeticarmoursmod", "curios", "accessories"), () -> new CosmeticScenario()),
            new Entry("accessories", List.of("accessories"), () -> new AccessoriesScenario())
    );

    private static final List<Entry> ENTRIES;

    static {
        final List<Entry> all = new ArrayList<>(PARTS);
        all.add(new Entry("all", List.of(), () -> new AllScenario(
                PARTS.stream().filter(BenchScenarios::loaded).map(e -> e.factory().get()).toList())));
        ENTRIES = List.copyOf(all);
    }

    private static boolean loaded(Entry e) {
        return e.requiredMods().stream().allMatch(m -> ModList.get().isLoaded(m));
    }

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
