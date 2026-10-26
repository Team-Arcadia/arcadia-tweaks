package com.teamarcadia.arcadiatweaks.neoforge.gametest;

import com.teamarcadia.arcadiatweaks.ArcadiaTweaks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game checks of the runaway movement guard ({@code EntityMoveGuardMixin}).
 *
 * Armor stands are used because they have physics (gravity, ground friction) but no AI, so
 * their movement only comes from the velocity the test gives them. The template is a stone
 * floor, 12 blocks long on X.
 *
 * @author vyrriox
 */
@GameTestHolder(ArcadiaTweaks.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EntityMoveGuardGameTests {

    private static final String FLOOR = "move_guard_floor";
    private static final String BATCH = ArcadiaTweaks.MOD_ID;
    private static final int SETTLE_TICKS = 5;
    private static final int FLOOR_Y = 1;

    private EntityMoveGuardGameTests() {}

    /**
     * Velocities above the 100 blocks per tick limit (and NaN) are stopped before Entity.move
     * runs: the entity stays where it is and its velocity is cleared. The +5000 X case also
     * proves the guard is what holds the entity: without it, the stand ends up 5000 blocks away.
     */
    @GameTest(template = FLOOR, batch = BATCH, timeoutTicks = 40)
    public static void entityMoveGuardStopsRunawayVelocity(GameTestHelper helper) {
        final ArmorStand fastX = spawnStand(helper, 1);
        final ArmorStand fallingY = spawnStand(helper, 2);
        final ArmorStand notANumber = spawnStand(helper, 3);
        final Vec3 fastXStart = fastX.position();
        final Vec3 fallingYStart = fallingY.position();
        final Vec3 notANumberStart = notANumber.position();

        fastX.setDeltaMovement(5000.0D, 0.0D, 0.0D);
        fallingY.setDeltaMovement(0.0D, -1.0E9D, 0.0D);
        notANumber.setDeltaMovement(Double.NaN, 0.0D, 0.0D);

        helper.startSequence()
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> {
                    assertStopped(helper, fastX, fastXStart, "+5000 X");
                    assertStopped(helper, fallingY, fallingYStart, "-1e9 Y");
                    assertStopped(helper, notANumber, notANumberStart, "NaN X");
                })
                .thenSucceed();
    }

    /** A velocity under the limit is left alone: the stand slides along the floor as usual. */
    @GameTest(template = FLOOR, batch = BATCH, timeoutTicks = 40)
    public static void entityMoveGuardKeepsNormalMovement(GameTestHelper helper) {
        final ArmorStand stand = spawnStand(helper, 2);
        final Vec3 start = stand.position();
        stand.setDeltaMovement(1.5D, 0.0D, 0.0D);

        helper.startSequence()
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> {
                    final double travelled = stand.getX() - start.x;
                    helper.assertTrue(stand.isAlive(), "Stand with a normal velocity should still be alive");
                    helper.assertTrue(travelled > 1.0D,
                            "Stand with a 1.5 blocks/tick velocity should slide more than 1 block, moved " + travelled);
                    helper.assertTrue(Math.abs(stand.getY() - start.y) < 0.5D,
                            "Stand with a normal velocity should stay on the floor, y " + start.y + " -> " + stand.getY());
                })
                .thenSucceed();
    }

    /**
     * Spawns an armor stand standing on the floor. In test coordinates the structure starts one
     * block above the test origin, so the floor layer is y = 1 and the stand stands at y = 2.
     */
    private static ArmorStand spawnStand(GameTestHelper helper, int row) {
        helper.assertBlockPresent(Blocks.STONE, new BlockPos(1, FLOOR_Y, row));
        return helper.spawn(EntityType.ARMOR_STAND, new Vec3(1.5D, FLOOR_Y + 1.0D, row + 0.5D));
    }

    private static void assertStopped(GameTestHelper helper, ArmorStand stand, Vec3 start, String label) {
        final Vec3 position = stand.position();
        final Vec3 velocity = stand.getDeltaMovement();
        helper.assertTrue(stand.isAlive(), label + ": stand should still be alive");
        helper.assertTrue(Double.isFinite(position.x) && Double.isFinite(position.y) && Double.isFinite(position.z),
                label + ": position should stay finite, got " + position);
        helper.assertTrue(position.distanceTo(start) < 1.0D,
                label + ": stand should stay in place, moved from " + start + " to " + position);
        helper.assertTrue(Double.isFinite(velocity.x) && Double.isFinite(velocity.y) && Double.isFinite(velocity.z)
                        && velocity.length() < 1.0D,
                label + ": velocity should be cleared, got " + velocity);
    }
}
