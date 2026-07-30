package com.warband.ai;

import com.warband.config.WarbandConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.phys.Vec3;

/**
 * Extra fireballs for ghasts and blazes, with spread that varies per shot.
 *
 * <p>Both mobs are entirely predictable in vanilla: a fixed cadence on a fixed
 * trajectory, which a player learns once and then sidesteps forever. Warband already
 * repositions them ({@code GHAST_REPOSITION}, {@code BLAZE_HOVER}); this makes the
 * shots themselves worth respecting.
 *
 * <p>Added on top of vanilla's own attack goals rather than replacing them, so the
 * baseline behaviour and its sound cues stay intact. Spread scales <i>down</i> with
 * difficulty, so a volley is more numerous and tighter as pressure rises, without
 * ever becoming perfectly aimed.
 */
public final class FireballVolley {

    /** Extra large fireballs a ghast adds, at difficulty 1.0. */
    private static final int MAX_EXTRA_GHAST_SHOTS = 2;
    /** Small fireballs a blaze adds per burst, at difficulty 1.0. */
    private static final int MAX_EXTRA_BLAZE_SHOTS = 3;

    private FireballVolley() {
    }

    /** Fires a short spread volley of large fireballs. No-op when disabled. */
    public static void ghast(ServerLevel level, Mob ghast, LivingEntity target, double difficulty) {
        if (!WarbandConfig.ghastVolleyEnabled) return;
        int shots = scaledShots(MAX_EXTRA_GHAST_SHOTS, difficulty);
        for (int i = 0; i < shots; i++) {
            Vec3 aim = spread(ghast, target, difficulty, 2.5);
            LargeFireball fireball = new LargeFireball(level, ghast, aim, 1);
            fireball.setPos(ghast.getX(), ghast.getY(0.5) + 0.5, ghast.getZ());
            level.addFreshEntity(fireball);
        }
    }

    /** Fires a short spread volley of small fireballs. No-op when disabled. */
    public static void blaze(ServerLevel level, Mob blaze, LivingEntity target, double difficulty) {
        if (!WarbandConfig.blazeFireballVariationEnabled) return;
        int shots = scaledShots(MAX_EXTRA_BLAZE_SHOTS, difficulty);
        for (int i = 0; i < shots; i++) {
            Vec3 aim = spread(blaze, target, difficulty, 1.5);
            SmallFireball fireball = new SmallFireball(level, blaze, aim);
            fireball.setPos(blaze.getX(), blaze.getY(0.5) + 0.5, blaze.getZ());
            level.addFreshEntity(fireball);
        }
    }

    /** At least one extra shot once the feature is live, rising with difficulty. */
    private static int scaledShots(int max, double difficulty) {
        return Math.max(1, (int) Math.round(max * Math.max(0.0, Math.min(1.0, difficulty))));
    }

    /**
     * Aim vector toward the target, jittered. {@code baseSpread} is the miss cone at
     * difficulty 0; it tightens as difficulty climbs so high-pressure volleys are
     * threatening rather than merely noisy.
     */
    private static Vec3 spread(Mob shooter, LivingEntity target, double difficulty, double baseSpread) {
        Vec3 toTarget = target.getEyePosition().subtract(shooter.position());
        double jitter = baseSpread * (1.0 - 0.6 * Math.max(0.0, Math.min(1.0, difficulty)));
        return toTarget.add(
                (shooter.getRandom().nextDouble() - 0.5) * jitter,
                (shooter.getRandom().nextDouble() - 0.5) * jitter * 0.5,
                (shooter.getRandom().nextDouble() - 0.5) * jitter);
    }
}
