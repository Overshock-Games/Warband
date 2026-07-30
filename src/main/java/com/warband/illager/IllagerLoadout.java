package com.warband.illager;

import com.warband.compat.IllagerKinds;
import com.warband.config.WarbandConfig;
import com.warband.entity.Role;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

/** Gives faction illagers readable equipment that matches their squad role. */
public final class IllagerLoadout {

    private IllagerLoadout() {
    }

    public static void equip(Mob mob, Role role, double difficulty) {
        if (!WarbandConfig.illagerRoleGearEnabled || !IllagerKinds.isIllagerLike(mob)) return;
        if (role == Role.NONE || difficulty < 0.35) return;

        switch (role) {
            case LEADER -> {
                equipArmor(mob, EquipmentSlot.HEAD, enchanted(mob, new ItemStack(Items.IRON_HELMET), Enchantments.PROTECTION, level(difficulty, 1, 3)));
                equipArmor(mob, EquipmentSlot.CHEST, enchanted(mob, new ItemStack(Items.IRON_CHESTPLATE), Enchantments.PROTECTION, level(difficulty, 1, 2)));
                ensureWeapon(mob, enchanted(mob, new ItemStack(Items.CROSSBOW), Enchantments.QUICK_CHARGE, level(difficulty, 1, 2)), false);
            }
            case MARKSMAN -> {
                ItemStack crossbow = enchanted(mob, new ItemStack(Items.CROSSBOW), Enchantments.QUICK_CHARGE, level(difficulty, 1, 3));
                if (difficulty >= 0.70) {
                    crossbow = enchanted(mob, crossbow, Enchantments.PIERCING, 1);
                }
                ensureWeapon(mob, crossbow, false);
                equipArmor(mob, EquipmentSlot.HEAD, enchanted(mob, new ItemStack(Items.LEATHER_HELMET), Enchantments.PROJECTILE_PROTECTION, level(difficulty, 1, 2)));
            }
            case SKIRMISHER -> {
                ensureWeapon(mob, enchanted(mob, new ItemStack(Items.CROSSBOW), Enchantments.QUICK_CHARGE, 1), false);
                equipArmor(mob, EquipmentSlot.FEET, enchanted(mob, new ItemStack(Items.LEATHER_BOOTS), Enchantments.PROTECTION, 1));
            }
            case SUPPORT -> {
                equipArmor(mob, EquipmentSlot.HEAD, enchanted(mob, new ItemStack(Items.CHAINMAIL_HELMET), Enchantments.PROTECTION, 1));
                equipArmor(mob, EquipmentSlot.CHEST, new ItemStack(Items.CHAINMAIL_CHESTPLATE));
            }
            case BRUISER -> {
                ensureWeapon(mob, enchanted(mob, new ItemStack(Items.IRON_AXE), Enchantments.SHARPNESS, level(difficulty, 1, 3)), true);
                equipArmor(mob, EquipmentSlot.CHEST, enchanted(mob, new ItemStack(Items.CHAINMAIL_CHESTPLATE), Enchantments.PROTECTION, level(difficulty, 1, 2)));
            }
            case NONE -> {
            }
        }
    }

    private static void ensureWeapon(Mob mob, ItemStack stack, boolean replaceExisting) {
        if (replaceExisting || (!mob.getMainHandItem().isEmpty() && mob.getMainHandItem().is(stack.getItem()))) {
            mob.setItemSlot(EquipmentSlot.MAINHAND, stack);
        } else if (mob.getMainHandItem().isEmpty()) {
            mob.setItemSlot(EquipmentSlot.MAINHAND, stack);
        }
        mob.setDropChance(EquipmentSlot.MAINHAND, 0.03f);
    }

    private static void equipArmor(Mob mob, EquipmentSlot slot, ItemStack stack) {
        if (!mob.getItemBySlot(slot).isEmpty()) return;
        mob.setItemSlot(slot, stack);
        mob.setDropChance(slot, 0.02f);
    }

    private static ItemStack enchanted(Mob mob, ItemStack stack, net.minecraft.resources.ResourceKey<Enchantment> key, int level) {
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
