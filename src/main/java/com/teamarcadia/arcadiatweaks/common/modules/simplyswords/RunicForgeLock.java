package com.teamarcadia.arcadiatweaks.common.modules.simplyswords;

import com.teamarcadia.arcadiatweaks.common.config.ArcadiaConfig;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.sweenus.simplyswords.api.AwakeningApi;

/**
 * Locked awakening level of a Simply Swords unique weapon in the Runic Forge (bug #321).
 *
 * Simply Swords reads a unique weapon without its {@code simplyswords:awakening} component as
 * fully awakened (level 8): weapons from before the awakening system, and every unique handed
 * out as a plain item (lootboxes, quests, commands). The Runic Forge turns the level of a weapon
 * into that many Runic Tablet items when the weapon is placed in it, so such a weapon gave 8
 * tablets that never existed.
 *
 * The first time such a weapon enters the forge, its implicit level is stored in its custom data
 * as a lock. The forge then hands out only the tablets above the lock (none), builds the weapon
 * back with lock + tablets placed, and only accepts tablets in the slots above the lock. The
 * weapon keeps its power and its gems can still be changed. Weapons that already carry the
 * component (every awakening done with tablets) never get a lock and behave as before.
 *
 * @author vyrriox
 */
public final class RunicForgeLock {

    public static final int MAX_LEVEL = 8;
    private static final String KEY = "arcadiatweaks_locked_awakening";
    private static final ResourceLocation AWAKENING_ID = ResourceLocation.fromNamespaceAndPath("simplyswords", "awakening");
    private static DataComponentType<?> awakeningType;

    private RunicForgeLock() {}

    public static boolean enabled() {
        return ArcadiaConfig.RUNIC_FORGE_LOCK_ENABLED.get();
    }

    /** Locked level stored on the weapon, 0 when it has none. */
    public static int get(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        final CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains(KEY)) {
            return 0;
        }
        return Math.clamp(data.copyTag().getInt(KEY), 0, MAX_LEVEL);
    }

    /**
     * Stores the implicit level of a weapon that has no awakening component yet, on the weapon
     * and on its saved copy. Does nothing for a weapon that already has the component or a lock.
     */
    public static void lockImplicitLevel(ItemStack stack, ItemStack savedCopy) {
        if (!AwakeningApi.usesAwakeningProgression(stack) || get(stack) > 0 || hasAwakeningComponent(stack)) {
            return;
        }
        final int implicit = Math.clamp(AwakeningApi.getLevel(stack), 0, MAX_LEVEL);
        if (implicit <= 0) {
            return;
        }
        put(stack, implicit);
        if (savedCopy != null && !savedCopy.isEmpty()) {
            put(savedCopy, implicit);
        }
    }

    private static void put(ItemStack stack, int level) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(KEY, level));
    }

    private static boolean hasAwakeningComponent(ItemStack stack) {
        if (awakeningType == null) {
            awakeningType = BuiltInRegistries.DATA_COMPONENT_TYPE.get(AWAKENING_ID);
        }
        return awakeningType != null && stack.has(awakeningType);
    }
}
