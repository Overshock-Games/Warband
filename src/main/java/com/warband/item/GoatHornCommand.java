package com.warband.item;

import com.warband.compat.IllagerKinds;
import com.warband.config.WarbandConfig;
import com.warband.entity.MobData;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Adds a Warband command pulse to goat horns without replacing vanilla horn use. */
public final class GoatHornCommand {

    private static final int COOLDOWN_TICKS = 20 * 20;
    private static final double RADIUS = 32.0;
    private static final Map<UUID, Long> NEXT_USE = new HashMap<>();

    private GoatHornCommand() {
    }

    public static void register() {
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (!WarbandConfig.goatHornCommandEnabled) {
                return InteractionResult.PASS;
            }
            if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
                return InteractionResult.PASS;
            }
            if (!player.getItemInHand(hand).is(Items.GOAT_HORN)) {
                return InteractionResult.PASS;
            }

            long now = level.getGameTime();
            long next = NEXT_USE.getOrDefault(player.getUUID(), 0L);
            if (now < next) return InteractionResult.PASS;
            NEXT_USE.put(player.getUUID(), now + COOLDOWN_TICKS);

            AABB box = AABB.ofSize(player.position(), RADIUS * 2.0, RADIUS, RADIUS * 2.0);
            for (AbstractGolem golem : serverLevel.getEntitiesOfClass(AbstractGolem.class, box, AbstractGolem::isAlive)) {
                golem.addEffect(new MobEffectInstance(MobEffects.SPEED, 20 * 12, 0, false, true));
                golem.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20 * 12, 0, false, true));
            }
            for (Mob mob : serverLevel.getEntitiesOfClass(Mob.class, box, mob ->
                    mob.isAlive() && MobData.isStamped(mob) && IllagerKinds.isIllagerLike(mob))) {
                mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 8, 0, false, true));
                mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 8, 0, false, true));
                if (mob.getTarget() == player) {
                    mob.setTarget(null);
                }
            }
            return InteractionResult.PASS;
        });
    }
}
