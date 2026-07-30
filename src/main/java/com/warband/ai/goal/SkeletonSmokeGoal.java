package com.warband.ai.goal;

import com.warband.ai.Squad;
import com.warband.ai.TacticalEffects;
import com.warband.entity.Tactic;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/** Ranged skeleton-family mobs make a visible escape move when crowded. */
public final class SkeletonSmokeGoal extends SquadGoal {

    private static final int COOLDOWN_TICKS = 90;

    private BlockPos retreat;

    public SkeletonSmokeGoal(Mob mob, Squad squad) {
        super(mob, squad, 1.2);
    }

    @Override
    public boolean canUse() {
        LivingEntity target = visibleTarget();
        if (target == null || mob.distanceToSqr(target) > 6.0 * 6.0 || !cooldownReady()) return false;

        retreat = awayFrom(target.position(), 8.0);
        return retreat != null;
    }

    @Override
    public void start() {
        resetCooldown(COOLDOWN_TICKS);
        if (retreat != null && moveTo(retreat)) {
            announceTactic(Tactic.SKELETON_SMOKE);
            TacticalEffects.smoke((ServerLevel) mob.level(), mob.position());
        }
    }
}
