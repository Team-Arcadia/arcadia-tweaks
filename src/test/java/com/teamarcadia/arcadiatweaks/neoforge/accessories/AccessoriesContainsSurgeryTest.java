package com.teamarcadia.arcadiatweaks.neoforge.accessories;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicVerifier;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AccessoriesContainsSurgery} on the real beta.54 {@code InventoryMixin} bytecode, remapped the
 * way Mixin merges it into {@code Inventory} (owner {@code Inventory}, handler names with a session id).
 *
 * @author vyrriox
 */
final class AccessoriesContainsSurgeryTest {

    private static final String MIXIN = "io/wispforest/accessories/mixin/InventoryMixin";
    private static final String ITEM_HANDLER = "handler$zkf000$accessories$extendContainsCheck";
    private static final String TAG_HANDLER = "handler$zkf001$accessories$extendContainsCheck";
    private static final String PREDICATE_HANDLER = "handler$zkf002$accessories$extendContainsCheck";
    private static final String TAG_DESC =
            "(Lnet/minecraft/tags/TagKey;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V";
    private static final String PREDICATE_DESC =
            "(Ljava/util/function/Predicate;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V";

    @Test
    void prependsTheHookOnceOnTheVerifiedShape() throws Exception {
        final ClassNode merged = mergedInventory();
        final int before = handler(merged, ITEM_HANDLER, AccessoriesContainsSurgery.HANDLER_DESC).instructions.size();

        assertEquals(AccessoriesContainsSurgery.Result.APPLIED, AccessoriesContainsSurgery.apply(merged));
        final MethodNode patched = handler(merged, ITEM_HANDLER, AccessoriesContainsSurgery.HANDLER_DESC);
        final List<AbstractInsnNode> code = AccessoriesContainsSurgery.code(patched);
        assertTrue(code.get(3) instanceof MethodInsnNode call
                && call.owner.equals(AccessoriesContainsSurgery.HOOK_OWNER)
                && call.desc.equals(AccessoriesContainsSurgery.HOOK_DESC), "Hook call should come first");
        assertEquals(Opcodes.IFEQ, code.get(4).getOpcode());
        assertEquals(Opcodes.RETURN, code.get(5).getOpcode());
        // The original body is still there, unchanged, after the guard.
        assertEquals(Opcodes.ALOAD, code.get(6).getOpcode());
        assertTrue(AccessoriesContainsSurgery.handlerShape(patched, code.subList(6, code.size())) != null,
                "Original body should follow the guard unchanged");

        assertEquals(AccessoriesContainsSurgery.Result.ALREADY_APPLIED, AccessoriesContainsSurgery.apply(merged));
        // 3 loads, the call, the jump, the return, the label and its frame.
        assertEquals(before + 8, handler(merged, ITEM_HANDLER, AccessoriesContainsSurgery.HANDLER_DESC).instructions.size(),
                "A second pass must not prepend again");

        // The other two contains overloads are not touched.
        assertEquals(fingerprint(mergedInventory(), TAG_HANDLER, TAG_DESC), fingerprint(merged, TAG_HANDLER, TAG_DESC));
        assertEquals(fingerprint(mergedInventory(), PREDICATE_HANDLER, PREDICATE_DESC),
                fingerprint(merged, PREDICATE_HANDLER, PREDICATE_DESC));

        verifies(merged, patched);
        writes(merged);
    }

    @Test
    void noHandlerWithTheItemStackDescriptorChangesNothing() throws Exception {
        final ClassNode merged = mergedInventory();
        merged.methods.removeIf(m -> m.name.equals(ITEM_HANDLER));
        final String tag = fingerprint(merged, TAG_HANDLER, TAG_DESC);
        assertEquals(AccessoriesContainsSurgery.Result.HANDLER_NOT_FOUND, AccessoriesContainsSurgery.apply(merged));
        assertEquals(tag, fingerprint(merged, TAG_HANDLER, TAG_DESC));
    }

    @Test
    void aLivePredicateIsLeftAlone() throws Exception {
        final ClassNode merged = mergedInventory();
        final MethodNode lambda = method(merged, "lambda$extendContainsCheck$1",
                "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z");
        for (AbstractInsnNode insn = lambda.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof JumpInsnNode jump) {
                lambda.instructions.set(jump, new JumpInsnNode(Opcodes.IFNE, jump.label));
                break;
            }
        }
        final String before = fingerprint(merged, ITEM_HANDLER, AccessoriesContainsSurgery.HANDLER_DESC);
        assertEquals(AccessoriesContainsSurgery.Result.PREDICATE_CHANGED, AccessoriesContainsSurgery.apply(merged));
        assertEquals(before, fingerprint(merged, ITEM_HANDLER, AccessoriesContainsSurgery.HANDLER_DESC));
    }

    @Test
    void aChangedHandlerBodyIsLeftAlone() throws Exception {
        final ClassNode merged = mergedInventory();
        final MethodNode handler = handler(merged, ITEM_HANDLER, AccessoriesContainsSurgery.HANDLER_DESC);
        handler.instructions.insert(new InsnNode(Opcodes.NOP));
        final String before = fingerprint(merged, ITEM_HANDLER, AccessoriesContainsSurgery.HANDLER_DESC);
        assertEquals(AccessoriesContainsSurgery.Result.HANDLER_SHAPE_CHANGED, AccessoriesContainsSurgery.apply(merged));
        assertEquals(before, fingerprint(merged, ITEM_HANDLER, AccessoriesContainsSurgery.HANDLER_DESC));
    }

    @Test
    void twoCandidateHandlersAreAmbiguous() throws Exception {
        final ClassNode merged = mergedInventory();
        final MethodNode copy = new MethodNode(Opcodes.ACC_PRIVATE, "handler$abc999$accessories$extendContainsCheck",
                AccessoriesContainsSurgery.HANDLER_DESC, null, null);
        handler(merged, ITEM_HANDLER, AccessoriesContainsSurgery.HANDLER_DESC).accept(copy);
        merged.methods.add(copy);
        assertEquals(AccessoriesContainsSurgery.Result.HANDLER_AMBIGUOUS, AccessoriesContainsSurgery.apply(merged));
    }

    @Test
    void handlerNamePatternMatchesOnlyAccessoriesItemHandler() {
        assertTrue(AccessoriesContainsSurgery.HANDLER_NAME.matcher(ITEM_HANDLER).matches());
        assertTrue(!AccessoriesContainsSurgery.HANDLER_NAME.matcher("handler$zkf000$curios$extendContainsCheck").matches());
        assertTrue(!AccessoriesContainsSurgery.HANDLER_NAME.matcher("extendContainsCheck").matches());
    }

    /** InventoryMixin as Mixin merges it: members owned by Inventory, handlers renamed with a session id. */
    private static ClassNode mergedInventory() throws IOException {
        final Map<String, String> mapping = new HashMap<>();
        mapping.put(MIXIN, AccessoriesContainsSurgery.INVENTORY);
        mapping.put(MIXIN + ".extendContainsCheck" + AccessoriesContainsSurgery.HANDLER_DESC, ITEM_HANDLER);
        mapping.put(MIXIN + ".extendContainsCheck" + TAG_DESC, TAG_HANDLER);
        mapping.put(MIXIN + ".extendContainsCheck" + PREDICATE_DESC, PREDICATE_HANDLER);
        try (InputStream in = AccessoriesContainsSurgeryTest.class.getClassLoader().getResourceAsStream(MIXIN + ".class")) {
            assertNotNull(in, "Accessories jar should be on the test classpath");
            final ClassNode node = new ClassNode();
            new ClassReader(in).accept(new ClassRemapper(node, new SimpleRemapper(mapping)), ClassReader.EXPAND_FRAMES);
            return node;
        }
    }

    private static MethodNode handler(ClassNode node, String name, String desc) {
        return method(node, name, desc);
    }

    private static MethodNode method(ClassNode node, String name, String desc) {
        for (MethodNode method : node.methods) {
            if (method.name.equals(name) && method.desc.equals(desc)) {
                return method;
            }
        }
        throw new AssertionError("Missing " + name + desc);
    }

    private static String fingerprint(ClassNode node, String name, String desc) {
        return com.teamarcadia.arcadiatweaks.neoforge.mixin.ArcadiaMixinTargetInspector.fingerprint(method(node, name, desc));
    }

    private static void verifies(ClassNode owner, MethodNode method) throws AnalyzerException {
        new Analyzer<>(new BasicVerifier()).analyze(owner.name, method);
    }

    private static void writes(ClassNode node) {
        final ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override
            protected String getCommonSuperClass(String type1, String type2) {
                return "java/lang/Object";
            }
        };
        node.accept(writer);
        final ClassNode reread = new ClassNode();
        new ClassReader(writer.toByteArray()).accept(reread, 0);
        assertNotNull(method(reread, ITEM_HANDLER, AccessoriesContainsSurgery.HANDLER_DESC));
    }
}
