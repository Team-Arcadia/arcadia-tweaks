package com.teamarcadia.arcadiatweaks.neoforge.gametest.bench.scenario;

import com.teamarcadia.arcadiatweaks.common.config.ArcadiaConfig;
import com.teamarcadia.arcadiatweaks.neoforge.gametest.bench.BenchScenario;
import com.teamarcadia.arcadiatweaks.neoforge.gametest.bench.BenchSwitches;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Safety guard overhead: a large fenced pasture of ordinary farm animals that walk around, so
 * {@code Entity.move} runs thousands of times per tick. The guard adds three comparisons per call;
 * this scenario shows whether that is measurable.
 *
 * @author vyrriox
 */
public final class EntityMoveGuardScenario implements BenchScenario {

    private static final int HALF = 40;
    private static final int PER_TYPE = 120;
    private static final List<EntityType<? extends Mob>> TYPES = List.of(
            EntityType.COW, EntityType.PIG, EntityType.SHEEP, EntityType.CHICKEN, EntityType.RABBIT);

    private final List<Entity> spawned = new ArrayList<>();
    private BlockPos origin;

    @Override
    public String id() {
        return "entity_move_guard";
    }

    @Override
    public String description() {
        return "Safety guard overhead: " + (PER_TYPE * TYPES.size()) + " farm animals wandering in an 81x81 pasture.";
    }

    @Override
    public List<BenchSwitches.Switch> switches() {
        return List.of(BenchSwitches.Switch.of("safety.entityMoveGuard", ArcadiaConfig.ENTITY_MOVE_GUARD_ENABLED));
    }

    @Override
    public int footprint() {
        return HALF + 2;
    }

    @Override
    public void build(ServerLevel level, BlockPos origin) {
        this.origin = origin;
        final int y = origin.getY();
        for (int x = -HALF - 1; x <= HALF + 1; x++) {
            for (int z = -HALF - 1; z <= HALF + 1; z++) {
                final BlockPos floor = origin.offset(x, -1, z);
                final boolean edge = Math.abs(x) == HALF + 1 || Math.abs(z) == HALF + 1;
                level.setBlock(floor, Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                for (int h = 0; h < 3; h++) {
                    level.setBlock(origin.offset(x, h, z), edge ? Blocks.GLASS.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        int i = 0;
        for (EntityType<? extends Mob> type : TYPES) {
            for (int n = 0; n < PER_TYPE; n++, i++) {
                // Spread on a grid so the animals do not start inside each other.
                final int gx = (i % 40) * 2 - 39;
                final int gz = ((i / 40) * 5) % 78 - 39;
                final Mob mob = type.create(level);
                if (mob == null) continue;
                mob.moveTo(origin.getX() + gx + 0.5, y, origin.getZ() + gz + 0.5, level.random.nextFloat() * 360f, 0f);
                mob.setPersistenceRequired();
                level.addFreshEntity(mob);
                spawned.add(mob);
            }
        }
    }

    @Override
    public Map<String, Object> size() {
        final Map<String, Object> m = new LinkedHashMap<>();
        m.put("animals", spawned.size());
        m.put("aliveAnimals", spawned.stream().filter(Entity::isAlive).count());
        m.put("types", TYPES.stream().map(t -> EntityType.getKey(t).toString()).toList());
        m.put("pastureBlocks", (2 * HALF + 1) * (2 * HALF + 1));
        return m;
    }

    @Override
    public void teardown(ServerLevel level) {
        for (Entity e : spawned) {
            e.discard();
        }
        if (origin != null) {
            final AABB box = new AABB(origin).inflate(HALF + 4, 8, HALF + 4);
            level.getEntities((Entity) null, box, e -> !(e instanceof net.minecraft.world.entity.player.Player)).forEach(Entity::discard);
        }
    }
}
