package com.teamarcadia.arcadiatweaks.neoforge.gametest;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.teamarcadia.arcadiatweaks.ArcadiaTweaks;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Results of the GameTest-hosted microbenchmarks ({@code -ParcadiaBench}), one JSON file per module in
 * {@code arcadia-bench/} of the game directory, as an array of
 * {@code {"name", "unit", "before", "after", "notes"}} entries ("before" = switch off, "after" = on).
 * Each file holds the entries recorded in this run; it is rewritten after every new entry.
 *
 * @author vyrriox
 */
public final class MicroBenchResults {

    private static final Map<String, JsonArray> FILES = new LinkedHashMap<>();

    private MicroBenchResults() {}

    public static synchronized void record(String file, String name, String unit, double before, double after, String notes) {
        final JsonObject entry = new JsonObject();
        entry.addProperty("name", name);
        entry.addProperty("unit", unit);
        entry.addProperty("before", Math.round(before * 10_000.0) / 10_000.0);
        entry.addProperty("after", Math.round(after * 10_000.0) / 10_000.0);
        entry.addProperty("notes", notes);
        final JsonArray entries = FILES.computeIfAbsent(file, k -> new JsonArray());
        entries.add(entry);
        try {
            final Path out = FMLPaths.GAMEDIR.get().resolve("arcadia-bench").resolve(file + ".json");
            Files.createDirectories(out.getParent());
            Files.writeString(out, new GsonBuilder().setPrettyPrinting().create().toJson(entries) + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            ArcadiaTweaks.LOGGER.error("[bench] cannot write arcadia-bench/{}.json", file, e);
        }
    }
}
