package com.warband.ai.goal;

import com.warband.ai.Squad;
import com.warband.ai.SquadCoordinator;
import com.warband.ai.TacticalEffects;
import com.warband.ai.IllagerLoadGuard;
import com.warband.entity.MobData;
import com.warband.entity.Role;
import com.warband.entity.Tactic;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/** Illagers behave like disciplined raiders, turning squad losses into pressure. */
public final class IllagerCommandGoal extends SquadGoal {

    private static final int COOLDOWN_TICKS = 70;

    private LivingEntity commandTarget;
    private boolean leaderPulse;
    private boolean vengefulPulse;

    public IllagerCommandGoal(Mob mob, Squad squad) {
        super(mob, squad, 1.0);
    }

    @Override
    public boolean canUse() {
        if (IllagerLoadGuard.tooDenseForHeavyDoctrine(mob)) return false;
        LivingEntity target = visibleTarget();
        if (target == null || squad.members().size() < 2 || !cooldownReady()) return false;

        commandTarget = target;
        leaderPulse = MobData.get(mob).role() == Role.LEADER;
        vengefulPulse = squad.morale() < 0.60f;
        return true;
    }

    @Override
    public void start() {
        if (commandTarget == null || !commandTarget.isAlive()) return;
        resetCooldown(COOLDOWN_TICKS);
        for (Mob ally : squad.members()) {
            if (!ally.isAlive() || mob.distanceToSqr(ally) >= 18.0 * 18.0) continue;
            ally.setTarget(commandTarget);
            ally.addEffect(new MobEffectInstance(MobEffects.SPEED, 90, vengefulPulse ? 1 : 0, false, true));
            if (leaderPulse || vengefulPulse) {
                ally.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 90, 0, false, true));
            }
        }
        if (vengefulPulse && squad.canCallBackup()) {
            SquadCoordinator.callBackup(squad, mob.blockPosition());
            squad.markBackupCalled();
        }
        if (leaderPulse) {
            commandTarget.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0, false, true));
        }
        announceTactic(Tactic.ILLAGER_COMMAND);
        TacticalEffects.signal((ServerLevel) mob.level(), mob);
    }
}
