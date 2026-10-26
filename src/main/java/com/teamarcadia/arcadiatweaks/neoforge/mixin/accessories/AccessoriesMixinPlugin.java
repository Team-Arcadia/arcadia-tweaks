package com.teamarcadia.arcadiatweaks.neoforge.mixin.accessories;

import com.teamarcadia.arcadiatweaks.ArcadiaTweaks;
import com.teamarcadia.arcadiatweaks.common.modules.accessories.AccessoriesTweaks;
import com.teamarcadia.arcadiatweaks.neoforge.accessories.AccessoriesContainsSurgery;
import com.teamarcadia.arcadiatweaks.neoforge.accessories.AccessoriesFingerprints;
import com.teamarcadia.arcadiatweaks.neoforge.mixin.ArcadiaMixinTargetInspector;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Applies the Accessories optimizations only on the exact Accessories build they were proven
 * against (1.1.0-beta.54). Without Accessories nothing is applied and nothing is logged above info;
 * on another build each optimization whose pinned bytecode changed is skipped with one warning.
 *
 * @author vyrriox
 */
public final class AccessoriesMixinPlugin implements IMixinConfigPlugin {

    static final String EVENT_HANDLER = "io.wispforest.accessories.impl.AccessoriesEventHandler";
    static final String HOLDER = "io.wispforest.accessories.impl.AccessoriesHolderImpl";
    static final String CAPABILITY_IMPL = "io.wispforest.accessories.impl.AccessoriesCapabilityImpl";
    static final String CONTAINER_IMPL = "io.wispforest.accessories.impl.AccessoriesContainerImpl";
    private static final String ANCHOR = "AccessoriesInventoryAnchorMixin";

    private Boolean accessoriesPresent;
    private Boolean containsCompatible;
    private Boolean tickCompatible;

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        final String simpleMixinName = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        if (!accessoriesPresent()) {
            return false;
        }
        return switch (simpleMixinName) {
            case ANCHOR -> containsCompatible();
            case "AccessoriesEventHandlerTickMixin", "AccessoriesEventHandlerInvoker", "AccessoriesHolderImplAccessor" ->
                    tickCompatible();
            default -> true;
        };
    }

    private boolean accessoriesPresent() {
        if (accessoriesPresent == null) {
            accessoriesPresent = ArcadiaMixinTargetInspector.hasClass(EVENT_HANDLER);
            if (!accessoriesPresent) {
                ArcadiaTweaks.LOGGER.info("[accessories] Accessories is not installed; the module patches nothing.");
                AccessoriesTweaks.setContainsPatchState("Accessories absent");
                AccessoriesTweaks.setTickPatchState("Accessories absent");
            }
        }
        return accessoriesPresent;
    }

    /** C1: the pinned bytecode, and no override of the default {@code isEquipped} chain. */
    public static List<String> containsMismatches() {
        final List<String> mismatches = new java.util.ArrayList<>(AccessoriesFingerprints.mismatches(AccessoriesFingerprints.CONTAINS));
        for (String desc : List.of(
                "(Ljava/util/function/Predicate;)Z",
                "(Ljava/util/function/Predicate;Lio/wispforest/accessories/api/EquipmentChecking;)Z",
                "(Lio/wispforest/accessories/api/caching/ItemStackBasedPredicate;Lio/wispforest/accessories/api/EquipmentChecking;)Z")) {
            if (ArcadiaMixinTargetInspector.hasMethod(CAPABILITY_IMPL, "isEquipped", desc)) {
                mismatches.add(CAPABILITY_IMPL + ".isEquipped" + desc + " (overridden)");
            }
        }
        if (ArcadiaMixinTargetInspector.hasMethod(CONTAINER_IMPL, "createReference", "(I)Lio/wispforest/accessories/api/slot/SlotReference;")) {
            mismatches.add(CONTAINER_IMPL + ".createReference (overridden)");
        }
        return mismatches;
    }

    /** T-A: the pinned tick, the members the copy uses, and no {@code createReference} override. */
    public static List<String> tickMismatches() {
        final List<String> mismatches = new java.util.ArrayList<>(AccessoriesFingerprints.mismatches(AccessoriesFingerprints.TICK));
        if (ArcadiaMixinTargetInspector.hasMethod(CONTAINER_IMPL, "createReference", "(I)Lio/wispforest/accessories/api/slot/SlotReference;")) {
            mismatches.add(CONTAINER_IMPL + ".createReference (overridden)");
        }
        if (!ArcadiaMixinTargetInspector.hasField(HOLDER, "loadedFromTag", "Z")) {
            mismatches.add(HOLDER + ".loadedFromTag (missing)");
        }
        if (!ArcadiaMixinTargetInspector.hasField(HOLDER, "invalidStacks", "Ljava/util/List;")) {
            mismatches.add(HOLDER + ".invalidStacks (missing)");
        }
        if (!ArcadiaMixinTargetInspector.hasMethod(HOLDER, "getAllSlotContainers", "()Ljava/util/Map;")) {
            mismatches.add(HOLDER + ".getAllSlotContainers (missing)");
        }
        if (!ArcadiaMixinTargetInspector.hasMethod(EVENT_HANDLER, "recursiveStackChange",
                "(Lio/wispforest/accessories/api/slot/SlotReference;"
                        + "Lio/wispforest/accessories/api/components/AccessoryNestContainerContents;"
                        + "Lio/wispforest/accessories/api/components/AccessoryNestContainerContents;)V")) {
            mismatches.add(EVENT_HANDLER + ".recursiveStackChange (missing)");
        }
        return mismatches;
    }

    private boolean containsCompatible() {
        if (containsCompatible == null) {
            final List<String> mismatches = containsMismatches();
            containsCompatible = mismatches.isEmpty();
            if (!containsCompatible) {
                ArcadiaTweaks.LOGGER.warn("[accessories] C1 (contains fast path) skipped: Accessories is not the build it was "
                        + "verified on (1.1.0-beta.54). Changed: {}", mismatches);
                AccessoriesTweaks.setContainsPatchState("skipped, Accessories build changed");
            }
        }
        return containsCompatible;
    }

    private boolean tickCompatible() {
        if (tickCompatible == null) {
            final List<String> mismatches = tickMismatches();
            tickCompatible = mismatches.isEmpty();
            if (tickCompatible) {
                AccessoriesTweaks.setTickPatchState("applied");
            } else {
                ArcadiaTweaks.LOGGER.warn("[accessories] T-A (allocation-trimmed tick) skipped: Accessories is not the build it "
                        + "was verified on (1.1.0-beta.54). Changed: {}", mismatches);
                AccessoriesTweaks.setTickPatchState("skipped, Accessories build changed");
            }
        }
        return tickCompatible;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        if (!mixinClassName.endsWith("." + ANCHOR)) {
            return;
        }
        final AccessoriesContainsSurgery.Result result = AccessoriesContainsSurgery.apply(targetClass);
        switch (result) {
            case APPLIED, ALREADY_APPLIED -> {
                AccessoriesTweaks.setContainsPatchState("applied");
                ArcadiaTweaks.LOGGER.info("[accessories] C1 contains fast path installed in {}.", targetClassName);
            }
            default -> {
                AccessoriesTweaks.setContainsPatchState("skipped, " + result);
                ArcadiaTweaks.LOGGER.warn("[accessories] C1 (contains fast path) skipped: merged Accessories handler in {} "
                        + "does not have the verified shape ({}). Accessories runs unchanged.", targetClassName, result);
            }
        }
    }
}
