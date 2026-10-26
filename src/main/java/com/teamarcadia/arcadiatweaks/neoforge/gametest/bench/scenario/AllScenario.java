package com.teamarcadia.arcadiatweaks.neoforge.gametest.bench.scenario;

import com.teamarcadia.arcadiatweaks.neoforge.gametest.bench.BenchScenario;
import com.teamarcadia.arcadiatweaks.neoforge.gametest.bench.BenchSwitches;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every other scenario at once in one world: each one is built in its own area next to the others,
 * all tick together, and the phases switch every module at once (OFF: every switch of every scenario
 * off; ON: every switch at its shipped default). The {@code tick} series is then a whole-server tick
 * figure for the sum of the scenarios. Extra measures of the scenarios are kept, prefixed with their id.
 *
 * @author vyrriox
 */
public final class AllScenario implements BenchScenario {

    private static final int GAP = 48;

    private final List<BenchScenario> parts;
    private final List<BlockPos> origins = new ArrayList<>();
    private final boolean[] ready;

    public AllScenario(List<BenchScenario> parts) {
        this.parts = List.copyOf(parts);
        this.ready = new boolean[parts.size()];
    }

    @Override
    public String id() {
        return "all";
    }

    @Override
    public String description() {
        final List<String> ids = parts.stream().map(BenchScenario::id).toList();
        return "Every scenario at once in one world (" + String.join(", ", ids) + "), all modules OFF vs all ON: whole-server tick.";
    }

    @Override
    public List<BenchSwitches.Switch> switches() {
        final Map<String, BenchSwitches.Switch> byName = new LinkedHashMap<>();
        for (BenchScenario p : parts) {
            for (BenchSwitches.Switch s : p.switches()) {
                byName.putIfAbsent(s.name(), s);
            }
        }
        return List.copyOf(byName.values());
    }

    @Override
    public int footprint() {
        int max = 0;
        for (BenchScenario p : parts) max = Math.max(max, p.footprint());
        return max;
    }

    /** Each part's own origin: in a row along z, separated by its footprint plus a gap. */
    private List<BlockPos> layout(BlockPos origin) {
        if (origins.isEmpty()) {
            int z = 0;
            for (BenchScenario p : parts) {
                z += p.footprint();
                origins.add(origin.offset(0, 0, z));
                z += p.footprint() + GAP;
            }
        }
        return origins;
    }

    @Override
    public List<ChunkPos> chunks(BlockPos origin) {
        final Set<ChunkPos> out = new LinkedHashSet<>();
        final List<BlockPos> o = layout(origin);
        for (int i = 0; i < parts.size(); i++) {
            out.addAll(parts.get(i).chunks(o.get(i)));
        }
        return new ArrayList<>(out);
    }

    @Override
    public void build(ServerLevel level, BlockPos origin) {
        final List<BlockPos> o = layout(origin);
        for (int i = 0; i < parts.size(); i++) {
            parts.get(i).build(level, o.get(i));
        }
    }

    @Override
    public boolean ready(ServerLevel level, int ticksSinceBuild) {
        boolean all = true;
        for (int i = 0; i < parts.size(); i++) {
            if (!ready[i]) {
                ready[i] = parts.get(i).ready(level, ticksSinceBuild);
                all &= ready[i];
            }
        }
        return all;
    }

    @Override
    public void tick(ServerLevel level) {
        for (BenchScenario p : parts) p.tick(level);
    }

    @Override
    public void beforePhase(ServerLevel level, boolean patchesOn) {
        for (BenchScenario p : parts) p.beforePhase(level, patchesOn);
    }

    @Override
    public Map<String, Double> afterPhase(ServerLevel level, boolean patchesOn) {
        final Map<String, Double> out = new LinkedHashMap<>();
        for (BenchScenario p : parts) {
            p.afterPhase(level, patchesOn).forEach((k, v) -> out.put(p.id() + "." + k, v));
        }
        return out;
    }

    @Override
    public Map<String, Object> size() {
        final Map<String, Object> m = new LinkedHashMap<>();
        for (BenchScenario p : parts) m.put(p.id(), p.size());
        return m;
    }

    @Override
    public void teardown(ServerLevel level) {
        RuntimeException first = null;
        for (BenchScenario p : parts) {
            try {
                p.teardown(level);
            } catch (RuntimeException e) {
                if (first == null) first = e;
            }
        }
        if (first != null) throw first;
    }
}
