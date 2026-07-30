package com.warband.ai.goal;

import com.warband.ai.Squad;
import com.warband.ai.TacticalEffects;
import com.warband.entity.Tactic;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;

/**
 * High-level zombies keep pressure in water instead of passively sinking.
 * Zombies that commit too long take controlled drowning damage, accelerating
 * vanilla drowned conversion instead of merely duplicating vanilla behavior.
 */
public final class WaterCommitGoal extends SquadGoal {

    private static final int COOLDOWN_TICKS = 20;

    private int waterCommitTicks;
    private LivingEntity waterTarget;

    public WaterCommitGoal(Mob mob, Squad squad) {
        super(mob, squad, 1.05);
    }

    @Override
    public boolean canUse() {
        if (!mob.isInWater() || !cooldownReady()) return false;
        LivingEntity target = visibleTarget();
        if (target == null) return false;
        waterTarget = target;
        return true;
    }

    @Override
    public void start() {
        if (waterTarget == null || !waterTarget.isAlive()) return;
        resetCooldown(COOLDOWN_TICKS);
        if (mob.getNavigation().moveTo(waterTarget, speed)) {
            pressureToward(waterTarget);
            accelerateDrowning();
            announceTactic(Tactic.WATER_COMMIT);
            TacticalEffects.waterCommit((ServerLevel) mob.level(), mob.position());
        }
    }

    private void pressureToward(LivingEntity target) {
        Vec3 delta = target.position().subtract(mob.position());
        Vec3 push = new Vec3(
                clamp(delta.x * 0.08, -0.12, 0.12),
                target.getY() > mob.getY() + 0.4 ? 0.12 : 0.02,
                clamp(delta.z * 0.08, -0.12, 0.12));
        mob.setDeltaMovement(mob.getDeltaMovement().add(push));
    }

    private void accelerateDrowning() {
        if (!(mob instanceof Zombie) || mob.getAirSupply() > 0) {
            waterCommitTicks = 0;
            return;
        }
        waterCommitTicks += 20;
        if (waterCommitTicks >= 60) {
            waterCommitTicks = 0;
            mob.hurtServer((ServerLevel) mob.level(), mob.damageSources().source(DamageTypes.DROWN), 2.0f);
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
