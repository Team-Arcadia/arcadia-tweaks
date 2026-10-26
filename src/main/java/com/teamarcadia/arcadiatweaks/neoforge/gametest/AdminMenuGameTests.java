package com.teamarcadia.arcadiatweaks.neoforge.gametest;

import com.mojang.authlib.GameProfile;
import com.teamarcadia.arcadiatweaks.ArcadiaTweaks;
import com.teamarcadia.arcadiatweaks.common.config.ArcadiaConfig;
import com.teamarcadia.arcadiatweaks.neoforge.admin.ArcadiaAdminMenu;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * In-game checks of the admin menu (/arcadia_tweaks gui): a click flips a setting and saves it,
 * pages can be browsed, and "Recommended defaults" only applies on the second click.
 *
 * @author vyrriox
 */
@GameTestHolder(ArcadiaTweaks.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AdminMenuGameTests {

    private static final String TEMPLATE = "empty";
    private static final String BATCH = ArcadiaTweaks.MOD_ID;
    private static final int SLOT_DEFAULTS = 47;
    private static final int SLOT_PAGE = 49;
    private static final int SLOT_NEXT = 53;
    private static final String MOVE_GUARD_LABEL = "Entity move guard: ";

    private AdminMenuGameTests() {}

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void adminMenuTogglesBrowsesAndResets(GameTestHelper helper) {
        final boolean original = ArcadiaConfig.ENTITY_MOVE_GUARD_ENABLED.get();
        try {
            final ServerPlayer player = FakePlayerFactory.get(helper.getLevel(),
                    new GameProfile(UUID.randomUUID(), "arcadia_admin_test"));
            final ArcadiaAdminMenu menu = ArcadiaAdminMenu.createDetached(1, player.getInventory());

            helper.assertTrue(name(menu, SLOT_PAGE).startsWith("Page 1 / "), "Page indicator should start at page 1");

            final int slot = findOnAnyPage(menu, player, MOVE_GUARD_LABEL);
            helper.assertTrue(slot >= 0, "The safety row should list the entity move guard");

            final boolean before = ArcadiaConfig.ENTITY_MOVE_GUARD_ENABLED.get();
            menu.clicked(slot, 0, ClickType.PICKUP, player);
            helper.assertValueEqual(ArcadiaConfig.ENTITY_MOVE_GUARD_ENABLED.get(), !before, "A click should flip the switch");
            helper.assertTrue(name(menu, slot).endsWith(before ? "OFF" : "ON"), "The item should show the new state");
            menu.clicked(slot, 0, ClickType.PICKUP, player);
            helper.assertValueEqual(ArcadiaConfig.ENTITY_MOVE_GUARD_ENABLED.get(), before, "A second click should flip it back");

            ArcadiaConfig.ENTITY_MOVE_GUARD_ENABLED.set(false);
            menu.clicked(SLOT_DEFAULTS, 0, ClickType.PICKUP, player);
            helper.assertValueEqual(ArcadiaConfig.ENTITY_MOVE_GUARD_ENABLED.get(), false, "One click on defaults must only arm it");
            helper.assertTrue(menu.getContainer().getItem(SLOT_DEFAULTS).is(Items.TNT), "Armed defaults should ask for confirmation");
            menu.clicked(SLOT_DEFAULTS, 0, ClickType.PICKUP, player);
            helper.assertValueEqual(ArcadiaConfig.ENTITY_MOVE_GUARD_ENABLED.get(),
                    ArcadiaConfig.ENTITY_MOVE_GUARD_ENABLED.getDefault(), "The second click should restore the default");
            helper.assertTrue(menu.getContainer().getItem(SLOT_DEFAULTS).is(Items.NETHER_STAR), "Defaults should be disarmed again");

            final ItemStack held = new ItemStack(Items.STONE);
            menu.setCarried(held);
            menu.clicked(slot, 0, ClickType.PICKUP, player);
            helper.assertTrue(menu.getCarried() == held && held.getCount() == 1,
                    "A click on a menu slot must never take the carried stack");
            helper.assertTrue(!menu.getContainer().getItem(slot).is(Items.STONE), "Menu slots must not receive items");
        } finally {
            ArcadiaConfig.ENTITY_MOVE_GUARD_ENABLED.set(original);
            ArcadiaConfig.SPEC.save();
        }
        helper.succeed();
    }

    /** Turning the [modules] botany switch off must stop every BotanyPots strategy at once. */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void botanyModuleSwitchStopsEveryStrategy(GameTestHelper helper) {
        final boolean original = ArcadiaConfig.MODULE_BOTANY_ENABLED.get();
        try {
            final var botany = ArcadiaConfig.BOTANY;
            ArcadiaConfig.MODULE_BOTANY_ENABLED.set(true);
            helper.assertTrue(botany.enabled(botany.s1MatchesCache) == botany.s1MatchesCache.get(),
                    "With the module on, a strategy follows its own switch");
            ArcadiaConfig.MODULE_BOTANY_ENABLED.set(false);
            for (var strategy : java.util.List.of(botany.s1MatchesCache, botany.s2TickCoalescing,
                    botany.s3HopperBackoff, botany.a1RequiredGrowthTicksCache, botany.a2LightFlagDowngrade)) {
                helper.assertTrue(!botany.enabled(strategy), "With the module off, every strategy must be off");
            }
        } finally {
            ArcadiaConfig.MODULE_BOTANY_ENABLED.set(original);
        }
        helper.succeed();
    }

    private static int findOnAnyPage(ArcadiaAdminMenu menu, ServerPlayer player, String labelPrefix) {
        for (int page = 0; page < 10; page++) {
            for (int i = 0; i < 45; i++) {
                if (name(menu, i).startsWith(labelPrefix)) {
                    return i;
                }
            }
            if (menu.getContainer().getItem(SLOT_NEXT).isEmpty()) {
                return -1;
            }
            menu.clicked(SLOT_NEXT, 0, ClickType.PICKUP, player);
        }
        return -1;
    }

    private static String name(ArcadiaAdminMenu menu, int slot) {
        final Component name = menu.getContainer().getItem(slot).get(DataComponents.CUSTOM_NAME);
        return name == null ? "" : name.getString();
    }
}
