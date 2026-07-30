package com.warband.illager;

import com.mojang.serialization.Codec;
import com.warband.spawn.SpawnDirector;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.resources.ResourceKey;

import java.util.Locale;

/**
 * How a named illager survivor was marked by the fight it walked away from, and what
 * it does about it next time.
 *
 * <p>The Shadow of Mordor loop, which is not really about naming enemies — Warband
 * already names them — but about enemies that come back <i>changed by the specific way
 * the last fight went</i>. Burn a squad down and the survivor returns wearing fire
 * resistance. Shoot them from a wall and it comes back in a projectile-proofed helmet.
 * The player's own tactics become the thing that escalates against them, which is a
 * far better difficulty curve than a number going up.
 *
 * <p>The scar is taken from how the survivor watched its <i>ally</i> die, not from its
 * own wounds — that is the data already in hand at witness time, and it is the better
 * fiction anyway: it learned from the death it stood next to.
 *
 * <p>Every adaptation is functional, never cosmetic. A survivor that looks armoured
 * but is not would be a lie to the player, so each entry does something the player can
 * actually feel: they shrug off the fire, your arrows stop mattering, your hits stop
 * staggering them.
 */
public enum IllagerScar {

    /** No lesson learned, or the fight predates this system. */
    NONE("", ""),
    FIRE("fire-scarred", "walks through flame now"),
    ARROWS("arrow-scarred", "came back helmeted"),
    BLADE("blade-scarred", "no longer staggers"),
    BLAST("blast-scarred", "came back armoured");

    /** English fallback for the short adjective used in names and intel listings. */
    private final String label;
    /** English fallback for the arrival-message clause, so the adaptation is announced not guessed. */
    private final String boast;

    IllagerScar(String label, String boast) {
        this.label = label;
        this.boast = boast;
    }

    public static final Codec<IllagerScar> CODEC =
            Codec.STRING.xmap(IllagerScar::fromString, IllagerScar::name);

    /** Short adjective for names and intel listings. */
    public Component label() {
        return text("label", label);
    }

    /** Clause for the arrival message, so the adaptation is announced not guessed. */
    public Component boast() {
        return text("boast", boast);
    }

    private Component text(String suffix, String fallback) {
        if (this == NONE) return Component.empty();
        return Component.translatableWithFallback(
                "warband.scar." + name().toLowerCase(Locale.ROOT) + "." + suffix, fallback);
    }

    public boolean marked() {
        return this != NONE;
    }

    public static IllagerScar fromString(String raw) {
        if (raw == null || raw.isBlank()) return NONE;
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }

    /**
     * Reads the lesson out of the killing blow.
     *
     * <p>Order matters: fire and explosions are checked before the generic projectile
     * and melee cases, because a flaming arrow or a blast should teach the more
     * specific lesson.
     */
    public static IllagerScar fromDamage(DamageSource source) {
        if (source == null) return NONE;
        if (source.is(DamageTypeTags.IS_FIRE)) return FIRE;
        if (source.is(DamageTypeTags.IS_EXPLOSION)) return BLAST;
        if (source.is(DamageTypeTags.IS_PROJECTILE)) return ARROWS;
        // A direct hit from a living attacker: the ordinary case, and still a lesson.
        return source.getDirectEntity() != null ? BLADE : NONE;
    }

    /** Applies this survivor's adaptation. Never downgrades gear it already has. */
    public void apply(Mob mob) {
        switch (this) {
            case NONE -> {
            }
            case FIRE -> mob.addEffect(new MobEffectInstance(
                    MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, true, false));
            case ARROWS -> equip(mob, EquipmentSlot.HEAD,
                    enchanted(mob, new ItemStack(Items.IRON_HELMET), Enchantments.PROJECTILE_PROTECTION, 3));
            case BLADE -> SpawnDirector.addFlat(mob, Attributes.KNOCKBACK_RESISTANCE,
                    SpawnDirector.warbandModifierId("scar_blade"), 0.4);
            case BLAST -> equip(mob, EquipmentSlot.CHEST,
                    enchanted(mob, new ItemStack(Items.IRON_CHESTPLATE), Enchantments.BLAST_PROTECTION, 3));
        }
    }

    private static void equip(Mob mob, EquipmentSlot slot, ItemStack stack) {
        if (!mob.getItemBySlot(slot).isEmpty()) return;
        mob.setItemSlot(slot, stack);
        // Same low drop chance as role gear: a scar is a story, not a loot piñata.
        mob.setDropChance(slot, 0.015f);
    }

    private static ItemStack enchanted(Mob mob, ItemStack stack, ResourceKey<Enchantment> key, int level) {
        Holder<Enchantment> enchantment = mob.level().registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(key);
        stack.enchant(enchantment, level);
        return stack;
    }
}
