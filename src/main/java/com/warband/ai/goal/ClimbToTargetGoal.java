package com.warband.ai.goal;

import com.warband.WarbandDebug;
import com.warband.config.WarbandConfig;
import com.warband.entity.MobData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Lets a mob standing on a ladder or vine actually go up it.
 *
 * <p>Vanilla pathfinding treats climbables as walkable only in narrow cases, so a
 * ladder is usually a hard stop: the mob arrives at the bottom, cannot path up, and
 * stands there. Warband already had leaping and mob-stacking for elevated targets,
 * but both are gated high (0.65 and above) and neither helps against a plain ladder
 * shaft.
 *
 * <p>Unlocks early, at difficulty {@value #MIN_DIFFICULTY}. Using a ladder is not
 * clever — it is the baseline expectation that a mob understands the thing it is
 * touching. The tactical behaviours stay reserved for higher bands.
 */
public final class ClimbToTargetGoal extends Goal implements WarbandGoal {

    private static final double MIN_DIFFICULTY = 0.30;
    /** Vertical speed while climbing. Vanilla players climb at 0.2. */
    private static final double CLIMB_SPEED = 0.16;
    private static final double MAX_HORIZONTAL_SQR = 12.0 * 12.0;

    private final Mob mob;

    public ClimbToTargetGoal(Mob mob) {
        this.mob = mob;
        // No MOVE flag: this rides alongside the attack goal's pathing rather than
        // competing with it, and only takes effect while actually on a climbable.
        setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        if (!WarbandConfig.climbableBlocksEnabled) return false;
        if (MobData.get(mob).difficulty() < MIN_DIFFICULTY) return false;
        if (mob.isPassenger()) return false;

        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return false;
        // Only climb toward a target that is meaningfully above us.
        if (target.getY() - mob.getY() < 1.5) return false;
        if (horizontalDistanceSqr(target) > MAX_HORIZONTAL_SQR) return false;
        return onClimbable();
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return false;
        if (target.getY() - mob.getY() < 0.5) return false;
        return onClimbable();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        LivingEntity target = mob.getTarget();
        WarbandDebug.event("CLIMB", mob, "rise=" + (target == null ? "?"
                : String.format("%.1f", target.getY() - mob.getY())));
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) return;

        Vec3 motion = mob.getDeltaMovement();
        mob.setDeltaMovement(motion.x, CLIMB_SPEED, motion.z);
        // Hold against the ladder, otherwise the climb drifts off the block and the
        // mob falls back down the shaft it just went up.
        Vec3 toward = new Vec3(target.getX() - mob.getX(), 0.0, target.getZ() - mob.getZ());
        if (toward.lengthSqr() > 0.04) {
            Vec3 press = toward.normalize().scale(0.04);
            mob.setDeltaMovement(mob.getDeltaMovement().add(press.x, 0.0, press.z));
        }
        mob.fallDistance = 0.0F;
    }

    /** Vanilla's own notion of "touching a ladder/vine", so modded climbables count too. */
    private boolean onClimbable() {
        return mob.onClimbable();
    }

    private double horizontalDistanceSqr(LivingEntity target) {
        double dx = target.getX() - mob.getX();
        double dz = target.getZ() - mob.getZ();
        return dx * dx + dz * dz;
    }
}
