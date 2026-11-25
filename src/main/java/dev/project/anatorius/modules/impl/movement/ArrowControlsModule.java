package dev.project.anatorius.modules.impl.movement;

import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.RenderWorldEvent;
import dev.project.anatorius.mixins.accessors.CreativeInventoryScreenAccessor;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.settings.impl.BooleanSetting;
import dev.project.anatorius.settings.impl.NumberSetting;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ingame.*;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemGroup;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

@RegisterModule(name = "ArrowControls", description = "Allows you to control the camera using your arrow keys.", category = Module.Category.MOVEMENT)
public class ArrowControlsModule extends Module {
    public NumberSetting speed = new NumberSetting("Speed", "The speed at which the camera will be moving at.", 1.0f, 0.1f, 10.0f);
    public BooleanSetting inventory = new BooleanSetting("Inventory", "Allows you to control the camera when a screen is opened.", true);

    private long lastTime;

    public volatile int forcedKeys;

    @Override
    public void onEnable() {
        lastTime = System.currentTimeMillis();
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldEvent event) {
        if (mc.player == null || mc.world == null) return;
        long now = System.currentTimeMillis();
        long dt = Math.min(now - lastTime, 100L);
        lastTime = now;
        if (mc.currentScreen != null && (!inventory.getValue() || (mc.currentScreen instanceof ChatScreen || mc.currentScreen instanceof BookEditScreen || mc.currentScreen instanceof SignEditScreen || mc.currentScreen instanceof JigsawBlockScreen || mc.currentScreen instanceof StructureBlockScreen || mc.currentScreen instanceof AnvilScreen || (mc.currentScreen instanceof CreativeInventoryScreen && CreativeInventoryScreenAccessor.getSelectedTab().getType() == ItemGroup.Type.SEARCH))))
            return;

        float yaw = 0.0f;
        float pitch = 0.0f;

        float amount = (dt / 10f) * speed.getValue().floatValue();

        int forced = forcedKeys;
        if (key(GLFW.GLFW_KEY_LEFT, forced & 1)) yaw -= amount;
        if (key(GLFW.GLFW_KEY_RIGHT, forced & 2)) yaw += amount;
        if (key(GLFW.GLFW_KEY_UP, forced & 4)) pitch -= amount;
        if (key(GLFW.GLFW_KEY_DOWN, forced & 8)) pitch += amount;
        if (yaw == 0.0f && pitch == 0.0f) return;

        float newPitch = MathHelper.clamp(mc.player.getPitch() + pitch, -90.0f, 90.0f);
        float appliedPitch = newPitch - mc.player.getPitch();
        mc.player.setYaw(mc.player.getYaw() + yaw);
        mc.player.lastYaw += yaw;
        mc.player.setPitch(newPitch);
        mc.player.lastPitch += appliedPitch;
    }

    private boolean key(int glfwKey, int forced) {
        return forced != 0 || InputUtil.isKeyPressed(mc.getWindow(), glfwKey);
    }

    @Override
    public String getMetaData() {
        return String.valueOf(speed.getValue().floatValue());
    }
}
