package com.teamarcadia.arcadiatweaks.neoforge.admin;

import com.teamarcadia.arcadiatweaks.common.config.ArcadiaConfig;
import com.teamarcadia.arcadiatweaks.common.module.ArcadiaPatchVariant;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.List;

public final class ArcadiaAdminMenu extends ChestMenu {

    private static final int ROWS = 4;
    private static final int SIZE = ROWS * 9;

    private final AdminContainer adminContainer;
    private final List<Entry> entries;

    private ArcadiaAdminMenu(int containerId, Inventory inventory, AdminContainer container) {
        super(MenuType.GENERIC_9x4, containerId, inventory, container, ROWS);
        this.adminContainer = container;
        this.entries = createEntries();
        refresh();
    }

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, ignored) -> new ArcadiaAdminMenu(
                        containerId,
                        inventory,
                        new AdminContainer()
                ),
                Component.literal(ArcadiaPatchVariant.menuTitle())
        ));
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < SIZE) {
            if (player instanceof ServerPlayer serverPlayer) {
                handleAdminClick(slotId, button, clickType, serverPlayer);
            }
            return;
        }
        if (clickType == ClickType.QUICK_MOVE || clickType == ClickType.SWAP || clickType == ClickType.QUICK_CRAFT) {
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canDragTo(net.minecraft.world.inventory.Slot slot) {
        return false;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        adminContainer.clearContent();
    }

    private void handleAdminClick(int slotId, int button, ClickType clickType, ServerPlayer player) {
        for (Entry entry : entries) {
            if (entry.slot == slotId) {
                entry.click(button, clickType, player);
                saveAndRefresh();
                return;
            }
        }
    }

    private void saveAndRefresh() {
        ArcadiaConfig.SPEC.save();
        refresh();
        broadcastFullState();
    }

    private void refresh() {
        adminContainer.clearContent();
        for (Entry entry : entries) {
            adminContainer.setItem(entry.slot, entry.stack());
        }
        adminContainer.setChanged();
    }

    private static List<Entry> createEntries() {
        final List<Entry> result = new ArrayList<>();

        int row = 0;
        if (ArcadiaPatchVariant.hasBotany()) {
            addBotanyEntries(result, row++ * 9);
        }
        if (ArcadiaPatchVariant.hasRefinedStorage()) {
            addRefinedStorageEntries(result, row++ * 9);
        }
        if (ArcadiaPatchVariant.hasMekanism()) {
            addMekanismEntries(result, row++ * 9);
        }
        if (ArcadiaPatchVariant.hasWaystones()) {
            addWaystonesEntries(result, row++ * 9);
        }
        result.add(Entry.close(SIZE - 1));

        return result;
    }

    private static void addBotanyEntries(List<Entry> result, int baseSlot) {
        result.add(Entry.header(baseSlot, "BotanyPots", "Patchs BotanyPots"));
        result.add(Entry.bool(baseSlot + 1, "S1 matches cache", ArcadiaConfig.BOTANY.s1MatchesCache));
        result.add(Entry.bool(baseSlot + 2, "S2 tick coalescing", ArcadiaConfig.BOTANY.s2TickCoalescing));
        result.add(Entry.integer(baseSlot + 3, "S2 coalesce N", ArcadiaConfig.BOTANY.s2CoalesceN, 1, 16, 1, 4));
        result.add(Entry.bool(baseSlot + 4, "S3 hopper backoff", ArcadiaConfig.BOTANY.s3HopperBackoff));
        result.add(Entry.integer(baseSlot + 5, "S3 backoff min", ArcadiaConfig.BOTANY.s3HopperBackoffMinTicks, 1, 200, 1, 16));
        result.add(Entry.integer(baseSlot + 6, "S3 backoff max", ArcadiaConfig.BOTANY.s3HopperBackoffMaxTicks, 1, 1200, 8, 64));
        result.add(Entry.bool(baseSlot + 7, "A1 growth ticks cache", ArcadiaConfig.BOTANY.a1RequiredGrowthTicksCache));
        result.add(Entry.bool(baseSlot + 8, "A2 light flags", ArcadiaConfig.BOTANY.a2LightFlagDowngrade));
    }

    private static void addRefinedStorageEntries(List<Entry> result, int baseSlot) {
        result.add(Entry.header(baseSlot, "Refined Storage", "Patchs Refined Storage 2"));
        result.add(Entry.bool(baseSlot + 1, "S1 activeness coalescing", ArcadiaConfig.REFINED_STORAGE.activenessCheckCoalescing));
        result.add(Entry.integer(baseSlot + 2, "S1 interval ticks", ArcadiaConfig.REFINED_STORAGE.activenessCheckInterval, 1, 200, 1, 20));
    }

    private static void addMekanismEntries(List<Entry> result, int baseSlot) {
        result.add(Entry.header(baseSlot, "Mekanism", "Patchs cables et reseaux Mekanism"));
        result.add(Entry.bool(baseSlot + 1, "M1 pull backoff", ArcadiaConfig.MEKANISM.transmitterPullBackoff));
        result.add(Entry.integer(baseSlot + 2, "M1 max ticks", ArcadiaConfig.MEKANISM.transmitterPullBackoffMaxTicks, 1, 200, 1, 20));
        result.add(Entry.bool(baseSlot + 3, "M2 emit backoff", ArcadiaConfig.MEKANISM.networkEmitBackoff));
        result.add(Entry.integer(baseSlot + 4, "M2 max ticks", ArcadiaConfig.MEKANISM.networkEmitBackoffMaxTicks, 1, 200, 1, 20));
        result.add(Entry.bool(baseSlot + 5, "M3 transporter idle", ArcadiaConfig.MEKANISM.logisticalTransporterIdleBackoff));
        result.add(Entry.integer(baseSlot + 6, "M3 max ticks", ArcadiaConfig.MEKANISM.logisticalTransporterIdleBackoffMaxTicks, 1, 200, 1, 20));
    }

    private static void addWaystonesEntries(List<Entry> result, int baseSlot) {
        result.add(Entry.header(baseSlot, "Waystones", "Visibilite team via FTB Teams"));
        result.add(Entry.bool(baseSlot + 1, "W1 team via FTB Teams", ArcadiaConfig.WAYSTONES.ftbTeamsTeamVisibility));
    }

    private static final class Entry {
        private final int slot;
        private final Kind kind;
        private final String label;
        private final ModConfigSpec.BooleanValue boolValue;
        private final ModConfigSpec.IntValue intValue;
        private final int min;
        private final int max;
        private final int step;
        private final int defaultValue;
        private final String description;

        private Entry(
                int slot,
                Kind kind,
                String label,
                ModConfigSpec.BooleanValue boolValue,
                ModConfigSpec.IntValue intValue,
                int min,
                int max,
                int step,
                int defaultValue,
                String description
        ) {
            this.slot = slot;
            this.kind = kind;
            this.label = label;
            this.boolValue = boolValue;
            this.intValue = intValue;
            this.min = min;
            this.max = max;
            this.step = step;
            this.defaultValue = defaultValue;
            this.description = description;
        }

        static Entry header(int slot, String label, String description) {
            return new Entry(slot, Kind.HEADER, label, null, null, 0, 0, 0, 0, description);
        }

        static Entry close(int slot) {
            return new Entry(slot, Kind.CLOSE, "Close", null, null, 0, 0, 0, 0, "Ferme le menu");
        }

        static Entry bool(int slot, String label, ModConfigSpec.BooleanValue value) {
            return new Entry(slot, Kind.BOOLEAN, label, value, null, 0, 0, 0, 0, "Clic: ON/OFF");
        }

        static Entry integer(
                int slot,
                String label,
                ModConfigSpec.IntValue value,
                int min,
                int max,
                int step,
                int defaultValue
        ) {
            return new Entry(slot, Kind.INTEGER, label, null, value, min, max, step, defaultValue,
                    "Gauche: +, Droite: -, Shift: reset");
        }

        ItemStack stack() {
            return switch (kind) {
                case HEADER -> named(Items.BOOK, label, ChatFormatting.GOLD, List.of(description));
                case CLOSE -> named(Items.BARRIER, label, ChatFormatting.RED, List.of(description));
                case BOOLEAN -> booleanStack();
                case INTEGER -> integerStack();
            };
        }

        void click(int button, ClickType clickType, ServerPlayer player) {
            switch (kind) {
                case HEADER -> {
                }
                case CLOSE -> player.closeContainer();
                case BOOLEAN -> {
                    if (clickType == ClickType.PICKUP) {
                        boolValue.set(!boolValue.get());
                    }
                }
                case INTEGER -> {
                    if (clickType == ClickType.QUICK_MOVE) {
                        intValue.set(defaultValue);
                    } else if (clickType == ClickType.PICKUP) {
                        final int direction = button == 1 ? -1 : 1;
                        intValue.set(clamp(intValue.get() + (direction * step), min, max));
                    }
                }
            }
        }

        private ItemStack booleanStack() {
            final boolean enabled = boolValue.get();
            final ChatFormatting color = enabled ? ChatFormatting.GREEN : ChatFormatting.RED;
            final String state = enabled ? "ON" : "OFF";
            return named(
                    enabled ? Items.LIME_DYE : Items.GRAY_DYE,
                    label + ": " + state,
                    color,
                    List.of(description)
            );
        }

        private ItemStack integerStack() {
            return named(
                    Items.REPEATER,
                    label + ": " + intValue.get(),
                    ChatFormatting.AQUA,
                    List.of(description, "Min " + min + " / Max " + max)
            );
        }

        private static ItemStack named(Item item, String name, ChatFormatting color, List<String> lore) {
            final ItemStack stack = new ItemStack(item);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(color));
            stack.set(DataComponents.LORE, new ItemLore(lore.stream()
                    .map(line -> Component.literal(line).withStyle(ChatFormatting.GRAY))
                    .map(Component.class::cast)
                    .toList()));
            return stack;
        }

        private static int clamp(int value, int min, int max) {
            return Math.max(min, Math.min(max, value));
        }
    }

    private enum Kind {
        HEADER,
        CLOSE,
        BOOLEAN,
        INTEGER
    }

    private static final class AdminContainer extends SimpleContainer {
        private AdminContainer() {
            super(SIZE);
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return false;
        }

        @Override
        public boolean canTakeItem(Container target, int slot, ItemStack stack) {
            return false;
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return ItemStack.EMPTY;
        }
    }
}
