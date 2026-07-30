package com.warband.ai.goal;

import com.warband.ai.Squad;
import com.warband.ai.TacticalEffects;
import com.warband.ai.TemporaryTacticBlocks;
import com.warband.entity.Tactic;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Endermen disrupt escape routes with teleports and short-lived block placement. */
public final class EndermanDisruptGoal extends SquadGoal {

    private static final int COOLDOWN_TICKS = 100;

    private LivingEntity disruptTarget;
    private BlockPos disruptionPos;

    public EndermanDisruptGoal(Mob mob, Squad squad) {
        super(mob, squad, 1.0);
    }

    @Override
    public boolean canUse() {
        LivingEntity target = visibleTarget();
        if (target == null || !cooldownReady()) return false;
        if (mob.distanceToSqr(target) > 18.0 * 18.0) return false;

        disruptTarget = target;
        disruptionPos = disruptionPos(target);
        return disruptionPos != null || mob.distanceToSqr(target) > 8.0 * 8.0;
    }

    @Override
    public void start() {
        if (disruptTarget == null || !disruptTarget.isAlive()) return;
        resetCooldown(COOLDOWN_TICKS);
        ServerLevel level = (ServerLevel) mob.level();
        boolean placed = false;
        if (disruptionPos != null) {
            placed = TemporaryTacticBlocks.place(level, disruptionPos, carriedOrFallback(), 20 * 8);
        }

        boolean teleported = false;
        if (mob.distanceToSqr(disruptTarget) > 8.0 * 8.0) {
            teleported = teleportNear(disruptTarget);
        }
        if (placed || teleported) {
            announceTactic(Tactic.ENDERMAN_DISRUPT);
            TacticalEffects.signal(level, mob);
        }
    }

    private BlockPos disruptionPos(LivingEntity target) {
        BlockPos base = target.blockPosition();
        Vec3 look = target.getLookAngle();
        BlockPos front = base.offset((int) Math.signum(look.x), 0, (int) Math.signum(look.z));
        if (mob.level().getBlockState(front).isAir()) return front;
        if (mob.level().getBlockState(base.above()).isAir()) return base.above();
        return null;
    }

    private BlockState carriedOrFallback() {
        if (mob instanceof EnderMan enderMan && enderMan.getCarriedBlock() != null) {
            return enderMan.getCarriedBlock();
        }
        return Blocks.DIRT.defaultBlockState();
    }

    private boolean teleportNear(LivingEntity target) {
        Vec3 look = target.getLookAngle();
        Vec3[] attempts = {
                target.position().subtract(look.scale(3.0)),
                target.position().add(look.yRot(1.5707964F).scale(2.5)),
                target.position().add(look.yRot(-1.5707964F).scale(2.5)),
                target.position().subtract(look.scale(4.5))
        };
        for (Vec3 attempt : attempts) {
            if (mob.randomTeleport(attempt.x, attempt.y, attempt.z, true)) {
                return true;
            }
        }
        return false;
    }
}
