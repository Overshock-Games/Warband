package com.warband.mixin;

import com.warband.config.WarbandConfig;
import com.warband.entity.MobData;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Difficulty-scaled shot cadence and accuracy for skeletons.
 *
 * <p>Vanilla ties both to the world difficulty setting alone, so a Warband skeleton
 * at difficulty 0.9 shoots exactly like one standing at world spawn. Warband's whole
 * premise is that the local scalar governs threat, and archery was the largest
 * remaining place where it did not.
 *
 * <p>Both effects are bounded by config ({@code rangedCadenceBonusMax},
 * {@code rangedAccuracyBonusMax}) and scale from zero, so this cannot produce
 * hitscan snipers — a fully-ramped skeleton fires somewhat faster and somewhat
 * straighter, not perfectly.
 *
 * <p>Skeletons only. Pillager crossbows run through {@code RangedCrossbowAttackGoal}
 * with no comparable seam, so they are deliberately untouched rather than reworked.
 */
@Mixin(AbstractSkeleton.class)
public abstract class AbstractSkeletonRangedMixin {

    /** Normal-difficulty shot interval. */
    @Inject(method = "getAttackInterval", at = @At("RETURN"), cancellable = true)
    private void warband$fasterCadence(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(warband$scaleInterval(cir.getReturnValueI()));
    }

    /** Hard-difficulty shot interval; vanilla picks between the two by world difficulty. */
    @Inject(method = "getHardAttackInterval", at = @At("RETURN"), cancellable = true)
    private void warband$fasterHardCadence(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(warband$scaleInterval(cir.getReturnValueI()));
    }

    /**
     * Tightens the arrow spread. Index 7 is the {@code inaccuracy} parameter of
     * {@code spawnProjectileUsingShoot(Projectile, ServerLevel, ItemStack, double,
     * double, double, float velocity, float inaccuracy)} — a lower value is a
     * straighter shot.
     */
    @ModifyArg(
            method = "performRangedAttack",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/projectile/Projectile;"
                            + "spawnProjectileUsingShoot("
                            + "Lnet/minecraft/world/entity/projectile/Projectile;"
                            + "Lnet/minecraft/server/level/ServerLevel;"
                            + "Lnet/minecraft/world/item/ItemStack;DDDFF)"
                            + "Lnet/minecraft/world/entity/projectile/Projectile;"),
            index = 7)
    private float warband$tighterSpread(float inaccuracy) {
        if (!WarbandConfig.rangedTuningEnabled) return inaccuracy;
        double difficulty = warband$difficulty();
        if (difficulty <= 0.0) return inaccuracy;
        double reduction = WarbandConfig.rangedAccuracyBonusMax * difficulty;
        return (float) (inaccuracy * (1.0 - Math.min(1.0, reduction)));
    }

    private int warband$scaleInterval(int interval) {
        if (!WarbandConfig.rangedTuningEnabled) return interval;
        double difficulty = warband$difficulty();
        if (difficulty <= 0.0) return interval;
        double reduction = WarbandConfig.rangedCadenceBonusMax * difficulty;
        // Never below 4 ticks: a floor keeps this "faster", not a machine gun.
        return Math.max(4, (int) Math.round(interval * (1.0 - Math.min(0.9, reduction))));
    }

    /** 0.0 for unstamped skeletons, so vanilla spawns keep vanilla archery exactly. */
    private double warband$difficulty() {
        Mob self = (Mob) (Object) this;
        if (!MobData.isStamped(self)) return 0.0;
        return MobData.get(self).difficulty();
    }
}
