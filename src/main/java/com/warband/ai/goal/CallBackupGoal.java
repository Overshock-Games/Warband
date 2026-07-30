package com.warband.ai.goal;

import com.warband.ai.Squad;
import com.warband.ai.SquadCoordinator;
import com.warband.ai.TacticalBarks;
import com.warband.ai.TacticalEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

/** Requests limited reinforcements when a squad is under pressure. */
public final class CallBackupGoal extends SquadGoal {

    private static final int COOLDOWN_TICKS = 40;

    public CallBackupGoal(Mob mob, Squad squad) {
        super(mob, squad, 1.0);
    }

    @Override
    public boolean canUse() {
        if (!squad.canCallBackup() || visibleTarget() == null || !cooldownReady()) return false;
        return mob.getHealth() / mob.getMaxHealth() <= 0.45f || squad.morale() < 0.45f;
    }

    @Override
    public void start() {
        resetCooldown(COOLDOWN_TICKS);
        if (SquadCoordinator.callBackup(squad, mob.blockPosition())) {
            TacticalEffects.signal((ServerLevel) mob.level(), mob);
            squad.markBackupCalled();
        }
        // Also wave in idle neighboring squads, so a fight isn't fought alone
        // even when no fresh recruit was available.
        SquadCoordinator.broadcastDistress(squad, mob.blockPosition());
        // The one rally that has no Tactic of its own, and the one players most need
        // to hear coming.
        TacticalBarks.rally(mob);
    }
}
