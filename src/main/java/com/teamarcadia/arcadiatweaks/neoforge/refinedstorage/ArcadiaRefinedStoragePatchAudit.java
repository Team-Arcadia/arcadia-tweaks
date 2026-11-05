package com.teamarcadia.arcadiatweaks.neoforge.refinedstorage;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Startup check of the Refined Storage patches A1 and B1 to B3: loads each target class (which is
 * when Mixin patches it, so a broken patch shows at startup instead of in the middle of a game) and
 * looks for what the patch added to it. A class only loads when it is first used, so without this a
 * fresh world with no importer or exporter would never say whether the patches apply.
 *
 * @author vyrriox
 */
public final class ArcadiaRefinedStoragePatchAudit {

    private static final String API = "com.refinedmods.refinedstorage.api.";

    private ArcadiaRefinedStoragePatchAudit() {}

    /** A1: the static field holding the network between the two redirects of {@code CraftingState.of}. */
    public static boolean liveViewApplied() {
        final Class<?> c = load(API + "autocrafting.calculation.CraftingState");
        if (c == null) return false;
        for (Field f : c.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType().getName().equals(API + "storage.root.RootStorage")) {
                return true;
            }
        }
        return false;
    }

    public static boolean importerBackoffApplied() {
        return hasBackoffField(API + "network.impl.node.importer.ImporterNetworkNode");
    }

    /** B2 needs both the exporter cycle and the filter result report. */
    public static boolean exporterBackoffApplied() {
        return hasBackoffField(API + "network.impl.node.exporter.ExporterNetworkNode")
                && hasMethodNamed(API + "network.impl.node.exporter.ExporterNetworkNode$ExporterTask", "arcadia$reportActivity");
    }

    public static boolean sinkBackoffApplied() {
        return hasBackoffField(API + "autocrafting.task.ExternalTaskPattern");
    }

    private static boolean hasBackoffField(String className) {
        final Class<?> c = load(className);
        if (c == null) return false;
        for (Field f : c.getDeclaredFields()) {
            if (f.getType() == ArcadiaIdleBackoff.class) return true;
        }
        return false;
    }

    private static boolean hasMethodNamed(String className, String part) {
        final Class<?> c = load(className);
        if (c == null) return false;
        for (Method m : c.getDeclaredMethods()) {
            if (m.getName().contains(part)) return true;
        }
        return false;
    }

    private static Class<?> load(String className) {
        try {
            return Class.forName(className, false, ArcadiaRefinedStoragePatchAudit.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }
}
