package com.teamarcadia.arcadiatweaks.neoforge.mixin;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ArcadiaMixinTargetInspector {

    private ArcadiaMixinTargetInspector() {}

    public static boolean hasClass(String className) {
        return readClassNode(className) != null;
    }

    public static boolean hasField(String className, String fieldName, String descriptor) {
        final ClassNode node = readClassNode(className);
        if (node == null) {
            return false;
        }
        for (FieldNode field : node.fields) {
            if (field.name.equals(fieldName) && field.desc.equals(descriptor)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasMethod(String className, String methodName, String descriptor) {
        final ClassNode node = readClassNode(className);
        return node != null && hasMethod(node, methodName, descriptor);
    }

    public static boolean hasMethodInHierarchy(String className, String methodName, String descriptor) {
        ClassNode node = readClassNode(className);
        while (node != null) {
            if (hasMethod(node, methodName, descriptor)) {
                return true;
            }
            node = node.superName == null ? null : readClassNode(node.superName.replace('/', '.'));
        }
        return false;
    }

    public static boolean hasMethodCall(
            String className,
            String methodName,
            String methodDescriptor,
            String targetOwner,
            String targetName,
            String targetDescriptor
    ) {
        final ClassNode node = readClassNodeWithCode(className);
        if (node == null) {
            return false;
        }
        final String targetOwnerInternal = targetOwner.replace('.', '/');
        for (MethodNode method : node.methods) {
            if (!method.name.equals(methodName) || !method.desc.equals(methodDescriptor)) {
                continue;
            }
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                    instruction != null;
                    instruction = instruction.getNext()) {
                if (instruction instanceof MethodInsnNode methodCall
                        && methodCall.owner.equals(targetOwnerInternal)
                        && methodCall.name.equals(targetName)
                        && methodCall.desc.equals(targetDescriptor)) {
                    return true;
                }
            }
            return false;
        }
        return false;
    }

    /**
     * True when the method body pushes the given int constant (iconst, bipush, sipush or ldc).
     */
    public static boolean hasIntConstantInMethod(String className, String methodName, String descriptor, int value) {
        final MethodNode method = findMethodWithCode(className, methodName, descriptor);
        if (method == null) {
            return false;
        }
        for (AbstractInsnNode instruction = method.instructions.getFirst();
                instruction != null;
                instruction = instruction.getNext()) {
            final int opcode = instruction.getOpcode();
            if (opcode >= Opcodes.ICONST_M1 && opcode <= Opcodes.ICONST_5 && opcode - Opcodes.ICONST_0 == value) {
                return true;
            }
            if ((opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH)
                    && instruction instanceof IntInsnNode intInsn && intInsn.operand == value) {
                return true;
            }
            if (instruction instanceof LdcInsnNode ldc && ldc.cst instanceof Integer constant && constant == value) {
                return true;
            }
        }
        return false;
    }

    /**
     * True when the method body contains at least one instruction with the given opcode.
     */
    public static boolean hasOpcodeInMethod(String className, String methodName, String descriptor, int opcode) {
        final MethodNode method = findMethodWithCode(className, methodName, descriptor);
        if (method == null) {
            return false;
        }
        for (AbstractInsnNode instruction = method.instructions.getFirst();
                instruction != null;
                instruction = instruction.getNext()) {
            if (instruction.getOpcode() == opcode) {
                return true;
            }
        }
        return false;
    }

    /**
     * Every method call of the method body in order, as {@code owner.name descriptor} with a
     * slash-separated owner. Null when the class or the method is absent.
     */
    public static List<String> methodCallsInMethod(String className, String methodName, String descriptor) {
        final MethodNode method = findMethodWithCode(className, methodName, descriptor);
        if (method == null) {
            return null;
        }
        final List<String> calls = new ArrayList<>();
        for (AbstractInsnNode instruction = method.instructions.getFirst();
                instruction != null;
                instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode methodCall) {
                calls.add(methodCall.owner + "." + methodCall.name + " " + methodCall.desc);
            }
        }
        return calls;
    }

    /**
     * Every method declared by the class (not inherited), as {@code name descriptor}. Null when
     * the class is absent.
     */
    public static Set<String> declaredMethods(String className) {
        final ClassNode node = readClassNode(className);
        if (node == null) {
            return null;
        }
        final Set<String> methods = new LinkedHashSet<>();
        for (MethodNode method : node.methods) {
            methods.add(method.name + " " + method.desc);
        }
        return methods;
    }

    private static MethodNode findMethodWithCode(String className, String methodName, String descriptor) {
        final ClassNode node = readClassNodeWithCode(className);
        if (node == null) {
            return null;
        }
        for (MethodNode method : node.methods) {
            if (method.name.equals(methodName) && method.desc.equals(descriptor)) {
                return method;
            }
        }
        return null;
    }

    private static boolean hasMethod(ClassNode node, String methodName, String descriptor) {
        for (MethodNode method : node.methods) {
            if (method.name.equals(methodName) && method.desc.equals(descriptor)) {
                return true;
            }
        }
        return false;
    }

    private static ClassNode readClassNode(String className) {
        return readClassNode(className, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }

    private static ClassNode readClassNodeWithCode(String className) {
        return readClassNode(className, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }

    private static ClassNode readClassNode(String className, int parsingOptions) {
        final String resourceName = className.replace('.', '/') + ".class";
        final ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        ClassNode node = readClassNode(contextLoader, resourceName, parsingOptions);
        if (node != null) {
            return node;
        }
        return readClassNode(ArcadiaMixinTargetInspector.class.getClassLoader(), resourceName, parsingOptions);
    }

    private static ClassNode readClassNode(ClassLoader loader, String resourceName, int parsingOptions) {
        if (loader == null) {
            return null;
        }
        try (InputStream input = loader.getResourceAsStream(resourceName)) {
            if (input == null) {
                return null;
            }
            final ClassReader reader = new ClassReader(input);
            final ClassNode node = new ClassNode();
            reader.accept(node, parsingOptions);
            return node;
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }
}
