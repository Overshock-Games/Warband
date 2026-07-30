package com.warband.entity;

import com.warband.compat.IllagerKinds;
import com.warband.config.WarbandConfig;
import com.warband.illager.IllagerFaction;
import com.warband.illager.IllagerFactionSystem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Mob;

/**
 * Assigns stable rank-style names to Warband illagers.
 *
 * <p>Names are assembled from translatable parts rather than concatenated strings. The
 * rank, the role title and the faction each carry their own key, and the word order that
 * joins them is itself a key — {@code "%1$s %2$s of the %3$s"} is English grammar, and a
 * language that puts the faction first cannot express that if the mod has already glued
 * the pieces together. Only the personal name stays literal: "Arvek" is a proper noun and
 * translating it would be wrong.
 */
public final class IllagerIdentity {

    private static final String[] NAMES = {
            "Arvek", "Borran", "Cald", "Dren", "Eskar", "Fenn", "Garrik", "Hask",
            "Ivek", "Jorren", "Karn", "Lorr", "Mavik", "Nesk", "Orren", "Pask",
            "Quell", "Rusk", "Sarn", "Tovik", "Urren", "Vask", "Werrik", "Yorn"
    };

    private IllagerIdentity() {
    }

    /**
     * The mob's personal name on its own, with no rank or faction attached.
     *
     * <p>Deterministic from the UUID, so the same mob always answers to the same name —
     * and a caller that needs to remember a mob can store this rather than keeping a
     * rendered display name and picking it apart later.
     */
    public static String personalName(Mob mob) {
        return NAMES[Math.floorMod(mob.getUUID().hashCode(), NAMES.length)];
    }

    public static void assignIfNeeded(Mob mob, Role role, double difficulty) {
        if (!IllagerKinds.isIllagerLike(mob) || mob.hasCustomName()) return;

        Component rank = IllagerKinds.hasRoleTitle(mob) && role != Role.LEADER
                ? IllagerKinds.roleTitle(mob)
                : rank(role, difficulty);
        mob.setCustomName(compose(mob, rank));
        // Hover-only: a visible custom name renders through walls, which reads as
        // a wallhack. Names show when the player looks at the mob.
        mob.setCustomNameVisible(false);
    }

    /**
     * Re-name a mob as its stronghold's single Warmarshal. Called once per
     * mansion by {@code StrongholdGarrison}; overrides the rank from
     * {@link #assignIfNeeded}.
     */
    public static void promoteToWarmarshal(Mob mob) {
        if (!IllagerKinds.isIllagerLike(mob)) return;
        mob.setCustomName(compose(mob, rankText("warmarshal", "Warmarshal")));
        mob.setCustomNameVisible(false);
    }

    /** Joins rank, personal name and — when factions are on — the faction, in a translatable order. */
    private static Component compose(Mob mob, Component rank) {
        String name = personalName(mob);
        if (!WarbandConfig.illagerFactionsEnabled) {
            return Component.translatableWithFallback("warband.name.plain", "%1$s %2$s", rank, name);
        }
        IllagerFaction faction = IllagerFactionSystem.factionOrDefault(mob);
        return Component.translatableWithFallback("warband.name.with_faction", "%1$s %2$s of the %3$s",
                rank, name, faction.displayName());
    }

    private static Component rank(Role role, double difficulty) {
        if (role == Role.LEADER) {
            // "Warmarshal" is not auto-assigned, it is a single per-stronghold
            // title granted by StrongholdGarrison via promoteToWarmarshal.
            if (difficulty >= 0.65) return rankText("captain", "Captain");
            return rankText("sergeant", "Sergeant");
        }
        if (role == Role.MARKSMAN) {
            return difficulty >= 0.70 ? rankText("deadeye", "Deadeye") : rankText("crossbowman", "Crossbowman");
        }
        if (role == Role.SKIRMISHER) return rankText("raider", "Raider");
        if (role == Role.SUPPORT) return rankText("standard", "Standard");
        if (difficulty >= 0.70) return rankText("enforcer", "Enforcer");
        return rankText("pillager", "Pillager");
    }

    private static Component rankText(String id, String fallback) {
        return Component.translatableWithFallback("warband.rank." + id, fallback);
    }
}
