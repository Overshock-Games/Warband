package com.warband.ai.goal;

import com.warband.ai.Squad;
import com.warband.entity.MobData;
import com.warband.entity.Tactic;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.monster.skeleton.Stray;
import net.minecraft.world.entity.monster.spider.CaveSpider;
import net.minecraft.world.phys.Vec3;

public final class ExtendedMobTacticGoal extends SquadGoal {

    private static final int COOLDOWN_TICKS = 60;

    public ExtendedMobTacticGoal(Mob mob, Squad squad) {
        super(mob, squad, 1.15);
    }

    @Override
    public boolean canUse() {
        return visibleTarget() != null && cooldownReady();
    }

    @Override
    public void start() {
        LivingEntity target = visibleTarget();
        if (target == null || !(mob.level() instanceof ServerLevel level)) return;
        resetCooldown(COOLDOWN_TICKS);

        if (mob instanceof Guardian) {
            mob.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 100, 0, false, true));
            mob.getNavigation().moveTo(target, 1.35);
            logTactic(Tactic.GUARDIAN_SURGE);
            mob.playAmbientSound();
            return;
        }

        if (mob instanceof Shulker) {
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 0, false, true));
            logTactic(Tactic.SHULKER_LOCKDOWN);
            level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.SHULKER_AMBIENT, SoundSource.HOSTILE, 0.8f, 0.75f);
            return;
        }

        if (mob instanceof Ghast) {
            Vec3 away = mob.position().subtract(target.position()).normalize().scale(0.7).add(0.0, 0.25, 0.0);
            mob.setDeltaMovement(mob.getDeltaMovement().add(away));
            logTactic(Tactic.GHAST_REPOSITION);
            level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.GHAST_WARN, SoundSource.HOSTILE, 0.9f, 0.9f);
            // Backing off used to be free for the player; cover the retreat with a
            // spread volley so a ghast drifting away is still a threat.
            com.warband.ai.FireballVolley.ghast(level, mob, target, MobData.get(mob).difficulty());
            return;
        }

        if (mob instanceof CaveSpider && mob.distanceToSqr(target) < 5.0 * 5.0) {
            target.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0, false, true));
            mob.addEffect(new MobEffectInstance(MobEffects.SPEED, 80, 0, false, true));
            logTactic(Tactic.CAVE_SPIDER_AMBUSH);
            return;
        }

        if (isBogged() && mob.distanceToSqr(target) < 8.0 * 8.0) {
            Vec3 away = mob.position().subtract(target.position());
            if (away.lengthSqr() > 0.001) {
                mob.setDeltaMovement(mob.getDeltaMovement().add(away.normalize().scale(0.75)).add(0.0, 0.18, 0.0));
            }
            AreaEffectCloud cloud = new AreaEffectCloud(level, mob.getX(), mob.getY(), mob.getZ());
            cloud.setOwner(mob);
            cloud.setRadius(2.25F);
            cloud.setDuration(80);
            cloud.setWaitTime(0);
            cloud.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 0, false, true));
            level.addFreshEntity(cloud);
            logTactic(Tactic.BOGGED_BACKDASH);
            level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.BOGGED_AMBIENT, SoundSource.HOSTILE, 0.8f, 1.2f);
            return;
        }

        if (mob instanceof Stray && mob.distanceToSqr(target) > 5.0 * 5.0) {
            Vec3 toTarget = target.position().subtract(mob.position());
            Vec3 direction = new Vec3(toTarget.x, 0.0, toTarget.z);
            if (direction.lengthSqr() > 0.001) {
                mob.setDeltaMovement(mob.getDeltaMovement().add(direction.normalize().scale(0.35)).add(0.0, 0.55, 0.0));
            }
            if (mob instanceof RangedAttackMob ranged) {
                ranged.performRangedAttack(target, 1.0F);
            }
            logTactic(Tactic.STRAY_JUMP_SHOT);
            level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.HOSTILE, 0.8f, 0.8f);
            return;
        }

        if (mob instanceof Ravager) {
            Vec3 charge = target.position().subtract(mob.position()).normalize().scale(0.85).add(0.0, 0.12, 0.0);
            mob.setDeltaMovement(mob.getDeltaMovement().add(charge));
            logTactic(Tactic.RAVAGER_BREAKER);
            level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.0f, 0.9f);
            return;
        }

        if (mob instanceof Warden) {
            mob.addEffect(new MobEffectInstance(MobEffects.SPEED, 100, mob.distanceToSqr(target) > 144.0 ? 1 : 0, false, true));
            mob.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 100, 0, false, true));
            logTactic(Tactic.WARDEN_PRESSURE);
            level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.WARDEN_ANGRY, SoundSource.HOSTILE, 1.0f, 0.75f);
        }
    }

    private boolean isBogged() {
        return "bogged".equals(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getPath());
    }
}
