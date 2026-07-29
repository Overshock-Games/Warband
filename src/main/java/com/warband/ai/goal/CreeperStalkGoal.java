package com.warband.ai.goal;

import com.warband.ai.Squad;
import com.warband.ai.TacticalEffects;
import com.warband.entity.Tactic;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/** Makes smarter creepers approach from side angles instead of beelining. */
public final class CreeperStalkGoal extends SquadGoal {

    private static final int COOLDOWN_TICKS = 70;

    private BlockPos stalkPos;

    public CreeperStalkGoal(Mob mob, Squad squad) {
        super(mob, squad, 0.95);
    }

    @Override
    public boolean canUse() {
        // A creeper too scared to approach is too scared to stalk — let vanilla's
        // cat/ocelot avoidance own the mob instead of racing it for the MOVE flag.
        if (frightened()) return false;
        LivingEntity target = visibleTarget();
        if (target == null || !cooldownReady()) return false;

        double distance = mob.distanceToSqr(target);
        if (distance < 5.0 * 5.0 || distance > 18.0 * 18.0) return false;

        Vec3 toMob = mob.position().subtract(target.position()).normalize();
        Vec3 side = new Vec3(-toMob.z, 0.0, toMob.x);
        if (mob.getRandom().nextBoolean()) {
            side = side.scale(-1.0);
        }
        Vec3 dest = target.position().add(side.scale(4.0)).add(toMob.scale(3.0));
        stalkPos = BlockPos.containing(dest.x, dest.y, dest.z);
        return true;
    }

    @Override
    public void start() {
        resetCooldown(COOLDOWN_TICKS);
        if (stalkPos != null && moveTo(stalkPos)) {
            logTactic(Tactic.CREEPER_STALK);
            TacticalEffects.search((ServerLevel) mob.level(), mob.position());
        }
    }
}
