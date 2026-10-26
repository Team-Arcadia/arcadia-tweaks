package com.teamarcadia.arcadiatweaks.neoforge.cosmetic;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.ClassInfo;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Server-start audit of the Mixins applied to the classes the presence gate reasons about.
 *
 * The gate is exact only while every contributor to {@code Inventory.contains(ItemStack)} needs an item that
 * the snapshot lists (vanilla, Curios, and Accessories which never matches a non-empty target). Plugin checks
 * cannot see other Mixin configs, so at server start this audit reads every Mixin applied to
 * {@code Inventory}, {@code ItemStack} and the gated procedure classes, and rejects any injector (or
 * {@code @Overwrite}) on a watched method that does not come from a reviewed Mixin. Anything unexpected turns
 * the gate off.
 *
 * @author vyrriox
 */
public final class CosmeticMixinAudit {

    public static final String INVENTORY = "net.minecraft.world.entity.player.Inventory";
    public static final String ITEM_STACK = "net.minecraft.world.item.ItemStack";

    /** Methods of Inventory whose behaviour the gate depends on, with the descriptor prefix (null = any overload). */
    static final Map<String, String> INVENTORY_WATCHED = Map.of("contains", "(Lnet/minecraft/world/item/ItemStack;)");
    /** Methods of ItemStack that decide "same item" for vanilla and Curios, and that the snapshot itself calls. */
    static final Map<String, String> ITEM_STACK_WATCHED = new LinkedHashMap<>();

    static {
        ITEM_STACK_WATCHED.put("isSameItemSameComponents", null);
        ITEM_STACK_WATCHED.put("is", null);
        ITEM_STACK_WATCHED.put("getItem", null);
        ITEM_STACK_WATCHED.put("isEmpty", null);
    }

    /**
     * Reviewed Mixins on {@code Inventory.contains(ItemStack)}: Accessories (its predicate can never match a
     * non-empty target, pinned by the plugin) and Curios (needs the same item, its slots are in the snapshot).
     * ArcadiaTweaks' own Mixins are reviewed together with this gate: they keep the answer of
     * {@code contains} unchanged (the Accessories handler optimization only skips pure work).
     */
    static final Set<String> INVENTORY_ALLOWED = Set.of(
            "io.wispforest.accessories.mixin.InventoryMixin",
            "top.theillusivec4.curios.mixin.core.MixinInventory"
    );
    /** Reviewed Mixins on the ItemStack comparison: none of them changes the "same item" test (design 2.1). */
    static final Set<String> ITEM_STACK_ALLOWED = Set.of(
            "com.frikinjay.almanac.mixin.ItemStackMixin",
            "com.recipeessentials.mixin.nbt.ItemStackMixin",
            "software.bernie.geckolib.mixin.common.ItemStackMixin"
    );
    static final String OWN_MIXIN_PREFIX = "com.teamarcadia.arcadiatweaks.";

    private CosmeticMixinAudit() {}

    /**
     * Mixins of {@code applied} (class name to class node) that hook one of the {@code watched} methods and
     * are neither in {@code allowed} nor ArcadiaTweaks' own. Returned as {@code "mixin -> selector"} lines.
     */
    public static List<String> findUnexpected(Map<String, ClassNode> applied, Map<String, String> watched, Set<String> allowed) {
        final List<String> unexpected = new ArrayList<>();
        for (Map.Entry<String, ClassNode> entry : applied.entrySet()) {
            final String mixinName = entry.getKey().replace('/', '.');
            if (allowed.contains(mixinName) || mixinName.startsWith(OWN_MIXIN_PREFIX)) {
                continue;
            }
            for (String selector : hookedSelectors(entry.getValue())) {
                if (matchesWatched(selector, watched)) {
                    unexpected.add(mixinName + " -> " + selector);
                }
            }
        }
        return unexpected;
    }

    /** Mixins of {@code applied} other than ArcadiaTweaks' own, for targets that only our Mixin may touch. */
    public static List<String> findForeign(Collection<String> appliedMixinNames) {
        final List<String> foreign = new ArrayList<>();
        for (String name : appliedMixinNames) {
            final String dotted = name.replace('/', '.');
            if (!dotted.startsWith(OWN_MIXIN_PREFIX)) {
                foreign.add(dotted);
            }
        }
        return foreign;
    }

    /**
     * Every target method selector of the Mixin: the {@code method} values of each method annotation
     * (Mixin and MixinExtras injectors), plus the name and descriptor of each {@code @Overwrite} method.
     */
    static List<String> hookedSelectors(ClassNode mixin) {
        final List<String> selectors = new ArrayList<>();
        if (mixin.methods == null) {
            return selectors;
        }
        for (MethodNode method : mixin.methods) {
            collect(method.visibleAnnotations, method, selectors);
            collect(method.invisibleAnnotations, method, selectors);
        }
        return selectors;
    }

    private static void collect(List<AnnotationNode> annotations, MethodNode method, List<String> selectors) {
        if (annotations == null) {
            return;
        }
        for (AnnotationNode annotation : annotations) {
            if (annotation.desc.equals("Lorg/spongepowered/asm/mixin/Overwrite;")) {
                selectors.add(method.name + method.desc);
                continue;
            }
            if (annotation.values == null) {
                continue;
            }
            for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
                if (!"method".equals(annotation.values.get(i))) {
                    continue;
                }
                final Object value = annotation.values.get(i + 1);
                if (value instanceof List<?> list) {
                    for (Object element : list) {
                        selectors.add(String.valueOf(element));
                    }
                } else if (value != null) {
                    selectors.add(String.valueOf(value));
                }
            }
        }
    }

    /**
     * True when a selector can reach one of the watched methods. Accepts the forms Mixin accepts: {@code name},
     * {@code name*}, {@code name(desc)}, {@code Lowner;name(desc)}, {@code owner.name(desc)}. A selector this
     * parser does not understand (regex, wildcard name) counts as a match, so the audit stays on the safe side.
     */
    static boolean matchesWatched(String selector, Map<String, String> watched) {
        String s = selector.trim();
        if (s.isEmpty()) {
            return false;
        }
        if (s.startsWith("desc:") || s.startsWith("*")) {
            return true;
        }
        final int firstParen = s.indexOf('(');
        final int ownerEnd = s.indexOf(';');
        if (s.startsWith("L") && ownerEnd >= 0 && (firstParen < 0 || ownerEnd < firstParen)) {
            s = s.substring(ownerEnd + 1);
        }
        final int paren = s.indexOf('(');
        String name = paren < 0 ? s : s.substring(0, paren);
        if (name.contains("/") || name.contains(";")) {
            return true;
        }
        final String descriptor = paren < 0 ? "" : s.substring(paren);
        final int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            name = name.substring(dot + 1);
        }
        boolean wildcard = false;
        if (name.endsWith("*")) {
            name = name.substring(0, name.length() - 1);
            wildcard = true;
        }
        if (name.contains("*") || name.contains("?") || name.contains("[")) {
            return true;
        }
        for (Map.Entry<String, String> entry : watched.entrySet()) {
            final String watchedName = entry.getKey();
            final boolean nameMatches = wildcard ? watchedName.startsWith(name) : watchedName.equals(name);
            if (!nameMatches) {
                continue;
            }
            final String watchedDescriptor = entry.getValue();
            if (watchedDescriptor == null || descriptor.isEmpty() || descriptor.startsWith(watchedDescriptor)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Mixins declared for a class (every config that targets it, whether its plugin applied it or not: a
     * superset of the applied ones), by class name. Returns null when Mixin cannot tell, which the caller
     * treats as an audit failure.
     *
     * {@code Mixins.getMixinsForClass(target)} cannot be used: in Mixin 0.8.7 {@code addAppliedMixin} records
     * the applied set on the Mixin's own {@code ClassInfo}, so the target's set is always empty. The target's
     * {@code ClassInfo} keeps the declared Mixins in its private {@code mixins} field, filled when each config
     * reads its targets.
     */
    public static Map<String, ClassNode> appliedMixins(String className) {
        final Set<IMixinInfo> infos = declaredMixins(className.replace('.', '/'));
        if (infos == null) {
            return null;
        }
        final Map<String, ClassNode> nodes = new LinkedHashMap<>();
        for (IMixinInfo info : infos) {
            ClassNode node;
            try {
                node = info.getClassNode(ClassReader.SKIP_CODE);
            } catch (RuntimeException | LinkageError e) {
                node = null;
            }
            if (node == null) {
                return null;
            }
            nodes.put(info.getClassName(), node);
        }
        return nodes;
    }

    private static Set<IMixinInfo> declaredMixins(String internalName) {
        try {
            final ClassInfo info = ClassInfo.fromCache(internalName);
            if (info == null) {
                return null;
            }
            final Field field = ClassInfo.class.getDeclaredField("mixins");
            field.setAccessible(true);
            final Object value = field.get(info);
            if (!(value instanceof Set<?> set)) {
                return null;
            }
            final Set<IMixinInfo> result = new LinkedHashSet<>();
            for (Object element : set) {
                if (!(element instanceof IMixinInfo mixin)) {
                    return null;
                }
                result.add(mixin);
            }
            return result;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return null;
        }
    }
}
