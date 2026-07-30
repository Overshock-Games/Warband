package com.warband.illager;

import com.warband.compat.IllagerKinds;
import com.warband.compat.StructureCompat;
import com.warband.config.WarbandConfig;
import com.warband.entity.WarbandAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.phys.AABB;

import java.util.List;

public final class IllagerFactionSystem {

    private IllagerFactionSystem() {
    }

    public static void assignIfNeeded(Mob mob) {
        if (!WarbandConfig.illagerFactionsEnabled) return;
        if (!IllagerKinds.isIllagerLike(mob)) return;
        if (!mob.hasAttached(WarbandAttachments.ILLAGER_FACTION)) {
            setFaction(mob, chooseFaction(mob));
        }
        assignSeatKeyIfNeeded(mob);
    }

    public static void setFaction(Mob mob, IllagerFaction faction) {
        if (!WarbandConfig.illagerFactionsEnabled) return;
        mob.setAttached(WarbandAttachments.ILLAGER_FACTION, IllagerFactionData.of(faction));
    }

    public static IllagerFaction factionOrDefault(Mob mob) {
        IllagerFactionData data = mob.getAttached(WarbandAttachments.ILLAGER_FACTION);
        return data != null ? data.faction() : chooseFaction(mob);
    }

    public static FactionDoctrine doctrineOrDefault(Mob mob) {
        if (!WarbandConfig.illagerDoctrineEnabled) return FactionDoctrine.COMMAND;
        IllagerFactionData data = mob.getAttached(WarbandAttachments.ILLAGER_FACTION);
        return data != null ? data.doctrine() : factionOrDefault(mob).doctrine();
    }

    private static IllagerFaction chooseFaction(Mob mob) {
        IllagerFaction raidFaction = nearbyRaidFaction(mob);
        if (raidFaction != null) return raidFaction;
        // A whole mansion or outpost resolves to one faction, its stronghold.
        if (mob.level() instanceof ServerLevel level) {
            Integer strongholdSeed = StructureCompat.strongholdSeed(level, mob.blockPosition());
            if (strongholdSeed != null) {
                return IllagerFaction.pick(strongholdSeed);
            }
        }
        int regionX = Math.floorDiv(mob.getBlockX(), 512);
        int regionZ = Math.floorDiv(mob.getBlockZ(), 512);
        int seed = regionX * 734_287 + regionZ * 912_271 + mob.level().dimension().hashCode();
        return IllagerFaction.pick(seed);
    }

    private static void assignSeatKeyIfNeeded(Mob mob) {
        if (mob.hasAttached(WarbandAttachments.STRONGHOLD_SEAT_KEY)) return;
        if (!(mob.level() instanceof ServerLevel level)) return;
        if (!StructureCompat.inFactionSeat(level, mob.blockPosition())) return;
        Integer seed = StructureCompat.strongholdSeed(level, mob.blockPosition());
        if (seed != null) {
            mob.setAttached(WarbandAttachments.STRONGHOLD_SEAT_KEY, SeatOfPowerState.key(level, seed));
        }
    }

    private static IllagerFaction nearbyRaidFaction(Mob mob) {
        if (!(mob instanceof Raider raider) || raider.getCurrentRaid() == null || !(mob.level() instanceof ServerLevel level)) {
            return null;
        }
        AABB box = AABB.ofSize(mob.position(), 96.0, 32.0, 96.0);
        List<Raider> raiders = level.getEntitiesOfClass(Raider.class, box, other ->
                other != mob && other.isAlive() && other.getCurrentRaid() == raider.getCurrentRaid());
        for (Raider other : raiders) {
            if (other instanceof Mob otherMob) {
                IllagerFactionData data = otherMob.getAttached(WarbandAttachments.ILLAGER_FACTION);
                if (data != null) return data.faction();
            }
        }
        return null;
    }
}
