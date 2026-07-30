package com.warband.ai.goal;

import com.warband.ai.Squad;
import com.warband.ai.TacticalEffects;
import com.warband.entity.Tactic;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;

/** Piglins grow bold in groups and panic/regroup when morale collapses. */
public final class PiglinSocialGoal extends SquadGoal {

    private static final int COOLDOWN_TICKS = 60;

    private BlockPos regroupPos;
    private boolean strengthPulse;

    public PiglinSocialGoal(Mob mob, Squad squad) {
        super(mob, squad, 1.15);
    }

    @Override
    public boolean canUse() {
        if (!cooldownReady()) return false;
        regroupPos = null;
        strengthPulse = false;

        if (squad.morale() < 0.45f) {
            regroupPos = BlockPos.containing(squad.center().x, squad.center().y, squad.center().z);
            return true;
        }

        if (squad.members().size() >= 3 && visibleTarget() != null) {
            strengthPulse = true;
            return true;
        }
        return false;
    }

    @Override
    public void start() {
        resetCooldown(COOLDOWN_TICKS);
        if (regroupPos != null && moveTo(regroupPos)) {
            mob.addEffect(new MobEffectInstance(MobEffects.SPEED, 80, 0, false, true));
            announceTactic(Tactic.PIGLIN_SOCIAL);
            TacticalEffects.signal((ServerLevel) mob.level(), mob);
        } else if (strengthPulse) {
            mob.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 80, 0, false, true));
            announceTactic(Tactic.PIGLIN_SOCIAL);
            TacticalEffects.signal((ServerLevel) mob.level(), mob);
        }
    }
}
