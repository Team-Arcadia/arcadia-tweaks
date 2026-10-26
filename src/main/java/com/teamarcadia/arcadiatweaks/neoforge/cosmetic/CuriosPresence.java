package com.teamarcadia.arcadiatweaks.neoforge.cosmetic;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * Curios part of the presence snapshot. Only loaded when Curios is installed.
 *
 * Uses the same calls as Curios' own {@code Inventory.contains(ItemStack)} hook
 * ({@code CuriosUtilMixinHooks.containsStack}: {@code CuriosApi.getCuriosInventory(player)} then
 * {@code findFirstCurio(Predicate)}), with a predicate that records each non-empty stack and never matches,
 * so exactly the stacks that hook would test are listed.
 *
 * @author vyrriox
 */
final class CuriosPresence {

    private static ItemPresence target;

    private static final Predicate<ItemStack> COLLECT = stack -> {
        if (!stack.isEmpty()) {
            target.add(stack.getItem());
        }
        return false;
    };

    private CuriosPresence() {}

    static void addCurioItems(Player player, ItemPresence snapshot) {
        final Optional<ICuriosItemHandler> handler = CuriosApi.getCuriosInventory(player);
        if (handler.isPresent()) {
            target = snapshot;
            try {
                handler.get().findFirstCurio(COLLECT);
            } finally {
                target = null;
            }
        }
    }
}
