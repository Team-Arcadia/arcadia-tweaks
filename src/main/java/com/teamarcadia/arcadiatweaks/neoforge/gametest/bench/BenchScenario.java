package com.teamarcadia.arcadiatweaks.neoforge.gametest.bench;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Map;

/**
 * One A/B benchmark scenario: a world setup that exercises one ArcadiaTweaks module, plus the
 * configuration switches that turn that module's patches off (phase OFF) or back to the shipped
 * defaults (phase ON).
 *
 * <p>Adding a scenario: implement this interface in one class and add one line to
 * {@link BenchScenarios}. The runner owns the timing; a scenario only builds, switches and cleans up.
 *
 * @author vyrriox
 */
public interface BenchScenario {

    /** Stable id used in the JSON results, e.g. {@code "botanypots"}. */
    String id();

    /** One sentence for the report. */
    String description();

    /**
     * The config switches this scenario's patches read at runtime. Phase OFF sets every boolean to
     * {@code false}; phase ON sets every switch (boolean or numeric) to its shipped default.
     */
    List<BenchSwitches.Switch> switches();

    /**
     * Builds the scenario in a fresh region. The region's chunks are already force-loaded by the
     * runner (see {@link #footprint()}). Called once, on the server thread.
     */
    void build(ServerLevel level, BlockPos origin);

    /** Half-size of the square region (in blocks, from {@code origin}) the scenario occupies. */
    int footprint();

    /** Called on the first tick after {@link #build}, and again every few ticks until it returns true. */
    default boolean ready(ServerLevel level, int ticksSinceBuild) {
        return ticksSinceBuild >= 20;
    }

    /** Called right before every measured phase (after the switches are applied), e.g. to refill buffers. */
    default void beforePhase(ServerLevel level, boolean patchesOn) {}

    /**
     * Extra measurements taken at the end of a measured phase (outside the tick timing), e.g. a timed
     * save of the scenario's saved data. Values are added to the phase record as {@code extra.<key>}.
     */
    default Map<String, Double> afterPhase(ServerLevel level, boolean patchesOn) {
        return Map.of();
    }

    /** Facts about the setup (block counts, entity counts...) recorded in the JSON. */
    Map<String, Object> size();

    /** Removes everything the scenario placed. The runner unloads the chunks afterwards. */
    void teardown(ServerLevel level);
}
