package dev.project.anatorius.modules.impl.player;

import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.*;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.settings.impl.BindSetting;
import dev.project.anatorius.settings.impl.ColorSetting;
import dev.project.anatorius.settings.impl.ModeSetting;
import dev.project.anatorius.utils.color.ColorUtils;
import dev.project.anatorius.utils.graphics.Renderer3D;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import dev.project.anatorius.modules.RegisterModule;
import org.lwjgl.glfw.GLFW;

@RegisterModule(name = "ChorusControl", description = "Holds a chorus-fruit teleport so you can see where it lands and confirm it with a key, instead of being thrown somewhere at random.", category = Module.Category.PLAYER)
public class ChorusControlModule extends Module {
    public BindSetting confirm = new BindSetting("Confirm", "The key that confirms the teleport.", GLFW.GLFW_KEY_LEFT_SHIFT);

    public ModeSetting mode = new ModeSetting("Render", "How the landing spot is drawn.", "Both", new String[]{"None", "Fill", "Outline", "Both"});
    public ColorSetting fillColor = new ColorSetting("FillColor", "The color that will be used for the fill rendering.", new ModeSetting.Visibility(mode, "Fill", "Both"), ColorUtils.getDefaultFillColor());
    public ColorSetting outlineColor = new ColorSetting("OutlineColor", "The color that will be used for the outline rendering.", new ModeSetting.Visibility(mode, "Outline", "Both"), ColorUtils.getDefaultOutlineColor());

    private PlayerPositionLookS2CPacket packet;
    private boolean cancel = false;

    @SubscribeEvent
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (!cancel && mc.player.isUsingItem()) {
            ItemStack stack = mc.player.getStackInHand(mc.player.getActiveHand());
            if (stack.getItem() == Items.CHORUS_FRUIT && stack.getMaxUseTime(mc.player) - mc.player.getItemUseTime() <= 1) {
                cancel = true;
            }
        }
    }

    @SubscribeEvent
    public void onKeyInput(KeyInputEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (packet == null) return;

        if (event.getKey() != confirm.getValue()) return;

        packet.apply(mc.getNetworkHandler());

        packet = null;
        cancel = false;
    }

    @SubscribeEvent
    public void onPacketSend(PacketSendEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (!cancel) return;

        if (event.getPacket() instanceof PlayerMoveC2SPacket packet && packet.changesPosition()) {
            event.setCancelled(true);
        }

        if (event.getPacket() instanceof TeleportConfirmC2SPacket) {
            event.setCancelled(true);
        }
    }

    @SubscribeEvent
    public void onPacketReceive(PacketReceiveEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (event.getPacket() instanceof PlayerPositionLookS2CPacket packet && cancel) {
            event.setCancelled(true);
            this.packet = packet;
        }
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (packet == null) return;

        Vec3d vec3d = new Vec3d(packet.change().position().getX(), packet.change().position().getY(), packet.change().position().getZ());
        Box box = net.minecraft.entity.EntityType.PLAYER.getDimensions().getBoxAt(vec3d);

        if (mode.getValue().equalsIgnoreCase("Fill") || mode.getValue().equalsIgnoreCase("Both")) Renderer3D.renderBox(event.getMatrices(), box, fillColor.getColor());
        if (mode.getValue().equalsIgnoreCase("Outline") || mode.getValue().equalsIgnoreCase("Both")) Renderer3D.renderBoxOutline(event.getMatrices(), box, outlineColor.getColor());
    }

    @Override
    public void onDisable() {
        if (mc.getNetworkHandler() != null && packet != null) {
            packet.apply(mc.getNetworkHandler());
            packet = null;
            cancel = false;
        }
    }
}
