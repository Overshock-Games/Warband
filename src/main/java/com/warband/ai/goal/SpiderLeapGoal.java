package com.warband.ai.goal;

import com.warband.ai.Squad;
import com.warband.ai.TacticalEffects;
import com.warband.entity.Tactic;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * When a spider is positioned well above its target (typically on a wall or
 * ceiling), it releases and arcs down at them with a visible web-string trail.
 * Reads as a deliberate drop attack, not random falling.
 */
public final class SpiderLeapGoal extends SquadGoal {

    private static final int COOLDOWN_TICKS = 20 * 8;
    private static final double TRIGGER_RANGE_SQR = 10.0 * 10.0;
    private static final double MIN_HEIGHT_ABOVE = 3.0;

    public SpiderLeapGoal(Mob mob, Squad squad) {
        super(mob, squad, 1.0);
        setFlags(EnumSet.of(Flag.JUMP, Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!cooldownReady()) return false;
        LivingEntity target = visibleTarget();
        if (target == null) return false;
        if (mob.getY() - target.getY() < MIN_HEIGHT_ABOVE) return false;
        if (mob.distanceToSqr(target) > TRIGGER_RANGE_SQR) return false;
        // Only leap if not already in mid-air, so a falling spider doesn't
        // re-trigger every tick.
        return mob.onGround() || mob.horizontalCollision;
    }

    @Override
    public boolean canContinueToUse() { return false; }

    @Override
    public void start() {
        resetCooldown(COOLDOWN_TICKS);
        LivingEntity target = mob.getTarget();
        if (target == null) return;

        Vec3 from = mob.position().add(0.0, mob.getBbHeight() * 0.5, 0.0);
        Vec3 to = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
        Vec3 horizontal = new Vec3(to.x - from.x, 0.0, to.z - from.z);
        double dist = horizontal.length();
        if (dist < 0.01) return;

        // Push horizontally toward target, with a small upward kick so the arc
        // reads as a leap rather than a straight drop.
        Vec3 launch = horizontal.normalize().scale(Math.min(0.65, dist * 0.12));
        mob.setDeltaMovement(launch.x, 0.35, launch.z);
        mob.hurtMarked = true;

        ServerLevel level = (ServerLevel) mob.level();
        TacticalEffects.webTrail(level, from, to);
        TacticalEffects.web(level, mob.blockPosition());
        announceTactic(Tactic.SPIDER_WEB);
    }
}
