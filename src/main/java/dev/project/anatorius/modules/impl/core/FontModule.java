package dev.project.anatorius.modules.impl.core;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.SettingChangeEvent;
import dev.project.anatorius.events.impl.TickEvent;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.settings.impl.*;
import dev.project.anatorius.utils.font.FontRenderer;

import java.awt.*;
import java.io.InputStream;

@RegisterModule(name = "Font", description = "Manages the client and the game's font rendering.", category = Module.Category.CORE, drawn = false)
public class FontModule extends Module {
    public CategorySetting customFontCategory = new CategorySetting("CustomFont", "The category for settings relating to custom fonts.");
    public StringSetting name = new StringSetting("Name", "The name of the font that will be rendered.", new CategorySetting.Visibility(customFontCategory), "Verdana");
    public NumberSetting size = new NumberSetting("Size", "The size of the custom font that will be rendered.", new CategorySetting.Visibility(customFontCategory), 18, 8, 48);
    public ModeSetting style = new ModeSetting("Style", "The style that will be used in the font's rendering.", new CategorySetting.Visibility(customFontCategory), "Regular", new String[]{"Regular", "Bold", "Italic", "Bold Italic"});

    public CategorySetting globalCategory = new CategorySetting("GlobalFont", "Draws the game's own text (not just the client's menus and HUD) with the custom font.");
    public BooleanSetting global = new BooleanSetting("Global", "Applies the custom font to the game's vanilla text too: chat, buttons, tooltips, item counts, nametags, signs, text fields. If the font cannot be loaded the game's font is kept.", new CategorySetting.Visibility(globalCategory), true);
    public BooleanSetting globalMenus = new BooleanSetting("Menus", "Global font while a screen is open: title screen, options, inventory and containers, chat input, the client's own screens.", new BooleanSetting.Visibility(global, true), true);
    public BooleanSetting globalInGame = new BooleanSetting("InGame", "Global font with no screen open: chat history, hotbar and item counts, boss bars, scoreboard, tooltips, nametags, signs.", new BooleanSetting.Visibility(global, true), true);

    public CategorySetting shadowsCategory = new CategorySetting("Shadows", "The category for settings related to font shadows.");
    public ModeSetting shadowMode = new ModeSetting("ShadowMode", "Mode", "The way that the shadow will be rendered.", new CategorySetting.Visibility(shadowsCategory), "Default", new String[]{"None", "Default", "Custom"});
    public NumberSetting shadowOffset = new NumberSetting("ShadowOffset", "Offset", "The distance of the shadow from the text being rendered.", new ModeSetting.Visibility(shadowMode, "Custom"), 0.5f, -1.0f, 1.0f);

    private boolean dirty;

    @SubscribeEvent
    public void onSettingChange(SettingChangeEvent event) {
        if (event.getSetting() == name || event.getSetting() == size || event.getSetting() == style) {
            dirty = true;
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent event) {
        if (!dirty) return;
        dirty = false;
        updateFontRenderer();
    }

    @Override
    public void onEnable() {
        dirty = false;
        updateFontRenderer();
    }

    @Override
    public void onDisable() {
        Anatorius.FONT_MANAGER.setFontRenderer(null);
    }

    private void updateFontRenderer() {
        String styleNormalized = style.getValue().replace(" ", "").toLowerCase();
        int awtStyle = styleNormalized.equals("bolditalic") ? Font.BOLD | Font.ITALIC : styleNormalized.equals("bold") ? Font.BOLD : styleNormalized.equals("italic") ? Font.ITALIC : Font.PLAIN;

        Font awtFont = null;
        String fontName = name.getValue();
        if (fontName.equalsIgnoreCase("Quicksand")) {
            try (InputStream in = dev.project.anatorius.utils.system.AssetVault.open("assets/anatorius/fonts/Quicksand.ttf")) {
                if (in != null) {
                    awtFont = Font.createFont(Font.TRUETYPE_FONT, in).deriveFont(awtStyle, size.getValue().floatValue());
                }
            } catch (Exception ignored) {
                awtFont = null;
            }
        }

        if (awtFont == null && !Anatorius.FONT_MANAGER.hasFont(fontName)) {
            Anatorius.FONT_MANAGER.setFontRenderer(null);
            if (mc.player != null) Anatorius.CHAT_MANAGER.message("The font \"" + fontName + "\" is not installed, so the game's font is used.", "module-font-missing");
            return;
        }
        if (awtFont == null) awtFont = new Font(fontName, awtStyle, size.getValue().intValue());

        try {
            Font fallback = new Font(Font.DIALOG, awtStyle, size.getValue().intValue());
            Anatorius.FONT_MANAGER.setFontRenderer(new FontRenderer(new Font[]{awtFont, fallback}, size.getValue().floatValue() / 2.0f));
        } catch (Throwable t) {
            Anatorius.FONT_MANAGER.setFontRenderer(null);
        }
    }
}
