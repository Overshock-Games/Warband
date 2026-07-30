package com.warband.ai.goal;

import com.warband.ai.Squad;
import com.warband.ai.TacticalEffects;
import com.warband.entity.Tactic;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

/**
 * Witches act as rear support: throw a Regeneration splash potion at a wounded
 * squadmate, or pre-buff a healthy frontline ally with Strength the moment a
 * threat is visible to the squad. Uses real thrown potion entities so the toss
 * is visible (and splash radius can catch adjacent allies, which is a feature).
 */
public final class WitchSupportGoal extends SquadGoal {

    private static final int COOLDOWN_TICKS = 100;
    private static final double SUPPORT_RADIUS_SQR = 12.0 * 12.0;

    private Mob supportTarget;
    private boolean healSupport;

    public WitchSupportGoal(Mob mob, Squad squad) {
        super(mob, squad, 1.0);
    }

    @Override
    public boolean canUse() {
        if (squad.members().size() < 2 || !cooldownReady()) return false;

        Mob wounded = null;
        Mob frontline = null;
        float lowest = 1.0f;
        for (Mob ally : squad.members()) {
            if (ally == mob || !ally.isAlive()) continue;
            if (mob.distanceToSqr(ally) > SUPPORT_RADIUS_SQR) continue;
            float ratio = ally.getHealth() / ally.getMaxHealth();
            if (ratio < lowest) {
                wounded = ally;
                lowest = ratio;
            }
            if (frontline == null || ally.distanceToSqr(mob) > frontline.distanceToSqr(mob)) {
                frontline = ally;
            }
        }

        if (wounded != null && lowest < 0.55f) {
            supportTarget = wounded;
            healSupport = true;
            return true;
        }

        LivingEntity threat = visibleTarget();
        if (threat != null && frontline != null) {
            supportTarget = frontline;
            healSupport = false;
            return true;
        }

        if (wounded != null && lowest < 1.0f) {
            supportTarget = wounded;
            healSupport = false;
            return true;
        }
        return false;
    }

    @Override
    public void start() {
        if (supportTarget == null || !supportTarget.isAlive()) return;
        resetCooldown(COOLDOWN_TICKS);
        throwSupportPotion(healSupport ? Potions.REGENERATION : Potions.STRENGTH);
        announceTactic(Tactic.WITCH_SUPPORT);
        TacticalEffects.signal((ServerLevel) mob.level(), mob);
    }

    private void throwSupportPotion(net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion> potion) {
        ServerLevel level = (ServerLevel) mob.level();
        ItemStack stack = PotionContents.createItemStack(Items.SPLASH_POTION, potion);
        ThrownSplashPotion thrown = new ThrownSplashPotion(level, mob, stack);
        // Lob it: aim slightly above the target's feet, mirroring vanilla witch
        // throw arc (pitch -20°).
        double dx = supportTarget.getX() - mob.getX();
        double dy = supportTarget.getEyeY() - 1.1 - thrown.getY();
        double dz = supportTarget.getZ() - mob.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        thrown.setXRot(thrown.getXRot() - 20.0f);
        thrown.shoot(dx, dy + horizontal * 0.2, dz, 0.75f, 8.0f);
        level.addFreshEntity(thrown);
    }
}
