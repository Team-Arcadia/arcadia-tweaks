package com.teamarcadia.arcadiatweaks.neoforge.mixin.copycats;

import com.teamarcadia.arcadiatweaks.ArcadiaTweaks;
import com.teamarcadia.arcadiatweaks.neoforge.mixin.ArcadiaMixinTargetInspector;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Applies the Copycats+ mixins only when the Copycats+ internals they target are present.
 *
 * @author vyrriox
 */
public final class CopycatsMixinPlugin implements IMixinConfigPlugin {

    private static final String STORAGE = "com.copycatsplus.copycats.foundation.copycat.multistate.MaterialItemStorage";
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
        if (!ArcadiaMixinTargetInspector.hasClass(targetClassName)) {
            // Copycats+ is not installed: nothing to patch, nothing to report.
            return false;
        }
        final boolean compatible = !"MultiStateCopycatKeepMaterialsMixin".equals(simpleMixinName)
                || ArcadiaMixinTargetInspector.hasMethodCall(targetClassName, "invalidate", "()V", STORAGE, "clear", "()V");
        if (!compatible && LOGGED_SKIPS.add(simpleMixinName)) {
            ArcadiaTweaks.LOGGER.warn("[copycats] Skipping {}: target structure changed in {}.", simpleMixinName, targetClassName);
        }
        return compatible;
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
