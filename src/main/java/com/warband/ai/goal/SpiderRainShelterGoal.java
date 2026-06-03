package com.warband.ai.goal;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;

import java.util.EnumSet;

/**
 * Out-of-combat spiders path to the nearest covered tile when caught in the
 * rain. Mirrors the natural spider preference for staying dry — they don't
 * like being exposed to weather. Skipped while engaged.
 */
public final class SpiderRainShelterGoal extends Goal implements WarbandGoal {

    private static final int SCAN_RADIUS = 15;
    private static final int VERTICAL_RADIUS = 4;
    private static final int RECHECK_TICKS = 30;

    private final Mob mob;
    private BlockPos shelter;
    private int recheckCounter;

    public SpiderRainShelterGoal(Mob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (mob.getTarget() != null) return false;
        Level level = mob.level();
        if (!level.isRaining()) return false;
        if (!level.canSeeSky(mob.blockPosition())) return false;
        if (--recheckCounter > 0) return shelter != null;
        recheckCounter = RECHECK_TICKS;
        shelter = findShelter(level, mob.blockPosition());
        return shelter != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (shelter == null || mob.getTarget() != null) return false;
        if (mob.blockPosition().distSqr(shelter) <= 4) return false;
        return !mob.getNavigation().isDone();
    }

    @Override
    public void start() {
        mob.getNavigation().moveTo(shelter.getX() + 0.5, shelter.getY(), shelter.getZ() + 0.5, 1.0);
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        shelter = null;
    }

    private BlockPos findShelter(Level level, BlockPos origin) {
        BlockPos best = null;
        long bestDist = Long.MAX_VALUE;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
            for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                for (int dy = -VERTICAL_RADIUS; dy <= VERTICAL_RADIUS; dy++) {
                    cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (level.canSeeSky(cursor)) continue;
                    if (!level.getBlockState(cursor).isAir()) continue;
                    if (level.getBlockState(cursor.below()).isAir()) continue;
                    long dist = (long) dx * dx + (long) dz * dz + (long) dy * dy * 2;
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = cursor.immutable();
                    }
                }
            }
        }
        return best;
    }
}
