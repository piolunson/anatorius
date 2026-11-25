package dev.project.anatorius.utils.minecraft;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.utils.IMinecraft;
import dev.project.anatorius.utils.rotations.Rotation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class PlayerUtils implements IMinecraft {
    public static boolean isElytraEquipped() {
        if (mc.player == null) return false;
        ItemStack stack = mc.player.getInventory().getStack(38);
        if (stack == null || stack.isEmpty()) return false;
        return stack.getItem().equals(Items.ELYTRA);
    }

    public static void disEquipElytra() {
        if (!isElytraEquipped()) return;

        int slot = InventoryUtils.findChestplate();
        if (slot != -1) {
            InventoryUtils.swapArmor(2, slot);
        }
    }

    public static void clientJump() {
        if (mc.player == null) return;

        float jumpVelocity = 0.42f;
        if (mc.player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.JUMP_BOOST)) {
            jumpVelocity += 0.1f * (float)(mc.player.getStatusEffect(net.minecraft.entity.effect.StatusEffects.JUMP_BOOST).getAmplifier() + 1);
        }

        Vec3d vec3d = mc.player.getVelocity();
        mc.player.setVelocity(vec3d.x, (double)jumpVelocity, vec3d.z);

        if (mc.player.isSprinting()) {
            float yaw = mc.player.getYaw() * ((float)Math.PI / 180F);
            mc.player.addVelocityInternal(
                new Vec3d(
                    (double)(-MathHelper.sin(yaw)) * 0.2,
                    0.0,
                    (double)MathHelper.cos(yaw) * 0.2
                )
            );
        }

        mc.player.velocityDirty = true;
    }

    public static boolean isMoving() {
        if (mc.player == null) return false;
        return mc.player.input.getMovementInput().lengthSquared() != 0.0F;
    }

    public static boolean isBoostedByFirework() {
        if (mc.world == null) return false;

        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof FireworkRocketEntity firework) {
                if (firework.getOwner() != null && firework.getOwner().equals(mc.player)) {
                    return true;
                }
            }
        }

        return false;
    }

    public static void doFirework() {
        if (mc.player == null) return;

        boolean inOffhand = mc.player.getOffHandStack().getItem() == Items.FIREWORK_ROCKET;
        int slot = InventoryUtils.find(Items.FIREWORK_ROCKET);
        if (!inOffhand && slot == -1) return;

        int previousSlot = mc.player.getInventory().getSelectedSlot();

        if (!inOffhand) {
            if (slot >= InventoryUtils.HOTBAR_START && slot <= InventoryUtils.HOTBAR_END) {
                InventoryUtils.switchSlot("Silent", slot, previousSlot);
            }
        }

        Rotation rotation = Anatorius.ROTATION_MANAGER.getRotation();
        float yaw = rotation != null ? rotation.getYaw() : mc.player.getYaw();
        float pitch = rotation != null ? rotation.getPitch() : mc.player.getPitch();

        NetworkUtils.sendSequencedPacket(id ->
            new PlayerInteractItemC2SPacket(inOffhand ? Hand.OFF_HAND : Hand.MAIN_HAND, id, yaw, pitch)
        );

        if (!inOffhand) {
            if (slot >= InventoryUtils.HOTBAR_START && slot <= InventoryUtils.HOTBAR_END) {
                InventoryUtils.switchBack("Silent", slot, previousSlot);
            }
        }
    }
}
