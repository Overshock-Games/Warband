package com.warband.ai;

import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.feline.Ocelot;
import net.minecraft.world.entity.monster.Creeper;

/** Warband-specific perception modifiers for tactical AI decisions. */
public final class VisibilityRules {

    /** Vanilla {@code Creeper} flees cats and ocelots inside this radius. */
    private static final double CAT_FEAR_RADIUS = 6.0;

    private VisibilityRules() {
    }

    /**
     * True when this mob should abandon Warband tactics because something it is
     * naturally afraid of is close enough to matter. Creepers only, for now.
     *
     * <p>Vanilla puts the creeper's cat/ocelot {@code AvoidEntityGoal} at goal
     * priority 3. Warband's own creeper tactics sit at 4 and 5 so avoidance
     * outranks them, but a squadded creeper also receives {@code RegroupGoal} at
     * priority 3 — a tie, and the goal selector only lets a <i>strictly</i>
     * higher-priority goal seize a flag from a running one. Whichever of the two
     * started first kept {@code MOVE}, so cats intermittently stopped working.
     * Rather than shuffle priorities and hope, every Warband movement goal yields
     * outright while a cat is near, which is also just correct: a creeper too
     * scared to approach is too scared to flank.
     */
    public static boolean frightenedByNearbyAnimal(Mob mob) {
        if (!(mob instanceof Creeper)) return false;
        // Mirrors vanilla's AvoidEntityGoal box for these two: inflate(6, 3, 6).
        return !mob.level().getEntitiesOfClass(LivingEntity.class,
                mob.getBoundingBox().inflate(CAT_FEAR_RADIUS, 3.0, CAT_FEAR_RADIUS),
                e -> e.isAlive() && (e instanceof Cat || e instanceof Ocelot)).isEmpty();
    }

    public static boolean canUseTacticalSight(Mob mob, LivingEntity target) {
        if (target == null || !target.isAlive()) return false;
        if (!mob.hasLineOfSight(target)) return false;
        if (target.hasEffect(MobEffects.GLOWING)) return true;
        double allowed = tacticalSightRange(mob, target);
        return mob.distanceToSqr(target) <= allowed * allowed;
    }

    private static double tacticalSightRange(Mob mob, LivingEntity target) {
        AttributeInstance followRange = mob.getAttribute(Attributes.FOLLOW_RANGE);
        double range = followRange == null ? 16.0 : followRange.getValue();
        if (range <= 0.0) range = 16.0;

        double multiplier = 1.0;
        if (mob.hasEffect(MobEffects.DARKNESS)) multiplier = Math.min(multiplier, 0.20);
        if (mob.hasEffect(MobEffects.BLINDNESS)) multiplier = Math.min(multiplier, 0.34);
        if (target.hasEffect(MobEffects.INVISIBILITY) || target.isInvisible()) multiplier = Math.min(multiplier, 0.25);
        if (target.isCrouching()) multiplier = Math.min(multiplier, 0.50);
        if (target.getBbHeight() < 1.0F) multiplier = Math.min(multiplier, 0.34);
        return Math.max(2.0, range * multiplier);
    }
}
