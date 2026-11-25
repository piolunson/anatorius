package dev.project.anatorius.modules.impl.visuals;

import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.settings.impl.BooleanSetting;
import dev.project.anatorius.settings.impl.ColorSetting;
import dev.project.anatorius.settings.impl.ModeSetting;
import dev.project.anatorius.settings.impl.NumberSetting;
import dev.project.anatorius.utils.color.ColorUtils;
import dev.project.anatorius.utils.graphics.SkyShaderRenderer;

@RegisterModule(name = "Skybox", description = "Replaces the sky with an animated shader.", category = Module.Category.VISUALS)
public class SkyboxModule extends Module {
    public ModeSetting mode = new ModeSetting("Mode", "Waves: flowing light. Galaxy: drifting nebula with stars.", "Waves", new String[]{"Waves", "Galaxy"});
    public ColorSetting color = new ColorSetting("Color", "Sky color (alpha = opacity).", ColorUtils.palette(ColorUtils.THEME_ORCHID, 255));
    public NumberSetting speed = new NumberSetting("Speed", "Animation speed.", 1.0f, 0.0f, 3.0f);
    public NumberSetting parallax = new NumberSetting("Parallax", "How much the sky moves when you turn.", 1.0f, 0.0f, 1.5f);
    public BooleanSetting end = new BooleanSetting("End", "Also replace the End sky.", true);

    public void renderSky(boolean endSky) {
        if (!isToggled() || (endSky && !end.getValue())) return;
        SkyShaderRenderer.render(mode.getValue().equals("Galaxy") ? 1 : 0, color.getColor().getRGB(),
                speed.getValue().floatValue(), parallax.getValue().floatValue());
    }
}
