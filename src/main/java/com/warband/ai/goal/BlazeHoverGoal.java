package com.warband.ai.goal;

import com.warband.ai.Squad;
import com.warband.ai.FireballVolley;
import com.warband.ai.TacticalEffects;
import com.warband.entity.MobData;
import com.warband.entity.Tactic;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/** Blazes keep vertical fire pressure instead of letting melee pin them. */
public final class BlazeHoverGoal extends SquadGoal {

    private static final int COOLDOWN_TICKS = 55;

    private BlockPos hover;

    public BlazeHoverGoal(Mob mob, Squad squad) {
        super(mob, squad, 1.0);
    }

    @Override
    public boolean canUse() {
        LivingEntity target = visibleTarget();
        if (target == null || !cooldownReady()) return false;

        double distance = mob.distanceToSqr(target);
        if (distance > 14.0 * 14.0) return false;

        hover = target.blockPosition().offset(
                mob.getRandom().nextInt(9) - 4,
                4 + mob.getRandom().nextInt(3),
                mob.getRandom().nextInt(9) - 4);
        return true;
    }

    @Override
    public void start() {
        resetCooldown(COOLDOWN_TICKS);
        if (hover != null && moveTo(hover)) {
            announceTactic(Tactic.BLAZE_HOVER);
            TacticalEffects.signal((ServerLevel) mob.level(), mob);
            // Fire while relocating, so taking the high ground costs the player
            // something instead of just being a repositioning animation.
            LivingEntity target = visibleTarget();
            if (target != null) {
                FireballVolley.blaze((ServerLevel) mob.level(), mob, target,
                        MobData.get(mob).difficulty());
            }
        }
    }
}
