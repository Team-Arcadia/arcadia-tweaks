package com.teamarcadia.arcadiatweaks.neoforge.gametest.bench;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.teamarcadia.arcadiatweaks.ArcadiaTweaks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Drives the in-server A/B benchmark, one scenario after the other, from server tick events.
 *
 * <h2>Method</h2>
 * Same JVM, same world, same scenario. After a warm-up (four blocks OFF, ON, OFF, ON so both code
 * paths are compiled and every back-off has run), the runner alternates measured phases OFF and ON for {@code rounds} rounds in ABBA order
 * (OFF ON, ON OFF, OFF ON...) so a slow drift (heap growth, JIT, crop growth) cancels out. Each phase
 * starts with {@code settleTicks} unmeasured ticks after the switch (a forced GC, then the patches'
 * back-off counters reach their steady state), then {@code measureTicks} measured ticks.
 *
 * <h2>Measures, per tick</h2>
 * <ul>
 *   <li>{@code tick}: wall time of the whole server tick, {@code ServerTickEvent.Pre} (highest
 *   priority) to {@code ServerTickEvent.Post} (lowest priority), with {@link System#nanoTime()}.</li>
 *   <li>{@code level}: wall time of the overworld tick ({@code LevelTickEvent.Pre} to {@code Post}):
 *   block entities, entities, scheduled ticks. The bench world holds nothing but the scenario, so
 *   this is the scenario's own block entity and entity work.</li>
 *   <li>{@code postEvents}: wall time of the {@code ServerTickEvent.Post} listeners, where Mekanism
 *   ticks its transmitter networks.</li>
 *   <li>{@code scenario}: {@code level + postEvents}, the scenario's own work.</li>
 * </ul>
 * Per phase: GC collections and GC time from the JMX beans, and bytes allocated by the server thread.
 *
 * <h2>Results JSON (schema {@code arcadia-bench/1})</h2>
 * <pre>
 * {
 *   "schema": "arcadia-bench/1", "startedAt": ISO time, "finishedAt": ISO time,
 *   "method": { "warmupTicks", "rounds", "settleTicks", "measureTicks", "order": "ABBA", "timer": "System.nanoTime" },
 *   "environment": { "javaVersion", "javaVm", "javaVendor", "jvmArgs", "maxHeapMb", "garbageCollectors": [],
 *                    "os", "cpu", "logicalCores", "minecraft", "neoforge", "mods": { modId: version } },
 *   "scenarios": [ {
 *     "id", "description", "size": { ... }, "switches": [ names ],
 *     "phases": [ { "round", "index", "mode": "OFF"|"ON", "ticks",
 *                   "tick"|"scenario"|"level"|"postEvents": { "n", "meanMs", "stdevMs", "medianMs", "p95Ms", "p99Ms", "minMs", "maxMs" },
 *                   "gc": { "count", "timeMs" }, "allocatedMb", "switches": { name: value }, "extra": { key: value } } ],
 *     "summary": {
 *       "off"|"on": { "tick": stats, "scenario": stats, "level": stats, "postEvents": stats, "gcCount", "gcTimeMs", "allocatedMbPerTick", "extra": { key: mean } },
 *       "tick"|"scenario"|"level"|"postEvents": { "offMeanMs", "onMeanMs", "deltaMeanPct", "deltaP95Pct", "roundDeltasPct": [], "roundsOnFaster", "rounds",
 *                            "pairedTTest": { "n", "meanDiff", "t", "df", "pTwoSided" }, "blockBootstrapMeanDiffMs95": [lo, hi] }
 *     } } ],
 *   "skipped": [ { "id", "reason" } ]
 * }
 * </pre>
 * Deltas are (ON - OFF) / OFF: negative means the patches made it faster. The main confidence
 * check is the paired t-test on the per-round means (each round holds one OFF and one ON phase);
 * the block bootstrap (blocks of 50 consecutive ticks, which keeps the tick-to-tick correlation)
 * is a second view.
 *
 * @author vyrriox
 */
final class BenchRunner {

    private static final int WARMUP = Integer.getInteger("arcadia.bench.warmup", 1200);
    private static final int ROUNDS = Integer.getInteger("arcadia.bench.rounds", 8);
    private static final int SETTLE = Integer.getInteger("arcadia.bench.settle", 60);
    private static final int MEASURE = Integer.getInteger("arcadia.bench.measure", 400);
    private static final int REGION_SPACING = 1024;

    private enum Stage { BUILD, WAIT_READY, WARMUP, SETTLE, MEASURE, TEARDOWN, DONE }

    private final GameTestHelper helper;
    private final MinecraftServer server;
    private final ServerLevel level;
    private final List<BenchScenario> scenarios;
    private final JsonArray skipped;
    private final BlockPos baseOrigin;
    private final BenchSwitches switches = new BenchSwitches();
    private final String startedAt = LocalDateTime.now().toString();
    private final JsonArray scenarioResults = new JsonArray();
    private final boolean[] originalNoSave;

    private int scenarioIndex = -1;
    private BenchScenario current;
    private BlockPos origin;
    private List<ChunkPos> forced = List.of();
    private Stage stage = Stage.BUILD;
    private int stageTicks;
    private int phaseIndex;
    private JsonArray phases;
    private final List<long[][]> phaseSamples = new ArrayList<>();

    // per-tick timing
    private long tickStart;
    private long levelStart;
    private long levelNs;
    private long postStart;
    private boolean inTick;

    // per-phase sample buffers: tick, level, post
    private long[] bufTick;
    private long[] bufLevel;
    private long[] bufPost;
    private int bufCount;
    private long gcCountStart;
    private long gcTimeStart;
    private long allocStart;

    private BenchRunner(GameTestHelper helper, List<BenchScenario> scenarios, JsonArray skipped) {
        this.helper = helper;
        this.level = helper.getLevel();
        this.server = level.getServer();
        this.scenarios = scenarios;
        this.skipped = skipped;
        final BlockPos testOrigin = helper.absolutePos(BlockPos.ZERO);
        this.baseOrigin = new BlockPos(testOrigin.getX() + 256, testOrigin.getY() + 1, testOrigin.getZ());
        final List<ServerLevel> levels = new ArrayList<>();
        server.getAllLevels().forEach(levels::add);
        this.originalNoSave = new boolean[levels.size()];
        for (int i = 0; i < levels.size(); i++) {
            originalNoSave[i] = levels.get(i).noSave;
            // No autosave during the bench: a full world save inside one phase would dominate it.
            levels.get(i).noSave = true;
        }
    }

    static void start(GameTestHelper helper) {
        final JsonArray skipped = new JsonArray();
        final List<BenchScenario> selected = BenchScenarios.create(skipped);
        final BenchRunner runner = new BenchRunner(helper, selected, skipped);
        ArcadiaTweaks.LOGGER.info("[bench] starting: {} scenario(s), warm-up {} ticks, {} rounds of OFF/ON {}+{} ticks (ABBA)",
                selected.size(), WARMUP, ROUNDS, SETTLE, MEASURE);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ServerTickEvent.Pre.class, e -> runner.onTickPre());
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ServerTickEvent.Post.class, e -> runner.onPostFirst());
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ServerTickEvent.Post.class, e -> runner.onPostLast());
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, LevelTickEvent.Pre.class, runner::onLevelPre);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LevelTickEvent.Post.class, runner::onLevelPost);
        // The first scenario is built on the first bench tick, inside the guarded advance().
        runner.stage = Stage.TEARDOWN;
        runner.stageTicks = 40;
    }

    private void onTickPre() {
        if (stage == Stage.DONE) return;
        inTick = true;
        levelNs = 0;
        tickStart = System.nanoTime();
    }

    private void onLevelPre(LevelTickEvent.Pre event) {
        if (inTick && event.getLevel() == level) {
            levelStart = System.nanoTime();
        }
    }

    private void onLevelPost(LevelTickEvent.Post event) {
        if (inTick && event.getLevel() == level) {
            levelNs += System.nanoTime() - levelStart;
        }
    }

    private void onPostFirst() {
        if (inTick) {
            postStart = System.nanoTime();
        }
    }

    private void onPostLast() {
        if (!inTick) return;
        final long now = System.nanoTime();
        inTick = false;
        if (stage == Stage.MEASURE) {
            bufTick[bufCount] = now - tickStart;
            bufLevel[bufCount] = levelNs;
            bufPost[bufCount] = now - postStart;
            bufCount++;
        }
        try {
            advance();
        } catch (Throwable t) {
            ArcadiaTweaks.LOGGER.error("[bench] scenario {} failed", current == null ? "?" : current.id(), t);
            if (current != null) {
                final JsonObject s = new JsonObject();
                s.addProperty("id", current.id());
                s.addProperty("reason", "failed: " + t);
                skipped.add(s);
                safeTeardown();
            }
            // Move on to the next scenario on the next tick.
            stage = Stage.TEARDOWN;
            stageTicks = 40;
        }
    }

    private void advance() {
        stageTicks++;
        switch (stage) {
            case WAIT_READY -> {
                if (current.ready(level, stageTicks)) {
                    BenchSwitches.applyOff(current.switches());
                    enter(Stage.WARMUP);
                }
            }
            case WARMUP -> {
                // Four blocks OFF, ON, OFF, ON so both code paths are compiled and every back-off
                // state machine has run before the first measured phase.
                if (stageTicks % Math.max(1, WARMUP / 4) == 0) {
                    if ((stageTicks / Math.max(1, WARMUP / 4)) % 2 == 1) {
                        BenchSwitches.applyOn(current.switches());
                    } else {
                        BenchSwitches.applyOff(current.switches());
                    }
                }
                if (stageTicks >= WARMUP) {
                    phaseIndex = 0;
                    startPhase();
                }
            }
            case SETTLE -> {
                if (stageTicks >= SETTLE) {
                    beginMeasure();
                    enter(Stage.MEASURE);
                }
            }
            case MEASURE -> {
                if (bufCount >= MEASURE) {
                    endPhase();
                    phaseIndex++;
                    if (phaseIndex >= ROUNDS * 2) {
                        finishScenario();
                    } else {
                        startPhase();
                    }
                }
            }
            case TEARDOWN -> {
                if (stageTicks >= 40) {
                    nextScenario();
                }
            }
            default -> {}
        }
    }

    private void enter(Stage next) {
        stage = next;
        stageTicks = 0;
    }

    /** ABBA: round 0 OFF then ON, round 1 ON then OFF, and so on. */
    private boolean phaseIsOn(int index) {
        final int round = index / 2;
        final boolean second = index % 2 == 1;
        return (round % 2 == 0) == second;
    }

    private void startPhase() {
        final boolean on = phaseIsOn(phaseIndex);
        if (on) {
            BenchSwitches.applyOn(current.switches());
        } else {
            BenchSwitches.applyOff(current.switches());
        }
        current.beforePhase(level, on);
        System.gc();
        enter(Stage.SETTLE);
    }

    private void beginMeasure() {
        bufTick = new long[MEASURE];
        bufLevel = new long[MEASURE];
        bufPost = new long[MEASURE];
        bufCount = 0;
        gcCountStart = gcCount();
        gcTimeStart = gcTime();
        allocStart = allocatedBytes();
    }

    private void endPhase() {
        final long gcCount = gcCount() - gcCountStart;
        final long gcTime = gcTime() - gcTimeStart;
        final long alloc = allocatedBytes() - allocStart;
        final boolean on = phaseIsOn(phaseIndex);
        final long[] scenario = new long[bufCount];
        for (int i = 0; i < bufCount; i++) {
            scenario[i] = bufLevel[i] + bufPost[i];
        }
        final JsonObject p = new JsonObject();
        p.addProperty("round", phaseIndex / 2);
        p.addProperty("index", phaseIndex);
        p.addProperty("mode", on ? "ON" : "OFF");
        p.addProperty("ticks", bufCount);
        p.add("tick", BenchStats.describeMs(bufTick));
        p.add("scenario", BenchStats.describeMs(scenario));
        p.add("level", BenchStats.describeMs(bufLevel));
        p.add("postEvents", BenchStats.describeMs(bufPost));
        final JsonObject gc = new JsonObject();
        gc.addProperty("count", gcCount);
        gc.addProperty("timeMs", gcTime);
        p.add("gc", gc);
        p.addProperty("allocatedMb", alloc < 0 ? -1 : BenchStats.round(alloc / (1024.0 * 1024.0), 3));
        p.add("switches", new GsonBuilder().create().toJsonTree(BenchSwitches.describe(current.switches())));
        final JsonObject extra = new JsonObject();
        for (Map.Entry<String, Double> e : current.afterPhase(level, on).entrySet()) {
            extra.addProperty(e.getKey(), BenchStats.round(e.getValue(), 4));
        }
        p.add("extra", extra);
        phases.add(p);
        phaseSamples.add(new long[][] {bufTick.clone(), scenario, bufLevel.clone(), bufPost.clone()});
        ArcadiaTweaks.LOGGER.info("[bench] {} round {} {}: tick mean {} ms p95 {} ms, scenario mean {} ms, gc {}",
                current.id(), phaseIndex / 2, on ? "ON " : "OFF",
                p.getAsJsonObject("tick").get("meanMs"), p.getAsJsonObject("tick").get("p95Ms"),
                p.getAsJsonObject("scenario").get("meanMs"), gcCount);
    }

    private void finishScenario() {
        final JsonObject result = new JsonObject();
        result.addProperty("id", current.id());
        result.addProperty("description", current.description());
        result.add("size", new GsonBuilder().create().toJsonTree(current.size()));
        final JsonArray names = new JsonArray();
        current.switches().forEach(s -> names.add(s.name()));
        result.add("switches", names);
        result.add("phases", phases);
        result.add("summary", BenchSummary.summarize(phases, phaseSamples));
        scenarioResults.add(result);
        BenchSummary.log(current.id(), result.getAsJsonObject("summary"));
        switches.restore();
        safeTeardown();
        enter(Stage.TEARDOWN);
    }

    private void safeTeardown() {
        try {
            switches.restore();
            current.teardown(level);
        } catch (Throwable t) {
            ArcadiaTweaks.LOGGER.warn("[bench] teardown of {} failed", current.id(), t);
        }
        for (ChunkPos c : forced) {
            level.setChunkForced(c.x, c.z, false);
        }
        forced = List.of();
    }

    private void nextScenario() {
        scenarioIndex++;
        phaseSamples.clear();
        phases = new JsonArray();
        if (scenarioIndex >= scenarios.size()) {
            finishAll();
            return;
        }
        current = scenarios.get(scenarioIndex);
        origin = baseOrigin.offset(scenarioIndex * REGION_SPACING, 0, 0);
        switches.snapshot(current.switches());
        final int r = current.footprint() + 16;
        final List<ChunkPos> chunks = new ArrayList<>();
        for (int cx = (origin.getX() - r) >> 4; cx <= (origin.getX() + r) >> 4; cx++) {
            for (int cz = (origin.getZ() - r) >> 4; cz <= (origin.getZ() + r) >> 4; cz++) {
                level.setChunkForced(cx, cz, true);
                chunks.add(new ChunkPos(cx, cz));
            }
        }
        forced = chunks;
        ArcadiaTweaks.LOGGER.info("[bench] building {} at {} ({} forced chunks)", current.id(), origin.toShortString(), chunks.size());
        current.build(level, origin);
        ArcadiaTweaks.LOGGER.info("[bench] {} built: {}", current.id(), current.size());
        enter(Stage.WAIT_READY);
    }

    private void finishAll() {
        stage = Stage.DONE;
        final List<ServerLevel> levels = new ArrayList<>();
        server.getAllLevels().forEach(levels::add);
        for (int i = 0; i < levels.size() && i < originalNoSave.length; i++) {
            levels.get(i).noSave = originalNoSave[i];
        }
        final JsonObject root = new JsonObject();
        root.addProperty("schema", "arcadia-bench/1");
        root.addProperty("startedAt", startedAt);
        root.addProperty("finishedAt", LocalDateTime.now().toString());
        final JsonObject method = new JsonObject();
        method.addProperty("warmupTicks", WARMUP);
        method.addProperty("rounds", ROUNDS);
        method.addProperty("settleTicks", SETTLE);
        method.addProperty("measureTicks", MEASURE);
        method.addProperty("order", "ABBA");
        method.addProperty("timer", "System.nanoTime");
        method.addProperty("server", "NeoForge GameTest server, ticks run back to back (no 50 ms sleep), autosave off");
        method.addProperty("off", "every ArcadiaTweaks switch of the scenario's module set to false at runtime");
        method.addProperty("on", "the same switches at their shipped defaults");
        root.add("method", method);
        root.add("environment", BenchEnvironment.describe());
        root.add("scenarios", scenarioResults);
        root.add("skipped", skipped);
        final String json = new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create().toJson(root);
        try {
            final Path dir = server.getServerDirectory().resolve("bench-results");
            Files.createDirectories(dir);
            final Path file = dir.resolve(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".json");
            Files.writeString(file, json, StandardCharsets.UTF_8);
            ArcadiaTweaks.LOGGER.info("[bench] results written to {}", file.toAbsolutePath());
            final String copyTo = System.getProperty("arcadia.bench.copyTo", "");
            if (!copyTo.isBlank()) {
                final Path target = Paths.get(copyTo);
                if (target.getParent() != null) Files.createDirectories(target.getParent());
                Files.writeString(target, json, StandardCharsets.UTF_8);
                ArcadiaTweaks.LOGGER.info("[bench] results copied to {}", target.toAbsolutePath());
            }
        } catch (IOException e) {
            ArcadiaTweaks.LOGGER.error("[bench] could not write results", e);
        }
        ArcadiaTweaks.LOGGER.info("[bench] done: {} scenario(s), {} skipped", scenarioResults.size(), skipped.size());
        helper.succeed();
    }

    private static long gcCount() {
        long sum = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            sum += Math.max(0, gc.getCollectionCount());
        }
        return sum;
    }

    private static long gcTime() {
        long sum = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            sum += Math.max(0, gc.getCollectionTime());
        }
        return sum;
    }

    private static long allocatedBytes() {
        if (ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean bean) {
            return bean.getThreadAllocatedBytes(Thread.currentThread().threadId());
        }
        return -1;
    }
}
