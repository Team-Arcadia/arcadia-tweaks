package com.teamarcadia.arcadiatweaks.neoforge.gametest;

import com.mcwdoors.kikoz.objects.GarageDoor;
import com.mojang.authlib.GameProfile;
import com.teamarcadia.arcadiatweaks.ArcadiaTweaks;
import com.teamarcadia.arcadiatweaks.common.config.ArcadiaConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The Macaw's Doors garage door guard. A small level that can refuse writes, as Bobby's cached chunks do, drives
 * the real {@code toggleDoor}: without the guard two refused doors overflow the stack, as in the crash report,
 * and with it they stop. Every world without a refused write must see the exact same writes either way.
 *
 * @author vyrriox
 */
@GameTestHolder(ArcadiaTweaks.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GarageDoorGameTests {

    private static final String TEMPLATE = "empty";
    private static final String BATCH = ArcadiaTweaks.MOD_ID;

    private GarageDoorGameTests() {}

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void twoRefusedDoorsOverflowOnlyWithoutTheGuard(GameTestHelper helper) {
        final BlockPos a = new BlockPos(0, 64, 0);
        final BlockPos b = a.east();
        for (boolean open : new boolean[]{true, false}) {
            final DoorLevel level = DoorLevel.create();
            level.put(a, door().defaultBlockState().setValue(GarageDoor.OPEN, !open));
            level.put(b, door().defaultBlockState().setValue(GarageDoor.OPEN, !open));
            level.refuse(a);
            level.refuse(b);

            helper.assertTrue(with(false, () -> overflows(level, a, open)), "Without the guard two refused doors should overflow the stack, open " + open);
            level.writes.clear();
            helper.assertTrue(!with(true, () -> overflows(level, a, open)), "With the guard two refused doors should stop, open " + open);
            // The top of the clicked door, and when closing the 4 cells of air above the floor.
            helper.assertValueEqual(level.writes.size(), open ? 1 : 5, "writes tried with the guard, open " + open);
            helper.assertValueEqual(level.blocks.size(), 2, "nothing was written, open " + open);
            helper.assertValueEqual(level.blocks.get(a).getValue(GarageDoor.OPEN), !open, "the refused door keeps its state");
            helper.assertValueEqual(level.blocks.get(b).getValue(GarageDoor.OPEN), !open, "the door next to it keeps its state");
        }
        helper.succeed();
    }

    /** A click on a door that accepts, next to doors that refuse: the first keeps its new state, nothing overflows. */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void aDoorThatAcceptsStillChangesNextToRefusedOnes(GameTestHelper helper) {
        final BlockPos a = new BlockPos(0, 64, 0);
        final BlockPos b = a.east();
        final BlockPos c = b.east();
        final DoorLevel level = DoorLevel.create();
        for (BlockPos pos : List.of(a, b, c)) {
            level.put(pos, door().defaultBlockState().setValue(GarageDoor.OPEN, false));
        }
        level.refuse(b);
        level.refuse(c);
        helper.assertTrue(with(false, () -> overflows(level, a, true)), "Without the guard the two refused doors should overflow the stack");
        level.put(a, door().defaultBlockState().setValue(GarageDoor.OPEN, false));
        helper.assertTrue(!with(true, () -> overflows(level, a, true)), "With the guard nothing should overflow");
        helper.assertValueEqual(level.blocks.get(a).getValue(GarageDoor.OPEN), true, "the door that accepts opens");
        helper.assertValueEqual(level.blocks.get(b).getValue(GarageDoor.OPEN), false, "the refused door stays closed");
        helper.succeed();
    }

    /** One refused door alone never overflowed: it must still answer without an error. */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void aSingleRefusedDoorChangesNothing(GameTestHelper helper) {
        final BlockPos a = new BlockPos(0, 64, 0);
        for (boolean guard : new boolean[]{false, true}) {
            final DoorLevel level = DoorLevel.create();
            level.put(a, door().defaultBlockState().setValue(GarageDoor.OPEN, false));
            level.refuse(a);
            helper.assertTrue(!with(guard, () -> overflows(level, a, true)), "A single refused door never overflows, guard " + guard);
            helper.assertValueEqual(level.blocks.get(a).getValue(GarageDoor.OPEN), false, "the door keeps its state, guard " + guard);
        }
        helper.succeed();
    }

    /** Closing a door above the void: the fill stops at the bottom of the world instead of running on below it. */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void closingADoorAboveTheVoidStops(GameTestHelper helper) {
        final BlockPos top = new BlockPos(0, 64, 0);
        final DoorLevel unguarded = DoorLevel.create();
        unguarded.floor = false;
        unguarded.writeBudget = 5000;
        unguarded.put(top, door().defaultBlockState().setValue(GarageDoor.OPEN, true));
        helper.assertTrue(with(false, () -> runsOutOfBudget(unguarded, top, false)), "Without the guard the fill should run on below the world");

        final DoorLevel level = DoorLevel.create();
        level.floor = false;
        level.writeBudget = 5000;
        level.put(top, door().defaultBlockState().setValue(GarageDoor.OPEN, true));
        helper.assertTrue(!with(true, () -> runsOutOfBudget(level, top, false)), "With the guard the fill should stop at the bottom of the world");
        // The top, then every position from 63 down to the bottom of the world, -64.
        helper.assertValueEqual(level.writes.size(), 1 + 128, "writes while closing above the void");
        helper.assertValueEqual(level.getBlockState(top.atY(-64)).is(door()), true, "the door reaches the bottom of the world");
        helper.succeed();
    }

    /** No refused write: the guard must not change a single write, in any shape of doors. */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void everyWorldWithoutARefusedWriteSeesTheSameWrites(GameTestHelper helper) {
        final Random random = new Random(20261106L);
        int doorsMoved = 0;
        for (int round = 0; round < 1000; round++) {
            final Map<BlockPos, BlockState> world = randomDoors(random);
            final List<BlockPos> doors = new ArrayList<>(new TreeMap<>(world).keySet());
            final BlockPos clicked = doors.get(random.nextInt(doors.size()));
            final boolean open = !world.get(clicked).getValue(GarageDoor.OPEN);

            final DoorLevel off = DoorLevel.create();
            final DoorLevel on = DoorLevel.create();
            off.blocks.putAll(world);
            on.blocks.putAll(world);
            final boolean overflowOff = with(false, () -> overflows(off, clicked, open));
            final boolean overflowOn = with(true, () -> overflows(on, clicked, open));

            helper.assertTrue(!overflowOff && !overflowOn, "No overflow without a refused write, round " + round);
            helper.assertValueEqual(on.writes, off.writes, "writes, round " + round);
            helper.assertValueEqual(new TreeMap<>(on.blocks).toString(), new TreeMap<>(off.blocks).toString(), "final doors, round " + round);
            doorsMoved += off.writes.size();
        }
        helper.assertTrue(doorsMoved > 1000, "The random worlds should really move doors");
        helper.succeed();
    }

    /** Real doors in the real level, clicked by a player: same result with the guard on and off. */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 40)
    public static void realDoorsOpenAndCloseTheSame(GameTestHelper helper) {
        final ServerLevel level = helper.getLevel();
        final ServerPlayer player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "door-test"), ClientInformation.createDefault());
        try {
            final List<String> off = with(false, () -> clickReal(helper, player));
            final List<String> on = with(true, () -> clickReal(helper, player));
            helper.assertValueEqual(on, off, "real doors with the guard on and off");
            helper.assertTrue(off.get(0).contains("open=true") && !off.get(0).contains("open=false"), "All the top doors should open together: " + off.get(0));
            helper.assertTrue(off.get(1).contains("open=false") && !off.get(1).contains("open=true"), "All the doors should close together: " + off.get(1));
        } finally {
            player.getAdvancements().stopListening();
        }
        helper.succeed();
    }

    private static List<String> clickReal(GameTestHelper helper, ServerPlayer player) {
        final List<BlockPos> tops = List.of(new BlockPos(1, 3, 1), new BlockPos(2, 3, 1), new BlockPos(3, 3, 1));
        final BlockState closed = door().defaultBlockState().setValue(GarageDoor.OPEN, false).setValue(GarageDoor.FACING, Direction.NORTH);
        for (BlockPos top : tops) {
            helper.setBlock(top, closed);
        }
        final List<String> out = new ArrayList<>();
        click(helper, player, tops.get(1));
        out.add(snapshot(helper, tops));
        click(helper, player, tops.get(1));
        out.add(snapshot(helper, tops));
        for (BlockPos top : tops) {
            for (int y = top.getY(); y >= -2; y--) {
                final BlockPos cell = new BlockPos(top.getX(), y, top.getZ());
                if (helper.getLevel().getBlockState(helper.absolutePos(cell)).is(door())) {
                    helper.setBlock(cell, Blocks.AIR);
                }
            }
        }
        return out;
    }

    private static void click(GameTestHelper helper, ServerPlayer player, BlockPos relative) {
        final BlockPos pos = helper.absolutePos(relative);
        final BlockState state = helper.getLevel().getBlockState(pos);
        state.useItemOn(ItemStack.EMPTY, helper.getLevel(), player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false));
    }

    private static String snapshot(GameTestHelper helper, List<BlockPos> tops) {
        final List<String> out = new ArrayList<>();
        for (BlockPos top : tops) {
            for (int y = top.getY(); y > 0; y--) {
                final BlockState state = helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(top.getX(), y, top.getZ())));
                out.add(y + ":" + (state.is(door()) ? "open=" + state.getValue(GarageDoor.OPEN) + " " + state.getValue(GarageDoor.PART).getSerializedName() : "air"));
            }
        }
        return out.toString();
    }

    private static Map<BlockPos, BlockState> randomDoors(Random random) {
        final Map<BlockPos, BlockState> world = new HashMap<>();
        final int doors = 1 + random.nextInt(30);
        for (int i = 0; i < doors; i++) {
            final BlockPos pos = new BlockPos(random.nextInt(5), 60 + random.nextInt(6), random.nextInt(5));
            world.put(pos, door().defaultBlockState()
                    .setValue(GarageDoor.OPEN, random.nextBoolean())
                    .setValue(GarageDoor.PART, random.nextBoolean() ? GarageDoor.GaragePart.TOP : GarageDoor.GaragePart.MIDDLE));
        }
        return world;
    }

    private static boolean runsOutOfBudget(DoorLevel level, BlockPos clicked, boolean open) {
        try {
            toggleDoor().invoke(door(), level, clicked, open, Direction.NORTH);
            return false;
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof BudgetExceeded) {
                return true;
            }
            throw new GameTestAssertException("toggleDoor failed: " + e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new GameTestAssertException("toggleDoor unavailable: " + e);
        }
    }

    private static final class BudgetExceeded extends RuntimeException {
    }

    private static boolean overflows(DoorLevel level, BlockPos clicked, boolean open) {
        try {
            toggleDoor().invoke(door(), level, clicked, open, Direction.NORTH);
            return false;
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof StackOverflowError) {
                return true;
            }
            throw new GameTestAssertException("toggleDoor failed: " + e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new GameTestAssertException("toggleDoor unavailable: " + e);
        }
    }

    private static Method toggleDoor() throws NoSuchMethodException {
        final Method method = GarageDoor.class.getDeclaredMethod("toggleDoor", Level.class, BlockPos.class, boolean.class, Direction.class);
        method.setAccessible(true);
        return method;
    }

    private static Block door() {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.parse("mcwdoors:garage_gray_door"));
    }

    private static <T> T with(boolean guard, Supplier<T> body) {
        final ModConfigSpec.BooleanValue flag = ArcadiaConfig.MACAWS_GARAGE_DOOR_GUARD;
        final boolean saved = flag.get();
        flag.set(guard);
        try {
            return body.get();
        } finally {
            flag.set(saved);
        }
    }

    /**
     * Only what {@code toggleDoor} uses of a level: reads and writes of block states, in a map, with stone below
     * y 60 unless {@code floor} is off. A refused column turns every write down without changing the block, like
     * a Bobby chunk. Built without running a constructor, since a real level needs a whole server behind it.
     */
    private static final class DoorLevel extends ServerLevel {
        Map<BlockPos, BlockState> blocks;
        Set<Long> refusedColumns;
        List<String> writes;
        boolean floor;
        /** The real loop would run for minutes below the world: past this many writes the test stops it. */
        int writeBudget;

        @SuppressWarnings("DataFlowIssue")
        private DoorLevel() {
            super(null, null, null, null, null, null, null, false, 0L, null, false, null);
        }

        static DoorLevel create() {
            try {
                final Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
                field.setAccessible(true);
                final DoorLevel level = (DoorLevel) ((sun.misc.Unsafe) field.get(null)).allocateInstance(DoorLevel.class);
                level.blocks = new HashMap<>();
                level.refusedColumns = new HashSet<>();
                level.writes = new ArrayList<>();
                level.floor = true;
                return level;
            } catch (ReflectiveOperationException e) {
                throw new GameTestAssertException("Test level unavailable: " + e);
            }
        }

        void put(BlockPos pos, BlockState state) {
            blocks.put(pos, state);
        }

        void refuse(BlockPos pos) {
            refusedColumns.add(pos.atY(0).asLong());
        }

        @Override
        public int getMinBuildHeight() {
            return -64;
        }

        @Override
        public int getHeight() {
            return 384;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            if (isOutsideBuildHeight(pos)) {
                return Blocks.VOID_AIR.defaultBlockState();
            }
            final BlockState found = blocks.get(pos);
            if (found != null) {
                return found;
            }
            return floor && pos.getY() < 60 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
        }

        @Override
        public boolean setBlock(BlockPos pos, BlockState state, int flags) {
            if (writeBudget > 0 && writes.size() >= writeBudget) {
                throw new BudgetExceeded();
            }
            writes.add(pos.toShortString() + " " + state);
            if (isOutsideBuildHeight(pos) || refusedColumns.contains(pos.atY(0).asLong())) {
                return false;
            }
            if (state.isAir()) {
                blocks.remove(pos);
            } else {
                blocks.put(pos.immutable(), state);
            }
            return true;
        }
    }
}
