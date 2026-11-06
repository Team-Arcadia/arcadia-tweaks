package com.teamarcadia.arcadiatweaks.common.modules.sparework;

import com.teamarcadia.arcadiatweaks.common.config.ArcadiaConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Config switches of the spare work patches, read on hot paths, copied into plain fields once per server
 * tick (same reasoning as {@code RefinedStorageRuntimeFlags}). The copy only changes at the start of a tick,
 * so no pair of calls inside a tick can see two different values. Until the config is loaded every switch
 * reads false, which is the original path.
 *
 * @author vyrriox
 */
public final class SpareWorkFlags {

    private static volatile boolean fastSuiteSkipEmptyParallelLookup;
    private static volatile boolean simplySwordsReusePityStack;
    private static volatile boolean structureLookupUsesLoadedChunk;
    private static volatile boolean crabbersDelightSkipEmptyTackleBoxSearch;
    private static volatile Thread serverThread;

    static {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Pre event) -> {
            serverThread = Thread.currentThread();
            refresh();
        });
        refresh();
    }

    private SpareWorkFlags() {}

    public static boolean fastSuiteSkipEmptyParallelLookup() {
        return fastSuiteSkipEmptyParallelLookup;
    }

    public static boolean simplySwordsReusePityStack() {
        return simplySwordsReusePityStack;
    }

    public static boolean structureLookupUsesLoadedChunk() {
        return structureLookupUsesLoadedChunk;
    }

    public static boolean crabbersDelightSkipEmptyTackleBoxSearch() {
        return crabbersDelightSkipEmptyTackleBoxSearch;
    }

    /** True on the thread that runs the server ticks, known from the first tick on. */
    public static boolean onServerThread() {
        return Thread.currentThread() == serverThread;
    }

    /** Copies the current config values. Safe to call before the config is loaded. */
    public static void refresh() {
        try {
            final boolean module = ArcadiaConfig.MODULE_SPARE_WORK_ENABLED.get();
            fastSuiteSkipEmptyParallelLookup = module && ArcadiaConfig.SPARE_WORK.fastSuiteSkipEmptyParallelLookup.get();
            simplySwordsReusePityStack = module && ArcadiaConfig.SPARE_WORK.simplySwordsReusePityStack.get();
            structureLookupUsesLoadedChunk = module && ArcadiaConfig.SPARE_WORK.structureLookupUsesLoadedChunk.get();
            crabbersDelightSkipEmptyTackleBoxSearch = module && ArcadiaConfig.SPARE_WORK.crabbersDelightSkipEmptyTackleBoxSearch.get();
        } catch (IllegalStateException | NullPointerException configNotLoaded) {
            fastSuiteSkipEmptyParallelLookup = false;
            simplySwordsReusePityStack = false;
            structureLookupUsesLoadedChunk = false;
            crabbersDelightSkipEmptyTackleBoxSearch = false;
        }
    }
}
