package com.warband.ai.goal;

import com.warband.text.WarbandText;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/** Periodic taunts so a bounty hunter is heard before it is seen. */
public final class BountyTauntGoal extends Goal {

    private static final int MIN_INTERVAL_TICKS = 20 * 10;
    private static final int MAX_INTERVAL_TICKS = 20 * 20;
    /**
      * Taunt lines, paired with the English each one falls back to.
      *
      * <p>Indexed keys rather than the text itself as the key, so a translator can change
      * the wording without the mod's behaviour depending on the English.
      */
    private static final String[][] LINES = {
            {"warband.taunt.smell", "I can smell you from here."},
            {"warband.taunt.stayed_home", "You should have stayed home."},
            {"warband.taunt.debt", "There is no leaving this debt unpaid."},
            {"warband.taunt.run", "Run. I prefer it when they run."},
            {"warband.taunt.end", "End of the road."},
    };

    private final Mob mob;
    private int nextTauntTick;

    public BountyTauntGoal(Mob mob) {
        this.mob = mob;
        setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        if (!(mob.level() instanceof ServerLevel)) return false;
        if (mob.tickCount < nextTauntTick) return false;
        return mob.getTarget() instanceof ServerPlayer && mob.isAlive();
    }

    @Override
    public boolean canContinueToUse() { return false; }

    @Override
    public void start() {
        ServerLevel level = (ServerLevel) mob.level();
        LivingEntity target = mob.getTarget();
        if (!(target instanceof ServerPlayer player)) return;

        SoundEvent sound = pickSound();
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), sound, SoundSource.HOSTILE, 1.1f, 0.85f);
        // Action bar — taunts are flavor, not chat clutter.
        String[] line = LINES[mob.getRandom().nextInt(LINES.length)];
        player.sendSystemMessage(WarbandText.quoted(line[0], line[1]), true);

        nextTauntTick = mob.tickCount + MIN_INTERVAL_TICKS + mob.getRandom().nextInt(MAX_INTERVAL_TICKS - MIN_INTERVAL_TICKS);
    }

    private SoundEvent pickSound() {
        int roll = mob.getRandom().nextInt(4);
        return switch (roll) {
            case 0 -> SoundEvents.EVOKER_PREPARE_ATTACK;
            case 1 -> SoundEvents.ILLUSIONER_PREPARE_MIRROR;
            case 2 -> SoundEvents.WITCH_CELEBRATE;
            default -> SoundEvents.RAVAGER_CELEBRATE;
        };
    }
}
