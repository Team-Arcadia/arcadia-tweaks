package com.teamarcadia.arcadiatweaks.neoforge.gametest.bench;

import com.google.gson.JsonObject;

import java.util.Arrays;
import java.util.SplittableRandom;

/**
 * Small statistics toolkit for the bench: descriptive stats of tick samples, a paired t-test on
 * round means and a bootstrap confidence interval of a difference of means.
 *
 * @author vyrriox
 */
public final class BenchStats {

    private BenchStats() {}

    /** Descriptive statistics of a sample in nanoseconds, reported in milliseconds. */
    public static JsonObject describeMs(long[] samplesNs) {
        final JsonObject o = new JsonObject();
        o.addProperty("n", samplesNs.length);
        if (samplesNs.length == 0) {
            return o;
        }
        final long[] sorted = samplesNs.clone();
        Arrays.sort(sorted);
        o.addProperty("meanMs", ms(mean(sorted)));
        o.addProperty("stdevMs", ms(stdev(sorted)));
        o.addProperty("medianMs", ms(percentile(sorted, 50)));
        o.addProperty("p95Ms", ms(percentile(sorted, 95)));
        o.addProperty("p99Ms", ms(percentile(sorted, 99)));
        o.addProperty("minMs", ms(sorted[0]));
        o.addProperty("maxMs", ms(sorted[sorted.length - 1]));
        return o;
    }

    public static double mean(long[] v) {
        double sum = 0;
        for (long x : v) sum += x;
        return v.length == 0 ? Double.NaN : sum / v.length;
    }

    public static double mean(double[] v) {
        double sum = 0;
        for (double x : v) sum += x;
        return v.length == 0 ? Double.NaN : sum / v.length;
    }

    public static double stdev(long[] v) {
        if (v.length < 2) return 0;
        final double m = mean(v);
        double ss = 0;
        for (long x : v) ss += (x - m) * (x - m);
        return Math.sqrt(ss / (v.length - 1));
    }

    public static double stdev(double[] v) {
        if (v.length < 2) return 0;
        final double m = mean(v);
        double ss = 0;
        for (double x : v) ss += (x - m) * (x - m);
        return Math.sqrt(ss / (v.length - 1));
    }

    /** Nearest-rank percentile on a sorted array. */
    public static double percentile(long[] sorted, double p) {
        if (sorted.length == 0) return Double.NaN;
        final int rank = (int) Math.ceil(p / 100.0 * sorted.length);
        return sorted[Math.max(0, Math.min(sorted.length - 1, rank - 1))];
    }

    public static double ms(double ns) {
        return Math.round(ns / 1_000.0) / 1_000.0;
    }

    /**
     * Paired two-sided t-test on per-round differences (ON minus OFF). Returns t, df and p.
     */
    public static JsonObject pairedTTest(double[] differences) {
        final JsonObject o = new JsonObject();
        final int n = differences.length;
        o.addProperty("n", n);
        if (n < 2) {
            return o;
        }
        final double m = mean(differences);
        final double sd = stdev(differences);
        final double t = sd == 0 ? (m == 0 ? 0 : Double.POSITIVE_INFINITY * Math.signum(m)) : m / (sd / Math.sqrt(n));
        final int df = n - 1;
        o.addProperty("meanDiff", m);
        o.addProperty("t", round(t, 3));
        o.addProperty("df", df);
        o.addProperty("pTwoSided", Double.isInfinite(t) ? 0.0 : round(studentTwoSidedP(t, df), 6));
        return o;
    }

    /**
     * Percentile 95% interval of mean(on) - mean(off) from a block bootstrap: the samples are cut
     * in consecutive blocks of {@code block} ticks and whole blocks are resampled, which keeps the
     * correlation between neighbouring ticks (resampling single ticks would give a far too narrow
     * interval). Fixed seed so a run is reproducible from its samples.
     */
    public static double[] blockBootstrapMeanDiffCi(long[] off, long[] on, int block, int resamples, long seed) {
        if (off.length < block || on.length < block) {
            return new double[] {Double.NaN, Double.NaN};
        }
        final double[] offBlocks = blockMeans(off, block);
        final double[] onBlocks = blockMeans(on, block);
        final SplittableRandom rnd = new SplittableRandom(seed);
        final double[] diffs = new double[resamples];
        for (int r = 0; r < resamples; r++) {
            diffs[r] = resampleMean(onBlocks, rnd) - resampleMean(offBlocks, rnd);
        }
        Arrays.sort(diffs);
        return new double[] {diffs[(int) (0.025 * resamples)], diffs[(int) Math.min(resamples - 1, 0.975 * resamples)]};
    }

    private static double[] blockMeans(long[] v, int block) {
        final double[] out = new double[v.length / block];
        for (int b = 0; b < out.length; b++) {
            double sum = 0;
            for (int i = 0; i < block; i++) sum += v[b * block + i];
            out[b] = sum / block;
        }
        return out;
    }

    private static double resampleMean(double[] v, SplittableRandom rnd) {
        double sum = 0;
        for (int i = 0; i < v.length; i++) {
            sum += v[rnd.nextInt(v.length)];
        }
        return sum / v.length;
    }

    public static double round(double v, int digits) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return v;
        final double f = Math.pow(10, digits);
        return Math.round(v * f) / f;
    }

    /** Two-sided p-value of Student's t: I_{df/(df+t^2)}(df/2, 1/2). */
    static double studentTwoSidedP(double t, int df) {
        final double x = df / (df + t * t);
        return regularizedIncompleteBeta(x, df / 2.0, 0.5);
    }

    private static double regularizedIncompleteBeta(double x, double a, double b) {
        if (x <= 0) return 0;
        if (x >= 1) return 1;
        final double lnBeta = logGamma(a + b) - logGamma(a) - logGamma(b);
        final double front = Math.exp(Math.log(x) * a + Math.log(1 - x) * b + lnBeta);
        if (x < (a + 1) / (a + b + 2)) {
            return front * betaContinuedFraction(x, a, b) / a;
        }
        return 1 - front * betaContinuedFraction(1 - x, b, a) / b;
    }

    private static double betaContinuedFraction(double x, double a, double b) {
        final double tiny = 1e-30;
        double c = 1;
        double d = 1 - (a + b) * x / (a + 1);
        if (Math.abs(d) < tiny) d = tiny;
        d = 1 / d;
        double h = d;
        for (int m = 1; m <= 300; m++) {
            final int m2 = 2 * m;
            double aa = m * (b - m) * x / ((a + m2 - 1) * (a + m2));
            d = 1 + aa * d;
            if (Math.abs(d) < tiny) d = tiny;
            c = 1 + aa / c;
            if (Math.abs(c) < tiny) c = tiny;
            d = 1 / d;
            h *= d * c;
            aa = -(a + m) * (a + b + m) * x / ((a + m2) * (a + m2 + 1));
            d = 1 + aa * d;
            if (Math.abs(d) < tiny) d = tiny;
            c = 1 + aa / c;
            if (Math.abs(c) < tiny) c = tiny;
            d = 1 / d;
            final double del = d * c;
            h *= del;
            if (Math.abs(del - 1) < 1e-12) break;
        }
        return h;
    }

    private static double logGamma(double x) {
        final double[] cof = {76.18009172947146, -86.50532032941677, 24.01409824083091,
                -1.231739572450155, 0.1208650973866179e-2, -0.5395239384953e-5};
        double y = x;
        double tmp = x + 5.5;
        tmp -= (x + 0.5) * Math.log(tmp);
        double ser = 1.000000000190015;
        for (double c : cof) {
            ser += c / ++y;
        }
        return -tmp + Math.log(2.5066282746310005 * ser / x);
    }
}
