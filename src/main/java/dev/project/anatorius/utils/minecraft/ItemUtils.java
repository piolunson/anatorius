package dev.project.anatorius.utils.minecraft;

import net.minecraft.component.ComponentMap;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class ItemUtils {
    private ItemUtils() {}

    public static boolean isArmor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return isArmor(stack.getComponents());
    }

    public static boolean isArmor(Item item) {
        if (item == null) return false;
        return isArmor(item.getComponents());
    }

    private static boolean isArmor(ComponentMap components) {
        EquippableComponent equippable = components.get(DataComponentTypes.EQUIPPABLE);
        return equippable != null && equippable.slot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR;
    }

    private static final EquipmentSlot[] ARMOR_SLOTS =
            {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};

    public static List<ItemStack> getArmorItems(LivingEntity entity) {
        List<ItemStack> armor = new ArrayList<>(4);
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            armor.add(entity.getEquippedStack(slot));
        }
        return armor;
    }
}
