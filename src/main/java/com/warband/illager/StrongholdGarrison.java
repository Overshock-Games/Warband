package com.warband.illager;

import com.warband.ai.SquadCoordinator;
import com.warband.compat.IllagerInvasionCompat;
import com.warband.compat.IllagerKinds;
import com.warband.compat.StructureCompat;
import com.warband.config.WarbandConfig;
import com.warband.entity.MobData;
import com.warband.entity.WarbandAttachments;
import com.warband.spawn.SpawnDirector;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.illager.Evoker;
import net.minecraft.world.entity.monster.illager.Illusioner;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Makes mansions and pillager outposts feel like faction strongholds: their
 * illagers spawn as an elevated garrison rather than scattered mobs.
 *
 * <p>A mansion's evokers and vindicators are placed straight from the structure
 * template as saved entities, they never pass through {@code Mob#finalizeSpawn},
 * so {@code SpawnDirector}'s mixin misses them. This catches them on entity load
 * instead. Naturally-spawned stronghold illagers (outpost pillagers) are handled
 * by {@code SpawnDirector} via {@link #floorFor}.
 */
public final class StrongholdGarrison {

    private static final int FALLBACK_WARMARSHAL_DELAY_TICKS = 20 * 8;
    private static final Map<String, PendingWarmarshal> PENDING_WARMARSHALS = new HashMap<>();

    private StrongholdGarrison() {
    }

    /** Register the entity-load garrison hook. Called from {@code onInitialize}. */
    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (!WarbandConfig.illagerStrongholdsEnabled) return;
            if (!(entity instanceof Mob mob)) return;
            if (mob.isRemoved() || mob.isDeadOrDying() || !mob.isAlive()) return;
            if (!IllagerKinds.isIllagerLike(mob)) return;

            BlockPos pos = mob.blockPosition();
            boolean seat = StructureCompat.inFactionSeat(level, pos);
            Integer seatSeed = seat ? StructureCompat.strongholdSeed(level, pos) : null;
            String seatKey = seat && seatSeed != null ? SeatOfPowerState.key(level, seatSeed) : null;
            SeatOfPowerState seatState = seatKey == null ? null : SeatOfPowerState.get(level);
            if (seatKey != null) {
                mob.setAttached(WarbandAttachments.STRONGHOLD_SEAT_KEY, seatKey);
            }
            if (seatState != null && Boolean.TRUE.equals(mob.getAttached(WarbandAttachments.WARMARSHAL))) {
                seatState.markCrowned(seatKey);
            }
            if (MobData.isStamped(mob)) return;

            double floor = seat ? WarbandConfig.mansionGarrisonFloor
                    : StructureCompat.inFactionCamp(level, pos) ? WarbandConfig.outpostGarrisonFloor : 0.0;
            if (floor <= 0.0) return;

            // Organize existing garrison residents into squads, spawnFormation
            // is false so a mansion is not multiplied, only coordinated.
            if (!SquadCoordinator.assignNaturalSpawn(mob, floor, false)) {
                SpawnDirector.stampVanillaAi(mob, floor);
                if (WarbandConfig.squadsEnabled) {
                    SquadCoordinator.bindStampedSolo(mob, level);
                }
            }

            maybeCrownOrQueueWarmarshal(level, mob, seatKey, seatState);
        });
        ServerTickEvents.END_SERVER_TICK.register(StrongholdGarrison::tickPendingWarmarshals);
    }

    /**
     * Difficulty floor for the stronghold containing {@code pos}, the mansion
     * or outpost garrison level, or {@code 0.0} if it is not in a stronghold.
     */
    public static double floorFor(ServerLevel level, BlockPos pos) {
        if (!WarbandConfig.illagerStrongholdsEnabled) return 0.0;
        if (StructureCompat.inFactionSeat(level, pos)) return WarbandConfig.mansionGarrisonFloor;
        if (StructureCompat.inFactionCamp(level, pos)) return WarbandConfig.outpostGarrisonFloor;
        return 0.0;
    }

    public static String seatKey(ServerLevel level, BlockPos pos) {
        if (!StructureCompat.inFactionSeat(level, pos)) return "";
        Integer seed = StructureCompat.strongholdSeed(level, pos);
        return seed == null ? "" : SeatOfPowerState.key(level, seed);
    }

    public static boolean isSeatBroken(ServerLevel level, BlockPos pos) {
        String key = seatKey(level, pos);
        return !key.isEmpty() && SeatOfPowerState.get(level).isBroken(key);
    }

    public static void markSeatBroken(ServerLevel level, BlockPos pos) {
        String key = seatKey(level, pos);
        markSeatBroken(level, key);
    }

    public static void markSeatBroken(ServerLevel level, String key) {
        if (!key.isEmpty()) {
            SeatOfPowerState.get(level).markBroken(key);
        }
    }

    private static boolean isWarmarshalCandidate(Mob mob) {
        if (IllagerInvasionCompat.isLoaded()) {
            return IllagerKinds.isSeatBossCandidate(mob);
        }
        return mob instanceof Evoker || mob instanceof Illusioner;
    }

    private static void maybeCrownOrQueueWarmarshal(ServerLevel level, Mob mob, String seatKey,
                                                    SeatOfPowerState seatState) {
        if (seatState == null || seatState.isBroken(seatKey) || seatState.isCrowned(seatKey)) return;
        if (!isWarmarshalCandidate(mob) || nearbyWarmarshal(level, mob)) return;
        if (!IllagerInvasionCompat.isLoaded() || IllagerKinds.isSummoner(mob)) {
            crownWarmarshal(level, mob, seatKey, seatState);
            return;
        }
        PENDING_WARMARSHALS.putIfAbsent(seatKey,
                new PendingWarmarshal(level, mob.getUUID(), level.getGameTime() + FALLBACK_WARMARSHAL_DELAY_TICKS));
    }

    private static void tickPendingWarmarshals(MinecraftServer server) {
        if (PENDING_WARMARSHALS.isEmpty()) return;
        Iterator<Map.Entry<String, PendingWarmarshal>> iterator = PENDING_WARMARSHALS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, PendingWarmarshal> entry = iterator.next();
            PendingWarmarshal pending = entry.getValue();
            ServerLevel level = pending.level();
            if (level.getServer() != server || level.getGameTime() < pending.readyAt()) continue;
            iterator.remove();

            SeatOfPowerState seatState = SeatOfPowerState.get(level);
            String seatKey = entry.getKey();
            if (seatState.isBroken(seatKey) || seatState.isCrowned(seatKey)) continue;
            Entity entity = level.getEntityInAnyDimension(pending.mobId());
            if (!(entity instanceof Mob mob) || !mob.isAlive() || mob.isRemoved()) continue;
            if (!seatKey.equals(mob.getAttached(WarbandAttachments.STRONGHOLD_SEAT_KEY))) continue;
            if (nearbyInvoker(level, mob, seatKey) || nearbyWarmarshal(level, mob)) continue;
            crownWarmarshal(level, mob, seatKey, seatState);
        }
    }

    private static void crownWarmarshal(ServerLevel level, Mob mob, String seatKey, SeatOfPowerState seatState) {
        seatState.markCrowned(seatKey);
        PENDING_WARMARSHALS.remove(seatKey);
        SpawnDirector.crownWarmarshal(mob);
    }

    private static boolean nearbyWarmarshal(ServerLevel level, Mob mob) {
        AABB box = AABB.ofSize(mob.position(), 96.0, 48.0, 96.0);
        return !level.getEntitiesOfClass(Mob.class, box, other ->
                other != mob
                        && other.isAlive()
                        && Boolean.TRUE.equals(other.getAttached(WarbandAttachments.WARMARSHAL))).isEmpty();
    }

    private static boolean nearbyInvoker(ServerLevel level, Mob mob, String seatKey) {
        AABB box = AABB.ofSize(mob.position(), 96.0, 48.0, 96.0);
        return !level.getEntitiesOfClass(Mob.class, box, other ->
                other != mob
                        && other.isAlive()
                        && seatKey.equals(other.getAttached(WarbandAttachments.STRONGHOLD_SEAT_KEY))
                        && IllagerKinds.isSummoner(other)).isEmpty();
    }

    private record PendingWarmarshal(ServerLevel level, UUID mobId, long readyAt) {
    }
}
