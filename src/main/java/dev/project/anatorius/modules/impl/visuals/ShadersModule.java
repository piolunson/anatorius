package dev.project.anatorius.modules.impl.visuals;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.settings.impl.*;
import dev.project.anatorius.utils.color.ColorUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.player.PlayerEntity;

import java.awt.*;

@RegisterModule(name = "Shaders", description = "Overlays specified entities with a customizable shader.", category = Module.Category.VISUALS)
public class ShadersModule extends Module {
    public CategorySetting targets = new CategorySetting("Targets", "The things that the shader rendering will be applied onto.");
    public BooleanSetting players = new BooleanSetting("Players", "Renders the shader effect on player entities.", new CategorySetting.Visibility(targets), true);
    public BooleanSetting hostiles = new BooleanSetting("Hostiles", "Renders the shader effect on hostile entities.", new CategorySetting.Visibility(targets), true);
    public BooleanSetting animals = new BooleanSetting("Animals", "Renders the shader effect on animal entities.", new CategorySetting.Visibility(targets), true);
    public BooleanSetting ambient = new BooleanSetting("Ambient", "Renders the shader effect on ambient entities.", new CategorySetting.Visibility(targets), false);
    public BooleanSetting invisibles = new BooleanSetting("Invisibles", "Renders the shader effect on invisible entities.", new CategorySetting.Visibility(targets), true);
    public BooleanSetting items = new BooleanSetting("Items", "Renders the shader effect on item entities.", new CategorySetting.Visibility(targets), true);
    public BooleanSetting crystals = new BooleanSetting("Crystals", "Renders the shader effect on crystal entities.", new CategorySetting.Visibility(targets), true);
    public BooleanSetting others = new BooleanSetting("Others", "Renders the shader effect on miscellaneous entities.", new CategorySetting.Visibility(targets), false);
    public BooleanSetting hands = new BooleanSetting("Hands", "Renders the shader effect on your hands.", new CategorySetting.Visibility(targets), true);
    public NumberSetting range = new NumberSetting("Range", "Max distance (blocks) the shader is drawn at. Lower = more FPS.", new CategorySetting.Visibility(targets), 128, 8, 256);

    public ModeSetting theme = new ModeSetting("Theme", "The shader theme to use for rendering.", "Default", new String[]{"None", "Default", "Gradient", "HueShift", "BlurGlow", "OutlineOnly", "ShorelineBloom", "Sky", "Custom"});

    public ModeSetting mode = new ModeSetting("Mode", "The rendering that will be applied to the target.", "Both", new String[]{"Fill", "Outline", "Both"});
    public NumberSetting opacity = new NumberSetting("Opacity", "The opacity of the fill (multiplies the fill colour's own alpha).", new ModeSetting.Visibility(mode, "Fill", "Both"), 255, 0, 255);
    public ColorSetting color = new ColorSetting("Color", "The color that will be used for the fill rendering.", ColorUtils.palette(ColorUtils.THEME_ACCENT, 120));

    public ColorSetting outlineColor = new ColorSetting("OutlineColor", "The color used for the outline rendering.", new ModeSetting.Visibility(mode, "Outline", "Both"), ColorUtils.palette(ColorUtils.THEME_ACCENT, 255));

    public ModeSetting friends = new ModeSetting("Friends",  "The color that will be applied to friended entities.", "Default", new String[]{"Default", "Custom", "Sync"});
    public ColorSetting friendColor = new ColorSetting("FriendColor", "The color that will be used for the shader effect on friends.", new ModeSetting.Visibility(friends, "Custom"), ColorUtils.paletteFill(ColorUtils.THEME_SAFE));

    public NumberSetting width = new NumberSetting("Width", "Outline width in pixels.", new ModeSetting.Visibility(theme, "Default", "Gradient", "HueShift", "BlurGlow", "OutlineOnly", "ShorelineBloom", "Sky", "Custom"), 3, 1, 10);
    public NumberSetting glowMultiplier = new NumberSetting("GlowMultiplier", "Glow intensity multiplier.", new ModeSetting.Visibility(theme, "Default", "Gradient", "HueShift", "BlurGlow", "OutlineOnly", "ShorelineBloom", "Sky", "Custom"), 1.5f, 0.1f, 5.0f);
    public BooleanSetting fastLines = new BooleanSetting("FastLines", "Samples the glow at half resolution: much cheaper, nearly the same look.", new ModeSetting.Visibility(theme, "Default", "Gradient", "HueShift", "OutlineOnly", "Sky", "Custom"), true);

    public CategorySetting dotsCategory = new CategorySetting("Dots", "Decorative dot/line pattern settings.", new ModeSetting.Visibility(theme, "Default", "Gradient", "HueShift", "BlurGlow"));
    public ModeSetting dotsMode = new ModeSetting("DotsMode", "Dot pattern type drawn inside the fill.", new CategorySetting.Visibility(dotsCategory), "None", new String[]{"None", "Dots", "Lines"});
    public NumberSetting dotsRadius = new NumberSetting("DotsRadius", "Spacing between dots/lines in pixels.", new ModeSetting.Visibility(dotsMode, "Dots", "Lines"), 5, 2, 20);
    public NumberSetting dotsAlpha = new NumberSetting("DotsAlpha", "Opacity of the dots/lines.", new ModeSetting.Visibility(dotsMode, "Dots", "Lines"), 0.8f, 0.0f, 1.0f);

    public CategorySetting gradientCategory = new CategorySetting("Gradient Settings", "Settings specific to the Gradient theme.", new ModeSetting.Visibility(theme, "Gradient"));
    public ColorSetting fillColor2 = new ColorSetting("FillColor2", "Second gradient fill color.", new CategorySetting.Visibility(gradientCategory), ColorUtils.palette(ColorUtils.THEME_PRIMARY, 128));
    public ColorSetting outlineColor2 = new ColorSetting("OutlineColor2", "Second gradient outline color.", new CategorySetting.Visibility(gradientCategory), ColorUtils.palette(ColorUtils.THEME_SKY, 255));
    public NumberSetting gradientStep = new NumberSetting("GradientStep", "How many color bands fit on the screen.", new CategorySetting.Visibility(gradientCategory), 1.0f, 0.1f, 5.0f);
    public NumberSetting gradientSpeed = new NumberSetting("GradientSpeed", "How fast the gradient moves (0 = still).", new CategorySetting.Visibility(gradientCategory), 1.0f, 0.0f, 10.0f);

    public CategorySetting hueShiftCategory = new CategorySetting("HueShift Settings", "Settings specific to the HueShift theme.", new ModeSetting.Visibility(theme, "HueShift"));
    public NumberSetting fillOffset = new NumberSetting("FillOffset", "Starting hue of the fill rainbow.", new CategorySetting.Visibility(hueShiftCategory), 0.5f, 0.0f, 1.0f);
    public NumberSetting fillStrength = new NumberSetting("FillStrength", "How many rainbow colors spread across the fill.", new CategorySetting.Visibility(hueShiftCategory), 1.0f, 0.0f, 5.0f);
    public NumberSetting outlineOffset = new NumberSetting("OutlineOffset", "Starting hue of the outline rainbow.", new CategorySetting.Visibility(hueShiftCategory), 0.5f, 0.0f, 1.0f);
    public NumberSetting outlineStrength = new NumberSetting("OutlineStrength", "How many rainbow colors spread across the outline.", new CategorySetting.Visibility(hueShiftCategory), 1.0f, 0.0f, 5.0f);
    public NumberSetting hueSpeed = new NumberSetting("HueSpeed", "How fast the rainbow cycles (0 = still).", new CategorySetting.Visibility(hueShiftCategory), 1.0f, 0.0f, 10.0f);

    public CategorySetting skyCategory = new CategorySetting("Sky Settings", "Settings specific to the Sky theme.", new ModeSetting.Visibility(theme, "Sky"));
    public ColorSetting skyColor = new ColorSetting("SkyColor", "Color of the sky light inside the entity.", new CategorySetting.Visibility(skyCategory), ColorUtils.palette(ColorUtils.THEME_ORCHID, 255));
    public NumberSetting skySpeed = new NumberSetting("SkySpeed", "How fast the sky light flows.", new CategorySetting.Visibility(skyCategory), 1.0f, 0.0f, 4.0f);
    public NumberSetting skyScale = new NumberSetting("SkyScale", "Size of the sky pattern (higher = smaller ripples).", new CategorySetting.Visibility(skyCategory), 1.0f, 0.25f, 4.0f);
    public NumberSetting parallax = new NumberSetting("Parallax", "How much the sky moves when you turn (0 = fixed).", new CategorySetting.Visibility(skyCategory), 1.0f, 0.0f, 1.5f);

    public CategorySetting customCategory = new CategorySetting("Custom Settings", "Custom theme: your own shader in Anatorius/Shaders/paint.glsl (reloads on save).", new ModeSetting.Visibility(theme, "Custom"));
    public NumberSetting customSpeed = new NumberSetting("CustomSpeed", "Speed of the TIME value your shader receives.", new CategorySetting.Visibility(customCategory), 1.0f, 0.0f, 5.0f);

    public NumberSetting glowQuality = new NumberSetting("GlowQuality", "Glow sample quality: higher looks smoother, lower is faster.", new ModeSetting.Visibility(theme, "BlurGlow", "ShorelineBloom"), 2, 1, 3);
    public BooleanSetting shorelineGlowInside = new BooleanSetting("GlowInside", "Glow inside the fill.", new ModeSetting.Visibility(theme, "ShorelineBloom"), false);

    private static final long CLOCK_ORIGIN = System.nanoTime();

    public boolean wantsChams() {
        return isToggled() && !theme.getValue().equals("None");
    }

    public boolean wantsHandChams() {
        return wantsChams() && hands.getValue();
    }

    private boolean isTheme(String name) {
        return theme.getValue().equals(name);
    }

    public boolean isScriptTheme() {
        return isTheme("Custom");
    }

    public int getGlowInside() {
        return isTheme("ShorelineBloom") && shorelineGlowInside.getValue() ? 1 : 0;
    }

    public int getRenderMode() {
        return switch (mode.getValue()) {
            case "Fill" -> 0;
            case "Outline" -> 1;
            default -> 2;
        };
    }

    public boolean isOutlineOnly() {
        return mode.getValue().equalsIgnoreCase("Outline") || isTheme("OutlineOnly");
    }

    public int getHandChamsColorArgb() {
        Color c = isOutlineOnly() ? outlineColor.getColor() : color.getColor();
        int a = Math.max(0, Math.min(255, opacity.getValue().intValue()));
        return (a << 24) | (c.getRGB() & 0xFFFFFF);
    }

    public int getHandBaseRgb() {
        if (isTheme("Sky") && !isOutlineOnly()) return getColorA();
        return getHandChamsColorArgb() & 0xFFFFFF;
    }

    public int getRgbFor(Entity entity) {
        boolean friend = entity instanceof PlayerEntity player
                && Anatorius.FRIEND_MANAGER.contains(player.getName().getString())
                && !friends.getValue().equals("Sync");
        Color c;
        if (friend) {
            c = friends.getValue().equals("Default") ? Anatorius.FRIEND_MANAGER.getDefaultFriendColor() : friendColor.getColor();
        } else {
            c = isOutlineOnly() ? outlineColor.getColor() : color.getColor();
        }
        return c.getRGB() & 0xFFFFFF;
    }

    public int getGlowRadius() {
        int base = Math.max(1, width.getValue().intValue());
        return getThemeIndex() == 3 ? Math.min(base * 3, 30) : base;
    }

    public int getGlowStep() {
        if (getThemeIndex() == 3) return Math.max(1, 4 - glowQuality.getValue().intValue());
        return fastLines.getValue() ? 2 : 1;
    }

    public int getThemeIndex() {
        return switch (theme.getValue()) {
            case "Gradient" -> 1;
            case "HueShift" -> 2;
            case "BlurGlow", "ShorelineBloom" -> 3;
            case "OutlineOnly" -> 4;
            case "Sky" -> 5;
            case "Custom" -> 6;
            default -> 0;
        };
    }

    public float getClock() {
        double speed = switch (theme.getValue()) {
            case "Gradient" -> gradientSpeed.getValue().doubleValue();
            case "HueShift" -> hueSpeed.getValue().doubleValue();
            case "Sky" -> skySpeed.getValue().doubleValue();
            case "Custom" -> customSpeed.getValue().doubleValue();
            default -> 0.0;
        };
        if (speed == 0.0) return 0.0f;
        double seconds = (System.nanoTime() - CLOCK_ORIGIN) / 1.0E9;
        return (float) ((seconds * speed) % 4096.0);
    }

    public float getViewX() {
        if (!isTheme("Sky") || mc.gameRenderer == null || mc.getWindow() == null) return 0.0f;
        float aspect = mc.getWindow().getFramebufferHeight() == 0 ? 1.0f
                : (float) mc.getWindow().getFramebufferWidth() / mc.getWindow().getFramebufferHeight();
        return mc.gameRenderer.getCamera().getYaw() / 180.0f * aspect * parallax.getValue().floatValue();
    }

    public float getViewY() {
        if (!isTheme("Sky") || mc.gameRenderer == null) return 0.0f;
        return -mc.gameRenderer.getCamera().getPitch() / 90.0f * parallax.getValue().floatValue();
    }

    public float getPatternScale() {
        if (isTheme("Sky")) return skyScale.getValue().floatValue();
        if (isTheme("Gradient")) return gradientStep.getValue().floatValue();
        return 1.0f;
    }

    public int getColorA() {
        if (isTheme("Sky")) return skyColor.getColor().getRGB() & 0xFFFFFF;
        return color.getColor().getRGB() & 0xFFFFFF;
    }

    public int getColorB() {
        return fillColor2.getColor().getRGB() & 0xFFFFFF;
    }

    public int getOutlineColorRgb() {
        return outlineColor.getColor().getRGB() & 0xFFFFFF;
    }

    public int getOutlineColorB() {
        return outlineColor2.getColor().getRGB() & 0xFFFFFF;
    }

    public float getGlowIntensity() {
        return glowMultiplier.getValue().floatValue();
    }

    public float getFillTint() {
        return 1.0f;
    }

    public float getFillAlpha() {
        return opacity.getValue().intValue() / 255.0f;
    }

    public float getGlobalAlpha() {
        Color c = isOutlineOnly() ? outlineColor.getColor() : isTheme("Sky") ? skyColor.getColor() : color.getColor();
        return c.getAlpha() / 255.0f;
    }

    public float getFillOffset() { return fillOffset.getValue().floatValue(); }
    public float getFillStrength() { return fillStrength.getValue().floatValue(); }
    public float getOutlineOffset() { return outlineOffset.getValue().floatValue(); }
    public float getOutlineStrength() { return outlineStrength.getValue().floatValue(); }

    public int getDotsMode() {
        if (!(isTheme("Default") || isTheme("Gradient") || isTheme("HueShift") || isTheme("BlurGlow"))) return 0;
        return switch (dotsMode.getValue()) {
            case "Dots" -> 1;
            case "Lines" -> 2;
            default -> 0;
        };
    }

    public int getDotsRadius() { return dotsRadius.getValue().intValue(); }
    public float getDotsAlpha() { return dotsAlpha.getValue().floatValue(); }

    public boolean isValidEntity(Entity entity) {
        if (mc.gameRenderer != null) {
            double max = range.getValue().doubleValue();
            if (entity.squaredDistanceTo(mc.gameRenderer.getCamera().getCameraPos()) > max * max) return false;
        }
        if (players.getValue() && entity.getType() == EntityType.PLAYER) return true;
        if (hostiles.getValue() && entity.getType().getSpawnGroup() == SpawnGroup.MONSTER) return true;
        if (animals.getValue() && (entity.getType().getSpawnGroup() == SpawnGroup.CREATURE || entity.getType().getSpawnGroup() == SpawnGroup.WATER_CREATURE || entity.getType().getSpawnGroup() == SpawnGroup.WATER_AMBIENT || entity.getType().getSpawnGroup() == SpawnGroup.UNDERGROUND_WATER_CREATURE || entity.getType().getSpawnGroup() == SpawnGroup.AXOLOTLS))
            return true;
        if (ambient.getValue() && entity.getType().getSpawnGroup() == SpawnGroup.AMBIENT) return true;
        if (invisibles.getValue() && entity.isInvisible()) return true;
        if (items.getValue() && (entity.getType() == EntityType.ITEM || entity.getType() == EntityType.EXPERIENCE_BOTTLE)) return true;
        if (crystals.getValue() && entity.getType() == EntityType.END_CRYSTAL) return true;
        return others.getValue();
    }
}
