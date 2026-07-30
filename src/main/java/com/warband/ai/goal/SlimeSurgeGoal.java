package com.warband.ai.goal;

import com.warband.ai.Squad;
import com.warband.ai.TacticalEffects;
import com.warband.entity.Tactic;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/** Slimes and magma cubes surge when close enough to swamp movement. */
public final class SlimeSurgeGoal extends SquadGoal {

    private static final int COOLDOWN_TICKS = 70;

    private LivingEntity surgeTarget;

    public SlimeSurgeGoal(Mob mob, Squad squad) {
        super(mob, squad, 1.25);
    }

    @Override
    public boolean canUse() {
        LivingEntity target = visibleTarget();
        if (target == null || !cooldownReady()) return false;
        if (mob.distanceToSqr(target) > 10.0 * 10.0) return false;
        surgeTarget = target;
        return true;
    }

    @Override
    public void start() {
        if (surgeTarget == null || !surgeTarget.isAlive()) return;
        resetCooldown(COOLDOWN_TICKS);
        mob.addEffect(new MobEffectInstance(MobEffects.SPEED, 80, 1, false, true));
        announceTactic(Tactic.SLIME_SURGE);
        TacticalEffects.search((ServerLevel) mob.level(), mob.position());
        mob.getNavigation().moveTo(surgeTarget, speed);
    }
}
