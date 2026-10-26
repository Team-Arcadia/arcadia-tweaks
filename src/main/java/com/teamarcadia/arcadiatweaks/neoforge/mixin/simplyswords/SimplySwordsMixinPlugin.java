package com.teamarcadia.arcadiatweaks.neoforge.mixin.simplyswords;

import com.teamarcadia.arcadiatweaks.ArcadiaTweaks;
import com.teamarcadia.arcadiatweaks.neoforge.mixin.ArcadiaMixinTargetInspector;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Applies the Runic Forge mixins only when the Simply Swords internals they target are present.
 *
 * @author vyrriox
 */
public final class SimplySwordsMixinPlugin implements IMixinConfigPlugin {

    private static final String HANDLER = "net.sweenus.simplyswords.screen.RunicForgeScreenHandler";
    private static final String AWAKENING_API = "net.sweenus.simplyswords.api.AwakeningApi";
    private static final String FORM_REGISTRY = "net.sweenus.simplyswords.api.AwakeningFormRegistry";
    private static final Set<String> LOGGED_SKIPS = new HashSet<>();

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
        final boolean compatible = switch (simpleMixinName) {
            case "RunicForgeScreenHandlerMixin" -> handlerCompatible();
            case "RunicForgeTabletSlotMixin" -> handlerCompatible()
                    && ArcadiaMixinTargetInspector.hasMethod(targetClassName, "mayPlace", "(Lnet/minecraft/world/item/ItemStack;)Z");
            default -> true;
        };
        if (!compatible && LOGGED_SKIPS.add(simpleMixinName)) {
            ArcadiaTweaks.LOGGER.warn("[simplyswords] Skipping {}: target structure changed in {}.", simpleMixinName, targetClassName);
        }
        return compatible;
    }

    private static boolean handlerCompatible() {
        return ArcadiaMixinTargetInspector.hasField(HANDLER, "originalStack", "Lnet/minecraft/world/item/ItemStack;")
                && ArcadiaMixinTargetInspector.hasMethod(HANDLER, "extractWeaponContents", "(Lnet/minecraft/world/item/ItemStack;)V")
                && ArcadiaMixinTargetInspector.hasMethod(HANDLER, "buildConfiguredPreview",
                "(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;")
                && ArcadiaMixinTargetInspector.hasMethodCall(HANDLER, "extractWeaponContents", "(Lnet/minecraft/world/item/ItemStack;)V",
                AWAKENING_API, "getLevel", "(Lnet/minecraft/world/item/ItemStack;)I")
                && ArcadiaMixinTargetInspector.hasMethodCall(HANDLER, "extractWeaponContents", "(Lnet/minecraft/world/item/ItemStack;)V",
                AWAKENING_API, "setLevel", "(Lnet/minecraft/world/item/ItemStack;I)V")
                && ArcadiaMixinTargetInspector.hasMethodCall(HANDLER, "buildConfiguredPreview",
                "(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;",
                AWAKENING_API, "setLevel", "(Lnet/minecraft/world/item/ItemStack;I)V")
                && ArcadiaMixinTargetInspector.hasMethodCall(HANDLER, "buildConfiguredPreview",
                "(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;",
                FORM_REGISTRY, "resolvePreview",
                "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;IILnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/item/ItemStack;");
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
    }
}
