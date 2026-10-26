package com.teamarcadia.arcadiatweaks.neoforge.cosmetic;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.teamarcadia.arcadiatweaks.ArcadiaTweaks;
import com.teamarcadia.arcadiatweaks.common.config.ArcadiaConfig;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import org.objectweb.asm.tree.ClassNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Item-presence gate for the CosmeticWeapons / CosmeticArmours advancement procedures.
 *
 * Each gated procedure ({@code *ProdProcedure.execute(Event, Entity)}) tests a chain of items with
 * {@code player.getInventory().contains(new ItemStack(ITEM))}, stopping at the first true. Between two of
 * those calls the bytecode only builds the next target stack (checked per class by the Mixin plugin), so
 * the inventory cannot change inside one call. {@code contains} can only answer true for a target whose
 * item sits in a vanilla slot or an active Curios slot (Accessories never matches a non-empty target, see
 * the plugin pin). So:
 * <ol>
 *     <li>the first {@code contains} of a call always runs unchanged;</li>
 *     <li>if it is false and the gate is active, the items of every slot {@code contains} reads are listed
 *     once (on the second call);</li>
 *     <li>a later call whose item is not listed returns false without scanning: the original would have
 *     returned false too. A listed item goes to the real {@code contains}, which decides with every hook.</li>
 * </ol>
 * The gate never turns a false into a true or a true into a false, so the chain takes the same branch and the
 * advancement is awarded at the same moment.
 *
 * Skipped calls are not observable. In the original they would repeat the first call's work on the same
 * stacks: Almanac's {@code checkAndFix} (idempotent, already applied by the first call), Curios capability
 * lookups and Accessories' {@code getHolder} / lookup cache reads. The first {@code contains} of every
 * procedure call still runs in the same tick, so those caches see the same accesses at the same moment;
 * only repeats of them inside one call disappear. This holds whether or not ArcadiaTweaks' Accessories
 * handler optimization is enabled, because the gate only depends on the answer of {@code contains}.
 *
 * The client ({@code LocalPlayer}) and any thread other than the server thread pass through unchanged.
 *
 * @author vyrriox
 */
public final class CosmeticGate {

    /** Classes the cosmetic Mixins target; only our Mixin may change them (checked by the startup audit). */
    public static final List<String> GATED_PROCEDURES = List.of(
            "cosmeticweaponsmod.procedures.DiamondTierWeaponAdvProdProcedure",
            "cosmeticweaponsmod.procedures.GildedNetheriteTierWeaponAdvProdProcedure",
            "cosmeticweaponsmod.procedures.GoldTierWeaponAdvProdProcedure",
            "cosmeticweaponsmod.procedures.IronTierWeaponAdvProdProcedure",
            "cosmeticweaponsmod.procedures.NetheriteTierWeaponAdvProdProcedure",
            "cosmeticweaponsmod.procedures.PrismarineWeaponAdvProdProcedure",
            "cosmeticweaponsmod.procedures.RoseGoldWeaponAdvProdProcedure",
            "cosmeticweaponsmod.procedures.StoneTierWeaponAdvProdProcedure",
            "cosmeticweaponsmod.procedures.WoodTierWeaponAdvProdProcedure",
            "cosmeticarmoursmod.procedures.AngelHaloAchivementProcedureProcedure",
            "cosmeticarmoursmod.procedures.AwokenHaloAdvProdProcedure",
            "cosmeticarmoursmod.procedures.FallenAngelHaloAchievementProcedureProcedure",
            "cosmeticarmoursmod.procedures.GloriousCoronationProdProcedure",
            "cosmeticarmoursmod.procedures.GoldenHaloAchievementProcedureProcedure",
            "cosmeticarmoursmod.procedures.WingedHaloAchievementProcedureProcedure"
    );

    private static final String OWN_INVENTORY_ACCESSOR = "com.teamarcadia.arcadiatweaks.neoforge.mixin.cosmetic.InventoryCompartmentsAccessor";

    /** Reused snapshot; only touched on the server thread (checked by {@link #active}). */
    private static final ItemPresence SNAPSHOT = new ItemPresence(64);

    private static volatile boolean auditPassed;
    private static volatile String auditFailure = "server not started";
    private static boolean curiosLoaded;
    /** Test hook: skip every later lookup, present or not. Proves the differential tests catch a wrong gate. */
    private static boolean brokenForTesting;
    /** Lookups answered without calling {@code contains}. Plain counter, server thread only. */
    private static long skippedLookups;
    /** Snapshots taken. Plain counter, server thread only. */
    private static long snapshots;

    private CosmeticGate() {}

    /**
     * Mixin entry, called for every {@code Inventory.contains(ItemStack)} of a gated procedure.
     * {@code presence} is a MixinExtras {@code @Share} local: null at the start of every {@code execute} call.
     */
    public static boolean contains(Inventory inventory, ItemStack target, Operation<Boolean> original, LocalRef<ItemPresence> presence) {
        ItemPresence snapshot = presence.get();
        if (snapshot == null) {
            final boolean found = original.call(inventory, target);
            presence.set(!found && active(inventory) ? ItemPresence.PENDING : ItemPresence.DISABLED);
            return found;
        }
        if (snapshot == ItemPresence.DISABLED || target.isEmpty()) {
            return original.call(inventory, target);
        }
        if (snapshot == ItemPresence.PENDING) {
            snapshot = capture(inventory);
            presence.set(snapshot);
        }
        if (brokenForTesting || !snapshot.mayContain(target.getItem())) {
            skippedLookups++;
            return false;
        }
        return original.call(inventory, target);
    }

    /** Config on, audit passed, real server player on the server thread. */
    public static boolean active(Inventory inventory) {
        return auditPassed
                && inventory.player instanceof ServerPlayer player
                && player.server != null
                && player.server.isSameThread()
                && ArcadiaConfig.COSMETIC.containsPresenceGate.get();
    }

    /**
     * Items of every slot {@code Inventory.contains} reads: the inventory compartments (main, armour,
     * offhand, read through the same list vanilla iterates) and, with Curios, every curio stack its
     * {@code contains} hook visits. Accessories adds nothing: its hook never matches a non-empty target.
     */
    static ItemPresence capture(Inventory inventory) {
        final ItemPresence snapshot = SNAPSHOT;
        snapshot.clear();
        for (NonNullList<ItemStack> compartment : ((CompartmentsView) inventory).arcadia$compartments()) {
            for (int i = 0, n = compartment.size(); i < n; i++) {
                final ItemStack stack = compartment.get(i);
                if (!stack.isEmpty()) {
                    snapshot.add(stack.getItem());
                }
            }
        }
        if (curiosLoaded) {
            CuriosPresence.addCurioItems(inventory.player, snapshot);
        }
        snapshots++;
        return snapshot;
    }

    /**
     * Runs at server start: checks that only reviewed Mixins hook {@code Inventory.contains} and the
     * ItemStack comparison, and that only our Mixin touches the gated procedures. Any surprise turns the gate
     * off with one warning naming it; the procedures then behave exactly as without ArcadiaTweaks.
     */
    public static void runStartupAudit() {
        curiosLoaded = ModList.get() != null && ModList.get().isLoaded("curios");
        final List<String> problems = new ArrayList<>();

        final Map<String, ClassNode> inventoryMixins = CosmeticMixinAudit.appliedMixins(CosmeticMixinAudit.INVENTORY);
        if (inventoryMixins == null) {
            problems.add("cannot list the Mixins applied to Inventory");
        } else if (!inventoryMixins.containsKey(OWN_INVENTORY_ACCESSOR)) {
            problems.add("Mixins listed for Inventory miss our own accessor, the listing cannot be trusted: " + inventoryMixins.keySet());
        } else {
            problems.addAll(CosmeticMixinAudit.findUnexpected(inventoryMixins,
                    CosmeticMixinAudit.INVENTORY_WATCHED, CosmeticMixinAudit.INVENTORY_ALLOWED));
        }
        final Map<String, ClassNode> itemStackMixins = CosmeticMixinAudit.appliedMixins(CosmeticMixinAudit.ITEM_STACK);
        if (itemStackMixins == null) {
            problems.add("cannot list the Mixins applied to ItemStack");
        } else {
            problems.addAll(CosmeticMixinAudit.findUnexpected(itemStackMixins,
                    CosmeticMixinAudit.ITEM_STACK_WATCHED, CosmeticMixinAudit.ITEM_STACK_ALLOWED));
        }
        for (String procedure : GATED_PROCEDURES) {
            final Map<String, ClassNode> procedureMixins = CosmeticMixinAudit.appliedMixins(procedure);
            if (procedureMixins != null) {
                for (String foreign : CosmeticMixinAudit.findForeign(procedureMixins.keySet())) {
                    problems.add(foreign + " -> " + procedure);
                }
            }
        }

        if (problems.isEmpty()) {
            auditFailure = null;
            auditPassed = true;
        } else {
            auditFailure = String.join("; ", problems);
            auditPassed = false;
            ArcadiaTweaks.LOGGER.warn("[cosmetic] Item-presence gate turned off: unexpected Mixin on a method it relies on: {}",
                    auditFailure);
        }
    }

    public static boolean auditPassed() {
        return auditPassed;
    }

    /** Why the audit failed, or null when it passed. */
    public static String auditFailure() {
        return auditFailure;
    }

    public static long skippedLookups() {
        return skippedLookups;
    }

    public static long snapshots() {
        return snapshots;
    }

    /** GameTest only. */
    public static void setBrokenForTesting(boolean broken) {
        brokenForTesting = broken;
    }

    /** GameTest only: forces the audit result (the dev runtime has no reason to fail it). */
    public static void setAuditPassedForTesting(boolean passed) {
        auditPassed = passed;
    }

    /** Implemented on {@code Inventory} by the cosmetic accessor Mixin. */
    public interface CompartmentsView {
        List<NonNullList<ItemStack>> arcadia$compartments();
    }
}
