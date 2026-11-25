package dev.project.anatorius.utils.graphics.winter;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.modules.impl.core.ClickGuiModule;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Util;

public final class WinterTheme {
    private static final String[] SPLASHES = {"Let it snow!", "Stay warm!", "Fresh powder!", "Snow day!"};

    private WinterTheme() {
    }

    public static boolean isActive() {
        return WinterScene.isActive();
    }

    public static boolean clickGuiActive() {
        if (!isActive() || Anatorius.MODULE_MANAGER == null) return false;
        ClickGuiModule clickGui = Anatorius.MODULE_MANAGER.getModule(ClickGuiModule.class);
        return clickGui != null && clickGui.winter.getValue();
    }

    public static String splashText() {
        return SPLASHES[(int) (Util.getMeasuringTimeMs() / 1000L % SPLASHES.length)];
    }

    public static void renderMenu(DrawContext context, int width, int height, float alpha) {
        float time = seconds();
        WinterScene.Pixels px = on(context, alpha);
        px.gradient(0, 0, width, height, 0x700B1426, 0xB01E3352);
        WinterScene.moon(px, width - 28, 26, 11);
        WinterScene.foreground(px, width, height, 12, time, 1);
        WinterScene.snowfall(px, width, height, time, 1.0f);
    }

    public static void renderSplash(DrawContext context, int width, int height, float alpha) {
        float time = seconds();
        WinterScene.Pixels px = on(context, alpha);
        int depth = 14;
        int scale = height >= 240 ? 2 : 1;
        WinterScene.sky(px, width, height);
        WinterScene.stars(px, width, height * 3 / 5, time, 46);
        WinterScene.moon(px, width - 46, 40, 16);
        WinterScene.mountains(px, width, height - depth + 2, height * 3 / 10);
        WinterScene.foreground(px, width, height, depth, time, scale);
        WinterScene.snowfall(px, width, height, time, 1.1f);
    }

    public static void progressBar(DrawContext context, int x1, int y1, int x2, int y2, float progress, float alpha) {
        WinterScene.progressBar(on(context, alpha), x1, y1, x2, y2, progress);
    }

    public static int logoColor(float alpha) {
        return fade(0xFFDCEAF7, alpha);
    }

    public static void renderClickGuiSnow(DrawContext context, int width, int height) {
        WinterScene.snowfall(on(context, 1.0f), width, height, seconds(), 0.7f);
    }

    public static void renderFrameCap(DrawContext context, int x, int y, int width, int seed) {
        WinterScene.snowCap(on(context, 1.0f), x, y, width, seed);
    }

    private static WinterScene.Pixels on(DrawContext context, float alpha) {
        return new WinterScene.Pixels() {
            @Override
            public void fill(int x1, int y1, int x2, int y2, int argb) {
                context.fill(x1, y1, x2, y2, fade(argb, alpha));
            }

            @Override
            public void gradient(int x1, int y1, int x2, int y2, int top, int bottom) {
                context.fillGradient(x1, y1, x2, y2, fade(top, alpha), fade(bottom, alpha));
            }
        };
    }

    private static float seconds() {
        return (Util.getMeasuringTimeMs() % 3_600_000L) / 1000f;
    }

    private static int fade(int color, float alpha) {
        return (int) ((color >>> 24) * alpha) << 24 | (color & 0xFFFFFF);
    }
}
