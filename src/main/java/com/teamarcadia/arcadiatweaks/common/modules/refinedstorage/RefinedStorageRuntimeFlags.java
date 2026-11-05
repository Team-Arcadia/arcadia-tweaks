package com.teamarcadia.arcadiatweaks.common.modules.refinedstorage;

import com.teamarcadia.arcadiatweaks.common.config.ArcadiaConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Config switches read on very hot Refined Storage paths (component lookup on every node work
 * cycle, log call sites in tickers), copied into plain fields once per server tick.
 *
 * Reading a {@code ModConfigSpec} value costs several checks and an unboxing, which is about as
 * much as the hash lookup S2 saves. The admin menu sets config values directly without a reload
 * event, so the copy is refreshed at the start of every server tick: a toggle applies from the
 * next tick. Until the config is loaded every switch reads false, which is the plain RS path.
 *
 * @author vyrriox
 */
public final class RefinedStorageRuntimeFlags {

    private static volatile boolean singleComponentLookup;
    private static volatile boolean quietHotDebugLogs;
    private static volatile boolean autocraftingLiveView;
    private static volatile Thread serverThread;

    static {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Pre event) -> {
            serverThread = Thread.currentThread();
            refresh();
        });
        refresh();
    }

    private RefinedStorageRuntimeFlags() {}

    public static boolean singleComponentLookup() {
        return singleComponentLookup;
    }

    public static boolean quietHotDebugLogs() {
        return quietHotDebugLogs;
    }

    public static boolean autocraftingLiveView() {
        return autocraftingLiveView;
    }

    /** True on the thread that runs the server ticks, known from the first tick on. */
    public static boolean onServerThread() {
        return Thread.currentThread() == serverThread;
    }

    /** Copies the current config values. Safe to call before the config is loaded. */
    public static void refresh() {
        try {
            final boolean module = ArcadiaConfig.MODULE_REFINED_STORAGE_ENABLED.get();
            singleComponentLookup = module && ArcadiaConfig.REFINED_STORAGE.singleComponentLookup.get();
            quietHotDebugLogs = module && ArcadiaConfig.REFINED_STORAGE.quietHotDebugLogs.get();
            autocraftingLiveView = module && ArcadiaConfig.REFINED_STORAGE.autocraftingLiveView.get();
        } catch (IllegalStateException | NullPointerException configNotLoaded) {
            singleComponentLookup = false;
            quietHotDebugLogs = false;
            autocraftingLiveView = false;
        }
    }
}
