package com.teamarcadia.arcadiatweaks.neoforge.mixin;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
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
     * Number of calls to {@code targetOwner.targetName targetDescriptor} in one method body, or -1 when the
     * class or the method is missing.
     */
    public static int countMethodCalls(
            String className,
            String methodName,
            String methodDescriptor,
            String targetOwner,
            String targetName,
            String targetDescriptor
    ) {
        final MethodNode method = findMethodWithCode(className, methodName, methodDescriptor);
        if (method == null) {
            return -1;
        }
        final String key = callKey(targetOwner.replace('.', '/'), targetName, targetDescriptor);
        int count = 0;
        for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode call && callKey(call).equals(key)) {
                count++;
            }
        }
        return count;
    }

    /**
     * True when every method call of the method body is in {@code allowedCalls} and the body has no
     * {@code invokedynamic} and no field write. Calls are written {@code owner.name descriptor} with an
     * internal owner name, e.g. {@code net/minecraft/world/entity/player/Inventory.contains(Lnet/minecraft/world/item/ItemStack;)Z}.
     * Used to prove that nothing but the listed calls can run inside a method.
     */
    public static boolean onlyCalls(String className, String methodName, String methodDescriptor, Set<String> allowedCalls) {
        final MethodNode method = findMethodWithCode(className, methodName, methodDescriptor);
        return method != null && onlyCalls(method, allowedCalls);
    }

    public static boolean onlyCalls(MethodNode method, Set<String> allowedCalls) {
        for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
            if (instruction instanceof InvokeDynamicInsnNode) {
                return false;
            }
            if (instruction instanceof FieldInsnNode field
                    && (field.getOpcode() == Opcodes.PUTFIELD || field.getOpcode() == Opcodes.PUTSTATIC)) {
                return false;
            }
            if (instruction instanceof MethodInsnNode call && !allowedCalls.contains(callKey(call))) {
                return false;
            }
        }
        return true;
    }

    /**
     * True when the method contains both calls and every {@code earlier} call comes before every {@code later}
     * call in instruction order. Calls use the {@code owner.name descriptor} form of {@link #onlyCalls}.
     */
    public static boolean callsStrictlyBefore(String className, String methodName, String methodDescriptor, String earlier, String later) {
        final MethodNode method = findMethodWithCode(className, methodName, methodDescriptor);
        if (method == null) {
            return false;
        }
        int index = 0;
        int lastEarlier = -1;
        int firstLater = -1;
        for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext(), index++) {
            if (instruction instanceof MethodInsnNode call) {
                final String key = callKey(call);
                if (key.equals(earlier)) {
                    lastEarlier = index;
                } else if (key.equals(later) && firstLater < 0) {
                    firstLater = index;
                }
            }
        }
        return lastEarlier >= 0 && firstLater >= 0 && lastEarlier < firstLater;
    }

    /** True when any method of the class calls {@code targetOwner.targetName targetDescriptor}. */
    public static boolean anyMethodCalls(String className, String targetOwner, String targetName, String targetDescriptor) {
        return !methodsCalling(className, null, targetOwner, targetName, targetDescriptor).isEmpty();
    }

    /**
     * Methods of the class (optionally only those with descriptor {@code methodDescriptor}) that call
     * {@code targetOwner.targetName targetDescriptor}. Lets a check find compiler-generated lambdas by what
     * they do instead of by their synthetic name.
     */
    public static List<MethodNode> methodsCalling(
            String className,
            String methodDescriptor,
            String targetOwner,
            String targetName,
            String targetDescriptor
    ) {
        final ClassNode node = readClassNodeWithCode(className);
        if (node == null) {
            return List.of();
        }
        final String key = callKey(targetOwner.replace('.', '/'), targetName, targetDescriptor);
        final List<MethodNode> result = new ArrayList<>();
        for (MethodNode method : node.methods) {
            if (methodDescriptor != null && !method.desc.equals(methodDescriptor)) {
                continue;
            }
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
                if (instruction instanceof MethodInsnNode call && callKey(call).equals(key)) {
                    result.add(method);
                    break;
                }
            }
        }
        return result;
    }

    /** {@link #methodStartsWith(MethodNode, String...)} on a named method; false when it is missing. */
    public static boolean methodStartsWith(String className, String methodName, String methodDescriptor, String... expected) {
        final MethodNode method = findMethodWithCode(className, methodName, methodDescriptor);
        return method != null && methodStartsWith(method, expected);
    }

    /**
     * True when the first real instructions of the method (labels, line numbers and frames skipped) are
     * {@code expected}, written as {@code OPCODE} or {@code OPCODE operand}: {@code ALOAD 1},
     * {@code INVOKEVIRTUAL net/minecraft/world/item/ItemStack.isEmpty()Z}, {@code IFEQ}, {@code GETFIELD owner.name desc}.
     * Jump targets are not compared.
     */
    public static boolean methodStartsWith(MethodNode method, String... expected) {
        int matched = 0;
        for (AbstractInsnNode instruction = method.instructions.getFirst();
                instruction != null && matched < expected.length;
                instruction = instruction.getNext()) {
            if (instruction.getOpcode() < 0) {
                continue;
            }
            if (!describe(instruction).equals(expected[matched])) {
                return false;
            }
            matched++;
        }
        return matched == expected.length;
    }

    static String describe(AbstractInsnNode instruction) {
        final String opcode = OPCODE_NAMES[instruction.getOpcode()];
        if (instruction instanceof VarInsnNode variable) {
            return opcode + " " + variable.var;
        }
        if (instruction instanceof MethodInsnNode call) {
            return opcode + " " + callKey(call);
        }
        if (instruction instanceof FieldInsnNode field) {
            return opcode + " " + field.owner + "." + field.name + " " + field.desc;
        }
        if (instruction instanceof TypeInsnNode type) {
            return opcode + " " + type.desc;
        }
        if (instruction instanceof IntInsnNode integer) {
            return opcode + " " + integer.operand;
        }
        return opcode;
    }

    private static String callKey(MethodInsnNode call) {
        return callKey(call.owner, call.name, call.desc);
    }

    private static String callKey(String internalOwner, String name, String descriptor) {
        return internalOwner + "." + name + descriptor;
    }

    private static MethodNode findMethodWithCode(String className, String methodName, String methodDescriptor) {
        final ClassNode node = readClassNodeWithCode(className);
        if (node == null) {
            return null;
        }
        for (MethodNode method : node.methods) {
            if (method.name.equals(methodName) && method.desc.equals(methodDescriptor)) {
                return method;
            }
        }
        return null;
    }

    private static final String[] OPCODE_NAMES = opcodeNames();

    private static String[] opcodeNames() {
        final String[] names = new String[256];
        for (java.lang.reflect.Field field : Opcodes.class.getFields()) {
            if (field.getType() != int.class || !java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            final String name = field.getName();
            if (name.startsWith("ACC_") || name.startsWith("ASM") || name.startsWith("V") && Character.isDigit(name.charAt(name.length() - 1))
                    || name.startsWith("T_") || name.startsWith("H_") || name.startsWith("F_") || name.equals("SOURCE_DEPRECATED")
                    || name.equals("SOURCE_MASK")) {
                continue;
            }
            try {
                final int value = field.getInt(null);
                if (value >= 0 && value < names.length && names[value] == null) {
                    names[value] = name;
                }
            } catch (IllegalAccessException ignored) {
                // Public constants, never thrown.
            }
        }
        for (int i = 0; i < names.length; i++) {
            if (names[i] == null) {
                names[i] = "OP_" + i;
            }
        }
        return names;
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
