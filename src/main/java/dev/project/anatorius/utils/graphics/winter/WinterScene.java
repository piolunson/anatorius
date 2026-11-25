package dev.project.anatorius.utils.graphics.winter;

import java.time.LocalDate;

public final class WinterScene {
    public interface Pixels {
        void fill(int x1, int y1, int x2, int y2, int argb);

        void gradient(int x1, int y1, int x2, int y2, int top, int bottom);
    }

    public static final int NIGHT = 0xFF0B1426;
    public static final int DUSK = 0xFF1E3352;
    public static final int SNOW = 0xFFEAF2FA;
    public static final int FROST = 0xFF9CC3E0;
    public static final int DRIFT = 0xFF3E5677;
    public static final int CREST = 0xFFA9BFD6;
    public static final int EMBER = 0xFFFFC46B;

    private static final int RIDGE_FAR = 0xFF253A5C;
    private static final int RIDGE_NEAR = 0xFF17263F;
    private static final int PEAK = 0xFF8EA7C4;
    private static final int PINE = 0xFF0C1828;
    private static final int PINE_SNOW = 0xFFC9D9EA;
    private static final int TRUNK = 0xFF1A1418;
    private static final int MOON = 0xFFE9F1FA;
    private static final int MOON_SHADE = 0xFFC9D8E8;
    private static final int WOOD = 0xFF2A1F30;
    private static final int WOOD_LINE = 0xFF1C1522;
    private static final int ROOF = 0xFF161120;
    private static final int BRICK = 0xFF2B1C22;
    private static final int DOOR = 0xFF120D17;
    private static final int ICICLE = 0xC09CC3E0;

    private static final boolean ACTIVE = computeActive();

    private WinterScene() {
    }

    public static boolean isActive() {
        return ACTIVE;
    }

    private static boolean computeActive() {
        String forced = System.getProperty("anatorius.winter");
        if (forced != null) return Boolean.parseBoolean(forced);
        return inSeason(LocalDate.now());
    }

    static boolean inSeason(LocalDate date) {
        int month = date.getMonthValue();
        return month == 12 || month <= 2 || (month == 11 && date.getDayOfMonth() >= 10);
    }

    public static void sky(Pixels px, int width, int height) {
        px.gradient(0, 0, width, height, NIGHT, DUSK);
    }

    public static void stars(Pixels px, int width, int height, float time, int count) {
        for (int i = 0; i < count; i++) {
            int x = (int) (rand(900 + i * 2) * width);
            int y = (int) (rand(901 + i * 2) * height);
            float twinkle = 0.55f + 0.45f * (float) Math.sin(time * (1.1f + rand(77 + i) * 1.7f) + i);
            int alpha = (int) (190 * twinkle);
            px.fill(x, y, x + 1, y + 1, alpha << 24 | 0xDCE8F5);
            if (i % 9 == 0) {
                int glow = (alpha / 3) << 24 | 0xDCE8F5;
                px.fill(x - 1, y, x, y + 1, glow);
                px.fill(x + 1, y, x + 2, y + 1, glow);
                px.fill(x, y - 1, x + 1, y, glow);
                px.fill(x, y + 1, x + 1, y + 2, glow);
            }
        }
    }

    public static void moon(Pixels px, int cx, int cy, int radius) {
        disc(px, cx, cy, radius + 8, 0x10E9F1FA);
        disc(px, cx, cy, radius + 4, 0x16E9F1FA);
        disc(px, cx, cy, radius, MOON);
        disc(px, cx - radius / 3, cy - radius / 4, Math.max(1, radius / 4), MOON_SHADE);
        disc(px, cx + radius / 3, cy + radius / 5, Math.max(1, radius / 5), MOON_SHADE);
        disc(px, cx - radius / 6, cy + radius / 2, Math.max(1, radius / 6), MOON_SHADE);
    }

    public static void mountains(Pixels px, int width, int base, int height) {
        for (int x = 0; x < width; x += 2) {
            double far = 0.55 * tri(x * 0.0105 + 0.3) + 0.3 * tri(x * 0.023 + 1.1) + 0.15 * tri(x * 0.051 + 2.0);
            int top = base - (int) (height * (0.35 + 0.65 * far));
            px.fill(x, top, x + 2, base, RIDGE_FAR);
            int cap = (int) ((far - 0.6) * height * 0.7);
            if (cap > 0) px.fill(x, top, x + 2, top + cap + ((x / 2) % 3 == 0 ? 1 : 0), PEAK);
        }
        int nearHeight = height * 11 / 20;
        for (int x = 0; x < width; x += 2) {
            double near = 0.6 * tri(x * 0.0072 + 2.4) + 0.4 * tri(x * 0.019 + 0.7);
            px.fill(x, base - (int) (nearHeight * (0.3 + 0.7 * near)), x + 2, base, RIDGE_NEAR);
        }
    }

    public static void snowfall(Pixels px, int width, int height, float time, float density) {
        float area = Math.max(0.4f, width * height / (480f * 270f)) * density;
        flakes(px, width, height, time, (int) (60 * area), 0, 9f, 2f, 0x70EAF2FA, 101);
        flakes(px, width, height, time, (int) (42 * area), 1, 17f, 4f, 0xB0EAF2FA, 202);
        flakes(px, width, height, time, (int) (16 * area), 2, 30f, 7f, 0xE6F4F8FC, 303);
    }

    private static void flakes(Pixels px, int width, int height, float time, int count, int depth, float speed, float sway, int color, int seed) {
        int spanX = width + 24, spanY = height + 12;
        float wind = 3f + depth * 3f;
        for (int i = 0; i < count; i++) {
            float a = rand(seed + i * 3), b = rand(seed + i * 3 + 1), c = rand(seed + i * 3 + 2);
            int y = (int) ((time * speed * (0.75f + 0.5f * c) + b * spanY) % spanY) - 6;
            float drift = a * spanX + time * wind + (float) Math.sin(time * (0.6f + c) + b * 6.283f) * sway;
            int x = Math.floorMod((int) drift, spanX) - 12;
            if (depth == 0) {
                px.fill(x, y, x + 1, y + 1, color);
            } else if (depth == 1) {
                int size = c > 0.6f ? 2 : 1;
                px.fill(x, y, x + size, y + size, color);
            } else if (c > 0.55f) {
                px.fill(x, y - 1, x + 1, y + 2, color);
                px.fill(x - 1, y, x, y + 1, color);
                px.fill(x + 1, y, x + 2, y + 1, color);
            } else {
                px.fill(x, y, x + 2, y + 2, color);
            }
        }
    }

    public static void ground(Pixels px, int width, int height, int depth, float time) {
        for (int x = 0; x < width; x += 2) {
            int top = groundTop(x, height, depth);
            px.fill(x, top, x + 2, height, DRIFT);
            px.fill(x, top, x + 2, top + 1, CREST);
        }
        for (int i = 0; i < width / 24; i++) {
            if (Math.sin(time * (0.9f + rand(600 + i)) + i * 2.3f) < 0.82) continue;
            int x = ((int) (rand(500 + i) * width)) & ~1;
            int y = groundTop(x, height, depth) + 1 + (int) (rand(700 + i) * Math.max(1, depth - 3));
            px.fill(x, y, x + 1, y + 1, SNOW);
        }
    }

    public static int groundTop(int x, int height, int depth) {
        x &= ~1;
        return height - depth - (int) Math.round(1.6 * Math.sin(x * 0.034) + 1.1 * Math.sin(x * 0.091 + 1.7));
    }

    public static void snowCap(Pixels px, int x, int y, int width, int seed) {
        for (int i = 0; i < width; i += 2) {
            double shape = 1.7 + Math.sin((i + seed * 13) * 0.19) + 0.8 * rand(seed * 131 + i);
            int lump = (int) Math.max(1, Math.min(3, Math.round(shape)));
            lump = Math.min(lump, 1 + Math.min(i, width - i - 2) / 2);
            int right = Math.min(x + i + 2, x + width);
            px.fill(x + i, y - lump, right, y, SNOW);
            px.fill(x + i, y, right, y + 1, PINE_SNOW);
        }
    }

    public static void foreground(Pixels px, int width, int height, int depth, float time, int s) {
        boolean wide = width > 260 * s;
        pine(px, 9 * s, groundTop(9 * s, height, depth) + 2, 30 * s);
        cabin(px, 20 * s, groundTop(32 * s, height, depth) + 2, s, time);
        if (wide) pine(px, 58 * s, groundTop(58 * s, height, depth) + 2, 19 * s);

        pine(px, width - 13 * s, groundTop(width - 13 * s, height, depth) + 2, 36 * s);
        pine(px, width - 31 * s, groundTop(width - 31 * s, height, depth) + 2, 24 * s);
        if (wide) pine(px, width - 46 * s, groundTop(width - 46 * s, height, depth) + 2, 15 * s);

        ground(px, width, height, depth, time);
    }

    public static void pine(Pixels px, int cx, int base, int h) {
        int trunk = Math.max(2, h / 9);
        px.fill(cx - 1, base - trunk, cx + 1, base, TRUNK);
        int body = h - trunk;
        int tierH = Math.max(4, (int) (body * 0.46));
        int step = (body - tierH) / 2;
        int top = base - h;
        for (int t = 0; t < 3; t++) {
            int y0 = top + t * step;
            float slope = 0.58f + 0.08f * t;
            for (int l = 0; l < tierH; l++) {
                int half = 1 + (int) (l * slope);
                int y = y0 + l;
                if (l < 2) {
                    px.fill(cx - half, y, cx + half, y + 1, PINE_SNOW);
                    continue;
                }
                px.fill(cx - half, y, cx + half, y + 1, PINE);
                if (l < tierH * 0.45f) px.fill(cx + half - 1, y, cx + half, y + 1, PINE_SNOW);
                if (l < 3) px.fill(cx - half, y, cx - half + 1, y + 1, PINE_SNOW);
            }
            int hem = 1 + (int) ((tierH - 1) * slope);
            int hemY = y0 + tierH - 1;
            px.fill(cx - hem, hemY - 1, cx - hem + 2, hemY, PINE_SNOW);
            px.fill(cx + hem - 2, hemY - 1, cx + hem, hemY, PINE_SNOW);
        }
    }

    public static void cabin(Pixels px, int x, int base, int s, float time) {
        int w = 24 * s, wallTop = base - 11 * s;
        float light = 0.82f + 0.18f * flicker(time);

        int chimney = x + 17 * s;
        px.fill(chimney, wallTop - 9 * s, chimney + 3 * s, wallTop, BRICK);
        px.fill(chimney - s / 2, wallTop - 10 * s, chimney + 3 * s + s / 2, wallTop - 9 * s, SNOW);

        px.fill(x, wallTop, x + w, base, WOOD);
        for (int y = wallTop + 2 * s; y < base; y += 3 * s) {
            px.fill(x, y, x + w, y + Math.max(1, s / 2), WOOD_LINE);
            px.fill(x - s, y - s, x, y + s, WOOD);
            px.fill(x + w, y - s, x + w + s, y + s, WOOD);
        }
        px.fill(x + 15 * s, base - 8 * s, x + 20 * s, base, DOOR);

        int wx = x + 4 * s, wy = wallTop + 3 * s, ws = 5 * s;
        px.fill(wx, wy, wx + ws, wy + ws, mix(EMBER, 0xFFFFE6AE, light));
        px.fill(wx + 2 * s, wy, wx + 3 * s, wy + ws, WOOD_LINE);
        px.fill(wx, wy + 2 * s, wx + ws, wy + 3 * s, WOOD_LINE);
        px.fill(wx - s, wy + ws, wx + ws + s, wy + ws + s, PINE_SNOW);

        int apex = x + w / 2, overhang = 2 * s, rows = 8 * s;
        for (int r = 0; r < rows; r++) {
            int half = (w / 2 + overhang) * (r + 1) / rows;
            int y = wallTop - rows + r;
            px.fill(apex - half, y, apex + half, y + 1, ROOF);
            if (r < 2 * s) {
                px.fill(apex - half, y, apex + half, y + 1, SNOW);
            } else {
                int thick = Math.min(half, 3 * s);
                px.fill(apex - half, y, apex - half + thick, y + 1, SNOW);
                px.fill(apex + half - thick, y, apex + half, y + 1, SNOW);
            }
        }
        px.fill(x - overhang, wallTop, x + w + overhang, wallTop + s, PINE_SNOW);
        for (int i = 0; i < 7; i++) {
            int ix = x - overhang + s + (int) (rand(40 + i) * (w + overhang * 2 - 2 * s));
            int length = (1 + (int) (rand(50 + i) * 3)) * s;
            px.fill(ix, wallTop + s, ix + Math.max(1, s / 2), wallTop + s + length, ICICLE);
        }

        int gx = wx + ws / 2, gy = wy + ws / 2;
        disc(px, gx, gy, ws * 3, alpha(EMBER, (int) (12 * light)));
        disc(px, gx, gy, ws * 2, alpha(EMBER, (int) (18 * light)));
        for (int i = 0; i < 6; i++) {
            float life = (time * 0.32f + i / 6f) % 1f;
            int sy = wallTop - 10 * s - (int) (life * 26 * s);
            int sx = chimney + s + (int) (life * life * 14 * s + Math.sin(time * 1.3 + i) * s);
            int radius = Math.max(1, (int) ((1 + life * 3) * s));
            disc(px, sx, sy, radius, alpha(0xFFB8C4D2, (int) (70 * (1 - life))));
        }
    }

    public static void progressBar(Pixels px, int x1, int y1, int x2, int y2, float progress) {
        int fill = x1 + 2 + (int) ((x2 - x1 - 4) * progress);
        px.fill(x1 + 2, y1 + 2, fill, y2 - 2, SNOW);
        int line = alpha(FROST, 0xC0);
        px.fill(x1, y1, x2, y1 + 1, line);
        px.fill(x1, y2 - 1, x2, y2, line);
        px.fill(x1, y1, x1 + 1, y2, line);
        px.fill(x2 - 1, y1, x2, y2, line);
        snowCap(px, x1, y1, x2 - x1, 7);
    }

    public static void disc(Pixels px, int cx, int cy, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int dx = (int) Math.sqrt(radius * radius - dy * dy);
            px.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    private static float flicker(float time) {
        double noise = Math.sin(time * 7.3) * 0.5 + Math.sin(time * 17.9) * 0.3 + Math.sin(time * 2.9) * 0.2;
        return (float) (0.5 + 0.5 * noise);
    }

    private static double tri(double x) {
        double f = x - Math.floor(x);
        return 1 - Math.abs(f * 2 - 1);
    }

    private static float rand(int n) {
        n = (n << 13) ^ n;
        n = n * (n * n * 15731 + 789221) + 1376312589;
        return (n & 0x7fffffff) / 2147483647f;
    }

    public static int alpha(int color, int alpha) {
        return Math.max(0, Math.min(255, alpha)) << 24 | (color & 0xFFFFFF);
    }

    private static int mix(int from, int to, float amount) {
        int r = (int) (((from >> 16) & 255) + (((to >> 16) & 255) - ((from >> 16) & 255)) * amount);
        int g = (int) (((from >> 8) & 255) + (((to >> 8) & 255) - ((from >> 8) & 255)) * amount);
        int b = (int) ((from & 255) + ((to & 255) - (from & 255)) * amount);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }
}
