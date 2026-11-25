package dev.project.anatorius.modules.impl.core;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.settings.impl.BooleanSetting;
import dev.project.anatorius.settings.impl.CategorySetting;
import dev.project.anatorius.settings.impl.ColorSetting;
import dev.project.anatorius.settings.impl.ModeSetting;
import dev.project.anatorius.settings.impl.NumberSetting;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.glfw.GLFW;

import java.awt.*;

@RegisterModule(name = "ClickGui", description = "Allows you to change and interact with the client's modules and settings through a GUI.", category = Module.Category.CORE, drawn = false, bind = GLFW.GLFW_KEY_RIGHT_SHIFT)
public class ClickGuiModule extends Module {
    public dev.project.anatorius.settings.impl.WhitelistSetting toggleSound = new dev.project.anatorius.settings.impl.WhitelistSetting("ToggleSound", "Sounds", "The sound of the ClickGui: turning modules on and off and changing settings with the mouse (not keybinds). None picked: silent.", dev.project.anatorius.settings.impl.WhitelistSetting.Type.SOUNDS).withDefault("click");
    public NumberSetting scrollSpeed = new NumberSetting("ScrollSpeed", "The speed at which the scrolling of the frames will be at.", 15, 1, 50);
    public NumberSetting scale = new NumberSetting("Scale", "The size of the ClickGui. Applied the next time you open it.", 1.04, 0.5, 1.5);
    public BooleanSetting winter = new BooleanSetting("Winter", "In winter (Nov 10 to the end of February), snow falls behind the frames and settles on their headers. Off: the usual look.", false);

    public CategorySetting textCategory = new CategorySetting("Text", "The category containing all settings related to text.");
    public ColorSetting textColor = new ColorSetting("TextColor", "TextColor", "The color used for text in the ClickGui.", new CategorySetting.Visibility(textCategory), new ColorSetting.Color(new Color(238, 244, 246, 255), false, false));
    public ColorSetting enabledTextColor = new ColorSetting("EnabledColor", "EnabledColor", "The color used for text when a module is enabled.", new CategorySetting.Visibility(textCategory), new ColorSetting.Color(new Color(255, 255, 255, 255), false, false));

    public CategorySetting shadersCategory = new CategorySetting("Shaders", "Settings related to GUI background shaders.");
    public ModeSetting backgroundShader = new ModeSetting("BackgroundShader", "Background", "The shader drawn on the category frames. The blurred game behind the GUI is the Blur module (Visuals).", new CategorySetting.Visibility(shadersCategory), "None", new String[]{"None", "Fluid", "Colorful Plasma"});
    public ModeSetting headerShader = new ModeSetting("Header", "Header", "The shader for category headers.", new CategorySetting.Visibility(shadersCategory), "None", new String[]{"None", "Fluid", "Colorful Plasma"});

    public CategorySetting colorsCategory = new CategorySetting("Colors", "The category containing all settings related to the ClickGui colors.");
    public ColorSetting color = new ColorSetting("Color", "Color", "The main color used for the outer frame of the ClickGui.", new CategorySetting.Visibility(colorsCategory), new ColorSetting.Color(new Color(118, 214, 201, 200), true, false));
    public ColorSetting background = new ColorSetting("Background", "Background", "The color used for the background of category frames.", new CategorySetting.Visibility(colorsCategory), new ColorSetting.Color(new Color(12, 17, 22, 205), false, false));
    public ColorSetting button = new ColorSetting("Button", "Button", "The color used for module and setting buttons.", new CategorySetting.Visibility(colorsCategory), new ColorSetting.Color(new Color(24, 32, 40, 165), false, false));
    public ColorSetting enabled = new ColorSetting("Enabled", "Enabled", "The color used for enabled module buttons.", new CategorySetting.Visibility(colorsCategory), new ColorSetting.Color(new Color(118, 214, 201, 130), true, false));
    public BooleanSetting tint = new BooleanSetting("Tint", "Tint", "Whether or not to apply tinting to the ClickGui.", new CategorySetting.Visibility(colorsCategory), false);
    public BooleanSetting gradient = new BooleanSetting("Gradient", "Gradient", "Whether or not to use gradients in the ClickGui.", new CategorySetting.Visibility(colorsCategory), false);

    private Screen returnScreen;

    public void openFrom(Screen parent) {
        returnScreen = parent;
        if (isToggled()) mc.setScreen(Anatorius.CLICK_GUI);
        else setToggled(true);
    }

    @Override
    public void onEnable() {
        if (mc.player == null && returnScreen == null) {
            setToggled(false);
            return;
        }

        mc.setScreen(Anatorius.CLICK_GUI);
    }

    @Override
    public void onDisable() {
        Screen back = returnScreen;
        returnScreen = null;
        mc.setScreen(back);
    }

    public boolean isRainbow() {
        if(color.isSync()) return Anatorius.MODULE_MANAGER.getModule(ColorModule.class).color.isRainbow();
        return color.isRainbow();
    }

    public static void playSound() {
        if (dev.project.anatorius.Anatorius.MODULE_MANAGER == null) return;
        ClickGuiModule clickGui = dev.project.anatorius.Anatorius.MODULE_MANAGER.getModule(ClickGuiModule.class);
        if (clickGui != null) dev.project.anatorius.utils.minecraft.ClientSounds.play(clickGui.toggleSound.getSound(), 1.0f);
    }
}
