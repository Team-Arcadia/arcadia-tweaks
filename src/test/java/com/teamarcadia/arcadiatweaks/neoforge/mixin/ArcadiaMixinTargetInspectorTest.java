package com.teamarcadia.arcadiatweaks.neoforge.mixin;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ArcadiaMixinTargetInspectorTest {

    @Test
    void detectsClassesFieldsAndMethods() {
        final String className = MethodCallFixture.class.getName();

        assertTrue(ArcadiaMixinTargetInspector.hasClass(className));
        assertTrue(ArcadiaMixinTargetInspector.hasField(className, "marker", "Ljava/lang/String;"));
        assertTrue(ArcadiaMixinTargetInspector.hasMethod(className, "caller", "()V"));
        assertFalse(ArcadiaMixinTargetInspector.hasMethod(className, "missing", "()V"));
    }

    @Test
    void walksSuperclassMethods() {
        assertTrue(ArcadiaMixinTargetInspector.hasMethodInHierarchy(ChildFixture.class.getName(), "inherited", "()Z"));
        assertFalse(ArcadiaMixinTargetInspector.hasMethodInHierarchy(ChildFixture.class.getName(), "missing", "()Z"));
    }

    @Test
    void detectsMethodCallsInsideMethodBody() {
        final String className = MethodCallFixture.class.getName();

        assertTrue(ArcadiaMixinTargetInspector.hasMethodCall(
                className,
                "caller",
                "()V",
                className,
                "callee",
                "()V"
        ));
        assertFalse(ArcadiaMixinTargetInspector.hasMethodCall(
                className,
                "caller",
                "()V",
                className,
                "missing",
                "()V"
        ));
    }

    private static final String CHAIN = ChainFixture.class.getName();
    private static final String LIST_CONTAINS = "java/util/List.contains(Ljava/lang/Object;)Z";
    private static final String CALL_A = ChainFixture.class.getName().replace('.', '/') + ".a()V";
    private static final String CALL_B = ChainFixture.class.getName().replace('.', '/') + ".b()V";

    @Test
    void countsCallsInOneMethod() {
        assertEquals(2, ArcadiaMixinTargetInspector.countMethodCalls(CHAIN, "chain", "(Ljava/util/List;)Z",
                "java.util.List", "contains", "(Ljava/lang/Object;)Z"));
        assertEquals(0, ArcadiaMixinTargetInspector.countMethodCalls(CHAIN, "ordered", "()V",
                "java.util.List", "contains", "(Ljava/lang/Object;)Z"));
        assertEquals(-1, ArcadiaMixinTargetInspector.countMethodCalls(CHAIN, "missing", "()V",
                "java.util.List", "contains", "(Ljava/lang/Object;)Z"));
    }

    @Test
    void onlyCallsAcceptsAllowListedCallsOnly() {
        assertTrue(ArcadiaMixinTargetInspector.onlyCalls(CHAIN, "chain", "(Ljava/util/List;)Z", Set.of(LIST_CONTAINS)));
        assertFalse(ArcadiaMixinTargetInspector.onlyCalls(CHAIN, "chain", "(Ljava/util/List;)Z", Set.of()),
                "a call outside the allow-list must fail");
        assertFalse(ArcadiaMixinTargetInspector.onlyCalls(CHAIN, "writer", "()V", Set.of()),
                "a field write must fail");
        assertFalse(ArcadiaMixinTargetInspector.onlyCalls(CHAIN, "lambda", "()Ljava/lang/Runnable;", Set.of()),
                "invokedynamic must fail");
        assertFalse(ArcadiaMixinTargetInspector.onlyCalls(CHAIN, "missing", "()V", Set.of()));
    }

    @Test
    void onlyCallsOnSyntheticMethodWithExtraCall() {
        final MethodNode method = new MethodNode(Opcodes.ACC_STATIC, "m", "()V", null, null);
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "a/B", "allowed", "()V"));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        assertTrue(ArcadiaMixinTargetInspector.onlyCalls(method, Set.of("a/B.allowed()V")));
        method.instructions.insert(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "a/B", "extra", "()V"));
        assertFalse(ArcadiaMixinTargetInspector.onlyCalls(method, Set.of("a/B.allowed()V")));
    }

    @Test
    void callOrderIsChecked() {
        assertTrue(ArcadiaMixinTargetInspector.callsStrictlyBefore(CHAIN, "ordered", "()V", CALL_A, CALL_B));
        assertFalse(ArcadiaMixinTargetInspector.callsStrictlyBefore(CHAIN, "ordered", "()V", CALL_B, CALL_A));
        assertFalse(ArcadiaMixinTargetInspector.callsStrictlyBefore(CHAIN, "interleaved", "()V", CALL_A, CALL_B),
                "an earlier-call after a later-call must fail");
        assertFalse(ArcadiaMixinTargetInspector.callsStrictlyBefore(CHAIN, "chain", "(Ljava/util/List;)Z", CALL_A, CALL_B),
                "missing calls must fail");
    }

    @Test
    void findsMethodsByWhatTheyCall() {
        assertTrue(ArcadiaMixinTargetInspector.anyMethodCalls(CHAIN, "java.lang.String", "isEmpty", "()Z"));
        assertFalse(ArcadiaMixinTargetInspector.anyMethodCalls(CHAIN, "java.lang.String", "missing", "()Z"));
        final List<MethodNode> comparing = ArcadiaMixinTargetInspector.methodsCalling(CHAIN,
                "(Ljava/lang/String;Ljava/lang/String;)Z", "java.lang.String", "equals", "(Ljava/lang/Object;)Z");
        assertEquals(2, comparing.size());
    }

    @Test
    void methodStartsWithComparesLeadingInstructions() {
        final String[] emptyFirst = {
                "ALOAD 1",
                "INVOKEVIRTUAL java/lang/String.isEmpty()Z",
                "IFEQ"
        };
        assertTrue(ArcadiaMixinTargetInspector.methodStartsWith(CHAIN, "emptyFirst",
                "(Ljava/lang/String;Ljava/lang/String;)Z", emptyFirst));
        assertFalse(ArcadiaMixinTargetInspector.methodStartsWith(CHAIN, "notEmptyFirst",
                "(Ljava/lang/String;Ljava/lang/String;)Z", emptyFirst), "IFNE instead of IFEQ must fail");
        assertFalse(ArcadiaMixinTargetInspector.methodStartsWith(CHAIN, "missing", "()Z", emptyFirst));

        final MethodNode tooShort = new MethodNode(Opcodes.ACC_STATIC, "m", "()V", null, null);
        tooShort.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        assertFalse(ArcadiaMixinTargetInspector.methodStartsWith(tooShort, emptyFirst));
    }

    @SuppressWarnings("unused")
    private static final class ChainFixture {
        private int field;

        static boolean chain(List<String> list) {
            return list.contains("a") || list.contains("b");
        }

        void writer() {
            field = 1;
        }

        static Runnable lambda() {
            return () -> { };
        }

        static void ordered() {
            a();
            a();
            b();
        }

        static void interleaved() {
            a();
            b();
            a();
        }

        static void a() {
        }

        static void b() {
        }

        static boolean emptyFirst(String a, String b) {
            return b.isEmpty() && a.equals(b);
        }

        static boolean notEmptyFirst(String a, String b) {
            return !b.isEmpty() && a.equals(b);
        }
    }

    private static class ParentFixture {
        public boolean inherited() {
            return true;
        }
    }

    private static final class ChildFixture extends ParentFixture {
    }

    private static final class MethodCallFixture {
        private String marker;

        public void caller() {
            callee();
        }

        private void callee() {
            marker = "called";
        }
    }
}
