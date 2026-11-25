package dev.project.anatorius.modules.impl.visuals;

import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.RenderShaderEvent;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.settings.impl.BooleanSetting;
import dev.project.anatorius.settings.impl.ModeSetting;
import dev.project.anatorius.settings.impl.NumberSetting;
import dev.project.anatorius.utils.graphics.MotionBlurRenderer;

@RegisterModule(name = "MotionBlur", description = "Real-time motion blur when you turn or run; sharp the moment you stop.", category = Module.Category.VISUALS)
public class MotionBlurModule extends Module {
    public final NumberSetting intensity = new NumberSetting("Intensity", "How long the virtual shutter stays open (stronger blur).", 55, 0, 95);
    public final ModeSetting quality = new ModeSetting("Quality", "Samples along the blur: more = smoother, a bit more GPU.", "Medium", new String[]{"Low", "Medium", "High"});
    public final BooleanSetting moveBlur = new BooleanSetting("MoveBlur", "Light zoom blur while moving forward.", true);

    @Override
    public void onDisable() {
        MotionBlurRenderer.INSTANCE.reset();
    }

    @SubscribeEvent
    public void onRenderShader(RenderShaderEvent.Post event) {
        if (getNull()) return;
        int samples = switch (quality.getValue()) {
            case "Low" -> 8;
            case "High" -> 24;
            default -> 14;
        };
        MotionBlurRenderer.INSTANCE.render(intensity.getValue().floatValue() / 100.0f, samples, moveBlur.getValue());
    }
}
