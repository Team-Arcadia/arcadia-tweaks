package com.teamarcadia.arcadiatweaks.neoforge.gametest.bench;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Entry point of the in-server A/B benchmark ({@code ./gradlew runBenchServer}).
 *
 * <p>The test lives in its own GameTest namespace, {@value #NAMESPACE}. The regular
 * {@code runGameTestServer} only enables the {@code arcadiatweaks} namespace, so this test is not
 * even registered there; {@code runBenchServer} enables only this namespace. As a second guard the
 * test passes at once unless the JVM runs with {@code -Darcadia.bench=true}.
 *
 * <p>The test holds the GameTest server open while {@link BenchRunner} works through the scenarios,
 * then succeeds, and the GameTest server shuts down as usual.
 *
 * @author vyrriox
 */
@GameTestHolder(ArcadiaBenchGameTest.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class ArcadiaBenchGameTest {

    static final String NAMESPACE = "arcadiatweaks_bench";

    private ArcadiaBenchGameTest() {}

    @GameTest(template = "empty", batch = NAMESPACE, timeoutTicks = 2_000_000)
    public static void inServerAbBenchmark(GameTestHelper helper) {
        if (!Boolean.getBoolean("arcadia.bench")) {
            helper.succeed();
            return;
        }
        BenchRunner.start(helper);
    }
}
