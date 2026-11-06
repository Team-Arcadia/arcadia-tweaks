package com.teamarcadia.arcadiatweaks.neoforge.mixin.macawsdoors;

import com.teamarcadia.arcadiatweaks.ArcadiaTweaks;
import com.teamarcadia.arcadiatweaks.neoforge.mixin.ArcadiaMixinTargetInspector;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Applies the Macaw's Doors guard only on the build it was written for: the guard reads a local variable of
 * {@code GarageDoor.toggleDoor}, so that method is pinned to its bytecode (Macaw's Doors 1.1.5). Any other build
 * keeps its own behaviour, with one warning. Macaw's Doors not being installed is not worth a warning.
 *
 * @author vyrriox
 */
public final class MacawsDoorsMixinPlugin implements IMixinConfigPlugin {

    public static final String GARAGE_DOOR = "com.mcwdoors.kikoz.objects.GarageDoor";
    public static final String TOGGLE_DOOR = "toggleDoor";
    public static final String TOGGLE_DOOR_DESC = "(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;ZLnet/minecraft/core/Direction;)V";
    public static final String TOGGLE_DOOR_FINGERPRINT = "65036a39722af24d7dec74ac3484a4f6c6343317f0e0a41d04a7b5375106d25e";

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
        if (!ArcadiaMixinTargetInspector.hasClass(targetClassName)) {
            return false;
        }
        final boolean compatible = TOGGLE_DOOR_FINGERPRINT.equals(
                ArcadiaMixinTargetInspector.methodFingerprint(targetClassName, TOGGLE_DOOR, TOGGLE_DOOR_DESC));
        if (!compatible && LOGGED_SKIPS.add(targetClassName)) {
            ArcadiaTweaks.LOGGER.warn("[macawsdoors] Skipping the garage door guard: {} changed.", targetClassName);
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
