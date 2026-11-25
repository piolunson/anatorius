package dev.project.anatorius.utils.minecraft;

import dev.project.anatorius.utils.minecraft.ItemUtils;
import dev.project.anatorius.utils.IMinecraft;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.DamageUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;

import java.util.HashMap;
import java.util.Map;

public class DamageUtils implements IMinecraft {
    private static final Map<String, Integer> PROTECTION_MAP = new HashMap<>() {{
        put("protection", 1);
        put("blast_protection", 2);
        put("projectile_protection", 1);
        put("feather_falling", 1);
        put("fire_protection", 1);
    }};

    public static float getCrystalDamage(Entity entity, Box box, EndCrystalEntity crystal, boolean ignoreTerrain) {
        return getDamage(entity, box, Vec3d.ofCenter(crystal.getBlockPos(), 0), 6.0f, null, ignoreTerrain);
    }

    public static float getCrystalDamage(Entity entity, Box box, BlockPos position, BlockPos exception, boolean ignoreTerrain) {
        return getDamage(entity, box, Vec3d.ofCenter(position, 1), 6.0f, exception, ignoreTerrain);
    }

    public static float calculateAnchorDamage(Vec3d anchorPos, Entity entity) {
        return getDamage(entity, entity.getBoundingBox(), anchorPos, 5.0f, null, true);
    }

    public static float getDamage(Entity entity, Box box, Vec3d vec3d, float power, BlockPos exception, boolean ignoreTerrain) {
        if (mc.world.getDifficulty() == Difficulty.PEACEFUL) return 0.0f;
        if (!(entity instanceof OtherClientPlayerEntity) && entity instanceof PlayerEntity player && EntityUtils.getGameMode(player) == GameMode.CREATIVE) return 0.0f;

        float diameter = power * 2.0f;

        double distance = Math.sqrt(box != null ? box.getCenter().add(0, -0.9, 0).squaredDistanceTo(vec3d) : entity.squaredDistanceTo(vec3d)) / diameter;
        if (distance > 1.0) return 0.0f;

        double exposure = (1.0 - distance) * getExposure(vec3d, entity.getBoundingBox(), exception, ignoreTerrain);
        float damage = (int) ((exposure * exposure + exposure) / 2.0 * 7.0 * diameter + 1.0);

        if (damage <= 0.0f) return 0.0f;

        if (entity instanceof LivingEntity livingEntity) {
            damage = mc.world.getDifficulty() == Difficulty.EASY ? Math.min(damage / 2 + 1, damage) : mc.world.getDifficulty() == Difficulty.HARD ? damage * 3 / 2 : damage;
            damage = DamageUtil.getDamageLeft(livingEntity, damage, mc.world.getDamageSources().explosion(null), (float) livingEntity.getArmor(), (float) livingEntity.getAttributeInstance(EntityAttributes.ARMOR_TOUGHNESS).getValue());
            damage *= livingEntity.hasStatusEffect(StatusEffects.RESISTANCE) ? 1 - ((livingEntity.getStatusEffect(StatusEffects.RESISTANCE).getAmplifier() + 1) * 0.2) : 1;
            damage = DamageUtil.getInflictedDamage(damage, getProtectionAmount(ItemUtils.getArmorItems(livingEntity)));
        }

        return Math.max(damage, 0.0f);
    }

    public static float getMeleeDamage(PlayerEntity attacker, ItemStack weapon, LivingEntity target, double fall, float charge, boolean crit) {
        float base = getAttackDamage(attacker, weapon) * (0.2f + charge * charge * 0.8f);
        int sharpness = getEnchantmentLevel(weapon, "sharpness");
        float enchant = charge * (sharpness > 0 ? 0.5f * sharpness + 0.5f : 0.0f);

        if (weapon.isOf(net.minecraft.item.Items.MACE) && fall > 1.5) {
            double smash = fall <= 3.0 ? 4.0 * fall : fall <= 8.0 ? 12.0 + 2.0 * (fall - 3.0) : 22.0 + fall - 8.0;
            base += (float) (smash + 0.5 * getEnchantmentLevel(weapon, "density") * fall);
        }
        if (charge > 0.9f && crit) base *= 1.5f;

        float amount = base + enchant;
        if (amount <= 0.0f) return 0.0f;

        float armor = (float) target.getArmor();
        float toughness = (float) target.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS);
        float reduction = MathHelper.clamp(armor - amount / (2.0f + toughness / 4.0f), armor * 0.2f, 20.0f) / 25.0f;
        reduction = MathHelper.clamp(reduction - 0.15f * getEnchantmentLevel(weapon, "breach"), 0.0f, 1.0f);
        amount *= 1.0f - reduction;
        if (target.hasStatusEffect(StatusEffects.RESISTANCE)) {
            amount *= Math.max(0.0f, 1.0f - (target.getStatusEffect(StatusEffects.RESISTANCE).getAmplifier() + 1) * 0.2f);
        }
        int protection = 0;
        for (ItemStack stack : ItemUtils.getArmorItems(target)) protection += getEnchantmentLevel(stack, "protection");
        amount *= 1.0f - MathHelper.clamp(protection, 0, 20) / 25.0f;
        return Math.max(0.0f, amount);
    }

    public static float getAttackDamage(PlayerEntity attacker, ItemStack weapon) {
        double[] add = {attacker.getAttributeBaseValue(EntityAttributes.ATTACK_DAMAGE)};
        double[] multiplyBase = {0.0};
        double[] multiplyTotal = {1.0};
        weapon.applyAttributeModifiers(net.minecraft.entity.EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (!attribute.equals(EntityAttributes.ATTACK_DAMAGE)) return;
            switch (modifier.operation()) {
                case ADD_VALUE -> add[0] += modifier.value();
                case ADD_MULTIPLIED_BASE -> multiplyBase[0] += modifier.value();
                default -> multiplyTotal[0] *= 1.0 + modifier.value();
            }
        });
        if (attacker.hasStatusEffect(StatusEffects.STRENGTH)) add[0] += 3.0 * (attacker.getStatusEffect(StatusEffects.STRENGTH).getAmplifier() + 1);
        if (attacker.hasStatusEffect(StatusEffects.WEAKNESS)) add[0] -= 4.0 * (attacker.getStatusEffect(StatusEffects.WEAKNESS).getAmplifier() + 1);
        return (float) Math.max(0.0, add[0] * (1.0 + multiplyBase[0]) * multiplyTotal[0]);
    }

    public static int getEnchantmentLevel(ItemStack stack, String id) {
        ItemEnchantmentsComponent enchantments = EnchantmentHelper.getEnchantments(stack);
        for (RegistryEntry<Enchantment> enchantment : enchantments.getEnchantments()) {
            if (enchantment.getIdAsString().equals("minecraft:" + id)) return enchantments.getLevel(enchantment);
        }
        return 0;
    }

    public static int getProtectionAmount(Iterable<ItemStack> armor) {
        int x = 0;
        for (ItemStack stack : armor) {
            x += getProtectionAmount(stack);
        }

        return x;
    }

    public static int getProtectionAmount(ItemStack armor) {
        int x = 0;
        ItemEnchantmentsComponent enchantments = EnchantmentHelper.getEnchantments(armor);
        for (RegistryEntry<Enchantment> enchantment : enchantments.getEnchantments()) {
            String id = enchantment.getIdAsString().replace("minecraft:", "");
            if (PROTECTION_MAP.containsKey(id)) {
                x += enchantments.getLevel(enchantment) * PROTECTION_MAP.get(id);
                break;
            }
        }

        return x;
    }

    private static float getExposure(Vec3d source, Box box, BlockPos exception, boolean ignoreTerrain) {
        int hitCount = 0;
        int count = 0;

        for (double k = 0.0; k <= 1.0; k += 0.4545454446934474) {
            for (double l = 0.0; l <= 1.0; l += 0.21739130885479366) {
                for (double m = 0.0; m <= 1.0; m += 0.4545454446934474) {
                    Vec3d vec3d = new Vec3d(MathHelper.lerp(k, box.minX, box.maxX) + 0.045454555306552624, MathHelper.lerp(l, box.minY, box.maxY), MathHelper.lerp(m, box.minZ, box.maxZ) + 0.045454555306552624);
                    if (raycast(vec3d, source, exception, ignoreTerrain) == HitResult.Type.MISS) ++hitCount;
                    ++count;
                }
            }
        }

        return (float) hitCount / (float) count;
    }

    private static HitResult.Type raycast(Vec3d start, Vec3d end, BlockPos exception, boolean ignoreTerrain) {
        return BlockView.raycast(start, end, null, (innerContext, blockPos) -> {
            BlockState blockState;
            if (blockPos.equals(exception)) {
                blockState = Blocks.AIR.getDefaultState();
            } else {
                blockState = mc.world.getBlockState(blockPos);
                if (blockState.getBlock().getBlastResistance() < 600 && ignoreTerrain) blockState = Blocks.AIR.getDefaultState();
            }

            BlockHitResult hitResult = blockState.getCollisionShape(mc.world, blockPos).raycast(start, end, blockPos);
            return hitResult == null ? null : hitResult.getType();
        }, (innerContext) -> HitResult.Type.MISS);
    }
}
