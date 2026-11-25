package dev.project.anatorius.modules.impl.movement;

import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.PacketReceiveEvent;
import dev.project.anatorius.events.impl.PacketSendEvent;
import dev.project.anatorius.events.impl.RenderWorldEvent;
import dev.project.anatorius.events.impl.TickEvent;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.settings.impl.BooleanSetting;
import dev.project.anatorius.settings.impl.CategorySetting;
import dev.project.anatorius.settings.impl.ColorSetting;
import dev.project.anatorius.settings.impl.NumberSetting;
import dev.project.anatorius.utils.color.ColorUtils;
import dev.project.anatorius.utils.graphics.Renderer3D;
import dev.project.anatorius.utils.system.Timer;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.network.packet.s2c.play.HealthUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;

@RegisterModule(name = "FakeLag", description = "Holds all your movement packets (blink), releasing them when you turn it off.", category = Module.Category.MOVEMENT)
public class FakeLagModule extends Module {
    public BooleanSetting autoDisable = new BooleanSetting("AutoDisable", "Only stay enabled for a short burst, then release the packets and turn the module off.", false);
    public NumberSetting duration = new NumberSetting("Duration", "Seconds to stay enabled before auto-disabling.", new BooleanSetting.Visibility(autoDisable, true), 0.6f, 0.3f, 5.0f);

    public CategorySetting renderCategory = new CategorySetting("Render", "Show where the server still thinks you are.");
    public BooleanSetting render = new BooleanSetting("Render", "Draw a ghost box at the held (server) position.", new CategorySetting.Visibility(renderCategory), true);
    public ColorSetting fillColor = new ColorSetting("FillColor", "Ghost fill.", new CategorySetting.Visibility(renderCategory), ColorUtils.getDefaultFillColor());
    public ColorSetting outlineColor = new ColorSetting("OutlineColor", "Ghost outline.", new CategorySetting.Visibility(renderCategory), ColorUtils.getDefaultOutlineColor());

    private final ArrayList<PlayerMoveC2SPacket> packets = new ArrayList<>();
    private final Timer enabledTimer = new Timer();
    private boolean sending = false;

    private Vec3d serverPos;

    @SubscribeEvent
    public void onPacketReceive(PacketReceiveEvent event) {
        if (getNull()) return;

        if (event.getPacket() instanceof PlayerPositionLookS2CPacket || event.getPacket() instanceof DisconnectS2CPacket
                || event.getPacket() instanceof HealthUpdateS2CPacket packet && packet.getHealth() <= 0) {
            sendPackets();
        }
    }

    @SubscribeEvent
    public void onPacketSend(PacketSendEvent event) {
        if (getNull() || sending || !(event.getPacket() instanceof PlayerMoveC2SPacket packet)) return;

        synchronized (packets) {
            event.setCancelled(true);
            packets.add(packet);
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent event) {
        if (getNull()) return;

        if (autoDisable.getValue() && enabledTimer.hasTimeElapsed((long) (duration.getValue().floatValue() * 1000))) {
            setToggled(false);
        }
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldEvent event) {
        if (getNull() || !render.getValue() || serverPos == null || packets.isEmpty()) return;

        Box box = new Box(serverPos.x - 0.3, serverPos.y, serverPos.z - 0.3, serverPos.x + 0.3, serverPos.y + 1.8, serverPos.z + 0.3);
        Renderer3D.renderBox(event.getMatrices(), box, fillColor.getColor());
        Renderer3D.renderBoxOutline(event.getMatrices(), box, outlineColor.getColor());
    }

    @Override
    public void onEnable() {
        enabledTimer.reset();
        synchronized (packets) {
            packets.clear();
        }
        serverPos = mc.player == null ? null : mc.player.getEntityPos();
    }

    @Override
    public void onDisable() {
        if (getNull()) return;
        sendPackets();
    }

    private void sendPackets() {
        synchronized (packets) {
            if (packets.isEmpty()) return;
            sending = true;
            for (PlayerMoveC2SPacket packet : packets) {
                mc.player.networkHandler.sendPacket(packet);
                if (packet.changesPosition()) {
                    serverPos = new Vec3d(packet.getX(serverPos == null ? mc.player.getX() : serverPos.x),
                            packet.getY(serverPos == null ? mc.player.getY() : serverPos.y),
                            packet.getZ(serverPos == null ? mc.player.getZ() : serverPos.z));
                }
            }
            packets.clear();
            sending = false;
        }
    }

    @Override
    public String getMetaData() {
        return String.valueOf(packets.size());
    }
}
