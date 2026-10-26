package com.teamarcadia.arcadiatweaks.neoforge.gametest.bench;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teamarcadia.arcadiatweaks.ArcadiaTweaks;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the phase records of one scenario into the OFF/ON comparison of the results JSON.
 *
 * @author vyrriox
 */
final class BenchSummary {

    private static final String[] SERIES = {"tick", "scenario", "level", "postEvents"};

    private BenchSummary() {}

    /** @param samples per phase, in phase order: {tick, scenario, level, postEvents} arrays in ns. */
    static JsonObject summarize(JsonArray phases, List<long[][]> samples) {
        final JsonObject summary = new JsonObject();
        summary.add("off", pooled(phases, samples, false));
        summary.add("on", pooled(phases, samples, true));
        summary.add("tick", compare(phases, samples, 0));
        summary.add("scenario", compare(phases, samples, 1));
        summary.add("level", compare(phases, samples, 2));
        summary.add("postEvents", compare(phases, samples, 3));
        return summary;
    }

    private static JsonObject pooled(JsonArray phases, List<long[][]> samples, boolean on) {
        final JsonObject o = new JsonObject();
        for (int s = 0; s < SERIES.length; s++) {
            o.add(SERIES[s], BenchStats.describeMs(concat(phases, samples, on, s)));
        }
        long gcCount = 0;
        long gcTime = 0;
        double alloc = 0;
        long ticks = 0;
        final Map<String, double[]> extra = new LinkedHashMap<>();
        for (int i = 0; i < phases.size(); i++) {
            final JsonObject p = phases.get(i).getAsJsonObject();
            if (isOn(p) != on) continue;
            gcCount += p.getAsJsonObject("gc").get("count").getAsLong();
            gcTime += p.getAsJsonObject("gc").get("timeMs").getAsLong();
            alloc += p.get("allocatedMb").getAsDouble();
            ticks += p.get("ticks").getAsLong();
            for (Map.Entry<String, JsonElement> e : p.getAsJsonObject("extra").entrySet()) {
                final double[] acc = extra.computeIfAbsent(e.getKey(), k -> new double[2]);
                acc[0] += e.getValue().getAsDouble();
                acc[1]++;
            }
        }
        o.addProperty("gcCount", gcCount);
        o.addProperty("gcTimeMs", gcTime);
        o.addProperty("allocatedMbPerTick", ticks == 0 ? 0 : BenchStats.round(alloc / ticks, 4));
        final JsonObject ex = new JsonObject();
        extra.forEach((k, v) -> ex.addProperty(k, BenchStats.round(v[0] / v[1], 4)));
        o.add("extra", ex);
        return o;
    }

    private static JsonObject compare(JsonArray phases, List<long[][]> samples, int series) {
        final JsonObject o = new JsonObject();
        final long[] off = concat(phases, samples, false, series);
        final long[] on = concat(phases, samples, true, series);
        final double offMean = BenchStats.mean(off);
        final double onMean = BenchStats.mean(on);
        o.addProperty("offMeanMs", BenchStats.ms(offMean));
        o.addProperty("onMeanMs", BenchStats.ms(onMean));
        o.addProperty("deltaMeanPct", pct(onMean, offMean));
        o.addProperty("deltaP95Pct", pct(p95(on), p95(off)));

        // Per round: the OFF and the ON phase of the same round, so drift between rounds cancels.
        final int rounds = phases.size() / 2;
        final double[] diffs = new double[rounds];
        final JsonArray roundPct = new JsonArray();
        int onFaster = 0;
        for (int r = 0; r < rounds; r++) {
            double offR = Double.NaN;
            double onR = Double.NaN;
            for (int k = 0; k < 2; k++) {
                final int idx = r * 2 + k;
                final double m = BenchStats.mean(samples.get(idx)[series]);
                if (isOn(phases.get(idx).getAsJsonObject())) onR = m; else offR = m;
            }
            diffs[r] = (onR - offR) / 1_000_000.0;
            roundPct.add(pct(onR, offR));
            if (onR < offR) onFaster++;
        }
        o.add("roundDeltasPct", roundPct);
        o.addProperty("rounds", rounds);
        o.addProperty("roundsOnFaster", onFaster);
        o.add("pairedTTest", BenchStats.pairedTTest(diffs));
        final double[] ci = BenchStats.blockBootstrapMeanDiffCi(off, on, 50, 2000, 0x5EED);
        final JsonArray ciJson = new JsonArray();
        ciJson.add(BenchStats.ms(ci[0]));
        ciJson.add(BenchStats.ms(ci[1]));
        o.add("blockBootstrapMeanDiffMs95", ciJson);
        return o;
    }

    static void log(String id, JsonObject summary) {
        for (String series : new String[] {"tick", "scenario", "level", "postEvents"}) {
            final JsonObject c = summary.getAsJsonObject(series);
            final JsonObject off = summary.getAsJsonObject("off").getAsJsonObject(series);
            final JsonObject on = summary.getAsJsonObject("on").getAsJsonObject(series);
            ArcadiaTweaks.LOGGER.info("[bench] SUMMARY {} {}: OFF mean {} p95 {} ms | ON mean {} p95 {} ms | delta {}% | ON faster in {}/{} rounds | t-test p={} | 95% CI of diff {} ms",
                    id, series, off.get("meanMs"), off.get("p95Ms"), on.get("meanMs"), on.get("p95Ms"),
                    c.get("deltaMeanPct"), c.get("roundsOnFaster"), c.get("rounds"),
                    c.getAsJsonObject("pairedTTest").get("pTwoSided"), c.get("blockBootstrapMeanDiffMs95"));
        }
    }

    private static boolean isOn(JsonObject phase) {
        return "ON".equals(phase.get("mode").getAsString());
    }

    private static long[] concat(JsonArray phases, List<long[][]> samples, boolean on, int series) {
        int n = 0;
        for (int i = 0; i < phases.size(); i++) {
            if (isOn(phases.get(i).getAsJsonObject()) == on) n += samples.get(i)[series].length;
        }
        final long[] out = new long[n];
        int k = 0;
        for (int i = 0; i < phases.size(); i++) {
            if (isOn(phases.get(i).getAsJsonObject()) != on) continue;
            final long[] s = samples.get(i)[series];
            System.arraycopy(s, 0, out, k, s.length);
            k += s.length;
        }
        return out;
    }

    private static double p95(long[] v) {
        final long[] sorted = v.clone();
        java.util.Arrays.sort(sorted);
        return BenchStats.percentile(sorted, 95);
    }

    private static double pct(double on, double off) {
        if (off == 0 || Double.isNaN(off) || Double.isNaN(on)) return Double.NaN;
        return BenchStats.round((on - off) / off * 100.0, 2);
    }
}
