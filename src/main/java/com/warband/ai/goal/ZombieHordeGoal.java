package com.warband.ai.goal;

import com.warband.ai.Squad;
import com.warband.ai.TacticalEffects;
import com.warband.entity.Tactic;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Zombie-family mobs surround a target by distributing themselves around it
 * by squad index, so members approach from all sides instead of stacking on
 * one flank or beelining in single file.
 *
 * <p>Falls back through alternative bearings when the preferred approach is
 * unreachable. A single unreachable slot used to abort the whole tactic, which
 * left the mob defaulting to the same direct approach as everyone else — the
 * reported behaviour of a horde bunching into one corner instead of trying
 * different paths.
 */
public final class ZombieHordeGoal extends SquadGoal {

    private static final int COOLDOWN_TICKS = 45;
    private static final double ENCIRCLE_RADIUS = 4.0;
    /** Bearings tried, in order, as fractions of a full turn away from the ideal slot. */
    private static final double[] BEARING_FALLBACKS = {0.0, 0.25, -0.25, 0.5};

    private LivingEntity hordeTarget;
    private double baseAngle;

    public ZombieHordeGoal(Mob mob, Squad squad) {
        super(mob, squad, 1.0);
    }

    @Override
    public boolean canUse() {
        LivingEntity target = visibleTarget();
        if (target == null || !cooldownReady()) return false;

        double distance = mob.distanceToSqr(target);
        if (distance < 3.0 * 3.0 || distance > 16.0 * 16.0) return false;

        int index = squad.members().indexOf(mob);
        int total = Math.max(squad.members().size(), 1);
        if (index < 0) index = (int) (mob.getId() & 0x7fffffff) % total;

        hordeTarget = target;
        baseAngle = (index * (Math.PI * 2.0)) / total;
        return true;
    }

    @Override
    public void start() {
        resetCooldown(COOLDOWN_TICKS);
        if (hordeTarget == null) return;

        for (double offset : BEARING_FALLBACKS) {
            double angle = baseAngle + offset * Math.PI * 2.0;
            Vec3 dest = hordeTarget.position().add(
                    Math.cos(angle) * ENCIRCLE_RADIUS, 0.0, Math.sin(angle) * ENCIRCLE_RADIUS);
            if (moveTo(BlockPos.containing(dest.x, dest.y, dest.z))) {
                if (squad.members().size() > 1) {
                    logTactic(Tactic.ZOMBIE_HORDE);
                    TacticalEffects.search((ServerLevel) mob.level(), mob.position());
                }
                return;
            }
        }
    }
}
