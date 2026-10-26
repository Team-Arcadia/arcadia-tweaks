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

/**
 * Server-side admin menu (/arcadia_tweaks gui): a 9x6 chest, one row per module (module switch in
 * the first slot, its settings after it), five rows per page, and a navigation row at the bottom.
 * Only modules whose target mod is present are listed. Every change is saved to the config file at
 * once and read by the patches on their next run.
 *
 * @author vyrriox
 */
public final class ArcadiaAdminMenu extends ChestMenu {

    private static final int ROWS = 6;
    private static final int SIZE = ROWS * 9;
    private static final int SECTIONS_PER_PAGE = ROWS - 1;
    private static final int NAV_ROW = (ROWS - 1) * 9;
    private static final int SLOT_PREVIOUS = NAV_ROW;
    private static final int SLOT_DEFAULTS = NAV_ROW + 2;
    private static final int SLOT_PAGE = NAV_ROW + 4;
    private static final int SLOT_CLOSE = NAV_ROW + 6;
    private static final int SLOT_NEXT = NAV_ROW + 8;

    private final AdminContainer adminContainer;
    private final List<Section> sections;
    private final int pageCount;
    private int page;
    private boolean defaultsArmed;
    private final List<Entry> visibleEntries = new ArrayList<>();

    private ArcadiaAdminMenu(int containerId, Inventory inventory, AdminContainer container) {
        super(MenuType.GENERIC_9x6, containerId, inventory, container, ROWS);
        this.adminContainer = container;
        this.sections = createSections();
        this.pageCount = Math.max(1, (sections.size() + SECTIONS_PER_PAGE - 1) / SECTIONS_PER_PAGE);
        refresh();
    }

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, ignored) -> new ArcadiaAdminMenu(containerId, inventory, new AdminContainer()),
                Component.literal(ArcadiaPatchVariant.menuTitle())
        ));
    }

    /** Builds the menu without opening it, for GameTests driving clicks directly. */
    public static ArcadiaAdminMenu createDetached(int containerId, Inventory inventory) {
        return new ArcadiaAdminMenu(containerId, inventory, new AdminContainer());
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
        if (slotId >= NAV_ROW) {
            handleNavigation(slotId, clickType, player);
            return;
        }
        for (Entry entry : visibleEntries) {
            if (entry.slot == slotId) {
                if (entry.click(button, clickType)) {
                    defaultsArmed = false;
                    ArcadiaConfig.SPEC.save();
                }
                refreshAndBroadcast();
                return;
            }
        }
    }

    private void handleNavigation(int slotId, ClickType clickType, ServerPlayer player) {
        if (clickType != ClickType.PICKUP) {
            return;
        }
        switch (slotId) {
            case SLOT_PREVIOUS -> {
                if (page > 0) {
                    page--;
                }
            }
            case SLOT_NEXT -> {
                if (page < pageCount - 1) {
                    page++;
                }
            }
            case SLOT_CLOSE -> {
                player.closeContainer();
                return;
            }
            case SLOT_DEFAULTS -> {
                if (defaultsArmed) {
                    resetAllToDefaults();
                    ArcadiaConfig.SPEC.save();
                    defaultsArmed = false;
                } else {
                    defaultsArmed = true;
                }
            }
            default -> {
                return;
            }
        }
        if (slotId != SLOT_DEFAULTS) {
            defaultsArmed = false;
        }
        refreshAndBroadcast();
    }

    private void resetAllToDefaults() {
        for (Section section : sections) {
            if (section.toggle != null) {
                section.toggle.resetToDefault();
            }
            for (Entry entry : section.settings) {
                entry.resetToDefault();
            }
        }
    }

    private void refreshAndBroadcast() {
        refresh();
        broadcastFullState();
    }

    private void refresh() {
        adminContainer.clearContent();
        visibleEntries.clear();
        final int first = page * SECTIONS_PER_PAGE;
        for (int row = 0; row < SECTIONS_PER_PAGE && first + row < sections.size(); row++) {
            final Section section = sections.get(first + row);
            final int base = row * 9;
            final Entry head = section.toggle != null
                    ? section.toggle.at(base)
                    : Entry.header(base, section.name, section.description);
            visibleEntries.add(head);
            for (int i = 0; i < section.settings.size() && i < 8; i++) {
                visibleEntries.add(section.settings.get(i).at(base + 1 + i));
            }
        }
        for (Entry entry : visibleEntries) {
            adminContainer.setItem(entry.slot, entry.stack());
        }
        if (page > 0) {
            adminContainer.setItem(SLOT_PREVIOUS, named(Items.ARROW, "Previous page", ChatFormatting.YELLOW,
                    List.of("Page " + page + " / " + pageCount)));
        }
        if (page < pageCount - 1) {
            adminContainer.setItem(SLOT_NEXT, named(Items.ARROW, "Next page", ChatFormatting.YELLOW,
                    List.of("Page " + (page + 2) + " / " + pageCount)));
        }
        adminContainer.setItem(SLOT_PAGE, named(Items.PAPER, "Page " + (page + 1) + " / " + pageCount,
                ChatFormatting.WHITE, List.of("Clic gauche: +, droit: -, Shift: valeur par defaut")));
        adminContainer.setItem(SLOT_DEFAULTS, defaultsArmed
                ? named(Items.TNT, "Confirm: recommended defaults", ChatFormatting.RED,
                        List.of("Clic encore pour tout remettre aux valeurs recommandees"))
                : named(Items.NETHER_STAR, "Recommended defaults", ChatFormatting.LIGHT_PURPLE,
                        List.of("Remet tous les reglages aux valeurs recommandees", "Demande une confirmation")));
        adminContainer.setItem(SLOT_CLOSE, named(Items.BARRIER, "Close", ChatFormatting.RED, List.of("Ferme le menu")));
        adminContainer.setChanged();
    }

    private static List<Section> createSections() {
        final List<Section> result = new ArrayList<>();
        if (ArcadiaPatchVariant.hasBotany()) {
            result.add(new Section("BotanyPots", "Patchs BotanyPots", ArcadiaConfig.MODULE_BOTANY_ENABLED, List.of(
                    Entry.bool("S1 matches cache", ArcadiaConfig.BOTANY.s1MatchesCache),
                    Entry.bool("S2 tick coalescing", ArcadiaConfig.BOTANY.s2TickCoalescing),
                    Entry.integer("S2 coalesce N", ArcadiaConfig.BOTANY.s2CoalesceN, 1),
                    Entry.bool("S3 hopper backoff", ArcadiaConfig.BOTANY.s3HopperBackoff),
                    Entry.integer("S3 backoff min", ArcadiaConfig.BOTANY.s3HopperBackoffMinTicks, 1),
                    Entry.integer("S3 backoff max", ArcadiaConfig.BOTANY.s3HopperBackoffMaxTicks, 8),
                    Entry.bool("A1 growth ticks cache", ArcadiaConfig.BOTANY.a1RequiredGrowthTicksCache),
                    Entry.bool("A2 light flags", ArcadiaConfig.BOTANY.a2LightFlagDowngrade))));
        }
        if (ArcadiaPatchVariant.hasRefinedStorage()) {
            result.add(new Section("Refined Storage", "Patchs Refined Storage 2", ArcadiaConfig.MODULE_REFINED_STORAGE_ENABLED, List.of(
                    Entry.bool("S1 activeness coalescing", ArcadiaConfig.REFINED_STORAGE.activenessCheckCoalescing),
                    Entry.integer("S1 interval ticks", ArcadiaConfig.REFINED_STORAGE.activenessCheckInterval, 1),
                    Entry.bool("S2 single component lookup", ArcadiaConfig.REFINED_STORAGE.singleComponentLookup),
                    Entry.bool("S3 disk save cache", ArcadiaConfig.REFINED_STORAGE.storageSaveCache),
                    Entry.integer("S3 verify every N saves", ArcadiaConfig.REFINED_STORAGE.storageSaveCacheVerifyInterval, 5),
                    Entry.bool("S4 quiet debug logs", ArcadiaConfig.REFINED_STORAGE.quietHotDebugLogs))));
        }
        if (ArcadiaPatchVariant.hasMekanism()) {
            result.add(new Section("Mekanism", "Patchs cables et reseaux Mekanism", ArcadiaConfig.MODULE_MEKANISM_ENABLED, List.of(
                    Entry.bool("M1 pull backoff", ArcadiaConfig.MEKANISM.transmitterPullBackoff),
                    Entry.integer("M1 max ticks", ArcadiaConfig.MEKANISM.transmitterPullBackoffMaxTicks, 1),
                    Entry.bool("M2 emit backoff", ArcadiaConfig.MEKANISM.networkEmitBackoff),
                    Entry.integer("M2 max ticks", ArcadiaConfig.MEKANISM.networkEmitBackoffMaxTicks, 1),
                    Entry.bool("M3 transporter idle", ArcadiaConfig.MEKANISM.logisticalTransporterIdleBackoff),
                    Entry.integer("M3 max ticks", ArcadiaConfig.MEKANISM.logisticalTransporterIdleBackoffMaxTicks, 1))));
        }
        if (ArcadiaPatchVariant.hasWaystones()) {
            result.add(new Section("Waystones", "Visibilite team via FTB Teams", ArcadiaConfig.MODULE_WAYSTONES_ENABLED, List.of(
                    Entry.bool("W1 team via FTB Teams", ArcadiaConfig.WAYSTONES.ftbTeamsTeamVisibility))));
        }
        result.add(new Section("Safety guards", "Gardes-fous du serveur", (ModConfigSpec.BooleanValue) null, List.of(
                Entry.bool("Entity move guard", ArcadiaConfig.ENTITY_MOVE_GUARD_ENABLED),
                Entry.decimal("Move guard limit", ArcadiaConfig.ENTITY_MOVE_GUARD_LIMIT, 10.0),
                Entry.bool("Runic Forge level lock", ArcadiaConfig.RUNIC_FORGE_LOCK_ENABLED))));
        return result;
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

    /** One menu row: a module switch (or a plain header) and up to eight settings. */
    private record Section(String name, String description, Entry toggle, List<Entry> settings) {

        Section(String name, String description, ModConfigSpec.BooleanValue moduleSwitch, List<Entry> settings) {
            this(name, description, moduleSwitch == null ? null : Entry.module(name, description, moduleSwitch), settings);
        }
    }

    private static final class Entry {
        private final int slot;
        private final Kind kind;
        private final String label;
        private final String description;
        private final ModConfigSpec.BooleanValue boolValue;
        private final ModConfigSpec.IntValue intValue;
        private final ModConfigSpec.DoubleValue doubleValue;
        private final double step;

        private Entry(int slot, Kind kind, String label, String description, ModConfigSpec.BooleanValue boolValue,
                      ModConfigSpec.IntValue intValue, ModConfigSpec.DoubleValue doubleValue, double step) {
            this.slot = slot;
            this.kind = kind;
            this.label = label;
            this.description = description;
            this.boolValue = boolValue;
            this.intValue = intValue;
            this.doubleValue = doubleValue;
            this.step = step;
        }

        static Entry header(int slot, String label, String description) {
            return new Entry(slot, Kind.HEADER, label, description, null, null, null, 0);
        }

        static Entry module(String label, String description, ModConfigSpec.BooleanValue value) {
            return new Entry(-1, Kind.MODULE, label, description, value, null, null, 0);
        }

        static Entry bool(String label, ModConfigSpec.BooleanValue value) {
            return new Entry(-1, Kind.BOOLEAN, label, "Clic: ON/OFF", value, null, null, 0);
        }

        static Entry integer(String label, ModConfigSpec.IntValue value, int step) {
            return new Entry(-1, Kind.INTEGER, label, "Gauche: +, Droite: -, Shift: defaut", null, value, null, step);
        }

        static Entry decimal(String label, ModConfigSpec.DoubleValue value, double step) {
            return new Entry(-1, Kind.DECIMAL, label, "Gauche: +, Droite: -, Shift: defaut", null, null, value, step);
        }

        Entry at(int newSlot) {
            return new Entry(newSlot, kind, label, description, boolValue, intValue, doubleValue, step);
        }

        void resetToDefault() {
            switch (kind) {
                case MODULE, BOOLEAN -> boolValue.set(boolValue.getDefault());
                case INTEGER -> intValue.set(intValue.getDefault());
                case DECIMAL -> doubleValue.set(doubleValue.getDefault());
                case HEADER -> {
                }
            }
        }

        /** Applies a click, returns true when a config value changed. */
        boolean click(int button, ClickType clickType) {
            switch (kind) {
                case MODULE, BOOLEAN -> {
                    if (clickType == ClickType.PICKUP) {
                        boolValue.set(!boolValue.get());
                        return true;
                    }
                }
                case INTEGER -> {
                    if (clickType == ClickType.QUICK_MOVE) {
                        intValue.set(intValue.getDefault());
                        return true;
                    }
                    if (clickType == ClickType.PICKUP) {
                        final int direction = button == 1 ? -1 : 1;
                        intValue.set(clampInt(intValue.get() + direction * (int) step, intValue));
                        return true;
                    }
                }
                case DECIMAL -> {
                    if (clickType == ClickType.QUICK_MOVE) {
                        doubleValue.set(doubleValue.getDefault());
                        return true;
                    }
                    if (clickType == ClickType.PICKUP) {
                        final double direction = button == 1 ? -1 : 1;
                        doubleValue.set(clampDouble(doubleValue.get() + direction * step, doubleValue));
                        return true;
                    }
                }
                case HEADER -> {
                }
            }
            return false;
        }

        ItemStack stack() {
            return switch (kind) {
                case HEADER -> named(Items.BOOK, label, ChatFormatting.GOLD, List.of(description));
                case MODULE -> {
                    final boolean on = boolValue.get();
                    yield named(on ? Items.ENCHANTED_BOOK : Items.BOOK, label + ": " + (on ? "ON" : "OFF"),
                            on ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY,
                            List.of(description, "Clic: active/desactive tout le module"));
                }
                case BOOLEAN -> {
                    final boolean on = boolValue.get();
                    yield named(on ? Items.LIME_DYE : Items.GRAY_DYE, label + ": " + (on ? "ON" : "OFF"),
                            on ? ChatFormatting.GREEN : ChatFormatting.RED,
                            List.of(description, "Defaut: " + (boolValue.getDefault() ? "ON" : "OFF")));
                }
                case INTEGER -> named(Items.REPEATER, label + ": " + intValue.get(), ChatFormatting.AQUA,
                        List.of(description, "Defaut: " + intValue.getDefault()));
                case DECIMAL -> named(Items.COMPARATOR, label + ": " + doubleValue.get(), ChatFormatting.AQUA,
                        List.of(description, "Defaut: " + doubleValue.getDefault()));
            };
        }

        private static int clampInt(int value, ModConfigSpec.IntValue spec) {
            final var range = spec.getSpec().getRange();
            if (range == null) {
                return value;
            }
            final int min = ((Number) range.getMin()).intValue();
            final int max = ((Number) range.getMax()).intValue();
            return Math.max(min, Math.min(max, value));
        }

        private static double clampDouble(double value, ModConfigSpec.DoubleValue spec) {
            final var range = spec.getSpec().getRange();
            if (range == null) {
                return value;
            }
            final double min = ((Number) range.getMin()).doubleValue();
            final double max = ((Number) range.getMax()).doubleValue();
            return Math.max(min, Math.min(max, value));
        }
    }

    private enum Kind {
        HEADER,
        MODULE,
        BOOLEAN,
        INTEGER,
        DECIMAL
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
