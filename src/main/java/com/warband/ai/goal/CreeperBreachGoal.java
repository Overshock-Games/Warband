package com.warband.ai.goal;

import com.warband.ai.Squad;
import com.warband.config.WarbandConfig;
import com.warband.entity.MobData;
import com.warband.entity.Tactic;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * A creeper that cannot path to its target walks up to the wall between them and
 * lights itself.
 *
 * <p>Answers the specific complaint that creepers never blow open a shelter. Vanilla
 * creepers only swell within about three blocks of a reachable target, so a player
 * behind one layer of dirt is untouchable by the one mob in the game whose entire
 * identity is removing walls.
 *
 * <p>Kept honest in two ways. It requires that pathing has actually failed, so
 * creepers do not detonate on scenery while walking to someone in the open. And it
 * requires {@code mobGriefing}, since the whole point is the crater — a creeper
 * blowing up harmlessly against a wall would be worse than not trying.
 *
 * <p><b>The one exception to reversible siege damage.</b> This detonation is an
 * ordinary vanilla explosion, so the hole it leaves is <i>permanent</i> regardless of
 * {@code siegeMiningPermanent}, and it can take out blocks the siege block tag
 * excludes — an iron door among them. That is deliberate rather than an oversight:
 * the mod is not making explosions more destructive, only helping the creeper reach
 * a wall it could already have blown up. Players who want no lasting damage at all
 * should turn {@code creeperBreachEnabled} off, since no restore ledger covers it.
 */
public final class CreeperBreachGoal extends SquadGoal {

    private static final double MIN_DIFFICULTY = 0.60;
    /** Close enough that the explosion will actually open the wall. */
    private static final double BREACH_RANGE = 3.0;
    /** Beyond this, go find another way in rather than committing to a blast. */
    private static final double MAX_TARGET_DISTANCE = 16.0;
    private static final int DECISION_INTERVAL = 40;

    private LivingEntity breachTarget;

    public CreeperBreachGoal(Mob mob, Squad squad) {
        super(mob, squad, 1.0);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!WarbandConfig.creeperBreachEnabled) return false;
        if (!(mob instanceof Creeper creeper)) return false;
        if (creeper.getSwellDir() > 0) return false;
        if (frightened()) return false;
        if (!(mob.level() instanceof ServerLevel level)) return false;
        if (!Boolean.TRUE.equals(level.getGameRules().get(GameRules.MOB_GRIEFING))) return false;
        if (MobData.get(mob).difficulty() < MIN_DIFFICULTY) return false;
        if (!decisionReady(DECISION_INTERVAL)) return false;

        LivingEntity target = visibleTarget();
        if (target == null) return false;
        double distance = mob.distanceToSqr(target);
        if (distance > MAX_TARGET_DISTANCE * MAX_TARGET_DISTANCE) return false;
        // Vanilla's SwellGoal already covers a reachable target at close range.
        if (canPathTo(target)) return false;

        breachTarget = target;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (breachTarget == null || !breachTarget.isAlive()) return false;
        if (frightened()) return false;
        if (!(mob instanceof Creeper)) return false;
        return mob.distanceToSqr(breachTarget) <= MAX_TARGET_DISTANCE * MAX_TARGET_DISTANCE;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (breachTarget == null || !(mob instanceof Creeper creeper)) return;

        mob.getLookControl().setLookAt(breachTarget, 30.0F, 30.0F);
        // Close on the wall between us and them, aiming at the obstruction rather
        // than the unreachable target itself.
        BlockPos wall = wallToward(breachTarget);
        if (wall != null && mob.distanceToSqr(Vec3.atCenterOf(wall)) > BREACH_RANGE * BREACH_RANGE) {
            moveTo(wall);
            return;
        }

        mob.getNavigation().stop();
        if (creeper.getSwellDir() <= 0) {
            creeper.setSwellDir(1);
            logTactic(Tactic.CREEPER_BREACH);
        }
    }

    @Override
    public void stop() {
        breachTarget = null;
        // Only stand the fuse down if we are the reason it is lit; vanilla SwellGoal
        // may legitimately own it by now.
        if (mob instanceof Creeper creeper && creeper.getSwellDir() > 0 && visibleTarget() == null) {
            creeper.setSwellDir(-1);
        }
    }

    private boolean canPathTo(LivingEntity target) {
        Path path = mob.getNavigation().createPath(target, 0);
        return path != null && path.canReach();
    }

    /** First solid block on the straight line toward the target. */
    private BlockPos wallToward(LivingEntity target) {
        Vec3 from = mob.getEyePosition();
        Vec3 to = target.getEyePosition();
        Vec3 step = to.subtract(from);
        double length = step.length();
        if (length < 0.5) return null;
        step = step.scale(1.0 / length);

        for (double travelled = 0.5; travelled <= Math.min(length, MAX_TARGET_DISTANCE); travelled += 0.5) {
            BlockPos pos = BlockPos.containing(
                    from.x + step.x * travelled,
                    from.y + step.y * travelled,
                    from.z + step.z * travelled);
            if (!mob.level().getBlockState(pos).isAir()) return pos;
        }
        return null;
    }
}
