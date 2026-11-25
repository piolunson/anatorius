package dev.project.anatorius.modules.impl.movement.elytrafly;

import dev.project.anatorius.modules.impl.movement.ElytraFlyModule;
import dev.project.anatorius.utils.minecraft.InventoryUtils;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;

public class VanillaMode extends ElytraFlyMode {
    private boolean descending, fireworkAscentPending, fireworkPending;
    private long fireworkUsedAt;
    private float targetPitch;

    public VanillaMode(ElytraFlyModule module) {
        super(module);
    }

    private float initialPitch() {
        return mc.player == null ? 40.0f : mc.player.getPitch();
    }

    @Override
    public void onEnable() {
        descending = true;
        targetPitch = initialPitch();
    }

    private boolean flightActive() {
        return module.infiniteDurability.getValue().equals("None") ? module.flying()
                : module.durabilityBypassActive() && !mc.player.isTouchingWater();
    }

    private float maximumAltitude() {
        return module.minY.getValue().intValue() + module.height.getValue().intValue();
    }

    @Override
    public void preTick() {
        if (!flightActive()) {
            targetPitch = initialPitch();
            return;
        }
        if (module.is2b2t() && mc.player.isUsingItem()) return;
        if (boostedByRocket()) return;
        if (fireworkPending) {
            useFirework();
            return;
        }
        double y = mc.player.getY();
        int minY = module.minY.getValue().intValue();
        if (!descending && y <= minY - 10 && module.useFirework.getValue() && mc.player.getVelocity().y < 0.0) fireworkAscentPending = true;
        if (y >= maximumAltitude()) fireworkAscentPending = false;
        if (fireworkAscentPending && module.useFirework.getValue()) {
            if (System.currentTimeMillis() - fireworkUsedAt >= 250L) fireworkPending = true;
            return;
        }
        if (y >= maximumAltitude()) descending = true;
        else if (y <= minY && descending) descending = false;

        targetPitch += descending ? 2.0f : -5.45f;
        targetPitch = MathHelper.clamp(targetPitch, -40.0f, 40.0f);
    }

    @Override
    public void clientTick() {
        if (!flightActive()) return;
        if (boostedByRocket()) targetPitch = -75.0f;
        request(Float.NaN, targetPitch);
    }

    private boolean boostedByRocket() {
        for (var entity : mc.world.getEntities()) {
            if (entity instanceof FireworkRocketEntity rocket && rocket.getOwner() == mc.player && rocket.squaredDistanceTo(mc.player) < 16.0) return true;
        }
        return false;
    }

    private void useFirework() {
        int selected = mc.player.getInventory().getSelectedSlot();
        int slot = InventoryUtils.find(Items.FIREWORK_ROCKET, InventoryUtils.HOTBAR_START, InventoryUtils.HOTBAR_END);
        String switchMode = "Silent";
        if (slot == -1) {
            slot = InventoryUtils.find(Items.FIREWORK_ROCKET, InventoryUtils.INVENTORY_START, InventoryUtils.INVENTORY_END);
            switchMode = "AltSwap";
        }
        if (slot == -1) return;
        if (slot != selected) InventoryUtils.switchSlot(switchMode, slot, selected);
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
        if (slot != selected) InventoryUtils.switchBack(switchMode, slot, selected);
        fireworkUsedAt = System.currentTimeMillis();
        fireworkPending = false;
    }
}
