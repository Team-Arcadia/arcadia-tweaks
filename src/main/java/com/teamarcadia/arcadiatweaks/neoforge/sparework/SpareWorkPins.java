package com.teamarcadia.arcadiatweaks.neoforge.sparework;

import com.teamarcadia.arcadiatweaks.neoforge.mixin.ArcadiaMixinTargetInspector;

import java.util.ArrayList;
import java.util.List;

/**
 * Bytecode the spare work patches are proven against: FastSuite 6.0.7, Simply Swords 1.70.2 and Crabber's
 * Delight 1.3.1. Any difference (an update, another build) turns the matching patch off.
 *
 * <p>Regenerate with {@code SpareWorkPinsTest} after re-verifying the proof on a new build.</p>
 *
 * @author vyrriox
 */
public final class SpareWorkPins {

    public record Pin(String className, String method, String descriptor, String sha256) {
        public String describe() {
            return className + "." + method + descriptor;
        }
    }

    private static final String CACHED_RECIPE_LIST = "dev.shadowsoffire.fastsuite.CachedRecipeList";
    private static final String PITY_CONTEXT = "net.sweenus.simplyswords.loot.PityLootContext";
    private static final String TACKLE_BOX_PROXIMITY = "alabaster.crabbersdelight.common.utils.TackleBoxProximity";
    private static final String RECIPE_INPUT = "Lnet/minecraft/world/item/crafting/RecipeInput;";
    private static final String LEVEL = "Lnet/minecraft/world/level/Level;";

    /** The constructor is the only writer of the list, and these lookups only read it. */
    public static final List<Pin> FASTSUITE = List.of(
            new Pin(CACHED_RECIPE_LIST, "<init>", "(Lnet/minecraft/world/item/crafting/RecipeType;Ljava/util/Collection;)V", "af8e267de36fefe35a2e4fed1fa99111b35e8793a0abb22b0d81efdca928af1b"),
            new Pin(CACHED_RECIPE_LIST, "getRecipeFor", "(" + RECIPE_INPUT + LEVEL + ")Ljava/util/Optional;", "8b90c1aa4093153bfaf4b509e79b2ad0a485f5346ed3f7886284cb1c2ca7b0b0"),
            new Pin(CACHED_RECIPE_LIST, "lambda$getRecipeFor$1", "(" + RECIPE_INPUT + LEVEL + ")Ljava/util/Optional;", "f83d1076bbb6159f1e86aa1bb501e6be089ea676a06ac136f80b9ae51d5d19a7"),
            new Pin(CACHED_RECIPE_LIST, "getRecipesFor", "(" + RECIPE_INPUT + LEVEL + ")Ljava/util/List;", "ba66531e1e94e2f1255fc6e655461b030366be412e2942a3e5caf7112d3c9033"),
            new Pin(CACHED_RECIPE_LIST, "lambda$getRecipesFor$5", "(Ljava/util/function/Predicate;)Ljava/util/List;", "275a62263cc32ff28f1f0e070bbebe282bb091e8f5dd57d9da3d8d475cd7d7ee")
    );

    /** The whole class: a stack in a thread local, removed only when empty. */
    public static final List<Pin> SIMPLY_SWORDS = List.of(
            new Pin(PITY_CONTEXT, "<clinit>", "()V", "27543da7405bd909c1eb298d3e9fa24108e171471dbca5a2c082e8bd70aeaae9"),
            new Pin(PITY_CONTEXT, "capture", "(Lnet/minecraft/resources/ResourceKey;)V", "922c0cdd1ac270e31225f87eb434a9950ae4c9c4152388b692f5bbd848c022ae"),
            new Pin(PITY_CONTEXT, "take", "()Lnet/minecraft/resources/ResourceKey;", "0fc8f45a9608825db5965cc2b8a144c4530301c42bf991aa8d9757ec5f0e2110")
    );

    /** The search: its cube, what it reads, and null when it finds nothing. */
    public static final List<Pin> CRABBERS_DELIGHT = List.of(
            new Pin(TACKLE_BOX_PROXIMITY, "findClosestPlaced",
                    "(Lnet/minecraft/world/entity/player/Player;)Lalabaster/crabbersdelight/common/utils/TackleBoxProximity$TackleBoxAccess;", "3d698b78391fa0004aad5bc4770d8a812b06b8708aaf9f00a070fb2426567e82")
    );

    private SpareWorkPins() {}

    /** Pins whose method is missing or whose fingerprint differs from the expected value. */
    public static List<String> mismatches(List<Pin> pins) {
        final List<String> out = new ArrayList<>();
        for (Pin pin : pins) {
            final String actual = ArcadiaMixinTargetInspector.methodFingerprint(pin.className(), pin.method(), pin.descriptor());
            if (actual == null) {
                out.add(pin.describe() + " (missing)");
            } else if (!actual.equals(pin.sha256())) {
                out.add(pin.describe() + " (" + actual + ")");
            }
        }
        return out;
    }
}
