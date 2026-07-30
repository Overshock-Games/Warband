package com.warband.ai;

import com.warband.config.WarbandConfig;
import com.warband.entity.MobData;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;

/**
 * Breaks a mob out of a boat or minecart it has been parked in.
 *
 * <p>Classic anti-cheese: shove a hostile into a boat and it is neutralised
 * permanently, which makes a mod about tactical opposition look silly. Sits
 * alongside {@link com.warband.spawn.AntiFarmDirector} in intent, and like it is
 * <b>not</b> difficulty-gated — being trapped in a vehicle is an exploit at every
 * difficulty, not a challenge that smart mobs earn their way out of.
 *
 * <p>Scanned on a slow tick rather than per-entity: this only has to beat a player's
 * patience, not react instantly.
 */
public final class VehicleEscape {

    /** Server ticks between sweeps. Two seconds is well inside "no longer useful". */
    private static final int SCAN_INTERVAL = 40;
    /** Grace period so a legitimate jockey/transport moment is not instantly undone. */
    private static final int TRAPPED_TICKS = 60;

    private static int tickCounter;

    private VehicleEscape() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!WarbandConfig.vehicleEscapeEnabled) return;
            if (++tickCounter < SCAN_INTERVAL) return;
            tickCounter = 0;

            for (ServerLevel level : server.getAllLevels()) {
                for (Entity entity : level.getAllEntities()) {
                    if (!(entity instanceof Mob mob)) continue;
                    if (!(mob instanceof Enemy)) continue;
                    if (!MobData.isStamped(mob)) continue;
                    breakOut(mob);
                }
            }
        });
    }

    private static void breakOut(Mob mob) {
        Entity vehicle = mob.getVehicle();
        if (!(vehicle instanceof AbstractBoat) && !(vehicle instanceof AbstractMinecart)) return;
        // Only when it has actually been stuck for a while, and only when the vehicle
        // is not carrying it anywhere useful.
        if (mob.getTicksFrozen() > 0) return;
        if (vehicle.getDeltaMovement().horizontalDistanceSqr() > 0.005) return;
        if (mob.tickCount < TRAPPED_TICKS) return;

        mob.stopRiding();
        // Destroy rather than merely dismount: otherwise the mob walks straight back
        // in, or the player simply re-seats it.
        vehicle.hurtServer((ServerLevel) mob.level(),
                mob.damageSources().mobAttack(mob), Float.MAX_VALUE);
        if (!vehicle.isRemoved()) {
            vehicle.discard();
        }
    }
}
