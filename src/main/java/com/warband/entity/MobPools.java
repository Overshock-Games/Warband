package com.warband.entity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;
import org.slf4j.Logger;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * User-declared membership of modded mobs in Warband's behaviour pools.
 *
 * <p>Warband decides what a mob can do from its {@link Tactic.Subject} set, which
 * is normally derived from vanilla classes. A modded mob that does not extend the
 * matching vanilla class — most custom zombies extend {@code Monster} directly —
 * gets no subjects, so no tactics, no squad, and no role. It stands next to
 * Warband's zombies looking conspicuously stupid.
 *
 * <p>This maps entity ids onto pools so those mobs opt in, including squadding up
 * with their vanilla counterparts. Membership is declared, never guessed: adding
 * every {@code RangedAttackMob} automatically would quietly pull in mobs from
 * unrelated mods.
 *
 * <p>Config format — one key, pools separated by {@code ;}, ids by {@code ,}:
 * <pre>
 * customMobPools=ZOMBIE_FAMILY&gt;examplemod:armored_zombie,examplemod:fast_zombie;SPIDER&gt;examplemod:giant_spider
 * </pre>
 * {@code >} separates the pool from its ids because entity ids already contain
 * {@code :}. Pool names are {@link Tactic.Subject} constants, case-insensitive.
 * The ids above are placeholders — read real ones off a mob with
 * {@code /data get entity @e[limit=1,sort=nearest] id}, or from the mod's own
 * registry dump. Unknown ids are inert, so a typo silently does nothing; check the
 * log line this class emits on load to confirm your entries were counted.
 */
public final class MobPools {

    private static Map<Identifier, EnumSet<Tactic.Subject>> pools = Map.of();

    private MobPools() {
    }

    /** Parses the {@code customMobPools} config value. Invalid entries are logged and skipped. */
    public static void load(String raw, Logger logger) {
        Map<Identifier, EnumSet<Tactic.Subject>> parsed = new HashMap<>();
        if (raw != null && !raw.isBlank()) {
            for (String group : raw.split(";")) {
                if (group.isBlank()) continue;
                int split = group.indexOf('>');
                if (split < 0) {
                    logger.warn("[Warband] customMobPools entry '{}' is missing '>' (expected POOL>id,id)", group.trim());
                    continue;
                }
                String poolName = group.substring(0, split).trim();
                Tactic.Subject pool;
                try {
                    pool = Tactic.Subject.valueOf(poolName.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    logger.warn("[Warband] '{}' is not a valid customMobPools pool name", poolName);
                    continue;
                }
                for (String id : group.substring(split + 1).split(",")) {
                    String trimmed = id.trim();
                    if (trimmed.isEmpty()) continue;
                    Identifier identifier = Identifier.tryParse(trimmed);
                    if (identifier == null) {
                        logger.warn("[Warband] '{}' is not a valid entity id in customMobPools", trimmed);
                        continue;
                    }
                    parsed.computeIfAbsent(identifier, k -> EnumSet.noneOf(Tactic.Subject.class)).add(pool);
                }
            }
        }
        pools = parsed;
        if (!parsed.isEmpty()) {
            logger.info("[Warband] custom mob pools: {} entity type(s) mapped", parsed.size());
        }
    }

    /** Configured pools for this mob, or an empty set. */
    public static EnumSet<Tactic.Subject> subjectsFor(Mob mob) {
        if (pools.isEmpty()) return EnumSet.noneOf(Tactic.Subject.class);
        return subjectsForId(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()));
    }

    /** Configured pools for an entity id, or an empty set. */
    public static EnumSet<Tactic.Subject> subjectsForId(Identifier id) {
        EnumSet<Tactic.Subject> found = pools.get(id);
        return found == null ? EnumSet.noneOf(Tactic.Subject.class) : EnumSet.copyOf(found);
    }

    /** True when the user has explicitly opted this mob's type into any pool. */
    public static boolean isConfigured(Mob mob) {
        return !pools.isEmpty() && pools.containsKey(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()));
    }
}
