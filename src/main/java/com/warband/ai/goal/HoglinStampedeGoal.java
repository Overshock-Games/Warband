package com.warband.ai.goal;

import com.warband.ai.Squad;
import com.warband.ai.TacticalEffects;
import com.warband.entity.Tactic;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/** Hoglins commit as a stampede when they have a clean visible target. */
public final class HoglinStampedeGoal extends SquadGoal {

    private static final int COOLDOWN_TICKS = 90;

    private LivingEntity stampedeTarget;

    public HoglinStampedeGoal(Mob mob, Squad squad) {
        super(mob, squad, 1.35);
    }

    @Override
    public boolean canUse() {
        LivingEntity target = visibleTarget();
        if (target == null || !cooldownReady()) return false;
        double dist = mob.distanceToSqr(target);
        if (dist < 4.0 * 4.0 || dist > 18.0 * 18.0) return false;
        stampedeTarget = target;
        return true;
    }

    @Override
    public void start() {
        if (stampedeTarget == null || !stampedeTarget.isAlive()) return;
        resetCooldown(COOLDOWN_TICKS);
        mob.addEffect(new MobEffectInstance(MobEffects.SPEED, 100, 0, false, true));
        mob.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 100, 0, false, true));
        announceTactic(Tactic.HOGLIN_STAMPEDE);
        TacticalEffects.signal((ServerLevel) mob.level(), mob);
        mob.getNavigation().moveTo(stampedeTarget, speed);
    }
}
