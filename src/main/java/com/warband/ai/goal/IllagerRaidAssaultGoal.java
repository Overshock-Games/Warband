package com.warband.ai.goal;

import com.warband.ai.Squad;
import com.warband.ai.TacticalEffects;
import com.warband.compat.RaidCompat;
import com.warband.config.WarbandConfig;
import com.warband.entity.MobData;
import com.warband.entity.Role;
import com.warband.entity.Tactic;
import com.warband.illager.FactionDoctrine;
import com.warband.illager.IllagerFactionSystem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.List;

/** Raid doctrine: prioritize village defenders and keep pressure on the settlement. */
public final class IllagerRaidAssaultGoal extends SquadGoal {

    private static final int COOLDOWN_TICKS = 35;

    private LivingEntity raidTarget;

    public IllagerRaidAssaultGoal(Mob mob, Squad squad) {
        super(mob, squad, 1.2);
    }

    @Override
    public boolean canUse() {
        if (!WarbandConfig.illagerRaidDoctrineEnabled) return false;
        if (!RaidCompat.isActiveRaider(mob) || !cooldownReady()) return false;

        raidTarget = priorityTarget();
        return raidTarget != null;
    }

    @Override
    public void start() {
        if (raidTarget == null || !raidTarget.isAlive()) return;
        resetCooldown(COOLDOWN_TICKS);

        mob.setTarget(raidTarget);
        Role role = MobData.get(mob).role();
        FactionDoctrine doctrine = IllagerFactionSystem.doctrineOrDefault(mob);
        if (role == Role.LEADER) {
            raidTarget.addEffect(new MobEffectInstance(MobEffects.GLOWING, 80, 0, false, true));
            TacticalEffects.signal((ServerLevel) mob.level(), mob);
        }
        if (role == Role.BRUISER || role == Role.SKIRMISHER) {
            mob.addEffect(new MobEffectInstance(MobEffects.SPEED, 80, doctrine == FactionDoctrine.HUNT ? 1 : 0, false, true));
        }
        if (doctrine == FactionDoctrine.AMBUSH) {
            // Brief but vicious ambush burst: speed II + strength on every squadmate.
            mob.addEffect(new MobEffectInstance(MobEffects.SPEED, 80, 1, false, true));
            mob.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 80, 0, false, true));
        }
        if (doctrine == FactionDoctrine.SIEGE && role == Role.BRUISER) {
            // Heavy infantry that break the line: hit harder, take less.
            mob.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 80, 0, false, true));
            mob.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 80, 0, false, true));
        }
        if (doctrine == FactionDoctrine.COMMAND && squad.members().size() >= 2) {
            // Organized force: every squadmate hits harder and the front line shrugs.
            mob.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 80, 0, false, true));
            if (role == Role.BRUISER || role == Role.LEADER) {
                mob.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 80, 0, false, true));
            }
        }
        if (doctrine == FactionDoctrine.BURN) {
            if (raidTarget instanceof Villager) {
                raidTarget.setRemainingFireTicks(Math.max(raidTarget.getRemainingFireTicks(), 60));
            } else if (role == Role.BRUISER || role == Role.SKIRMISHER) {
                // Burn squad scorches whatever they engage, not just villagers.
                raidTarget.setRemainingFireTicks(Math.max(raidTarget.getRemainingFireTicks(), 40));
            }
        }
        if (role == Role.MARKSMAN || role == Role.SUPPORT || role == Role.LEADER) {
            announceTactic(Tactic.ILLAGER_COMMAND);
            moveTo(raidTarget.blockPosition().offset(
                    mob.getRandom().nextInt(9) - 4,
                    0,
                    mob.getRandom().nextInt(9) - 4));
            return;
        }
        announceTactic(Tactic.ILLAGER_COMMAND);
        moveTo(raidTarget.blockPosition());
    }

    private LivingEntity priorityTarget() {
        LivingEntity current = visibleTarget();
        if (current instanceof Villager || current instanceof IronGolem) {
            return current;
        }

        AABB box = AABB.ofSize(mob.position(), 48.0, 24.0, 48.0);
        List<LivingEntity> candidates = ((ServerLevel) mob.level()).getEntitiesOfClass(LivingEntity.class, box,
                entity -> entity.isAlive() && (entity instanceof Villager || entity instanceof IronGolem));
        LivingEntity defender = candidates.stream()
                .min(Comparator.comparingDouble(entity -> {
                    double distance = mob.distanceToSqr(entity);
                    return entity instanceof Villager ? distance : distance + 24.0;
                }))
                .orElse(null);
        if (defender != null) return defender;

        // Raid predation: with no village defenders left, raiders pillage every
        // animal they can reach — leashed pets, named pigs, tamed wolves, the
        // lot. The raid is the setpiece, the destruction is the point.
        if (!WarbandConfig.raidPredationEnabled) return current;
        List<Animal> prey = ((ServerLevel) mob.level()).getEntitiesOfClass(Animal.class, box,
                a -> a.isAlive());
        return prey.stream()
                .min(Comparator.comparingDouble(mob::distanceToSqr))
                .map(a -> (LivingEntity) a)
                .orElse(current);
    }
}
