package dev.project.anatorius.utils.graphics;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Util;

import java.time.LocalDate;

public final class HalloweenEgg {
    private static final boolean ACTIVE = inSeason(LocalDate.now());

    private static final int ORANGE = 0xFFF0801A;
    private static final int ORANGE_DARK = 0xFFB9560F;
    private static final int STEM = 0xFF4E7A25;
    private static final int NIGHT = 0xFF0C0616;
    private static final int DUSK = 0xFF2B0E3D;
    private static final int SILHOUETTE = 0xFF080410;
    private static final int MOON = 0xFFF6E9B2;
    private static final int MOON_SHADE = 0xFFDCC98C;

    private static final String[] PUMPKIN = {
            "....oodoo",
            "..ooodooo",
            ".oooodooo",
            "oooodoooo",
            "ooooyoooo",
            "oooyyyooo",
            "ooyyyyyoo",
            "oooooooyo",
            "ooooooyyy",
            "oyooooooo",
            "oyyoyyoyy",
            "ooyyyyyyy",
            "oooyyoyyo",
            ".oooodooo",
            "...oodooo",
    };
    private static final String[][] BAT = {
            {"x.........x", "xx..x.x..xx", "xxx.xxx.xxx", ".xxxxxxxxx.", "...xxxxx...", ".....x....."},
            {"....x.x....", "....xxx....", "..xxxxxxx..", ".xxxxxxxxx.", "xx..xxx..xx", "x....x....x"},
    };

    private HalloweenEgg() {
    }

    public static boolean isActive() {
        return ACTIVE;
    }

    static boolean inSeason(LocalDate date) {
        int day = date.getDayOfMonth();
        return (date.getMonthValue() == 10 && day >= 24) || (date.getMonthValue() == 11 && day <= 7);
    }

    public static void renderMenu(DrawContext context, int width, int height, float alpha) {
        float time = seconds();
        context.fillGradient(0, 0, width, height, fade(0x88200A38, alpha), fade(0xB03A1404, alpha));
        moon(context, width - 26, 24, 14, alpha);
        web(context, alpha);
        embers(context, width, height, time, 36, alpha);
        bats(context, width, height / 2, time, 5, alpha);
        ground(context, width, height, 12, new int[]{84, width - 86}, alpha);
        pumpkin(context, 10, height - 44, 2, time, alpha);
        pumpkin(context, width - 44, height - 44, 2, time + 1.7f, alpha);
        if (width > 300) {
            pumpkin(context, 50, height - 29, 1, time + 3.1f, alpha);
            pumpkin(context, width - 67, height - 29, 1, time + 4.3f, alpha);
        }
    }

    public static void renderSplash(DrawContext context, int width, int height, float alpha) {
        float time = seconds();
        context.fillGradient(0, 0, width, height, fade(NIGHT, alpha), fade(DUSK, alpha));
        moon(context, width - 48, 40, 20, alpha);
        embers(context, width, height, time, 30, alpha);
        bats(context, width, height / 3, time, 4, alpha);
        ground(context, width, height, 18, new int[]{28, 72, width - 30, width - 76}, alpha);
        int scale = Math.max(2, Math.min(4, height / 90));
        int size = 17 * scale;
        int logoTop = height / 2 - (int) (Math.min(width * 0.75, height) * 0.125);
        pumpkin(context, width / 2 - size / 2, Math.max(4, logoTop - size - 3 * scale), scale, time, alpha);
    }

    public static void progressBar(DrawContext context, int x1, int y1, int x2, int y2, float progress, float alpha) {
        int fill = x1 + 2 + (int) ((x2 - x1 - 4) * progress);
        context.fill(x1 + 2, y1 + 2, fill, y2 - 2, fade(ORANGE, alpha));
        int line = fade(ORANGE_DARK, alpha);
        context.fill(x1, y1, x2, y1 + 1, line);
        context.fill(x1, y2 - 1, x2, y2, line);
        context.fill(x1, y1, x1 + 1, y2, line);
        context.fill(x2 - 1, y1, x2, y2, line);
    }

    public static int logoColor(float alpha) {
        float light = 0.88f + 0.12f * flicker(seconds());
        return fade(0xFF000000 | (int) (0xFF * light) << 16 | (int) (0x8C * light) << 8 | (int) (0x1A * light), alpha);
    }

    private static void pumpkin(DrawContext context, int x, int y, int scale, float time, float alpha) {
        float light = flicker(time);
        int face = fade(mix(0xFFFFB52E, 0xFFFFF27A, light), alpha);
        int glow = fade(0x00FF9A2E | (int) (26 + 24 * light) << 24, alpha);
        for (int i = 3; i >= 1; i--) {
            disc(context, x + 17 * scale / 2, y + 15 * scale / 2, 9 * scale + i * 2 * scale, glow);
        }
        int stem = fade(STEM, alpha);
        context.fill(x + 8 * scale, y - scale, x + 10 * scale, y + scale, stem);
        context.fill(x + 9 * scale, y - 2 * scale, x + 11 * scale, y - scale, stem);
        int shell = fade(ORANGE, alpha);
        int rib = fade(ORANGE_DARK, alpha);
        for (int row = 0; row < PUMPKIN.length; row++) {
            String line = PUMPKIN[row];
            int top = y + row * scale;
            for (int column = 0; column < line.length(); column++) {
                char pixel = line.charAt(column);
                if (pixel == '.') continue;
                int color = pixel == 'y' ? face : pixel == 'd' ? rib : shell;
                int left = x + column * scale;
                context.fill(left, top, left + scale, top + scale, color);
                if (column < 8) {
                    int mirrored = x + (16 - column) * scale;
                    context.fill(mirrored, top, mirrored + scale, top + scale, color);
                }
            }
        }
    }

    private static void moon(DrawContext context, int cx, int cy, int radius, float alpha) {
        int halo = fade(0x1CF6E9B2, alpha);
        disc(context, cx, cy, radius + 7, halo);
        disc(context, cx, cy, radius + 3, halo);
        disc(context, cx, cy, radius, fade(MOON, alpha));
        int shade = fade(MOON_SHADE, alpha);
        disc(context, cx - radius / 3, cy - radius / 3, radius / 4, shade);
        disc(context, cx + radius / 3, cy + radius / 6, radius / 5, shade);
        disc(context, cx - radius / 5, cy + radius / 2, radius / 6, shade);
    }

    private static void disc(DrawContext context, int cx, int cy, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int dx = (int) Math.sqrt(radius * radius - dy * dy);
            context.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    private static void bats(DrawContext context, int width, int band, float time, int count, float alpha) {
        int color = fade(SILHOUETTE, alpha);
        for (int i = 0; i < count; i++) {
            float speed = 18 + 9 * (i % 3);
            int span = width + 40;
            int x = (int) ((time * speed + i * 97) % span) - 20;
            int y = 12 + (i * 53) % Math.max(1, band - 24) + (int) (Math.sin(time * 1.7 + i * 2.1) * 7);
            String[] frame = BAT[(int) (time * 7 + i) % 2];
            for (int row = 0; row < frame.length; row++) {
                String line = frame[row];
                for (int column = 0; column < line.length(); column++) {
                    if (line.charAt(column) == 'x') context.fill(x + column, y + row, x + column + 1, y + row + 1, color);
                }
            }
        }
    }

    private static void embers(DrawContext context, int width, int height, float time, int count, float alpha) {
        for (int i = 0; i < count; i++) {
            int seed = i * 7919;
            float rise = 9 + seed % 13;
            float life = ((time * rise + seed % 613) % height) / height;
            int x = (seed % 1009) * width / 1009 + (int) (Math.sin(time * 0.9 + i) * 6);
            int y = height - (int) (life * height);
            int a = (int) (190 * (1 - life));
            if (a <= 0) continue;
            context.fill(x, y, x + 1 + i % 2, y + 1 + i % 2, fade(a << 24 | (i % 3 == 0 ? 0xFFD24A : 0xFF7A1C), alpha));
        }
    }

    private static void web(DrawContext context, float alpha) {
        int color = fade(0x40FFFFFF, alpha);
        int size = 54;
        double[] angles = {0.08, 0.42, 0.78, 1.14, 1.49};
        for (double angle : angles) {
            for (int r = 0; r < size; r += 2) {
                int x = (int) (Math.cos(angle) * r);
                int y = (int) (Math.sin(angle) * r);
                context.fill(x, y, x + 1, y + 1, color);
            }
        }
        for (int ring = 16; ring < size; ring += 14) {
            for (int i = 0; i + 1 < angles.length; i++) {
                for (int step = 0; step <= 6; step++) {
                    double t = step / 6.0;
                    double angle = angles[i] + (angles[i + 1] - angles[i]) * t;
                    double sag = 1 - 0.14 * Math.sin(t * Math.PI);
                    int x = (int) (Math.cos(angle) * ring * sag);
                    int y = (int) (Math.sin(angle) * ring * sag);
                    context.fill(x, y, x + 1, y + 1, color);
                }
            }
        }
    }

    private static void ground(DrawContext context, int width, int height, int base, int[] graves, float alpha) {
        int color = fade(SILHOUETTE, alpha);
        for (int x = 0; x < width; x += 3) {
            int top = height - base - (int) (2.5 * Math.sin(x * 0.043) + 1.5 * Math.sin(x * 0.11));
            context.fill(x, top, x + 3, height, color);
        }
        for (int x : graves) {
            int top = height - base - 13;
            context.fill(x - 4, top + 3, x + 5, height - base + 3, color);
            context.fill(x - 2, top, x + 3, top + 3, color);
        }
    }

    private static float flicker(float time) {
        double noise = Math.sin(time * 9.1) * 0.5 + Math.sin(time * 23.7) * 0.3 + Math.sin(time * 3.3) * 0.2;
        return (float) (0.5 + 0.5 * noise);
    }

    private static float seconds() {
        return (Util.getMeasuringTimeMs() % 3_600_000L) / 1000f;
    }

    private static int mix(int from, int to, float amount) {
        int r = (int) (((from >> 16) & 255) + (((to >> 16) & 255) - ((from >> 16) & 255)) * amount);
        int g = (int) (((from >> 8) & 255) + (((to >> 8) & 255) - ((from >> 8) & 255)) * amount);
        int b = (int) ((from & 255) + ((to & 255) - (from & 255)) * amount);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static int fade(int color, float alpha) {
        return (int) ((color >>> 24) * alpha) << 24 | (color & 0xFFFFFF);
    }
}
