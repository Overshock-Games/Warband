package com.warband.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.illager.AbstractIllager;

import java.util.Locale;
import java.util.Map;

/**
 * Which mobs Warband treats as illagers, and what tactical role each one plays.
 *
 * <p>This used to be a hardcoded list of {@code illagerinvasion:} entity ids matched by
 * namespace and path. That worked, but it meant Warband only ever recognised the one mod
 * whose ids someone had typed in, and recognising a second one required a code change and
 * a new release. Entity-type tags are the vanilla answer to exactly this problem, so the
 * lists now live in {@code data/warband/tags/entity_type/} where a datapack — or another
 * mod — can extend them without Warband knowing the mod exists.
 *
 * <p>Every shipped entry uses {@code "required": false}, which is what lets a tag name an
 * entity type from a mod that is not installed without failing tag load.
 *
 * <p>Vanilla illagers are still recognised by class as well as by tag. A broken or
 * {@code "replace": true} datapack should not be able to switch the whole mod off by
 * accident, and {@link AbstractIllager} cannot lie about what it is.
 */
public final class IllagerKinds {

    /** Everything Warband treats as an illager. Vanilla illagers are included via {@code #minecraft:illager}. */
    public static final TagKey<EntityType<?>> ILLAGER_LIKE = tag("illager_like");
    /** Spellcaster/buffer types that take the SUPPORT role instead of being classified by their attack. */
    public static final TagKey<EntityType<?>> SUPPORT = tag("illager_support");
    /** Types that summon reinforcements, and so should be crowned before their escort. */
    public static final TagKey<EntityType<?>> SUMMONER = tag("illager_summoner");
    /** Types eligible to become a stronghold's single Warmarshal. */
    public static final TagKey<EntityType<?>> SEAT_BOSS = tag("faction_seat_boss");

    /**
     * Role titles that are not simply the entity type's own name.
     *
     * <p>Only ever used as the <i>fallback</i> for a translation key derived from the
     * entity id, so a language file or resource pack overrides it. Illager Invasion's
     * {@code basher} is the one type whose Warband title deliberately differs from its
     * registry name; everything else reads its title straight off its own id, including
     * types from mods that did not exist when this was written.
     */
    private static final Map<String, String> ROLE_TITLE_FALLBACKS = Map.of(
            "illagerinvasion:basher", "Bulwark"
    );

    private IllagerKinds() {
    }

    private static TagKey<EntityType<?>> tag(String path) {
        return TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("warband", path));
    }

    private static boolean is(Mob mob, TagKey<EntityType<?>> tag) {
        return BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(mob.getType()).is(tag);
    }

    public static boolean isIllagerLike(Mob mob) {
        return mob instanceof AbstractIllager || is(mob, ILLAGER_LIKE);
    }

    public static boolean isSupport(Mob mob) {
        return is(mob, SUPPORT);
    }

    public static boolean isSummoner(Mob mob) {
        return is(mob, SUMMONER);
    }

    public static boolean isSeatBossCandidate(Mob mob) {
        return is(mob, SEAT_BOSS);
    }

    /**
     * The mob's type-derived title, or empty for a vanilla illager.
     *
     * <p>Vanilla illagers get Warband's own rank ladder from {@code IllagerIdentity}; a
     * modded illager is named for the thing it actually is, since "Inquisitor" carries more
     * than "Enforcer" would.
     *
     * <p>The test is the registry namespace, because neither of the alternatives works.
     * Illager Invasion's mobs extend {@link AbstractIllager}, so class cannot separate
     * them; and they are listed in vanilla's own {@code #minecraft:illager} tag — correctly,
     * since that is how they inherit raid behaviour — so tag membership cannot either. An
     * earlier version of this method tested {@code !is(EntityTypeTags.ILLAGER)} and
     * silently gave every modded illager a vanilla rank instead of its own title.
     *
     * <p>Note this asks "is this type vanilla", not "is this type from Illager Invasion",
     * so it works for any mod's illagers without naming the mod.
     */
    public static Component roleTitle(Mob mob) {
        if (!hasRoleTitle(mob)) return Component.empty();
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        return Component.translatableWithFallback(
                "warband.role." + id.getNamespace() + "." + id.getPath(),
                ROLE_TITLE_FALLBACKS.getOrDefault(id.toString(), prettify(id.getPath())));
    }

    /** True when {@link #roleTitle} would return something displayable. */
    public static boolean hasRoleTitle(Mob mob) {
        return is(mob, ILLAGER_LIKE)
                && !BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType())
                        .getNamespace().equals(Identifier.DEFAULT_NAMESPACE);
    }

    /** {@code fire_caller} to {@code Fire Caller}, so an unknown modded type still reads as a title. */
    private static String prettify(String path) {
        StringBuilder out = new StringBuilder(path.length());
        for (String word : path.split("_")) {
            if (word.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return out.toString();
    }
}
