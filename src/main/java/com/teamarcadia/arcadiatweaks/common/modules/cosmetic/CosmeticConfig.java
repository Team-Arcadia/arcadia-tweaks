package com.teamarcadia.arcadiatweaks.common.modules.cosmetic;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * CosmeticWeapons / CosmeticArmours optimizations.
 *
 * @author vyrriox
 */
public final class CosmeticConfig {

    public final ModConfigSpec.BooleanValue containsPresenceGate;

    public CosmeticConfig(ModConfigSpec.Builder b) {
        b.comment(
                "CosmeticWeapons / CosmeticArmours optimizations. Each entry is a kill-switch - set false to",
                "disable the corresponding Mixin path while keeping the rest active."
        ).push("cosmetic");

        containsPresenceGate = b
                .comment(
                        "Item-presence gate for the advancement tick procedures.",
                        "Every player tick, 15 procedures of the two mods test up to 22 items each with",
                        "Inventory.contains (a full scan of the inventory, Curios and Accessories per item).",
                        "When enabled, the first test of each procedure runs normally; if it finds nothing, the",
                        "items present in the inventory and Curios slots are listed once, and the later tests of",
                        "that call skip the scan for an item that is not in the list (it cannot match).",
                        "Exact: same answers, same advancements at the same moment. Server side only. Turns itself",
                        "off at server start, with a warning, when another mod hooks Inventory.contains or the",
                        "ItemStack comparison it relies on."
                )
                .define("contains_presence_gate", true);

        b.pop();
    }
}
