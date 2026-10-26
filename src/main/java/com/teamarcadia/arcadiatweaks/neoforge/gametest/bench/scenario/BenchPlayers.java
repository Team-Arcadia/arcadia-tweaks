package com.teamarcadia.arcadiatweaks.neoforge.gametest.bench.scenario;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Test players for the bench scenarios that need players.
 *
 * <p>A real {@code ServerPlayer} (its constructor loads real {@code PlayerAdvancements}, and Curios and
 * Accessories attach their data to it) that is never placed in the world: placing one through a mock
 * connection fails with Accessories installed (it syncs its data at login to a client that never
 * negotiated its channel). So the player is not in the level's entity list and the level does not
 * tick it; a scenario fires its tick events itself. A silent connection drops every packet a mod
 * sends to it.
 *
 * @author vyrriox
 */
final class BenchPlayers {

    /** Common survival inventory content, none of it part of a CosmeticWeapons / CosmeticArmours chain. */
    private static final Item[] FILLER = {
            Items.STONE, Items.DIRT, Items.COBBLESTONE, Items.OAK_LOG, Items.TORCH, Items.BREAD, Items.COAL,
            Items.IRON_INGOT, Items.GOLD_INGOT, Items.DIAMOND, Items.ARROW, Items.BOW, Items.SHIELD, Items.IRON_PICKAXE,
            Items.IRON_SHOVEL, Items.OAK_PLANKS, Items.STICK, Items.STRING, Items.BONE, Items.ROTTEN_FLESH,
            Items.APPLE, Items.COOKED_BEEF, Items.WATER_BUCKET, Items.LAVA_BUCKET, Items.FLINT_AND_STEEL,
            Items.REDSTONE, Items.GLASS, Items.SAND, Items.GRAVEL, Items.CRAFTING_TABLE, Items.FURNACE, Items.CHEST,
            Items.LADDER, Items.ENDER_PEARL, Items.EMERALD, Items.LAPIS_LAZULI, Items.SPRUCE_LOG, Items.GRANITE,
            Items.DEEPSLATE, Items.COBBLED_DEEPSLATE, Items.RAW_IRON, Items.RAW_COPPER, Items.COPPER_INGOT,
            Items.GOLDEN_CARROT, Items.COOKED_PORKCHOP, Items.BUCKET, Items.SHEARS, Items.FISHING_ROD,
            Items.OBSIDIAN, Items.GLOWSTONE_DUST, Items.QUARTZ, Items.NETHERRACK, Items.SCAFFOLDING
    };

    private BenchPlayers() {}

    static ServerPlayer create(ServerLevel level, String name, Vec3 pos) {
        final MinecraftServer server = level.getServer();
        final ServerPlayer player = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), name),
                ClientInformation.createDefault());
        player.setPos(pos);
        player.connection = new SilentListener(server, player);
        return player;
    }

    /**
     * A mixed survival inventory: 36 main stacks (cycling through the filler list from a per-player
     * offset, never one of {@code avoid}), a full armour set and an offhand item.
     */
    static void fillInventory(ServerPlayer player, int seed, Set<Item> avoid) {
        final List<Item> filler = new ArrayList<>();
        for (Item item : FILLER) {
            if (!avoid.contains(item)) filler.add(item);
        }
        final Inventory inventory = player.getInventory();
        for (int i = 0; i < 36; i++) {
            final Item item = filler.get((seed * 7 + i * 3) % filler.size());
            inventory.items.set(i, new ItemStack(item, Math.min(item.getDefaultMaxStackSize(), 1 + (seed + i * 5) % 64)));
        }
        final boolean diamond = seed % 3 == 0;
        inventory.armor.set(0, new ItemStack(diamond ? Items.DIAMOND_BOOTS : Items.IRON_BOOTS));
        inventory.armor.set(1, new ItemStack(diamond ? Items.DIAMOND_LEGGINGS : Items.IRON_LEGGINGS));
        inventory.armor.set(2, new ItemStack(diamond ? Items.DIAMOND_CHESTPLATE : Items.IRON_CHESTPLATE));
        inventory.armor.set(3, new ItemStack(diamond ? Items.DIAMOND_HELMET : Items.IRON_HELMET));
        inventory.offhand.set(0, seed % 2 == 0 ? new ItemStack(Items.TORCH, 32) : new ItemStack(Items.SHIELD));
    }

    static void remove(ServerPlayer player) {
        player.getInventory().clearContent();
        player.getAdvancements().stopListening();
    }

    private static final class SilentConnection extends Connection {
        SilentConnection() {
            super(PacketFlow.SERVERBOUND);
        }

        @Override
        public void setListenerForServerboundHandshake(PacketListener listener) {
        }
    }

    /** Like NeoForge's fake player connection: drops every packet. */
    private static final class SilentListener extends ServerGamePacketListenerImpl {
        SilentListener(MinecraftServer server, ServerPlayer player) {
            super(server, new SilentConnection(), player, CommonListenerCookie.createInitial(player.getGameProfile(), false));
        }

        @Override
        public void tick() {
        }

        @Override
        public void send(Packet<?> packet) {
        }

        @Override
        public void send(Packet<?> packet, PacketSendListener listener) {
        }
    }
}
