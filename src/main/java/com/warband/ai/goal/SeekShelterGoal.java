package com.warband.ai.goal;

import com.warband.ai.ShelterScan;
import com.warband.WarbandDebug;
import com.warband.config.WarbandConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.level.Level;

import java.util.EnumSet;

/**
 * Sun-sensitive undead path to the nearest shaded tile at dawn instead of
 * standing in the open until they catch fire. Predictive: triggers on
 * daytime + sky exposure, not on {@code isOnFire}, so mobs start moving
 * before the first burn tick.
 *
 * <p><b>Out of combat only.</b> A mob that has a target commits to it and burns
 * if it must, exactly as vanilla undead do. Running this during a chase caused
 * mobs to abandon the player for any nearby overhang, and because leaving the
 * shade re-armed the goal instantly, they oscillated in place under it — the
 * "zombies stuck rocking under a floating block" report. Cover a player pillars
 * up and then abandons is the textbook trigger: the tiles beneath the leftover
 * blocks read as shelter, and every zombie underneath got pinned there.
 */
public final class SeekShelterGoal extends Goal implements WarbandGoal {

    private static final int SCAN_RADIUS = 10;
    private static final int VERTICAL_RADIUS = 2;
    private static final int RECHECK_TICKS = 20;
    /**
     * Quiet window after sheltering before this mob may seek shade again. Without
     * it, a mob that steps out of cover for any reason re-triggers on the next
     * tick and paces the shade boundary.
     */
    private static final int REARM_TICKS = 20 * 6;

    private final Mob mob;
    private BlockPos shelter;
    private int recheckCounter;
    private int rearmAtTick;

    public SeekShelterGoal(Mob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!WarbandConfig.seekShelterEnabled) return false;
        // Committed to a fight: never break off, and never pace the shade line.
        if (mob.getTarget() != null) return false;
        if (mob.tickCount < rearmAtTick) return false;
        if (mob instanceof Husk || mob instanceof WitherSkeleton) return false;
        if (mob.isInWaterOrRain()) return false;
        if (!mob.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) return false;
        Level level = mob.level();
        if (!level.isBrightOutside()) return false;
        if (!level.canSeeSky(mob.blockPosition())) return false;
        if (--recheckCounter > 0) return shelter != null;
        recheckCounter = RECHECK_TICKS;
        shelter = ShelterScan.nearestCovered(level, mob.blockPosition(), SCAN_RADIUS, VERTICAL_RADIUS, true);
        return shelter != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (shelter == null) return false;
        if (mob.getTarget() != null) return false;
        if (mob.isInWaterOrRain()) return false;
        if (mob.blockPosition().distSqr(shelter) <= 4) return false;
        return !mob.getNavigation().isDone();
    }

    @Override
    public void start() {
        mob.getNavigation().moveTo(shelter.getX() + 0.5, shelter.getY(), shelter.getZ() + 0.5, 1.3);
        WarbandDebug.event("SEEK_SHELTER", mob, "shelter=" + shelter.getX() + " " + shelter.getY()
                + " " + shelter.getZ() + " target=none");
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        shelter = null;
        rearmAtTick = mob.tickCount + REARM_TICKS;
    }
}
