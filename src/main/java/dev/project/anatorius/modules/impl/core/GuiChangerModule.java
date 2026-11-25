package dev.project.anatorius.modules.impl.core;

import dev.project.anatorius.gui.style.GuiStyle;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.settings.impl.BooleanSetting;
import dev.project.anatorius.settings.impl.ColorSetting;
import dev.project.anatorius.settings.impl.ModeSetting;

import java.awt.Color;

@RegisterModule(name = "GuiChanger", description = "Switches the look of the ClickGui and the HUD.", category = Module.Category.CORE, persistent = true, drawn = false)
public class GuiChangerModule extends Module {
    public final ModeSetting styleSetting = new ModeSetting("Style", "Anatorius: the client's own look. Earthhack: 3arthh4ck ClickGui + Mio HUD. Lavender: slim lavender columns. Minimal: rounded, quiet panels.",
            "Anatorius", new String[]{"Anatorius", "Earthhack", "Lavender", "Minimal"});

    public final ColorSetting earthColor = new ColorSetting("EarthColor", "Color", "Earthhack accent: headers, enabled modules, checkboxes and sliders.",
            new ModeSetting.Visibility(styleSetting, "Earthhack"), new ColorSetting.Color(new Color(0, 80, 255, 255), false, false));
    public final BooleanSetting mioHud = new BooleanSetting("MioHud", "Mio HUD", "Draw the HUD in the Mio style while the Earthhack look is on.",
            new ModeSetting.Visibility(styleSetting, "Earthhack"), true);
    public final ColorSetting mioColor = new ColorSetting("MioColor", "HUD Color", "Mio HUD text colour (names and labels; values stay white).",
            new ModeSetting.Visibility(styleSetting, "Earthhack"), new ColorSetting.Color(new Color(189, 153, 255, 255), false, false));
    public final ModeSetting mioFade = new ModeSetting("MioFade", "HUD Fade", "None: one colour. Pulse: fades between HUD Color and Second Color down the lists.",
            new ModeSetting.Visibility(styleSetting, "Earthhack"), "None", new String[]{"None", "Pulse"});
    public final ColorSetting mioSecondColor = new ColorSetting("MioSecondColor", "Second Color", "The second colour of the Pulse fade.",
            new ModeSetting.Visibility(styleSetting, "Earthhack"), new ColorSetting.Color(new Color(105, 86, 143, 255), false, false));

    public final ColorSetting lavenderColor = new ColorSetting("LavenderColor", "Color", "Lavender highlight behind enabled modules and settings.",
            new ModeSetting.Visibility(styleSetting, "Lavender"), new ColorSetting.Color(new Color(150, 145, 200, 110), false, false));

    public final ColorSetting minimalColor = new ColorSetting("MinimalColor", "Color", "Minimal accent: enabled modules, switches and sliders.",
            new ModeSetting.Visibility(styleSetting, "Minimal"), new ColorSetting.Color(new Color(167, 139, 250, 255), false, false));

    public GuiStyle style() {
        return switch (styleSetting.getValue()) {
            case "Earthhack" -> GuiStyle.EARTHHACK;
            case "Lavender" -> GuiStyle.LAVENDER;
            case "Minimal" -> GuiStyle.MINIMAL;
            default -> GuiStyle.ANATORIUS;
        };
    }

    public boolean mioHudActive() {
        return style() == GuiStyle.EARTHHACK && mioHud.getValue();
    }

    public Color mioColorAt(int y) {
        Color a = mioColor.getColor();
        if (!mioFade.getValue().equals("Pulse")) return a;
        Color b = mioSecondColor.getColor();
        double t = ((System.currentTimeMillis() + y * 5L * 10L / 11L) % 2000L) / 2000.0;
        double f = t <= 0.5 ? t * 2.0 : 1.0 - (t - 0.5) * 2.0;
        return new Color((int) (a.getRed() + (b.getRed() - a.getRed()) * f), (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * f),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * f), a.getAlpha());
    }
}
