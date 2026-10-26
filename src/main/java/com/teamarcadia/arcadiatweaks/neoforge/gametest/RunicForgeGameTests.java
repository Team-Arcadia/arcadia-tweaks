package com.teamarcadia.arcadiatweaks.neoforge.gametest;

import com.mojang.authlib.GameProfile;
import com.teamarcadia.arcadiatweaks.ArcadiaTweaks;
import com.teamarcadia.arcadiatweaks.common.modules.simplyswords.RunicForgeLock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.sweenus.simplyswords.api.AwakeningApi;
import net.sweenus.simplyswords.screen.RunicForgeScreenHandler;

import java.util.UUID;

/**
 * In-game checks of the Runic Forge fix for bug #321 ({@code RunicForgeScreenHandlerMixin},
 * {@code RunicForgeTabletSlotMixin}, {@link RunicForgeLock}).
 *
 * Each test opens the real {@code RunicForgeScreenHandler} for a fake player next to a real
 * Runic Forge block and moves the weapon with the same container clicks a player makes: a
 * pick-up click on the weapon slot to put the weapon in, another to take it back out.
 *
 * Simply Swords types only appear inside method bodies, so this class can still be scanned
 * when Simply Swords is absent; the tests then fail with an explicit message.
 *
 * @author vyrriox
 */
@GameTestHolder(ArcadiaTweaks.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RunicForgeGameTests {

    private static final String TEMPLATE = "empty";
    private static final String BATCH = ArcadiaTweaks.MOD_ID;
    private static final int WEAPON_SLOT = 0;
    private static final int TABLET_START = 3;
    private static final int TABLET_COUNT = 8;

    private RunicForgeGameTests() {}

    /**
     * Bug #321: a unique weapon without the awakening component reads as level 8. Placed in the
     * forge it must hand out no tablet, stay at level 8, and come back out at level 8.
     * With the lock disabled, the forge hands out 8 Runic Tablets here.
     */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void runicForgeGivesNoTabletsForImplicitLevel(GameTestHelper helper) {
        if (!requireSimplySwords(helper)) {
            return;
        }
        final ItemStack weapon = new ItemStack(claymore());
        helper.assertTrue(AwakeningApi.usesAwakeningProgression(weapon), "Watcher Claymore should use awakening progression");
        helper.assertValueEqual(AwakeningApi.getLevel(weapon), RunicForgeLock.MAX_LEVEL,
                "Watcher Claymore without awakening component should read as level 8");

        final ServerPlayer player = fakePlayer(helper);
        final AbstractContainerMenu menu = openForge(helper, player);
        insertWeapon(menu, player, weapon);

        helper.assertValueEqual(tabletCount(menu), 0, "Forge should hand out no Runic Tablet for an implicit level");
        helper.assertValueEqual(RunicForgeLock.get(menu.getSlot(WEAPON_SLOT).getItem()), RunicForgeLock.MAX_LEVEL,
                "Weapon in the forge should carry a level 8 lock");
        helper.assertValueEqual(AwakeningApi.getLevel(menu.getSlot(WEAPON_SLOT).getItem()), RunicForgeLock.MAX_LEVEL,
                "Weapon in the forge should keep its locked level 8");

        final ItemStack taken = takeWeapon(menu, player);
        helper.assertTrue(taken.is(claymore()), "Taken item should be the Watcher Claymore, got " + taken);
        helper.assertValueEqual(AwakeningApi.getLevel(taken), RunicForgeLock.MAX_LEVEL,
                "Weapon taken out of the forge should still be level 8");
        helper.assertValueEqual(tabletCount(menu), 0, "No Runic Tablet should be left in the forge");
        helper.succeed();
    }

    /** Unchanged behaviour: a weapon awakened with tablets (explicit level 3) gives back exactly 3. */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void runicForgeReturnsTabletsOfExplicitLevel(GameTestHelper helper) {
        if (!requireSimplySwords(helper)) {
            return;
        }
        final ItemStack weapon = new ItemStack(claymore());
        AwakeningApi.setLevel(weapon, 3);
        helper.assertValueEqual(AwakeningApi.getLevel(weapon), 3, "Weapon should be set to awakening level 3");

        final ServerPlayer player = fakePlayer(helper);
        final AbstractContainerMenu menu = openForge(helper, player);
        insertWeapon(menu, player, weapon);

        helper.assertValueEqual(tabletCount(menu), 3, "Forge should hand out exactly 3 Runic Tablets for level 3");
        helper.assertValueEqual(RunicForgeLock.get(menu.getSlot(WEAPON_SLOT).getItem()), 0,
                "Weapon with an awakening component should not get a lock");

        final ItemStack taken = takeWeapon(menu, player);
        helper.assertValueEqual(AwakeningApi.getLevel(taken), 3,
                "Weapon taken out with its 3 tablets still placed should be rebuilt at level 3");
        helper.assertValueEqual(RunicForgeLock.get(taken), 0, "Weapon taken out should carry no lock");
        helper.succeed();
    }

    /**
     * A locked weapon (implicit level 8) refuses tablets in its locked slots, so no tablet can be
     * lost on top of level 8. A weapon without a lock still accepts tablets.
     */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void runicForgeLockedSlotsRefuseTablets(GameTestHelper helper) {
        if (!requireSimplySwords(helper)) {
            return;
        }
        final ItemStack tablet = new ItemStack(tablet());
        final ServerPlayer player = fakePlayer(helper);

        final AbstractContainerMenu locked = openForge(helper, player);
        insertWeapon(locked, player, new ItemStack(claymore()));
        helper.assertValueEqual(RunicForgeLock.get(locked.getSlot(WEAPON_SLOT).getItem()), RunicForgeLock.MAX_LEVEL,
                "Weapon in the forge should carry a level 8 lock");
        for (int i = 0; i < TABLET_COUNT; i++) {
            helper.assertFalse(locked.getSlot(TABLET_START + i).mayPlace(tablet),
                    "Tablet slot " + i + " should refuse tablets for a weapon locked at level 8");
        }

        final ItemStack unlockedWeapon = new ItemStack(claymore());
        AwakeningApi.setLevel(unlockedWeapon, 0);
        final AbstractContainerMenu unlocked = openForge(helper, player);
        insertWeapon(unlocked, player, unlockedWeapon);
        helper.assertValueEqual(RunicForgeLock.get(unlocked.getSlot(WEAPON_SLOT).getItem()), 0,
                "Weapon at explicit level 0 should not get a lock");
        for (int i = 0; i < TABLET_COUNT; i++) {
            helper.assertTrue(unlocked.getSlot(TABLET_START + i).mayPlace(tablet),
                    "Tablet slot " + i + " should accept tablets for a weapon without a lock");
        }
        helper.succeed();
    }

    private static Item claymore() {
        return BuiltInRegistries.ITEM.get(id("watcher_claymore"));
    }

    private static Item tablet() {
        return BuiltInRegistries.ITEM.get(id("runic_tablet"));
    }

    private static Block forgeBlock() {
        return BuiltInRegistries.BLOCK.get(id("runic_forge"));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("simplyswords", path);
    }

    private static boolean requireSimplySwords(GameTestHelper helper) {
        if (!ModList.get().isLoaded("simplyswords")) {
            helper.fail("Simply Swords is not loaded: add it and its runtime dependencies to the dev runtime (see build.gradle)");
            return false;
        }
        return true;
    }

    private static ServerPlayer fakePlayer(GameTestHelper helper) {
        final String name = "arcforge_" + UUID.randomUUID().toString().substring(0, 6);
        final ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        player.moveTo(helper.absoluteVec(new Vec3(0.5D, 1.0D, 0.5D)));
        return player;
    }

    private static AbstractContainerMenu openForge(GameTestHelper helper, ServerPlayer player) {
        helper.setBlock(BlockPos.ZERO, forgeBlock());
        return new RunicForgeScreenHandler(0, player.getInventory(), helper.absolutePos(BlockPos.ZERO));
    }

    /** Puts the weapon in the weapon slot the way a player does: carried stack, pick-up click. */
    private static void insertWeapon(AbstractContainerMenu menu, ServerPlayer player, ItemStack weapon) {
        menu.setCarried(weapon);
        menu.clicked(WEAPON_SLOT, 0, ClickType.PICKUP, player);
        if (!menu.getCarried().isEmpty()) {
            throw new IllegalStateException("Runic Forge refused the weapon " + weapon);
        }
    }

    /** Takes the weapon back the way a player does: pick-up click with an empty cursor. */
    private static ItemStack takeWeapon(AbstractContainerMenu menu, ServerPlayer player) {
        menu.clicked(WEAPON_SLOT, 0, ClickType.PICKUP, player);
        final ItemStack taken = menu.getCarried();
        menu.setCarried(ItemStack.EMPTY);
        return taken;
    }

    private static int tabletCount(AbstractContainerMenu menu) {
        int count = 0;
        for (int i = 0; i < TABLET_COUNT; i++) {
            final ItemStack stack = menu.getSlot(TABLET_START + i).getItem();
            if (stack.is(tablet())) {
                count += stack.getCount();
            }
        }
        return count;
    }
}
