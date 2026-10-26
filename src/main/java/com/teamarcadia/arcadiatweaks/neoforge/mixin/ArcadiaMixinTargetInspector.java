package com.teamarcadia.arcadiatweaks.neoforge.mixin;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

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
     * SHA-256 of the normalized instruction stream of a method, or {@code null} when the class or the
     * method is missing or has no code. See {@link #fingerprint(MethodNode)}.
     */
    public static String methodFingerprint(String className, String methodName, String descriptor) {
        final ClassNode node = readClassNodeWithCode(className);
        if (node == null) {
            return null;
        }
        for (MethodNode method : node.methods) {
            if (method.name.equals(methodName) && method.desc.equals(descriptor)) {
                return method.instructions.size() == 0 ? null : fingerprint(method);
            }
        }
        return null;
    }

    /**
     * Hashes what a method does, not how it was compiled: opcodes; owner, name and descriptor of field
     * and method instructions; type, constant, int and variable operands; jump and switch targets as
     * label indexes; invokedynamic bootstrap handles and arguments; try/catch blocks. Line numbers,
     * frames and local variable names are ignored, so a rebuild of the same source gives the same hash
     * and any change of behaviour gives another.
     */
    public static String fingerprint(MethodNode method) {
        final Map<LabelNode, Integer> labels = new HashMap<>();
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof LabelNode label) {
                labels.put(label, labels.size());
            }
        }
        final StringBuilder out = new StringBuilder(method.instructions.size() * 16);
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof LineNumberNode || insn instanceof FrameNode) {
                continue;
            }
            if (insn instanceof LabelNode label) {
                out.append('L').append(labels.get(label)).append('\n');
                continue;
            }
            out.append(insn.getOpcode());
            switch (insn) {
                case IntInsnNode n -> out.append(' ').append(n.operand);
                case VarInsnNode n -> out.append(' ').append(n.var);
                case TypeInsnNode n -> out.append(' ').append(n.desc);
                case FieldInsnNode n -> out.append(' ').append(n.owner).append('.').append(n.name).append(':').append(n.desc);
                case MethodInsnNode n -> out.append(' ').append(n.owner).append('.').append(n.name).append(n.desc)
                        .append(n.itf ? " itf" : "");
                case InvokeDynamicInsnNode n -> {
                    out.append(' ').append(n.name).append(n.desc).append(' ');
                    appendConstant(out, n.bsm);
                    for (Object arg : n.bsmArgs) {
                        out.append(' ');
                        appendConstant(out, arg);
                    }
                }
                case JumpInsnNode n -> out.append(" L").append(labels.get(n.label));
                case LdcInsnNode n -> {
                    out.append(' ');
                    appendConstant(out, n.cst);
                }
                case IincInsnNode n -> out.append(' ').append(n.var).append(' ').append(n.incr);
                case TableSwitchInsnNode n -> {
                    out.append(' ').append(n.min).append(' ').append(n.max).append(" L").append(labels.get(n.dflt));
                    for (LabelNode target : n.labels) {
                        out.append(" L").append(labels.get(target));
                    }
                }
                case LookupSwitchInsnNode n -> {
                    out.append(" L").append(labels.get(n.dflt));
                    for (int i = 0; i < n.keys.size(); i++) {
                        out.append(' ').append(n.keys.get(i)).append(":L").append(labels.get(n.labels.get(i)));
                    }
                }
                case MultiANewArrayInsnNode n -> out.append(' ').append(n.desc).append(' ').append(n.dims);
                default -> {
                }
            }
            out.append('\n');
        }
        for (TryCatchBlockNode block : method.tryCatchBlocks) {
            out.append("TRY L").append(labels.get(block.start)).append(" L").append(labels.get(block.end))
                    .append(" L").append(labels.get(block.handler)).append(' ').append(block.type).append('\n');
        }
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(out.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static void appendConstant(StringBuilder out, Object constant) {
        switch (constant) {
            case Handle h -> out.append('H').append(h.getTag()).append(' ').append(h.getOwner()).append('.')
                    .append(h.getName()).append(h.getDesc()).append(h.isInterface() ? " itf" : "");
            case Type t -> out.append('T').append(t.getDescriptor());
            case String str -> out.append("S\"").append(str).append('"');
            case null -> out.append("null");
            default -> out.append(constant.getClass().getSimpleName()).append(':').append(constant);
        }
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
