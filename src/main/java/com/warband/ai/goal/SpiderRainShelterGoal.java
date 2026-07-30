package com.warband.ai.goal;

import com.warband.ai.ShelterScan;
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
    /** Quiet window after sheltering, so a spider does not pace the cover boundary. */
    private static final int REARM_TICKS = 20 * 6;

    private final Mob mob;
    private BlockPos shelter;
    private int recheckCounter;
    private int rearmAtTick;

    public SpiderRainShelterGoal(Mob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (mob.getTarget() != null) return false;
        if (mob.tickCount < rearmAtTick) return false;
        Level level = mob.level();
        if (!level.isRaining()) return false;
        if (!level.canSeeSky(mob.blockPosition())) return false;
        if (--recheckCounter > 0) return shelter != null;
        recheckCounter = RECHECK_TICKS;
        shelter = ShelterScan.nearestCovered(level, mob.blockPosition(), SCAN_RADIUS, VERTICAL_RADIUS, false);
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
        rearmAtTick = mob.tickCount + REARM_TICKS;
    }
}
