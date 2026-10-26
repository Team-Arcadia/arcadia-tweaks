package com.teamarcadia.arcadiatweaks.neoforge.accessories;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * C1: puts a guarded call to {@link ArcadiaAccessoriesContains#handle} in front of the body of the
 * handler Accessories merges into {@code Inventory} for {@code contains(ItemStack)}.
 *
 * <p>The handler is a merged Mixin method whose name carries a per-session id
 * ({@code handler$zkf000$accessories$extendContainsCheck}), so no injector can address it. This runs
 * from the {@code postApply} of an empty anchor Mixin applied after Accessories, finds the handler by
 * pattern, checks every instruction of it and of its predicate lambda against the beta.54 shape, and
 * only then prepends:
 * <pre>
 *   if (ArcadiaAccessoriesContains.handle(this, stack, cir)) return;
 *   ...original body, unchanged...
 * </pre>
 * The original body stays, so the kill switch, the client side and unknown capability classes run it
 * as before. Any difference in shape leaves the class untouched.
 *
 * <p>Holds only ASM code and class names as strings: it runs while {@code Inventory} is being
 * transformed and must not load game classes.
 *
 * @author vyrriox
 */
public final class AccessoriesContainsSurgery {

    public enum Result {
        APPLIED,
        ALREADY_APPLIED,
        HANDLER_NOT_FOUND,
        HANDLER_AMBIGUOUS,
        HANDLER_SHAPE_CHANGED,
        PREDICATE_NOT_FOUND,
        PREDICATE_CHANGED
    }

    public static final Pattern HANDLER_NAME = Pattern.compile("^handler\\$[0-9a-z]+\\$accessories\\$extendContainsCheck$");
    public static final String HANDLER_DESC =
            "(Lnet/minecraft/world/item/ItemStack;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V";

    static final String INVENTORY = "net/minecraft/world/entity/player/Inventory";
    static final String PLAYER_DESC = "Lnet/minecraft/world/entity/player/Player;";
    static final String ITEM_STACK = "net/minecraft/world/item/ItemStack";
    static final String CAPABILITY = "io/wispforest/accessories/api/AccessoriesCapability";
    static final String CIR = "org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable";
    static final String PREDICATE_LAMBDA_DESC = "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z";

    public static final String HOOK_OWNER = "com/teamarcadia/arcadiatweaks/neoforge/accessories/ArcadiaAccessoriesContains";
    public static final String HOOK_NAME = "handle";
    public static final String HOOK_DESC = "(L" + INVENTORY + ";L" + ITEM_STACK + ";L" + CIR + ";)Z";

    private AccessoriesContainsSurgery() {}

    public static Result apply(ClassNode target) {
        final List<MethodNode> handlers = new ArrayList<>();
        for (MethodNode method : target.methods) {
            if (HANDLER_NAME.matcher(method.name).matches() && HANDLER_DESC.equals(method.desc)) {
                handlers.add(method);
            }
        }
        if (handlers.isEmpty()) {
            return Result.HANDLER_NOT_FOUND;
        }
        if (handlers.size() > 1) {
            return Result.HANDLER_AMBIGUOUS;
        }
        final MethodNode handler = handlers.get(0);
        final List<AbstractInsnNode> code = code(handler);
        if (!code.isEmpty() && isHookCall(code.get(3 < code.size() ? 3 : 0))) {
            return Result.ALREADY_APPLIED;
        }
        final Handle lambda = handlerShape(handler, code);
        if (lambda == null) {
            return Result.HANDLER_SHAPE_CHANGED;
        }
        final MethodNode predicate = findMethod(target, lambda.getName(), lambda.getDesc());
        if (predicate == null || (predicate.access & Opcodes.ACC_STATIC) == 0) {
            return Result.PREDICATE_NOT_FOUND;
        }
        if (!isDeadPredicate(predicate)) {
            return Result.PREDICATE_CHANGED;
        }

        final LabelNode original = new LabelNode();
        final InsnList hook = new InsnList();
        hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
        hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
        hook.add(new VarInsnNode(Opcodes.ALOAD, 2));
        hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK_OWNER, HOOK_NAME, HOOK_DESC, false));
        hook.add(new JumpInsnNode(Opcodes.IFEQ, original));
        hook.add(new InsnNode(Opcodes.RETURN));
        hook.add(original);
        // Same locals and an empty stack as at method entry; the class is written with COMPUTE_FRAMES
        // anyway, this keeps the method valid for a writer that does not recompute frames.
        hook.add(new FrameNode(Opcodes.F_NEW, 3, new Object[]{INVENTORY, ITEM_STACK, CIR}, 0, new Object[0]));
        handler.instructions.insert(hook);
        handler.maxStack = Math.max(handler.maxStack, 3);
        return Result.APPLIED;
    }

    private static boolean isHookCall(AbstractInsnNode insn) {
        return insn instanceof MethodInsnNode call
                && call.getOpcode() == Opcodes.INVOKESTATIC
                && call.owner.equals(HOOK_OWNER)
                && call.name.equals(HOOK_NAME);
    }

    /**
     * Checks the merged handler instruction by instruction against beta.54:
     * <pre>
     * AccessoriesCapability capability = AccessoriesCapability.get(this.player);
     * if (capability == null) return;
     * boolean bl = capability.isEquipped(stack1 -> ...);
     * if (bl) cir.setReturnValue(true);
     * </pre>
     * Returns the lambda implementation handle, or {@code null} on any difference.
     */
    static Handle handlerShape(MethodNode handler, List<AbstractInsnNode> code) {
        if (code.size() != 19) {
            return null;
        }
        if (!isVar(code.get(0), Opcodes.ALOAD, 0)
                || !isField(code.get(1), Opcodes.GETFIELD, INVENTORY, "player", PLAYER_DESC)
                || !isCall(code.get(2), Opcodes.INVOKESTATIC, CAPABILITY, "get",
                "(Lnet/minecraft/world/entity/LivingEntity;)L" + CAPABILITY + ";")
                || !isVar(code.get(3), Opcodes.ASTORE, 3)
                || !isVar(code.get(4), Opcodes.ALOAD, 3)
                || !isJump(code.get(5), Opcodes.IFNONNULL)
                || code.get(6).getOpcode() != Opcodes.RETURN
                || !isVar(code.get(7), Opcodes.ALOAD, 3)
                || !isVar(code.get(8), Opcodes.ALOAD, 1)
                || !(code.get(9) instanceof InvokeDynamicInsnNode indy)
                || !isCall(code.get(10), Opcodes.INVOKEINTERFACE, CAPABILITY, "isEquipped", "(Ljava/util/function/Predicate;)Z")
                || !isVar(code.get(11), Opcodes.ISTORE, 4)
                || !isVar(code.get(12), Opcodes.ILOAD, 4)
                || !isJump(code.get(13), Opcodes.IFEQ)
                || !isVar(code.get(14), Opcodes.ALOAD, 2)
                || code.get(15).getOpcode() != Opcodes.ICONST_1
                || !isCall(code.get(16), Opcodes.INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;")
                || !isCall(code.get(17), Opcodes.INVOKEVIRTUAL, CIR, "setReturnValue", "(Ljava/lang/Object;)V")
                || code.get(18).getOpcode() != Opcodes.RETURN) {
            return null;
        }
        if (jumpTarget(code, code.get(5)) != 7 || jumpTarget(code, code.get(13)) != 18) {
            return null;
        }
        if (!handler.tryCatchBlocks.isEmpty()) {
            return null;
        }
        if (!"test".equals(indy.name)
                || !("(L" + ITEM_STACK + ";)Ljava/util/function/Predicate;").equals(indy.desc)
                || !"java/lang/invoke/LambdaMetafactory".equals(indy.bsm.getOwner())
                || !"metafactory".equals(indy.bsm.getName())
                || indy.bsmArgs.length != 3
                || !(indy.bsmArgs[1] instanceof Handle impl)) {
            return null;
        }
        if (impl.getTag() != Opcodes.H_INVOKESTATIC
                || !INVENTORY.equals(impl.getOwner())
                || !PREDICATE_LAMBDA_DESC.equals(impl.getDesc())) {
            return null;
        }
        return impl;
    }

    /**
     * The beta.54 predicate {@code (target, stack1) -> stack1.isEmpty() && isSameItemSameComponents(stack1, target)}.
     * Accessories only tests it on non-empty stacks, so it never matches; an upstream fix to
     * {@code !stack1.isEmpty()} fails this check and C1 stays off.
     */
    static boolean isDeadPredicate(MethodNode predicate) {
        final List<AbstractInsnNode> code = code(predicate);
        if (code.size() != 11 || !predicate.tryCatchBlocks.isEmpty()) {
            return false;
        }
        return isVar(code.get(0), Opcodes.ALOAD, 1)
                && isCall(code.get(1), Opcodes.INVOKEVIRTUAL, ITEM_STACK, "isEmpty", "()Z")
                && isJump(code.get(2), Opcodes.IFEQ)
                && isVar(code.get(3), Opcodes.ALOAD, 1)
                && isVar(code.get(4), Opcodes.ALOAD, 0)
                && isCall(code.get(5), Opcodes.INVOKESTATIC, ITEM_STACK, "isSameItemSameComponents",
                "(L" + ITEM_STACK + ";L" + ITEM_STACK + ";)Z")
                && isJump(code.get(6), Opcodes.IFEQ)
                && code.get(7).getOpcode() == Opcodes.ICONST_1
                && isJump(code.get(8), Opcodes.GOTO)
                && code.get(9).getOpcode() == Opcodes.ICONST_0
                && code.get(10).getOpcode() == Opcodes.IRETURN
                && jumpTarget(code, code.get(2)) == 9
                && jumpTarget(code, code.get(6)) == 9
                && jumpTarget(code, code.get(8)) == 10;
    }

    /** Real instructions: no labels, line numbers or frames. */
    static List<AbstractInsnNode> code(MethodNode method) {
        final List<AbstractInsnNode> code = new ArrayList<>();
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (!(insn instanceof LabelNode) && !(insn instanceof LineNumberNode) && !(insn instanceof FrameNode)) {
                code.add(insn);
            }
        }
        return code;
    }

    /** Index in {@code code} of the first real instruction after the jump's label, or -1. */
    private static int jumpTarget(List<AbstractInsnNode> code, AbstractInsnNode jump) {
        AbstractInsnNode insn = ((JumpInsnNode) jump).label;
        while (insn != null && (insn instanceof LabelNode || insn instanceof LineNumberNode || insn instanceof FrameNode)) {
            insn = insn.getNext();
        }
        return insn == null ? -1 : code.indexOf(insn);
    }

    private static MethodNode findMethod(ClassNode target, String name, String desc) {
        for (MethodNode method : target.methods) {
            if (method.name.equals(name) && method.desc.equals(desc)) {
                return method;
            }
        }
        return null;
    }

    private static boolean isVar(AbstractInsnNode insn, int opcode, int var) {
        return insn instanceof VarInsnNode v && v.getOpcode() == opcode && v.var == var;
    }

    private static boolean isJump(AbstractInsnNode insn, int opcode) {
        return insn instanceof JumpInsnNode && insn.getOpcode() == opcode;
    }

    private static boolean isField(AbstractInsnNode insn, int opcode, String owner, String name, String desc) {
        return insn instanceof FieldInsnNode f && f.getOpcode() == opcode
                && f.owner.equals(owner) && f.name.equals(name) && f.desc.equals(desc);
    }

    private static boolean isCall(AbstractInsnNode insn, int opcode, String owner, String name, String desc) {
        return insn instanceof MethodInsnNode m && m.getOpcode() == opcode
                && m.owner.equals(owner) && m.name.equals(name) && m.desc.equals(desc);
    }
}
