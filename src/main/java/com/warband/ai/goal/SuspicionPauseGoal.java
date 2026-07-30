package com.warband.ai.goal;

import com.warband.ai.PerceptionCues;
import com.warband.config.WarbandConfig;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/**
 * The visible half of a suspicious state: the mob stops dead and stares.
 *
 * <p>{@link PerceptionCues} owns the state machine and the audio, which is the channel
 * that survives a player being behind cover or in the dark. This is the tell for a
 * player who <i>can</i> see: a mob halting mid-wander and turning to look is
 * unmistakable, entirely diegetic, and needs no new assets.
 *
 * <p>Only interrupts wandering. It takes MOVE at priority 1 — above the vanilla stroll
 * goals — but requires no target, so a mob that has actually found someone is never
 * held up by it. Short by design: this is a beat, not a stun.
 */
public final class SuspicionPauseGoal extends Goal implements WarbandGoal {

    private static final int HOLD_TICKS = 24;
    /** Quiet window after a pause, so a mob does not freeze every scan tick. */
    private static final int REARM_TICKS = 20 * 5;
    private static final double LOOK_RANGE = 24.0;

    private final Mob mob;
    private int holdRemaining;
    private int rearmAtTick;

    public SuspicionPauseGoal(Mob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!WarbandConfig.perceptionCuesEnabled) return false;
        // A mob with a target is past being suspicious.
        if (mob.getTarget() != null) return false;
        if (mob.tickCount < rearmAtTick) return false;
        return PerceptionCues.isSuspicious(mob);
    }

    @Override
    public boolean canContinueToUse() {
        return holdRemaining > 0 && mob.getTarget() == null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        holdRemaining = HOLD_TICKS;
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        holdRemaining--;
        mob.getNavigation().stop();
        Player nearest = mob.level().getNearestPlayer(mob, LOOK_RANGE);
        if (nearest != null) {
            mob.getLookControl().setLookAt(nearest, 30.0F, 30.0F);
        }
    }

    @Override
    public void stop() {
        holdRemaining = 0;
        rearmAtTick = mob.tickCount + REARM_TICKS;
    }
}
