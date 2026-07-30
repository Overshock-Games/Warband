package com.warband.ai.goal;

import com.warband.ai.Squad;
import com.warband.ai.TacticalEffects;
import com.warband.entity.Tactic;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.phys.Vec3;

/** Ranged mobs sidestep after lining up a shot instead of staying planted. */
public final class RangedRepositionGoal extends SquadGoal {

    private static final int COOLDOWN_TICKS = 70;
    private BlockPos reposition;

    public RangedRepositionGoal(Mob mob, Squad squad) {
        super(mob, squad, 1.15);
    }

    @Override
    public boolean canUse() {
        if (!(mob instanceof RangedAttackMob)) return false;
        LivingEntity target = visibleTarget();
        if (target == null || !cooldownReady()) return false;
        double distance = mob.distanceToSqr(target);
        if (distance < 4.0 * 4.0 || distance > 18.0 * 18.0) return false;

        Vec3 fromTarget = mob.position().subtract(target.position());
        Vec3 lateral = new Vec3(-fromTarget.z, 0.0, fromTarget.x);
        if (lateral.lengthSqr() < 0.001) return false;
        if (mob.getRandom().nextBoolean()) lateral = lateral.scale(-1.0);
        Vec3 dest = mob.position().add(lateral.normalize().scale(4.0 + mob.getRandom().nextDouble() * 3.0));
        reposition = BlockPos.containing(dest.x, mob.getY(), dest.z);
        return true;
    }

    @Override
    public void start() {
        resetCooldown(COOLDOWN_TICKS);
        if (reposition != null && moveTo(reposition)) {
            announceTactic(Tactic.RANGED_REPOSITION);
            if (mob.level() instanceof ServerLevel level) {
                TacticalEffects.search(level, mob.position());
            }
        }
    }
}
