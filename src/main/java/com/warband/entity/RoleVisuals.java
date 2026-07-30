package com.warband.entity;

import com.warband.WarbandMod;
import com.warband.ai.TacticalEffects;
import com.warband.compat.IllagerInvasionCompat;
import com.warband.config.WarbandConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

/** Shared visual language for squad roles on vanilla mobs. */
public final class RoleVisuals {

    private static final Identifier SCALE_MOD = Identifier.fromNamespaceAndPath(WarbandMod.MOD_ID, "role_scale");

    private RoleVisuals() {
    }

    /** Below this, role gear never appears. */
    private static final double GEAR_MIN_DIFFICULTY = 0.25;
    /** At and above this, every piece of a role's kit is guaranteed. */
    private static final double GEAR_FULL_DIFFICULTY = 0.80;

    public static void apply(Mob mob, Role role, double difficulty) {
        if (role == Role.NONE) return;

        // Only illagers get named (via IllagerIdentity). Generic mobs stay anonymous;
        // their role reads through visuals (gear, scale) and behavior, not a label.
        TacticalEffects.roleCue(mob, role);
        if (!WarbandConfig.roleVisualsEnabled) return;
        applyScale(mob, role);
        if (IllagerInvasionCompat.isIllagerLike(mob)) return;

        // Each piece rolls independently, and armour tier climbs with difficulty.
        // This used to be a hard cutoff at 0.35: one step earlier a mob had nothing,
        // one step later it wore a full iron kit. Together with leaders unlocking at
        // 0.40 and squad formations at 0.45, three step functions fired inside the
        // same narrow band — the reported jump from "very easy" to caves "with lots
        // of armor and tools" with no transition between. Partial kits now fade in:
        // a lone chestplate here, a helmet there, before full sets show up.
        double chance = gearChance(difficulty);
        if (chance <= 0.0) return;

        switch (role) {
            case LEADER -> {
                // Gold stays the leader's visual signature at every tier — it is a
                // tell for players, and gold is weak armour so it costs no balance.
                if (roll(mob, chance)) {
                    equipArmor(mob, EquipmentSlot.HEAD, enchanted(mob, new ItemStack(Items.GOLDEN_HELMET), Enchantments.PROTECTION, 1));
                }
                if (roll(mob, chance)) {
                    equipArmor(mob, EquipmentSlot.CHEST, new ItemStack(Items.GOLDEN_CHESTPLATE));
                }
            }
            case BRUISER -> {
                if (mob instanceof Zombie && roll(mob, chance)) {
                    replaceWeapon(mob, enchanted(mob, new ItemStack(axeFor(difficulty)), Enchantments.SHARPNESS, level(difficulty, 1, 2)));
                }
                if (roll(mob, chance)) {
                    equipArmor(mob, EquipmentSlot.CHEST, new ItemStack(chestplateFor(difficulty)));
                }
            }
            case MARKSMAN -> {
                if (roll(mob, chance)) {
                    equipArmor(mob, EquipmentSlot.HEAD, enchanted(mob, new ItemStack(helmetFor(difficulty)), Enchantments.PROJECTILE_PROTECTION, 1));
                }
                if (mob instanceof RangedAttackMob && !mob.getMainHandItem().isEmpty() && roll(mob, chance)) {
                    enchantExistingWeapon(mob, difficulty);
                }
            }
            case SKIRMISHER -> {
                if (roll(mob, chance)) {
                    equipArmor(mob, EquipmentSlot.FEET, enchanted(mob, new ItemStack(Items.LEATHER_BOOTS), Enchantments.PROTECTION, 1));
                }
                if (roll(mob, chance)) {
                    equipArmor(mob, EquipmentSlot.LEGS, new ItemStack(Items.LEATHER_LEGGINGS));
                }
            }
            case SUPPORT -> {
                if (roll(mob, chance)) {
                    equipArmor(mob, EquipmentSlot.HEAD, new ItemStack(helmetFor(difficulty)));
                }
                if (roll(mob, chance)) {
                    equipArmor(mob, EquipmentSlot.CHEST, enchanted(mob, new ItemStack(Items.CHAINMAIL_CHESTPLATE), Enchantments.PROTECTION, 1));
                }
            }
            case NONE -> {
            }
        }
    }

    /** Per-piece equip probability: 0 at {@value #GEAR_MIN_DIFFICULTY}, 1 at {@value #GEAR_FULL_DIFFICULTY}. */
    private static double gearChance(double difficulty) {
        if (difficulty <= GEAR_MIN_DIFFICULTY) return 0.0;
        return Math.min(1.0, (difficulty - GEAR_MIN_DIFFICULTY) / (GEAR_FULL_DIFFICULTY - GEAR_MIN_DIFFICULTY));
    }

    private static boolean roll(Mob mob, double chance) {
        return chance >= 1.0 || mob.getRandom().nextDouble() < chance;
    }

    private static Item helmetFor(double difficulty) {
        if (difficulty >= 0.70) return Items.IRON_HELMET;
        if (difficulty >= 0.45) return Items.CHAINMAIL_HELMET;
        return Items.LEATHER_HELMET;
    }

    private static Item chestplateFor(double difficulty) {
        if (difficulty >= 0.70) return Items.IRON_CHESTPLATE;
        if (difficulty >= 0.45) return Items.CHAINMAIL_CHESTPLATE;
        return Items.LEATHER_CHESTPLATE;
    }

    private static Item axeFor(double difficulty) {
        return difficulty >= 0.55 ? Items.IRON_AXE : Items.STONE_AXE;
    }

    private static void applyScale(Mob mob, Role role) {
        AttributeInstance scale = mob.getAttribute(Attributes.SCALE);
        if (scale == null || scale.hasModifier(SCALE_MOD)) return;

        double amount = switch (role) {
            case LEADER -> 0.12;
            case BRUISER -> 0.08;
            case SKIRMISHER -> -0.06;
            case MARKSMAN -> 0.03;
            case SUPPORT -> -0.03;
            case NONE -> 0.0;
        };
        if (amount != 0.0) {
            scale.addPermanentModifier(new AttributeModifier(SCALE_MOD, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }

    private static void equipArmor(Mob mob, EquipmentSlot slot, ItemStack stack) {
        if (!mob.getItemBySlot(slot).isEmpty()) return;
        mob.setItemSlot(slot, stack);
        mob.setDropChance(slot, 0.015f);
    }

    private static void replaceWeapon(Mob mob, ItemStack stack) {
        mob.setItemSlot(EquipmentSlot.MAINHAND, stack);
        mob.setDropChance(EquipmentSlot.MAINHAND, 0.02f);
    }

    private static void enchantExistingWeapon(Mob mob, double difficulty) {
        ItemStack weapon = mob.getMainHandItem();
        if (weapon.is(Items.BOW)) {
            enchanted(mob, weapon, Enchantments.POWER, level(difficulty, 1, 2));
        } else if (weapon.is(Items.CROSSBOW)) {
            enchanted(mob, weapon, Enchantments.QUICK_CHARGE, level(difficulty, 1, 2));
        }
    }

    private static ItemStack enchanted(Mob mob, ItemStack stack, ResourceKey<Enchantment> key, int level) {
        Holder<Enchantment> enchantment = mob.level().registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(key);
        stack.enchant(enchantment, level);
        return stack;
    }

    private static int level(double difficulty, int min, int max) {
        return Math.max(min, Math.min(max, min + (int) Math.floor(difficulty * max)));
    }
}
