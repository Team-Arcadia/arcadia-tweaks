package com.teamarcadia.arcadiatweaks.neoforge.sparework;

import java.lang.reflect.Method;

/**
 * Startup check of the spare work patches: loads each target class (which is when Mixin patches it, so a
 * broken patch shows at startup instead of in the middle of a game) and looks for the handler the patch added.
 * False for a mod that is not installed.
 *
 * @author vyrriox
 */
public final class SpareWorkPatchAudit {

    private SpareWorkPatchAudit() {}

    public static boolean fastSuiteApplied() {
        return hasMethodNamed("dev.shadowsoffire.fastsuite.CachedRecipeList", "arcadia$skipEmptyParallelLookup")
                && hasMethodNamed("dev.shadowsoffire.fastsuite.CachedRecipeList", "arcadia$skipEmptyParallelList");
    }

    public static boolean simplySwordsApplied() {
        return hasMethodNamed("net.sweenus.simplyswords.loot.PityLootContext", "arcadia$reusableStack");
    }

    public static boolean structuresApplied() {
        return hasMethodNamed("net.minecraft.world.level.StructureManager", "arcadia$useLoadedChunk");
    }

    public static boolean crabbersDelightApplied() {
        return hasMethodNamed("alabaster.crabbersdelight.common.utils.TackleBoxProximity", "arcadia$skipEmptySearch");
    }

    private static boolean hasMethodNamed(String className, String part) {
        try {
            final Class<?> c = Class.forName(className, false, SpareWorkPatchAudit.class.getClassLoader());
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().contains(part)) {
                    return true;
                }
            }
            return false;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }
}
