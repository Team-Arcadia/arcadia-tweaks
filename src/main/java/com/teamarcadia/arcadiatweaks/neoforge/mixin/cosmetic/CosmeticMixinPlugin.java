package com.teamarcadia.arcadiatweaks.neoforge.mixin.cosmetic;

import com.teamarcadia.arcadiatweaks.ArcadiaTweaks;
import com.teamarcadia.arcadiatweaks.neoforge.mixin.ArcadiaMixinTargetInspector;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Structural checks for the CosmeticWeapons / CosmeticArmours presence gate.
 *
 * Per procedure class: the MCreator template is unchanged (the {@code execute(Event, Entity)} body calls
 * nothing but the allow-listed methods, writes no field, has at least two {@code contains} calls, and awards
 * only after the last of them). That is what proves the inventory cannot change between two
 * {@code contains} calls of one procedure call.
 *
 * Once per environment: the Curios and Accessories {@code Inventory.contains(ItemStack)} hooks still have the
 * shape the gate relies on. In particular Accessories' predicate must still start with
 * {@code e.isEmpty() &&} (it never matches a non-empty target); if Accessories fixes it, its stacks would have
 * to be part of the snapshot, so the whole gate is skipped with one warning instead.
 *
 * @author vyrriox
 */
public final class CosmeticMixinPlugin implements IMixinConfigPlugin {

    static final String EXECUTE = "execute";
    static final String EXECUTE_DESC = "(Lnet/neoforged/bus/api/Event;Lnet/minecraft/world/entity/Entity;)V";
    static final String ON_PLAYER_TICK = "onPlayerTick";
    static final String ON_PLAYER_TICK_DESC = "(Lnet/neoforged/neoforge/event/tick/PlayerTickEvent$Post;)V";

    static final String CONTAINS = "net/minecraft/world/entity/player/Inventory.contains(Lnet/minecraft/world/item/ItemStack;)Z";
    static final String AWARD = "net/minecraft/server/PlayerAdvancements.award(Lnet/minecraft/advancements/AdvancementHolder;Ljava/lang/String;)Z";

    /** Every call the MCreator template makes; none of them touches an inventory. */
    static final Set<String> ALLOWED_CALLS = Set.of(
            "net/minecraft/server/level/ServerPlayer.level()Lnet/minecraft/world/level/Level;",
            "net/minecraft/server/level/ServerPlayer.getAdvancements()Lnet/minecraft/server/PlayerAdvancements;",
            "net/minecraft/server/MinecraftServer.getAdvancements()Lnet/minecraft/server/ServerAdvancementManager;",
            "net/minecraft/resources/ResourceLocation.parse(Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;",
            "net/minecraft/server/ServerAdvancementManager.get(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/advancements/AdvancementHolder;",
            "net/minecraft/server/PlayerAdvancements.getOrStartProgress(Lnet/minecraft/advancements/AdvancementHolder;)Lnet/minecraft/advancements/AdvancementProgress;",
            "net/minecraft/advancements/AdvancementProgress.isDone()Z",
            "net/minecraft/advancements/AdvancementProgress.getRemainingCriteria()Ljava/lang/Iterable;",
            "java/lang/Iterable.iterator()Ljava/util/Iterator;",
            "java/util/Iterator.hasNext()Z",
            "java/util/Iterator.next()Ljava/lang/Object;",
            "net/minecraft/world/entity/player/Player.getInventory()Lnet/minecraft/world/entity/player/Inventory;",
            "net/neoforged/neoforge/registries/DeferredItem.get()Ljava/lang/Object;",
            "net/minecraft/world/item/ItemStack.<init>(Lnet/minecraft/world/level/ItemLike;)V",
            CONTAINS,
            AWARD
    );

    static final String CURIOS_INVENTORY_MIXIN = "top.theillusivec4.curios.mixin.core.MixinInventory";
    static final String CURIOS_HOOKS = "top.theillusivec4.curios.mixin.CuriosUtilMixinHooks";
    static final String ACCESSORIES_INVENTORY_MIXIN = "io.wispforest.accessories.mixin.InventoryMixin";
    static final String ITEM_STACK = "net.minecraft.world.item.ItemStack";
    static final String SAME_COMPONENTS_DESC = "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z";
    static final String CIR_ITEM_STACK_DESC =
            "(Lnet/minecraft/world/item/ItemStack;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V";

    private static final Set<String> LOGGED_SKIPS = new HashSet<>();
    private static String environmentProblem;
    private static boolean environmentChecked;

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
        if (simpleMixinName.equals("InventoryCompartmentsAccessor")) {
            return true;
        }
        if (!ArcadiaMixinTargetInspector.hasClass(targetClassName)) {
            if (modClassesPresent(targetClassName) && LOGGED_SKIPS.add(targetClassName)) {
                ArcadiaTweaks.LOGGER.warn("[cosmetic] Skipping presence gate for {}: class missing in this mod version.", targetClassName);
            }
            return false;
        }
        final String problem = environmentProblem();
        if (problem != null) {
            if (LOGGED_SKIPS.add("environment")) {
                ArcadiaTweaks.LOGGER.warn("[cosmetic] Presence gate skipped for all procedures: {}", problem);
            }
            return false;
        }
        if (!procedureCompatible(targetClassName)) {
            if (LOGGED_SKIPS.add(targetClassName)) {
                ArcadiaTweaks.LOGGER.warn("[cosmetic] Skipping presence gate for {}: procedure structure changed.", targetClassName);
            }
            return false;
        }
        return true;
    }

    /** The per-class proof of design section 3.A.3. */
    public static boolean procedureCompatible(String className) {
        return ArcadiaMixinTargetInspector.hasMethod(className, EXECUTE, EXECUTE_DESC)
                && ArcadiaMixinTargetInspector.hasMethod(className, ON_PLAYER_TICK, ON_PLAYER_TICK_DESC)
                && ArcadiaMixinTargetInspector.countMethodCalls(className, EXECUTE, EXECUTE_DESC,
                "net.minecraft.world.entity.player.Inventory", "contains", "(Lnet/minecraft/world/item/ItemStack;)Z") >= 2
                && ArcadiaMixinTargetInspector.hasMethodCall(className, EXECUTE, EXECUTE_DESC,
                "net.minecraft.advancements.AdvancementProgress", "isDone", "()Z")
                && ArcadiaMixinTargetInspector.hasMethodCall(className, EXECUTE, EXECUTE_DESC,
                "net.minecraft.server.PlayerAdvancements", "award", "(Lnet/minecraft/advancements/AdvancementHolder;Ljava/lang/String;)Z")
                && ArcadiaMixinTargetInspector.onlyCalls(className, EXECUTE, EXECUTE_DESC, ALLOWED_CALLS)
                && ArcadiaMixinTargetInspector.callsStrictlyBefore(className, EXECUTE, EXECUTE_DESC, CONTAINS, AWARD);
    }

    /** Null when the Curios and Accessories hooks have the expected shape (or are absent). Cached. */
    public static synchronized String environmentProblem() {
        if (!environmentChecked) {
            environmentProblem = computeEnvironmentProblem();
            environmentChecked = true;
        }
        return environmentProblem;
    }

    static String computeEnvironmentProblem() {
        if (ArcadiaMixinTargetInspector.hasClass(CURIOS_INVENTORY_MIXIN) && !curiosHookCompatible()) {
            return "Curios' Inventory.contains hook changed";
        }
        if (ArcadiaMixinTargetInspector.hasClass(ACCESSORIES_INVENTORY_MIXIN) && !accessoriesHookNeverMatches(ACCESSORIES_INVENTORY_MIXIN)) {
            return "Accessories' Inventory.contains hook changed (its predicate may now match equipped accessories)";
        }
        return null;
    }

    static boolean curiosHookCompatible() {
        return ArcadiaMixinTargetInspector.hasMethodCall(CURIOS_INVENTORY_MIXIN, "curios$containsStack", CIR_ITEM_STACK_DESC,
                CURIOS_HOOKS, "containsStack", "(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)Z")
                && ArcadiaMixinTargetInspector.hasMethodCall(CURIOS_HOOKS, "containsStack",
                "(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)Z",
                "top.theillusivec4.curios.api.CuriosApi", "getCuriosInventory",
                "(Lnet/minecraft/world/entity/LivingEntity;)Ljava/util/Optional;")
                && ArcadiaMixinTargetInspector.anyMethodCalls(CURIOS_HOOKS,
                "top.theillusivec4.curios.api.type.capability.ICuriosItemHandler", "findFirstCurio",
                "(Ljava/util/function/Predicate;)Ljava/util/Optional;")
                && ArcadiaMixinTargetInspector.anyMethodCalls(CURIOS_HOOKS, ITEM_STACK, "isSameItemSameComponents", SAME_COMPONENTS_DESC);
    }

    /**
     * Accessories 1.1.0-beta.54 (and the beta.48 Aether bundles) builds its {@code contains(ItemStack)}
     * predicate as {@code e.isEmpty() && isSameItemSameComponents(e, target)}: never true for a non-empty
     * target. Pinned on the bytecode of every static {@code (ItemStack, ItemStack)Z} lambda of the Mixin that
     * compares components.
     */
    public static boolean accessoriesHookNeverMatches(String mixinClassName) {
        if (!ArcadiaMixinTargetInspector.hasMethodCall(mixinClassName, "extendContainsCheck", CIR_ITEM_STACK_DESC,
                "io.wispforest.accessories.api.AccessoriesCapability", "isEquipped", "(Ljava/util/function/Predicate;)Z")) {
            return false;
        }
        final List<MethodNode> predicates = ArcadiaMixinTargetInspector.methodsCalling(mixinClassName, SAME_COMPONENTS_DESC,
                ITEM_STACK, "isSameItemSameComponents", SAME_COMPONENTS_DESC);
        if (predicates.isEmpty()) {
            return false;
        }
        for (MethodNode predicate : predicates) {
            if (!ArcadiaMixinTargetInspector.methodStartsWith(predicate,
                    "ALOAD 1",
                    "INVOKEVIRTUAL net/minecraft/world/item/ItemStack.isEmpty()Z",
                    "IFEQ")) {
                return false;
            }
        }
        return true;
    }

    private static boolean modClassesPresent(String targetClassName) {
        if (targetClassName.startsWith("cosmeticweaponsmod.")) {
            return ArcadiaMixinTargetInspector.hasClass("cosmeticweaponsmod.init.CosmeticweaponsmodModItems");
        }
        if (targetClassName.startsWith("cosmeticarmoursmod.")) {
            return ArcadiaMixinTargetInspector.hasClass("cosmeticarmoursmod.init.CosmeticarmoursmodModItems");
        }
        return false;
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
