package dev.project.anatorius.modules.impl.player;

import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.PacketSendEvent;
import dev.project.anatorius.events.impl.UpdateMovementEvent;
import dev.project.anatorius.mixins.accessors.PlayerMoveC2SPacketAccessor;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.modules.impl.player.SpeedMineModule;
import dev.project.anatorius.Anatorius;
import dev.project.anatorius.settings.impl.BooleanSetting;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

@RegisterModule(name = "AntiHunger", description = "Reduces the amount of hunger consumption.", category = Module.Category.PLAYER)
public class AntiHungerModule extends Module {
    public BooleanSetting ground = new BooleanSetting("Ground", "Modifies movement packets to decrease hunger consumption.", true);
    public BooleanSetting sprint = new BooleanSetting("Sprint", "Spoofs sprinting packets to decrease hunger consumption.", true);

    private boolean lastOnGround = false;
    private boolean ignore = false;

    @SubscribeEvent
    public void onPacketSend(PacketSendEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (ignore && event.getPacket() instanceof PlayerMoveC2SPacket) {
            ignore = false;
            return;
        }

        if (mc.player.hasVehicle() || mc.player.isTouchingWater() || mc.player.isSubmergedInWater()) return;

        if (event.getPacket() instanceof PlayerMoveC2SPacket packet && ground.getValue()) {
            SpeedMineModule speedMine = Anatorius.MODULE_MANAGER.getModule(SpeedMineModule.class);
            boolean packetMining = speedMine != null && speedMine.isPacketMining();
            if (mc.player.isOnGround() && mc.player.fallDistance <= 0.0 && !mc.interactionManager.isBreakingBlock() && !packetMining) {
                ((PlayerMoveC2SPacketAccessor) packet).setOnGround(false);
            }
        }

        if (event.getPacket() instanceof ClientCommandC2SPacket packet && sprint.getValue()) {
            if (packet.getMode() == ClientCommandC2SPacket.Mode.START_SPRINTING) {
                event.setCancelled(true);
            }
        }
    }

    @SubscribeEvent
    public void onUpdateMovement(UpdateMovementEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (mc.player.isOnGround() && !lastOnGround && ground.getValue()) ignore = true;
        lastOnGround = mc.player.isOnGround();
    }

    @Override
    public void onEnable() {
        if (mc.player == null) return;
        lastOnGround = mc.player.isOnGround();
    }
}
