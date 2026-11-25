package dev.project.anatorius.managers;

import lombok.Getter;
import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.PacketReceiveEvent;
import dev.project.anatorius.events.impl.PacketSendEvent;
import dev.project.anatorius.modules.impl.core.AntiCheatModule;
import dev.project.anatorius.utils.IMinecraft;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import dev.project.anatorius.utils.minecraft.InventoryUtils;
import net.minecraft.util.Hand;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.item.ItemStack;
import dev.project.anatorius.events.impl.ClientDisconnectEvent;
import dev.project.anatorius.events.impl.SendMovementEvent;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public class PositionManager implements IMinecraft {
    public PositionManager() {
        Anatorius.EVENT_HANDLER.subscribe(this);
    }

    private double serverX;
    private double serverY;
    private double serverZ;

    private boolean serverOnGround;

    private boolean serverSprinting;
    private boolean serverSneaking;

    private volatile int serverSlot;

    @SubscribeEvent
    public void onPacketSend(PacketSendEvent event) {
        if (mc.player == null) return;

        if (event.getPacket() instanceof PlayerMoveC2SPacket packet) {
            if (packet.changesPosition()) {
                serverX = packet.getX(mc.player.getX());
                serverY = packet.getY(mc.player.getY());
                serverZ = packet.getZ(mc.player.getZ());
            }

            serverOnGround = packet.isOnGround();
        }

        if (event.getPacket() instanceof UpdateSelectedSlotC2SPacket packet) {
            serverSlot = packet.getSelectedSlot();
        }

        syncMainHandForUse(event);

        if (event.getPacket() instanceof ClientCommandC2SPacket packet) {
            switch (packet.getMode()) {
                case START_SPRINTING -> serverSprinting = true;
                case STOP_SPRINTING -> serverSprinting = false;
                default -> {}
            }
        }
    }

    @SubscribeEvent
    public void onPacketReceive(PacketReceiveEvent event) {
        handleSlotUpdate(event);
        if (!(event.getPacket() instanceof UpdateSelectedSlotS2CPacket packet)) return;
        AntiCheatModule antiCheat = Anatorius.MODULE_MANAGER == null ? null : Anatorius.MODULE_MANAGER.getModule(AntiCheatModule.class);
        if (antiCheat != null && antiCheat.noServerSlot.getValue()) return;
        if (packet.slot() >= 0 && packet.slot() <= 8) serverSlot = packet.slot();
    }

    private final Map<Integer, DeferredSlotUpdate> deferredSlotUpdates = new ConcurrentHashMap<>();
    private static final long SLOT_UPDATE_DEFER_MS = 500L;

    private record DeferredSlotUpdate(ScreenHandlerSlotUpdateS2CPacket packet, long resumeAt) { }

    private void handleSlotUpdate(PacketReceiveEvent event) {
        if (event.getPacket() instanceof InventoryS2CPacket) {
            deferredSlotUpdates.clear();
            return;
        }
        if (!(event.getPacket() instanceof ScreenHandlerSlotUpdateS2CPacket packet)) return;
        if (mc.player == null || packet.getSyncId() != mc.player.playerScreenHandler.syncId) return;

        int slot = packet.getSlot();
        if (slot < 36 || slot > 44) return;
        if (slot == 36 + mc.player.getInventory().getSelectedSlot()) return;

        if (!matchesOtherHeldStack(packet.getStack(), slot)) {
            deferredSlotUpdates.remove(slot);
            return;
        }
        deferredSlotUpdates.put(slot, new DeferredSlotUpdate(packet, System.currentTimeMillis() + SLOT_UPDATE_DEFER_MS));
        event.setCancelled(true);
    }

    private boolean matchesOtherHeldStack(ItemStack stack, int menuSlot) {
        if (stack.isEmpty()) return false;
        if (ItemStack.areEqual(mc.player.getOffHandStack(), stack)) return true;
        for (int i = 0; i < 9; i++) {
            if (menuSlot - 36 == i) continue;
            if (ItemStack.areEqual(mc.player.getInventory().getStack(i), stack)) return true;
        }
        return false;
    }

    @SubscribeEvent(priority = Integer.MAX_VALUE)
    public void onSendMovement(SendMovementEvent event) {
        if (deferredSlotUpdates.isEmpty()) return;
        if (mc.player == null || mc.getNetworkHandler() == null) {
            deferredSlotUpdates.clear();
            return;
        }
        long now = System.currentTimeMillis();
        for (Integer slot : new ArrayList<>(deferredSlotUpdates.keySet())) {
            DeferredSlotUpdate update = deferredSlotUpdates.get(slot);
            if (update == null || now < update.resumeAt()) continue;
            if (deferredSlotUpdates.remove(slot, update)) mc.getNetworkHandler().onScreenHandlerSlotUpdate(update.packet());
        }
    }

    @SubscribeEvent
    public void onDisconnect(ClientDisconnectEvent event) {
        deferredSlotUpdates.clear();
    }

    private void syncMainHandForUse(PacketSendEvent event) {
        Hand hand;
        if (event.getPacket() instanceof PlayerInteractBlockC2SPacket packet) hand = packet.getHand();
        else if (event.getPacket() instanceof PlayerInteractItemC2SPacket packet) hand = packet.getHand();
        else return;
        if (hand != Hand.MAIN_HAND || event.isCancelled()) return;

        int selected = mc.player.getInventory().getSelectedSlot();
        if (selected == serverSlot || selected < 0 || selected > 8) return;
        if (InventoryUtils.hasPendingSilentSwitch()) return;

        mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(selected));
    }
}
