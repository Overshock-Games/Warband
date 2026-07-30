package com.warband.ai.goal;

import com.warband.ai.Squad;
import com.warband.ai.TacticalEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

/** Moves toward the blackboard's decaying last-known position after LOS loss. */
public final class InvestigateLastKnownGoal extends SquadGoal {

    private static final int COOLDOWN_TICKS = 30;

    private BlockPos investigatePos;

    public InvestigateLastKnownGoal(Mob mob, Squad squad) {
        super(mob, squad, 1.0);
    }

    @Override
    public boolean canUse() {
        if (visibleTarget() != null || !cooldownReady()) return false;

        // Own sighting as well as the squad's: a mob with no real squad had a
        // permanently null blackboard, so solo mobs never searched for a lost player.
        BlockPos lastKnown = rememberedTargetPos();
        if (lastKnown == null || mob.blockPosition().distSqr(lastKnown) < 4.0) return false;
        investigatePos = lastKnown;
        return true;
    }

    @Override
    public void start() {
        resetCooldown(COOLDOWN_TICKS);
        if (investigatePos != null && moveTo(investigatePos)) {
            TacticalEffects.search((ServerLevel) mob.level(), mob.position());
        }
    }
}
