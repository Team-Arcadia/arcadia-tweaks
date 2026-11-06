package com.teamarcadia.arcadiatweaks.neoforge.gametest;

import com.teamarcadia.arcadiatweaks.ArcadiaTweaks;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraft.gametest.framework.GameTestTicker;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

/**
 * Runs the GameTests on a copy of the server pack, a production launch where NeoForge leaves them out.
 *
 * <p>A production launch does honour {@code -Dneoforge.gameTestServer=true} and starts the GameTest server, but
 * {@code GameTestHooks.isGametestEnabled()} is false there: no test is registered and the test ticker never
 * runs. With {@code -Darcadia.packTests=true} this class does both for the tests that make sense in the pack.
 * It is not in any released jar (the whole package is excluded): the run needs a jar that adds it, and an
 * {@code empty} template as a data resource, because {@code gameteststructures} is only read in a dev run.</p>
 *
 * @author vyrriox
 */
@EventBusSubscriber(modid = ArcadiaTweaks.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class SpareWorkPackRun {

    public static final String PROPERTY = "arcadia.packTests";

    private SpareWorkPackRun() {}

    @SubscribeEvent
    @SuppressWarnings("deprecation")
    public static void registerInThePack(FMLCommonSetupEvent event) {
        if (!Boolean.getBoolean(PROPERTY)) {
            return;
        }
        final Set<String> namespaces = Set.of(ArcadiaTweaks.MOD_ID);
        for (Class<?> tests : List.of(SpareWorkGameTests.class, SpareWorkPackGameTests.class, CosmeticPresenceGateGameTests.class, GarageDoorGameTests.class)) {
            for (Method method : tests.getDeclaredMethods()) {
                GameTestRegistry.register(method, namespaces);
            }
        }
    }

    @SubscribeEvent
    public static void tickTheTests(ServerTickEvent.Post event) {
        if (Boolean.getBoolean(PROPERTY)) {
            GameTestTicker.SINGLETON.tick();
        }
    }
}
