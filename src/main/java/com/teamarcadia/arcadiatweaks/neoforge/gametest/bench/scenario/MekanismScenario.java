package com.teamarcadia.arcadiatweaks.neoforge.gametest.bench.scenario;

import com.teamarcadia.arcadiatweaks.common.config.ArcadiaConfig;
import com.teamarcadia.arcadiatweaks.common.modules.mekanism.MekanismConfig;
import com.teamarcadia.arcadiatweaks.neoforge.gametest.bench.BenchScenario;
import com.teamarcadia.arcadiatweaks.neoforge.gametest.bench.BenchSwitches;
import mekanism.api.RelativeSide;
import mekanism.api.chemical.ChemicalStack;
import mekanism.common.content.network.ChemicalNetwork;
import mekanism.common.content.network.EnergyNetwork;
import mekanism.common.content.network.FluidNetwork;
import mekanism.common.content.network.transmitter.LogisticalTransporterBase;
import mekanism.common.content.network.transmitter.Transmitter;
import mekanism.common.content.transporter.TransporterStack;
import mekanism.common.lib.transmitter.ConnectionType;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.registries.MekanismChemicals;
import mekanism.common.tile.TileEntityChemicalTank;
import mekanism.common.tile.TileEntityEnergyCube;
import mekanism.common.tile.TileEntityFluidTank;
import mekanism.common.tile.component.config.ConfigInfo;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.prefab.TileEntityConfigurableMachine;
import mekanism.common.tile.transmitter.TileEntityLogisticalTransporterBase;
import mekanism.common.tile.transmitter.TileEntityTransmitter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mekanism idle networks: Universal Cable, Mechanical Pipe and Pressurized Tube lines that hold
 * content but are connected only to full acceptors (energy cubes, fluid tanks, chemical tanks that
 * accept nothing: the network emit back-off case), with sides in PULL mode against empty sources
 * (the pull back-off case), and Logistical Transporter lines along full chests carrying item stacks
 * that have no destination (the idle path back-off case, the hot path of the 2026-05 Server 1
 * profile).
 *
 * @author vyrriox
 */
public final class MekanismScenario implements BenchScenario {

    private static final int LENGTH = 48;
    private static final int ENERGY_LINES = 6;
    private static final int FLUID_LINES = 6;
    private static final int CHEMICAL_LINES = 6;
    private static final int TRANSPORTER_LINES = 12;
    private static final int STACKS_PER_TRANSPORTER_LINE = 32;
    private static final int LINE_SPACING = 4;

    private enum Kind { ENERGY, FLUID, CHEMICAL, ITEM }

    private record Line(Kind kind, int z, List<BlockPos> transmitters) {}

    private final List<BlockPos> placed = new ArrayList<>();
    private final List<Line> lines = new ArrayList<>();
    private BlockPos origin;
    private int nextStackId = 1_000_000;
    private int fullAcceptors;
    private int pullSides;
    private int linesWithNetwork;
    private int droppedStacks;
    private int stacksInjected;

    @Override
    public String id() {
        return "mekanism";
    }

    @Override
    public String description() {
        return "Mekanism idle networks: " + ENERGY_LINES + " cable, " + FLUID_LINES + " pipe and " + CHEMICAL_LINES
                + " tube lines of " + LENGTH + " on full acceptors with PULL sides on empty sources, and "
                + TRANSPORTER_LINES + " Logistical Transporter lines of " + LENGTH + " along full chests carrying "
                + (TRANSPORTER_LINES * STACKS_PER_TRANSPORTER_LINE) + " stacks with no destination.";
    }

    @Override
    public List<BenchSwitches.Switch> switches() {
        final MekanismConfig m = ArcadiaConfig.MEKANISM;
        return List.of(
                BenchSwitches.Switch.of("modules.mekanism", ArcadiaConfig.MODULE_MEKANISM_ENABLED),
                BenchSwitches.Switch.of("mekanism.transmitter_pull_backoff_enabled", m.transmitterPullBackoff),
                BenchSwitches.Switch.of("mekanism.transmitter_pull_backoff_max_ticks", m.transmitterPullBackoffMaxTicks),
                BenchSwitches.Switch.of("mekanism.network_emit_backoff_enabled", m.networkEmitBackoff),
                BenchSwitches.Switch.of("mekanism.network_emit_backoff_max_ticks", m.networkEmitBackoffMaxTicks),
                BenchSwitches.Switch.of("mekanism.logistical_transporter_idle_backoff_enabled", m.logisticalTransporterIdleBackoff),
                BenchSwitches.Switch.of("mekanism.logistical_transporter_idle_backoff_max_ticks", m.logisticalTransporterIdleBackoffMaxTicks)
        );
    }

    @Override
    public int footprint() {
        return Math.max(LENGTH / 2, (ENERGY_LINES + FLUID_LINES + CHEMICAL_LINES + TRANSPORTER_LINES) * LINE_SPACING / 2) + 4;
    }

    @Override
    public void build(ServerLevel level, BlockPos center) {
        this.origin = center;
        final int total = ENERGY_LINES + FLUID_LINES + CHEMICAL_LINES + TRANSPORTER_LINES;
        int z = -total * LINE_SPACING / 2;
        for (int i = 0; i < ENERGY_LINES; i++, z += LINE_SPACING) buildLine(level, Kind.ENERGY, z);
        for (int i = 0; i < FLUID_LINES; i++, z += LINE_SPACING) buildLine(level, Kind.FLUID, z);
        for (int i = 0; i < CHEMICAL_LINES; i++, z += LINE_SPACING) buildLine(level, Kind.CHEMICAL, z);
        for (int i = 0; i < TRANSPORTER_LINES; i++, z += LINE_SPACING) buildLine(level, Kind.ITEM, z);
    }

    private void buildLine(ServerLevel level, Kind kind, int z) {
        final Block transmitter = block(switch (kind) {
            case ENERGY -> "basic_universal_cable";
            case FLUID -> "basic_mechanical_pipe";
            case CHEMICAL -> "basic_pressurized_tube";
            case ITEM -> "basic_logistical_transporter";
        });
        final Block acceptor = switch (kind) {
            case ENERGY -> block("basic_energy_cube");
            case FLUID -> block("basic_fluid_tank");
            case CHEMICAL -> block("basic_chemical_tank");
            case ITEM -> Blocks.CHEST;
        };
        final List<BlockPos> line = new ArrayList<>();
        for (int x = -LENGTH / 2; x < LENGTH / 2; x++) {
            final BlockPos pos = origin.offset(x, 1, z);
            place(level, pos, transmitter);
            line.add(pos);
        }
        for (int x = -LENGTH / 2; x < LENGTH / 2; x++) {
            final BlockPos pos = origin.offset(x, 1, z);
            if ((x & 1) == 0) {
                final BlockPos full = pos.south();
                place(level, full, acceptor);
                fillFull(level.getBlockEntity(full), kind);
                fullAcceptors++;
            }
            if (kind != Kind.ITEM && Math.floorMod(x, 4) == 1) {
                final BlockPos source = pos.north();
                place(level, source, acceptor);
                setAllSides(level.getBlockEntity(source), kind, DataType.OUTPUT);
                if (level.getBlockEntity(pos) instanceof TileEntityTransmitter tile) {
                    final Transmitter<?, ?, ?> t = tile.getTransmitter();
                    t.setConnectionTypeRaw(Direction.NORTH, ConnectionType.PULL);
                    t.onModeChange(Direction.NORTH);
                    t.refreshConnections();
                    t.notifyTileChange();
                    pullSides++;
                }
            }
        }
        lines.add(new Line(kind, z, line));
    }

    private void fillFull(BlockEntity be, Kind kind) {
        switch (kind) {
            case ENERGY -> {
                if (be instanceof TileEntityEnergyCube cube) {
                    setAllSides(cube, kind, DataType.INPUT);
                    cube.getEnergyContainer().setEnergy(cube.getEnergyContainer().getMaxEnergy());
                }
            }
            case FLUID -> {
                if (be instanceof TileEntityFluidTank tank) {
                    tank.fluidTank.setStack(new FluidStack(Fluids.WATER, tank.fluidTank.getCapacity()));
                }
            }
            case CHEMICAL -> {
                if (be instanceof TileEntityChemicalTank tank) {
                    setAllSides(tank, kind, DataType.INPUT);
                    tank.getChemicalTank().setStack(new ChemicalStack(MekanismChemicals.HYDROGEN, tank.getChemicalTank().getCapacity()));
                }
            }
            case ITEM -> {
                if (be instanceof Container chest) {
                    for (int s = 0; s < chest.getContainerSize(); s++) {
                        chest.setItem(s, new ItemStack(Items.COBBLESTONE, 64));
                    }
                }
            }
        }
    }

    private static void setAllSides(BlockEntity be, Kind kind, DataType type) {
        if (!(be instanceof TileEntityConfigurableMachine machine)) return;
        final TransmissionType transmission = kind == Kind.ENERGY ? TransmissionType.ENERGY : TransmissionType.CHEMICAL;
        final ConfigInfo info = machine.getConfig().getConfig(transmission);
        if (info == null) return;
        for (RelativeSide side : RelativeSide.values()) {
            if (info.setDataType(type, side)) {
                machine.getConfig().sideChanged(transmission, side);
            }
        }
    }

    @Override
    public boolean ready(ServerLevel level, int ticksSinceBuild) {
        if (ticksSinceBuild < 40) return false;
        linesWithNetwork = 0;
        for (Line line : lines) {
            if (level.getBlockEntity(line.transmitters().get(0)) instanceof TileEntityTransmitter tile
                    && tile.getTransmitter().hasTransmitterNetwork()) {
                linesWithNetwork++;
            }
        }
        return linesWithNetwork == lines.size() || ticksSinceBuild > 200;
    }

    @Override
    public void beforePhase(ServerLevel level, boolean patchesOn) {
        // Remove items the transporters dropped, then top every line back up to the same load.
        final AABB box = new AABB(origin).inflate(footprint() + 4, 8, footprint() + 4);
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box)) {
            droppedStacks++;
            item.discard();
        }
        for (Line line : lines) {
            final BlockEntity be = level.getBlockEntity(line.transmitters().get(0));
            if (!(be instanceof TileEntityTransmitter tile) || !tile.getTransmitter().hasTransmitterNetwork()) continue;
            switch (line.kind()) {
                case ENERGY -> {
                    final EnergyNetwork net = (EnergyNetwork) tile.getTransmitter().getTransmitterNetwork();
                    net.energyContainer.setEnergy(net.energyContainer.getMaxEnergy() / 2);
                }
                case FLUID -> {
                    final FluidNetwork net = (FluidNetwork) tile.getTransmitter().getTransmitterNetwork();
                    net.fluidTank.setStack(new FluidStack(Fluids.WATER, Math.max(1, net.fluidTank.getCapacity() / 2)));
                }
                case CHEMICAL -> {
                    final ChemicalNetwork net = (ChemicalNetwork) tile.getTransmitter().getTransmitterNetwork();
                    net.chemicalTank.setStack(new ChemicalStack(MekanismChemicals.HYDROGEN, Math.max(1, net.chemicalTank.getCapacity() / 2)));
                }
                case ITEM -> topUpTransporters(level, line);
            }
        }
    }

    private void topUpTransporters(ServerLevel level, Line line) {
        int present = 0;
        for (BlockPos p : line.transmitters()) {
            if (level.getBlockEntity(p) instanceof TileEntityLogisticalTransporterBase t) {
                present += t.getTransmitter().getTransit().size();
            }
        }
        final int step = Math.max(1, line.transmitters().size() / STACKS_PER_TRANSPORTER_LINE);
        for (int i = 0; present < STACKS_PER_TRANSPORTER_LINE && i < line.transmitters().size(); i += step) {
            final BlockPos p = line.transmitters().get(i);
            if (!(level.getBlockEntity(p) instanceof TileEntityLogisticalTransporterBase tile)) continue;
            final LogisticalTransporterBase transporter = tile.getTransmitter();
            // Home is the full chest beside the line, so the stack can go neither home nor anywhere.
            final TransporterStack stack = transporter.createInsertStack(p.south().asLong(), null);
            stack.itemStack = new ItemStack(Items.DIRT, 8);
            transporter.addStack(nextStackId++, stack);
            present++;
            stacksInjected++;
        }
    }

    @Override
    public Map<String, Double> afterPhase(ServerLevel level, boolean patchesOn) {
        int stacks = 0;
        for (Line line : lines) {
            if (line.kind() != Kind.ITEM) continue;
            for (BlockPos p : line.transmitters()) {
                if (level.getBlockEntity(p) instanceof TileEntityLogisticalTransporterBase t) {
                    stacks += t.getTransmitter().getTransit().size();
                }
            }
        }
        return Map.of("transporterStacksAtEnd", (double) stacks);
    }

    @Override
    public Map<String, Object> size() {
        final Map<String, Object> m = new LinkedHashMap<>();
        m.put("lineLength", LENGTH);
        m.put("universalCableLines", ENERGY_LINES);
        m.put("mechanicalPipeLines", FLUID_LINES);
        m.put("pressurizedTubeLines", CHEMICAL_LINES);
        m.put("logisticalTransporterLines", TRANSPORTER_LINES);
        m.put("transmitters", lines.size() * LENGTH);
        m.put("fullAcceptors", fullAcceptors);
        m.put("pullSidesOnEmptySources", pullSides);
        m.put("idleStacksTarget", TRANSPORTER_LINES * STACKS_PER_TRANSPORTER_LINE);
        m.put("idleStacksInjectedTotal", stacksInjected);
        m.put("droppedStacksRemoved", droppedStacks);
        m.put("linesWithNetwork", linesWithNetwork);
        return m;
    }

    @Override
    public void teardown(ServerLevel level) {
        for (int i = placed.size() - 1; i >= 0; i--) {
            final BlockPos p = placed.get(i);
            if (level.getBlockEntity(p) instanceof Container c) c.clearContent();
            level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
        }
        placed.clear();
        if (origin != null) {
            final AABB box = new AABB(origin).inflate(footprint() + 4, 8, footprint() + 4);
            level.getEntitiesOfClass(ItemEntity.class, box).forEach(ItemEntity::discard);
        }
    }

    private void place(ServerLevel level, BlockPos pos, Block block) {
        level.setBlock(pos, block.defaultBlockState(), 3);
        placed.add(pos);
    }

    private static Block block(String path) {
        final ResourceLocation id = ResourceLocation.fromNamespaceAndPath("mekanism", path);
        final Block b = BuiltInRegistries.BLOCK.get(id);
        if (b == Blocks.AIR) throw new IllegalStateException("missing block " + id);
        return b;
    }
}
