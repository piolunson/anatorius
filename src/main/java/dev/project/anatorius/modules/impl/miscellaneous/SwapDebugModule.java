package dev.project.anatorius.modules.impl.miscellaneous;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.PacketReceiveEvent;
import dev.project.anatorius.events.impl.PacketSendEvent;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import net.minecraft.entity.EntityType;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerActionResponseS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket;

@RegisterModule(name = "SwapDebug", description = "Logs swaps / item uses and the server's answers to latest.log.", category = Module.Category.MISCELLANEOUS)
public class SwapDebugModule extends Module {
    @SubscribeEvent(priority = Integer.MIN_VALUE)
    public void onPacketSend(PacketSendEvent event) {
        if (getNull()) return;
        String line = describeSent(event.getPacket());
        if (line != null) log(">> " + line);
    }

    @SubscribeEvent(priority = Integer.MAX_VALUE)
    public void onPacketReceive(PacketReceiveEvent event) {
        if (getNull()) return;
        String line = describeReceived(event.getPacket());
        if (line != null) log("<< " + line);
    }

    private String describeSent(Packet<?> packet) {
        if (packet instanceof UpdateSelectedSlotC2SPacket p) return "SelectSlot " + p.getSelectedSlot();
        if (packet instanceof ClickSlotC2SPacket p) {
            return "ClickSlot sync=" + p.syncId() + " rev=" + p.revision() + " slot=" + p.slot() + " button=" + p.button() + " " + p.actionType();
        }
        if (packet instanceof PlayerInteractItemC2SPacket p) return "UseItem " + p.getHand() + " seq=" + p.getSequence();
        if (packet instanceof PlayerInteractEntityC2SPacket) return "InteractEntity/Attack";
        if (packet instanceof HandSwingC2SPacket p) return "Swing " + p.getHand();
        if (packet instanceof ClientCommandC2SPacket p) return "Command " + p.getMode();
        if (packet instanceof PlayerActionC2SPacket p) return "Action " + p.getAction() + " seq=" + p.getSequence();
        return null;
    }

    private String describeReceived(Packet<?> packet) {
        if (packet instanceof UpdateSelectedSlotS2CPacket p) return "SERVER SETS SLOT " + p.slot();
        if (packet instanceof ScreenHandlerSlotUpdateS2CPacket p) {
            return "SlotUpdate sync=" + p.getSyncId() + " rev=" + p.getRevision() + " slot=" + p.getSlot() + " -> " + p.getStack().getCount() + "x " + p.getStack().getItem();
        }
        if (packet instanceof InventoryS2CPacket p) return "INVENTORY RESYNC sync=" + p.syncId() + " rev=" + p.revision();
        if (packet instanceof PlayerActionResponseS2CPacket p) return "ActionAck seq=" + p.sequence();
        if (packet instanceof PlayerPositionLookS2CPacket p) return "SETBACK/teleport id=" + p.teleportId();
        if (packet instanceof EntitySpawnS2CPacket p && p.getEntityType() == EntityType.FIREWORK_ROCKET) return "Firework spawned id=" + p.getEntityId();
        if (packet instanceof net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket p && p.id() == mc.player.getId()) {
            for (var entry : p.trackedValues()) {
                if (entry.id() == 0 && entry.value() instanceof Byte flags) {
                    return "SERVER GLIDING=" + ((flags & (1 << 7)) != 0);
                }
            }
        }
        return null;
    }

    public static void logClick(int syncId, int slot, int button, net.minecraft.screen.slot.SlotActionType type) {
        SwapDebugModule module = Anatorius.MODULE_MANAGER == null ? null : Anatorius.MODULE_MANAGER.getModule(SwapDebugModule.class);
        if (module == null || !module.isToggled() || module.getNull()) return;
        module.log(">> Click sync=" + syncId + " slot=" + slot + " button=" + button + " " + type);
    }

    private void log(String line) {
        Anatorius.LOGGER.info("[SwapDebug] t={} held={} server={} main={} chest={} | {}",
                mc.player.age,
                mc.player.getInventory().getSelectedSlot(),
                Anatorius.POSITION_MANAGER.getServerSlot(),
                mc.player.getMainHandStack().getItem(),
                mc.player.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST).getItem(),
                line);
    }
}
