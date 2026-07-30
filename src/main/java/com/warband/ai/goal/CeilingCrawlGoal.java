package com.warband.ai.goal;

import com.warband.ai.Squad;
import com.warband.ai.TacticalEffects;
import com.warband.entity.Tactic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Lets pursuing spiders cling to low ceilings and move across their underside.
 * When the target passes directly below, the spider holds for a brief tell
 * (so the player has a beat to look up) and then drops with a web-string
 * particle trail.
 */
public final class CeilingCrawlGoal extends SquadGoal {

    private static final int CHECK_INTERVAL_TICKS = 12;
    private static final double MAX_DISTANCE_SQR = 18.0 * 18.0;
    private static final double HORIZONTAL_SPEED = 0.16;
    /** Horizontal distance under which we consider the target "directly below". */
    private static final double DROP_HORIZONTAL_SQR = 2.5 * 2.5;
    /** Vertical clearance required before dropping (don't drop from a 1-block ceiling). */
    private static final double DROP_MIN_HEIGHT = 2.0;
    /** Tell window: how long the spider hovers, twitching, before it releases. */
    private static final int DROP_TELL_TICKS = 12;
    /**
     * Inside this range the vanilla {@code SpiderAttackGoal} takes over. Warband
     * registers this goal at the same priority (4) as that attack goal, and the
     * goal selector only lets a <i>strictly</i> higher-priority goal take a flag
     * from a running one — so whichever started first keeps MOVE indefinitely.
     * Without an explicit handoff, a spider that reached the player kept crawling
     * and never bit: the reported "climbs but doesn't attack" and "shoots web then
     * stops dead in front of you".
     */
    private static final double MELEE_HANDOFF_SQR = 3.0 * 3.0;
    /** A wall is only worth climbing when the target is meaningfully above us. */
    private static final double CLIMB_MIN_RISE = 1.5;

    private LivingEntity target;
    private boolean logged;
    private int dropTellRemaining;

    public CeilingCrawlGoal(Mob mob, Squad squad) {
        super(mob, squad, 1.0);
    }

    @Override
    public boolean canUse() {
        if (!(mob instanceof Spider)) return false;
        if (!decisionReady(CHECK_INTERVAL_TICKS)) return false;
        target = visibleTarget();
        if (target == null || mob.distanceToSqr(target) > MAX_DISTANCE_SQR) return false;
        if (mob.distanceToSqr(target) <= MELEE_HANDOFF_SQR) return false;
        return hasCeilingGrip() || wantsWallClimb();
    }

    @Override
    public boolean canContinueToUse() {
        target = visibleTarget();
        if (target == null || mob.distanceToSqr(target) > MAX_DISTANCE_SQR) return false;
        if (hasCeilingGrip()) {
            // Clinging right over the target but with no headroom to drop: release
            // so the attack goal can act instead of grinding along the ceiling.
            return !(horizontalToTargetSqr() < DROP_HORIZONTAL_SQR
                    && mob.getY() - target.getY() < DROP_MIN_HEIGHT);
        }
        // Off the ceiling this goal is only a wall-climb, so require a target
        // actually above us and stop once we are in biting range.
        return wantsWallClimb() && mob.distanceToSqr(target) > MELEE_HANDOFF_SQR;
    }

    /**
     * {@code horizontalCollision} alone is true whenever the spider is pressed
     * against anything at all, so on flat ground any wall bump used to hijack the
     * spider's movement into "climb mode" and suppress its attack.
     */
    private boolean wantsWallClimb() {
        return mob.horizontalCollision && target != null
                && target.getY() - mob.getY() >= CLIMB_MIN_RISE;
    }

    private double horizontalToTargetSqr() {
        double dx = target.getX() - mob.getX();
        double dz = target.getZ() - mob.getZ();
        return dx * dx + dz * dz;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        logged = false;
        dropTellRemaining = 0;
    }

    @Override
    public void tick() {
        if (target == null) return;

        if (!hasCeilingGrip()) {
            if (mob.horizontalCollision) {
                Vec3 current = mob.getDeltaMovement();
                mob.setDeltaMovement(current.x, Math.max(current.y, 0.22), current.z);
            }
            mob.getNavigation().moveTo(target, 1.1);
            return;
        }

        if (!logged) {
            announceTactic(Tactic.CEILING_CRAWL);
            logged = true;
        }

        // Drop-attack path: target is directly below with clearance. Hold for
        // a tell, then release the ceiling and fall with a web-string trail.
        double horizontalSqr = horizontalToTargetSqr();
        double heightAbove = mob.getY() - target.getY();
        if (horizontalSqr < DROP_HORIZONTAL_SQR && heightAbove >= DROP_MIN_HEIGHT) {
            if (dropTellRemaining == 0) {
                dropTellRemaining = DROP_TELL_TICKS;
            }
            if (--dropTellRemaining <= 0) {
                releaseAsDrop();
                return;
            }
            // Hover in place during the tell so the player has time to react.
            mob.setDeltaMovement(0.0, 0.0, 0.0);
            mob.fallDistance = 0.0F;
            return;
        } else {
            dropTellRemaining = 0;
        }

        Vec3 toTarget = target.position().subtract(mob.position());
        Vec3 horizontal = new Vec3(toTarget.x, 0.0, toTarget.z);
        Vec3 current = mob.getDeltaMovement();
        if (horizontal.lengthSqr() > 0.01) {
            Vec3 crawl = horizontal.normalize().scale(HORIZONTAL_SPEED);
            current = new Vec3(crawl.x, current.y, crawl.z);
        }

        BlockPos ceiling = gripBlock();
        double desiredY = ceiling.getY() - mob.getBbHeight() - 0.04;
        double yCorrection = Math.max(-0.08, Math.min(0.12, (desiredY - mob.getY()) * 0.35));
        mob.setDeltaMovement(current.x, yCorrection, current.z);
        mob.fallDistance = 0.0F;
    }

    private void releaseAsDrop() {
        if (!(mob.level() instanceof ServerLevel level)) return;
        Vec3 from = mob.position();
        Vec3 to = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
        TacticalEffects.webTrail(level, from, to);
        TacticalEffects.web(level, mob.blockPosition());
        // Small downward kick to commit the drop; gravity does the rest.
        mob.setDeltaMovement(mob.getDeltaMovement().x, -0.25, mob.getDeltaMovement().z);
        mob.hurtMarked = true;
    }

    private boolean hasCeilingGrip() {
        return isGripBlock(gripBlock());
    }

    private BlockPos gripBlock() {
        return BlockPos.containing(mob.getX(), mob.getY() + mob.getBbHeight() + 0.08, mob.getZ());
    }

    private boolean isGripBlock(BlockPos pos) {
        BlockState state = mob.level().getBlockState(pos);
        return !state.isAir() && state.isFaceSturdy(mob.level(), pos, Direction.DOWN);
    }
}
