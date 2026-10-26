package com.teamarcadia.arcadiatweaks.neoforge.accessories;

import com.teamarcadia.arcadiatweaks.neoforge.mixin.ArcadiaMixinTargetInspector;
import com.teamarcadia.arcadiatweaks.neoforge.mixin.accessories.AccessoriesMixinPlugin;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pinned fingerprints match the Accessories jar in libs/, and a one-instruction change of the
 * pinned code changes its fingerprint.
 *
 * @author vyrriox
 */
final class AccessoriesFingerprintTest {

    @Test
    void pinnedFingerprintsMatchTheBundledAccessoriesBuild() {
        final List<String> report = new ArrayList<>();
        for (List<AccessoriesFingerprints.Pin> pins : List.of(AccessoriesFingerprints.CONTAINS, AccessoriesFingerprints.TICK)) {
            for (AccessoriesFingerprints.Pin pin : pins) {
                final String actual = ArcadiaMixinTargetInspector.methodFingerprint(pin.className(), pin.method(), pin.descriptor());
                report.add(pin.describe() + " = " + actual);
            }
        }
        System.out.println(String.join("\n", report));
        assertEquals(List.of(), AccessoriesFingerprints.mismatches(AccessoriesFingerprints.CONTAINS));
        assertEquals(List.of(), AccessoriesFingerprints.mismatches(AccessoriesFingerprints.TICK));
    }

    @Test
    void pluginStructuralChecksPassOnTheBundledBuild() {
        assertEquals(List.of(), AccessoriesMixinPlugin.containsMismatches());
        assertEquals(List.of(), AccessoriesMixinPlugin.tickMismatches());
    }

    @Test
    void anUpstreamPredicateFixChangesTheFingerprint() throws IOException {
        final MethodNode lambda = method("io.wispforest.accessories.mixin.InventoryMixin",
                "lambda$extendContainsCheck$1", "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z");
        final String before = ArcadiaMixinTargetInspector.fingerprint(lambda);
        final JumpInsnNode firstTest = firstJump(lambda);
        assertEquals(Opcodes.IFEQ, firstTest.getOpcode());
        // stack1.isEmpty() becomes !stack1.isEmpty(): the predicate the upstream author meant.
        lambda.instructions.set(firstTest, new JumpInsnNode(Opcodes.IFNE, firstTest.label));
        assertNotEquals(before, ArcadiaMixinTargetInspector.fingerprint(lambda));
        assertTrue(AccessoriesContainsSurgery.isDeadPredicate(method("io.wispforest.accessories.mixin.InventoryMixin",
                "lambda$extendContainsCheck$1", "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z")));
        assertTrue(!AccessoriesContainsSurgery.isDeadPredicate(lambda), "A fixed predicate must not pass the dead-predicate check");
    }

    @Test
    void oneExtraCallInTheTickChangesTheFingerprint() throws IOException {
        final MethodNode tick = method("io.wispforest.accessories.impl.AccessoriesEventHandler",
                "onLivingEntityTick", "(Lnet/minecraft/world/entity/LivingEntity;)V");
        final String before = ArcadiaMixinTargetInspector.fingerprint(tick);
        final AbstractInsnNode first = tick.instructions.getFirst();
        tick.instructions.insertBefore(first, new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Thread", "onSpinWait", "()V", false));
        assertNotEquals(before, ArcadiaMixinTargetInspector.fingerprint(tick));
        tick.instructions.remove(tick.instructions.getFirst());
        assertEquals(before, ArcadiaMixinTargetInspector.fingerprint(tick));
        tick.instructions.insertBefore(tick.instructions.getFirst(), new InsnNode(Opcodes.NOP));
        assertNotEquals(before, ArcadiaMixinTargetInspector.fingerprint(tick));
    }

    @Test
    void missingPinsAreReported() {
        final List<String> mismatches = AccessoriesFingerprints.mismatches(List.of(
                new AccessoriesFingerprints.Pin("missing.accessories.Thing", "tick", "()V", "00")));
        assertEquals(1, mismatches.size());
        assertTrue(mismatches.get(0).endsWith("(missing)"));
    }

    static MethodNode method(String className, String name, String desc) throws IOException {
        final ClassNode node = classNode(className);
        for (MethodNode method : node.methods) {
            if (method.name.equals(name) && method.desc.equals(desc)) {
                return method;
            }
        }
        throw new AssertionError("Missing " + className + "." + name + desc);
    }

    static ClassNode classNode(String className) throws IOException {
        try (InputStream in = AccessoriesFingerprintTest.class.getClassLoader()
                .getResourceAsStream(className.replace('.', '/') + ".class")) {
            assertNotNull(in, className + " should be on the test classpath (libs/accessories jar)");
            final ClassNode node = new ClassNode();
            new ClassReader(in).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }

    private static JumpInsnNode firstJump(MethodNode method) {
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof JumpInsnNode jump) {
                return jump;
            }
        }
        throw new AssertionError("No jump in " + method.name);
    }
}
