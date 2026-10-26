package com.teamarcadia.arcadiatweaks.neoforge.cosmetic;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Startup audit on synthetic Mixin class nodes.
 *
 * @author vyrriox
 */
final class CosmeticMixinAuditTest {

    private static final String INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;";
    private static final String WRAP_OPERATION = "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;";
    private static final String OVERWRITE = "Lorg/spongepowered/asm/mixin/Overwrite;";

    @Test
    void reviewedInventoryMixinsPass() {
        final Map<String, ClassNode> applied = new LinkedHashMap<>();
        applied.put("io.wispforest.accessories.mixin.InventoryMixin",
                mixin(injector(INJECT, "extendContainsCheck", "contains(Lnet/minecraft/world/item/ItemStack;)Z")));
        applied.put("top.theillusivec4.curios.mixin.core.MixinInventory",
                mixin(injector(INJECT, "curios$containsStack", "contains(Lnet/minecraft/world/item/ItemStack;)Z")));
        applied.put("com.teamarcadia.arcadiatweaks.neoforge.mixin.cosmetic.InventoryCompartmentsAccessor", mixin());
        applied.put("some.other.mod.InventoryMixin", mixin(injector(INJECT, "onTick", "tick()V")));

        assertTrue(CosmeticMixinAudit.findUnexpected(applied, CosmeticMixinAudit.INVENTORY_WATCHED,
                CosmeticMixinAudit.INVENTORY_ALLOWED).isEmpty());
    }

    @Test
    void unknownInjectorOnContainsFails() {
        final Map<String, ClassNode> applied = new LinkedHashMap<>();
        applied.put("some.other.mod.InventoryMixin",
                mixin(injector(INJECT, "hook", "contains(Lnet/minecraft/world/item/ItemStack;)Z")));
        final List<String> unexpected = CosmeticMixinAudit.findUnexpected(applied, CosmeticMixinAudit.INVENTORY_WATCHED,
                CosmeticMixinAudit.INVENTORY_ALLOWED);
        assertEquals(1, unexpected.size());
        assertTrue(unexpected.get(0).startsWith("some.other.mod.InventoryMixin"), unexpected.get(0));
    }

    @Test
    void selectorFormsAreRecognised() {
        final Map<String, String> watched = CosmeticMixinAudit.INVENTORY_WATCHED;
        assertTrue(CosmeticMixinAudit.matchesWatched("contains", watched), "bare name covers every overload");
        assertTrue(CosmeticMixinAudit.matchesWatched("contains*", watched));
        assertTrue(CosmeticMixinAudit.matchesWatched("cont*", watched));
        assertTrue(CosmeticMixinAudit.matchesWatched("Lnet/minecraft/world/entity/player/Inventory;contains(Lnet/minecraft/world/item/ItemStack;)Z", watched));
        assertTrue(CosmeticMixinAudit.matchesWatched("desc:something", watched), "unparsed forms count as a match");
        assertFalse(CosmeticMixinAudit.matchesWatched("contains(Lnet/minecraft/tags/TagKey;)Z", watched),
                "another overload is not watched");
        assertFalse(CosmeticMixinAudit.matchesWatched("getContainerSize()I", watched));
        assertFalse(CosmeticMixinAudit.matchesWatched("containsAny(Ljava/util/Set;)Z", watched));
    }

    @Test
    void itemStackComparisonHooksAreChecked() {
        final Map<String, ClassNode> applied = new LinkedHashMap<>();
        applied.put("com.frikinjay.almanac.mixin.ItemStackMixin",
                mixin(injector(INJECT, "fix", "isSameItemSameComponents")));
        applied.put("com.recipeessentials.mixin.nbt.ItemStackMixin",
                mixin(injector(INJECT, "eq", "isSameItemSameComponents")));
        applied.put("software.bernie.geckolib.mixin.common.ItemStackMixin",
                mixin(injector(WRAP_OPERATION, "skip", "isSameItemSameComponents")));
        applied.put("some.mod.TooltipMixin", mixin(injector(INJECT, "tooltip", "getTooltipLines")));
        assertTrue(CosmeticMixinAudit.findUnexpected(applied, CosmeticMixinAudit.ITEM_STACK_WATCHED,
                CosmeticMixinAudit.ITEM_STACK_ALLOWED).isEmpty());

        applied.put("some.mod.ItemMixin", mixin(injector(WRAP_OPERATION, "swap", "getItem()Lnet/minecraft/world/item/Item;")));
        applied.put("some.mod.IsMixin", mixin(injector(INJECT, "is", "is(Lnet/minecraft/world/item/Item;)Z")));
        assertEquals(2, CosmeticMixinAudit.findUnexpected(applied, CosmeticMixinAudit.ITEM_STACK_WATCHED,
                CosmeticMixinAudit.ITEM_STACK_ALLOWED).size());
    }

    @Test
    void isEmptyIsNotMistakenForIs() {
        assertTrue(CosmeticMixinAudit.matchesWatched("isEmpty()Z", CosmeticMixinAudit.ITEM_STACK_WATCHED));
        assertFalse(CosmeticMixinAudit.matchesWatched("isDamageableItem()Z", CosmeticMixinAudit.ITEM_STACK_WATCHED));
    }

    @Test
    void overwriteOfAWatchedMethodFails() {
        final MethodNode overwrite = new MethodNode(Opcodes.ACC_PUBLIC, "contains", "(Lnet/minecraft/world/item/ItemStack;)Z", null, null);
        overwrite.visibleAnnotations = new ArrayList<>(List.of(new AnnotationNode(OVERWRITE)));
        final Map<String, ClassNode> applied = Map.of("some.mod.OverwriteMixin", mixin(overwrite));
        assertEquals(1, CosmeticMixinAudit.findUnexpected(applied, CosmeticMixinAudit.INVENTORY_WATCHED,
                CosmeticMixinAudit.INVENTORY_ALLOWED).size());
    }

    @Test
    void onlyOwnMixinsMayTouchTheProcedures() {
        assertTrue(CosmeticMixinAudit.findForeign(List.of(
                "com.teamarcadia.arcadiatweaks.neoforge.mixin.cosmetic.CosmeticWeaponsProcedureMixin")).isEmpty());
        assertEquals(List.of("other.mod.ProcedureMixin"), CosmeticMixinAudit.findForeign(List.of(
                "com.teamarcadia.arcadiatweaks.neoforge.mixin.cosmetic.CosmeticWeaponsProcedureMixin",
                "other/mod/ProcedureMixin")));
    }

    private static MethodNode injector(String annotationDesc, String handlerName, String selector) {
        final MethodNode method = new MethodNode(Opcodes.ACC_PRIVATE, handlerName, "()V", null, null);
        final AnnotationNode annotation = new AnnotationNode(annotationDesc);
        annotation.values = new ArrayList<>(List.of("method", new ArrayList<>(List.of(selector))));
        method.invisibleAnnotations = new ArrayList<>(List.of(annotation));
        return method;
    }

    private static ClassNode mixin(MethodNode... methods) {
        final ClassNode node = new ClassNode();
        node.methods = new ArrayList<>(List.of(methods));
        return node;
    }
}
