package dev.project.anatorius.modules.impl.visuals;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.settings.impl.BooleanSetting;
import dev.project.anatorius.settings.impl.ColorSetting;
import dev.project.anatorius.settings.impl.ModeSetting;
import dev.project.anatorius.settings.impl.NumberSetting;
import dev.project.anatorius.utils.color.ColorUtils;

@RegisterModule(name = "Atmosphere", description = "Modifies the world's atmosphere, such as time, weather and color.", category = Module.Category.VISUALS)
public class AtmosphereModule extends Module {
    public BooleanSetting modifyTime = new BooleanSetting("ModifyTime", "Modifies the world's time.", true);
    public NumberSetting time = new NumberSetting("Time", "The time that the world will be set to (x100 ticks: 0 sunrise, 60 noon, 120 sunset, 180 midnight). Only dimensions with a day cycle (the Overworld) react.", new BooleanSetting.Visibility(modifyTime, true), 60, -200, 200);
    public BooleanSetting modifyFog = new BooleanSetting("ModifyFog", "Modifies certain things about the world's fog.", false);
    public NumberSetting fogStart = new NumberSetting("FogStart", "The start value of the world's fog.", new BooleanSetting.Visibility(modifyFog, true), 50, 0, 300);
    public NumberSetting fogEnd = new NumberSetting("FogEnd", "The end value of the world's fog.", new BooleanSetting.Visibility(modifyFog, true), 150, 0, 300);
    public ColorSetting fogColor = new ColorSetting("FogColor", "Modifies the color of the world's fog.", new BooleanSetting.Visibility(modifyFog, true), ColorUtils.palette(new java.awt.Color(60, 157, 173), 180));
    public ModeSetting modifyWeather = new ModeSetting("ModifyWeather", "Client-side weather: Snow, Rain, Clear (nothing falls) or Vanilla (whatever the server has).", "Vanilla", new String[]{"Vanilla", "Clear", "Rain", "Snow"});

    public static boolean readingRealTime;

    public static Long timeOverride() {
        if (readingRealTime || Anatorius.MODULE_MANAGER == null) return null;
        AtmosphereModule module = Anatorius.MODULE_MANAGER.getModule(AtmosphereModule.class);
        if (module == null || !module.isToggled() || !module.modifyTime.getValue()) return null;
        return module.time.getValue().longValue() * 100L;
    }

    public static AtmosphereModule fogModule() {
        if (Anatorius.MODULE_MANAGER == null) return null;
        AtmosphereModule module = Anatorius.MODULE_MANAGER.getModule(AtmosphereModule.class);
        return module != null && module.isToggled() && module.modifyFog.getValue() ? module : null;
    }

    public static String weatherOverride() {
        if (Anatorius.MODULE_MANAGER == null) return null;
        AtmosphereModule module = Anatorius.MODULE_MANAGER.getModule(AtmosphereModule.class);
        if (module == null || !module.isToggled() || module.modifyWeather.getValue().equalsIgnoreCase("Vanilla")) return null;
        return module.modifyWeather.getValue();
    }
}
