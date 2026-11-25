package dev.project.anatorius.modules.impl.visuals;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.TickEvent;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.settings.impl.BooleanSetting;
import dev.project.anatorius.settings.impl.NumberSetting;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

@RegisterModule(name = "Zoom", description = "Zooms your view in like a spyglass, with a smooth animation and scroll to adjust.", category = Module.Category.VISUALS)
public class ZoomModule extends Module {
    public NumberSetting factor = new NumberSetting("Factor", "How many times closer things look.", 4.0, 1.5, 50.0);
    public BooleanSetting hold = new BooleanSetting("Hold", "Zooms only while the bind is held (when the module has a bind).", true);
    public BooleanSetting scroll = new BooleanSetting("Scroll", "The mouse wheel zooms further in or out while zoomed.", true);
    public BooleanSetting smooth = new BooleanSetting("Smooth", "Animates the zoom in and out.", true);
    public BooleanSetting cinematic = new BooleanSetting("Cinematic", "Uses the smooth (cinematic) camera while zoomed.", false);
    public BooleanSetting hideHand = new BooleanSetting("HideHand", "Hides your hand while zoomed.", true);

    private static double current = 1.0;
    private static long lastFrame;
    private double scrollMultiplier = 1.0;
    private boolean cinematicBefore;
    private boolean cinematicSet;

    @Override
    public void onEnable() {
        scrollMultiplier = 1.0;
        cinematicSet = cinematic.getValue();
        if (cinematicSet) {
            cinematicBefore = mc.options.smoothCameraEnabled;
            mc.options.smoothCameraEnabled = true;
        }
    }

    @Override
    public void onDisable() {
        if (cinematicSet) mc.options.smoothCameraEnabled = cinematicBefore;
        cinematicSet = false;
    }

    @SubscribeEvent
    public void onTick(TickEvent event) {
        if (hold.getValue() && getBind() != 0 && mc.currentScreen == null && !bindHeld()) setToggled(false);
    }

    private boolean bindHeld() {
        int bind = getBind();
        long handle = mc.getWindow().getHandle();
        if (bind < 0) return GLFW.glfwGetMouseButton(handle, -bind - 1) == GLFW.GLFW_PRESS;
        return InputUtil.isKeyPressed(mc.getWindow(), bind);
    }

    public boolean onScroll(double amount) {
        if (!scroll.getValue() || amount == 0) return false;
        double base = factor.getValue().doubleValue();
        scrollMultiplier = Math.max(1.0 / base, Math.min(50.0 / base, scrollMultiplier * (amount > 0 ? 1.25 : 0.8)));
        return true;
    }

    private double target() {
        return isToggled() ? Math.max(1.0, factor.getValue().doubleValue() * scrollMultiplier) : 1.0;
    }

    public static double zoom() {
        ZoomModule module = Anatorius.MODULE_MANAGER == null ? null : Anatorius.MODULE_MANAGER.getModule(ZoomModule.class);
        if (module == null) return 1.0;
        long now = System.nanoTime();
        double dt = lastFrame == 0 ? 0 : Math.min(0.1, (now - lastFrame) / 1e9);
        lastFrame = now;
        double target = module.target();
        if (!module.smooth.getValue()) current = target;
        else current += (target - current) * (1.0 - Math.exp(-dt * 14.0));
        if (Math.abs(current - target) < 0.001) current = target;
        return current;
    }

    public static double currentZoom() {
        return current;
    }

    public static boolean hidesHand() {
        ZoomModule module = Anatorius.MODULE_MANAGER == null ? null : Anatorius.MODULE_MANAGER.getModule(ZoomModule.class);
        return module != null && module.hideHand.getValue() && current > 1.05;
    }

    @Override
    public String getMetaData() {
        return String.format(java.util.Locale.ROOT, "%.1fx", isToggled() ? target() : factor.getValue().doubleValue());
    }
}
