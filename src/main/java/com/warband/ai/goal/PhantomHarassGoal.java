package com.warband.ai.goal;

import com.warband.ai.Squad;
import com.warband.ai.TacticalEffects;
import com.warband.entity.Tactic;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/** Phantoms make repeated high-angle harassment passes. */
public final class PhantomHarassGoal extends SquadGoal {

    private static final int COOLDOWN_TICKS = 70;

    private BlockPos pass;

    public PhantomHarassGoal(Mob mob, Squad squad) {
        super(mob, squad, 1.2);
    }

    @Override
    public boolean canUse() {
        LivingEntity target = visibleTarget();
        if (target == null || !cooldownReady()) return false;

        pass = target.blockPosition().offset(
                mob.getRandom().nextInt(9) - 4,
                5 + mob.getRandom().nextInt(4),
                mob.getRandom().nextInt(9) - 4);
        return true;
    }

    @Override
    public void start() {
        resetCooldown(COOLDOWN_TICKS);
        if (pass != null && moveTo(pass)) {
            announceTactic(Tactic.PHANTOM_HARASS);
            TacticalEffects.search((ServerLevel) mob.level(), mob.position());
        }
    }
}
