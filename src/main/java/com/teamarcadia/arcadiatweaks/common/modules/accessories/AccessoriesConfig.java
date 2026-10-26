package com.teamarcadia.arcadiatweaks.common.modules.accessories;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Kill switches of the Accessories (wisp-forest) module. Both optimizations are server side only and
 * pinned to the bytecode of Accessories 1.1.0-beta.54: on any other build the Mixin plugin skips them
 * with one warning and Accessories keeps its own behaviour.
 *
 * @author vyrriox
 */
public final class AccessoriesConfig {

    public final ModConfigSpec.BooleanValue containsFastPath;
    public final ModConfigSpec.BooleanValue tickAllocationTrimmed;

    public AccessoriesConfig(ModConfigSpec.Builder b) {
        b.comment(
                "Accessories optimizations. Each entry is a kill-switch - set false to disable",
                "the corresponding Mixin path while keeping the rest active."
        ).push("accessories");

        containsFastPath = b
                .comment(
                        "C1 - Skip the dead accessory scan in Inventory.contains(ItemStack).",
                        "Accessories extends contains(ItemStack) with a scan of every accessory slot, but its",
                        "predicate (isEmpty instead of !isEmpty) can never match, so the scan never changes the",
                        "answer. When enabled, only the calls of that scan that change state run (capability",
                        "lookup, holder validation, container update); the answer stays exactly the same,",
                        "accessories still do not count for contains(ItemStack), as in Accessories itself.",
                        "Server side only. Skipped automatically on any Accessories build but 1.1.0-beta.54."
                )
                .define("contains_fast_path", true);

        tickAllocationTrimmed = b
                .comment(
                        "T-A - Run an allocation-trimmed copy of Accessories' per-entity tick.",
                        "Same calls in the same order as Accessories' own tick, but slot references, slot ids,",
                        "dirty maps and attribute builders are only created when a slot actually needs them.",
                        "Server side only. Skipped automatically on any Accessories build but 1.1.0-beta.54."
                )
                .define("tick_allocation_trimmed", true);

        b.pop();
    }
}
