package dev.project.anatorius.modules.impl.movement.fakefly;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.modules.impl.combat.AutoMaceModule;
import dev.project.anatorius.modules.impl.movement.ElytraFlyModule;
import dev.project.anatorius.modules.impl.movement.FakeFlyModule;
import dev.project.anatorius.utils.IMinecraft;
import dev.project.anatorius.utils.minecraft.InventoryUtils;
import dev.project.anatorius.utils.system.Timer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;

public class InfDurability implements IMinecraft {
    private static final int CHEST_SLOT = 6;

    private final FakeFlyModule module;
    private final Timer durabilitySwapTimer = new Timer();
    private final Timer fireworkUseTimer = new Timer();
    private final Timer elytraSearchTimer = new Timer();
    private final Timer rocketUseTimer = new Timer();
    private boolean fireworkUsePending;
    private boolean replayingFireworkUse;
    private boolean temporaryElytraSwapActive;
    private int temporarySwapTicks;
    private int temporarySwapButton;

    public InfDurability(FakeFlyModule module) {
        this.module = module;
    }

    public void onEnable() {
        durabilitySwapTimer.setMs(1000L);
        fireworkUseTimer.setMs(1000L);
        elytraSearchTimer.reset();
        clearRocketUseTimer();
        fireworkUsePending = false;
        replayingFireworkUse = false;
        temporaryElytraSwapActive = false;
    }

    public void onDisable() {
        fireworkUsePending = false;
        fireworkUseTimer.reset();
        if (temporaryElytraSwapActive && mc.player != null && mc.interactionManager != null) {
            temporarySwapTicks = 0;
            tickTemporarySwap();
        }
        temporaryElytraSwapActive = false;
    }

    public void preTick() {
        tickTemporarySwap();
        autoLaunch();
        updateDurabilitySwap();
    }

    private void tickTemporarySwap() {
        if (!temporaryElytraSwapActive || --temporarySwapTicks > 0) return;
        try {
            if (mc.player.currentScreenHandler == mc.player.playerScreenHandler) {
                mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, CHEST_SLOT, temporarySwapButton, SlotActionType.SWAP, mc.player);
            }
        } finally {
            temporaryElytraSwapActive = false;
        }
    }

    public static boolean isFallFlying() {
        boolean hookWasActive = FakeFlyModule.hook;
        if (!hookWasActive) FakeFlyModule.hook = true;
        try {
            return mc.player.isGliding();
        } finally {
            if (!hookWasActive) FakeFlyModule.hook = false;
        }
    }

    public static boolean collisionBelow() {
        return !mc.world.isSpaceEmpty(mc.player, mc.player.getBoundingBox().stretch(0.0, -0.1, 0.0));
    }

    private void updateDurabilitySwap() {
        if (temporaryElytraSwapActive) return;
        if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) return;

        int elytraSlot = module.elytraHome();
        boolean inventorySwapRequired = elytraSlot >= 9 && elytraSlot < 36;
        if (!shouldSwapForDurability(inventorySwapRequired) || !durabilitySwapTimer.hasTimeElapsed(200L)
                || !isDurabilityBypassActive()) {
            return;
        }
        int clicksRequired = inventorySwapRequired ? 4 : 2;
        if (module.wouldReachClickLimit(clicksRequired)) return;
        if (module.wearsElytra()) {
            startFallFlyingWithPacket();
            return;
        }
        if (elytraSlot == -1) return;

        int syncId = mc.player.playerScreenHandler.syncId;
        int originalMenuSlot = elytraSlot;
        int button = elytraSlot == 45 ? 40 : elytraSlot - 36;
        if (inventorySwapRequired) {
            button = 0;
            mc.interactionManager.clickSlot(syncId, originalMenuSlot, 0, SlotActionType.SWAP, mc.player);
        }
        mc.interactionManager.clickSlot(syncId, CHEST_SLOT, button, SlotActionType.SWAP, mc.player);
        try {
            startFallFlyingWithPacket();
            if (fireworkUsePending && fireworkUseTimer.hasTimeElapsed(150L)) {
                fireworkUseTimer.reset();
                replayingFireworkUse = true;
                try {
                    module.useFirework();
                } finally {
                    replayingFireworkUse = false;
                }
                fireworkUsePending = false;
                durabilitySwapTimer.reset();
            }
        } finally {
            mc.interactionManager.clickSlot(syncId, CHEST_SLOT, button, SlotActionType.SWAP, mc.player);
            if (inventorySwapRequired) {
                mc.interactionManager.clickSlot(syncId, originalMenuSlot, 0, SlotActionType.SWAP, mc.player);
            }
        }
    }

    private boolean shouldSwapForDurability(boolean inventorySwapRequired) {
        boolean wearing = module.wearsElytra();
        if (!wearing && !mc.player.currentScreenHandler.getCursorStack().isEmpty()) return false;
        return switch (module.infDurability.getValue()) {
            case "Plain" -> true;
            case "Grim" -> !isFallFlying();
            case "2b2t" -> {
                if (wearing) yield true;
                if (inventorySwapRequired) yield false;
                int tick = mc.player.age;
                yield tick % 2 != 0 && tick % 20 != 1;
            }
            default -> false;
        };
    }

    private void startFallFlyingWithPacket() {
        mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
        mc.player.startGliding();
    }

    public boolean isDurabilityBypassActive() {
        if (mc.player == null || mc.world == null || FakeFlyModule.hook) return false;
        if (!module.isToggled() || !module.zio()) return false;
        if (collisionBelow()) return false;
        int elytraSlot = InventoryUtils.findHotbar(Items.ELYTRA);
        if (elytraSlot == -1) {
            elytraSlot = InventoryUtils.find(Items.ELYTRA);
        } else {
            elytraSearchTimer.reset();
        }
        return elytraSlot != -1 || !elytraSearchTimer.hasTimeElapsed(150L) || module.wearsElytra();
    }

    public boolean onUseItem(Hand hand) {
        if (!isDurabilityBypassActive() || replayingFireworkUse) return false;
        ItemStack heldStack = mc.player.getStackInHand(hand);
        if (heldStack.isOf(Items.FIREWORK_ROCKET) && !module.wearsElytra()) {
            fireworkUsePending = true;
            return true;
        }
        return false;
    }

    public void onRocketSpawn() {
        durabilitySwapTimer.reset();
    }

    public void onReleaseUseItem() {
        ElytraFlyModule elytraFly = Anatorius.MODULE_MANAGER.getModule(ElytraFlyModule.class);
        if (elytraFly != null && elytraFly.is2b2t()) durabilitySwapTimer.reset();
    }

    private void autoLaunch() {
        if (module.takeFireworkRequest()) {
            fireworkUsePending = true;
            rocketUseTimer.reset();
        }
        if (!module.autoFirework.getValue() || AutoMaceModule.ownsFakeFlyFireworks()) return;

        boolean fallFlyingForLaunch = isFallFlying() || isDurabilityBypassActive();
        if (!fallFlyingForLaunch || mc.player.isOnGround()) clearRocketUseTimer();
        if (isRocketBoostActive()) return;
        if (mc.player.isOnGround()) return;
        ElytraFlyModule elytraFly = Anatorius.MODULE_MANAGER.getModule(ElytraFlyModule.class);
        if (elytraFly == null || !elytraFly.is2b2t()) temporarilyEquipElytra();
        tryUseRocket();
    }

    private void temporarilyEquipElytra() {
        int elytraSlot = module.elytraHome();
        if (elytraSlot < 36 || elytraSlot > 44 || temporaryElytraSwapActive) return;
        if (!hasRocket() || mc.player.isUsingItem() || module.wouldReachClickLimit(2)) return;
        if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) return;
        temporaryElytraSwapActive = true;
        temporarySwapButton = elytraSlot - 36;
        temporarySwapTicks = 3;
        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, CHEST_SLOT, temporarySwapButton, SlotActionType.SWAP, mc.player);
        startFallFlyingWithPacket();
    }

    private void tryUseRocket() {
        if ((!isFallFlying() && !temporaryElytraSwapActive) || mc.player.isUsingItem() || fireworkUsePending) return;
        if (!hasRocket()) return;
        if (temporaryElytraSwapActive || module.wearsElytra()) module.useFirework();
        else fireworkUsePending = true;
        rocketUseTimer.reset();
    }

    private static boolean hasRocket() {
        return mc.player.getOffHandStack().isOf(Items.FIREWORK_ROCKET) || InventoryUtils.find(Items.FIREWORK_ROCKET) != -1;
    }

    private boolean isRocketBoostActive() {
        return !rocketUseTimer.hasTimeElapsed(500L) || module.ownRocketAlive();
    }

    private void clearRocketUseTimer() {
        rocketUseTimer.setMs(Long.MAX_VALUE / 4);
    }
}
