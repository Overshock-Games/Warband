package com.warband.ai.goal;

import com.warband.ai.Squad;
import com.warband.ai.VisibilityRules;
import com.warband.WarbandDebug;
import com.warband.WarbandMod;
import com.warband.config.WarbandConfig;
import com.warband.entity.MobData;
import com.warband.entity.Tactic;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

abstract class SquadGoal extends Goal implements WarbandGoal {

    protected final Mob mob;
    protected final Squad squad;
    protected final double speed;
    private int nextDecisionTick;

    SquadGoal(Mob mob, Squad squad, double speed) {
        this.mob = mob;
        this.squad = squad;
        this.speed = speed;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canContinueToUse() {
        if (VisibilityRules.frightenedByNearbyAnimal(mob)) return false;
        return mob.isAlive() && !mob.isDeadOrDying() && !mob.isRemoved() && !mob.getNavigation().isDone();
    }

    /**
     * Shared gate for {@code canUse()} in subclasses: don't start a tactic while
     * the mob is fleeing something it fears. Paired with the same check in
     * {@link #canContinueToUse()}, which releases the MOVE flag mid-tactic.
     */
    protected boolean frightened() {
        return VisibilityRules.frightenedByNearbyAnimal(mob);
    }

    protected boolean decisionReady(int interval) {
        if (mob.tickCount < nextDecisionTick) return false;
        nextDecisionTick = mob.tickCount + interval + mob.getRandom().nextInt(interval + 1);
        return true;
    }

    protected boolean cooldownReady() {
        return mob.tickCount >= nextDecisionTick;
    }

    protected void resetCooldown(int interval) {
        nextDecisionTick = mob.tickCount + interval + mob.getRandom().nextInt(interval + 1);
    }

    protected @Nullable LivingEntity visibleTarget() {
        LivingEntity target = mob.getTarget();
        if (VisibilityRules.canUseTacticalSight(mob, target)) {
            return target;
        }
        target = squad.target();
        return VisibilityRules.canUseTacticalSight(mob, target) ? target : null;
    }

    protected boolean moveTo(BlockPos pos) {
        return mob.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, speed);
    }

    protected void logTactic(Tactic tactic) {
        if (!WarbandConfig.debugTacticLogs) return;
        LivingEntity target = mob.getTarget();
        // Routed through WarbandDebug so tactic logs share the machine-readable
        // EVENT=/key=value shape with every other behaviour trace.
        WarbandDebug.event(tactic.name(), mob, String.format("diff=%.2f target=%s",
                MobData.get(mob).difficulty(),
                target == null ? "none" : target.getType().toShortString()));
    }

    protected @Nullable BlockPos awayFrom(Vec3 threat, double distance) {
        Vec3 current = mob.position();
        Vec3 delta = current.subtract(threat);
        if (delta.lengthSqr() < 0.001) return null;
        Vec3 dest = current.add(delta.normalize().scale(distance));
        return BlockPos.containing(dest.x, dest.y, dest.z);
    }
}
