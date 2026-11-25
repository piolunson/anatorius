package dev.project.anatorius.utils.animations;

import net.minecraft.util.math.MathHelper;

public class Easing {
    public static float ease(float x, Method method) {
        if (x <= 0.0f) return 0.0f;
        if (x >= 1.0f) return 1.0f;
        double t = x;
        return (float) switch (method) {
            case EASE_IN_SINE -> 1 - Math.cos(t * Math.PI / 2);
            case EASE_OUT_SINE -> Math.sin(t * Math.PI / 2);
            case EASE_IN_OUT_SINE -> -(Math.cos(Math.PI * t) - 1) / 2;
            case EASE_IN_QUAD -> t * t;
            case EASE_OUT_QUAD -> 1 - (1 - t) * (1 - t);
            case EASE_IN_OUT_QUAD -> t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
            case EASE_IN_CUBIC -> t * t * t;
            case EASE_OUT_CUBIC -> 1 - Math.pow(1 - t, 3);
            case EASE_IN_OUT_CUBIC -> t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
            case EASE_IN_QUART -> t * t * t * t;
            case EASE_OUT_QUART -> 1 - Math.pow(1 - t, 4);
            case EASE_IN_OUT_QUART -> t < 0.5 ? 8 * t * t * t * t : 1 - Math.pow(-2 * t + 2, 4) / 2;
            case EASE_IN_EXPO -> Math.pow(2, 10 * t - 10);
            case EASE_OUT_EXPO -> 1 - Math.pow(2, -10 * t);
            case EASE_IN_OUT_EXPO -> t < 0.5 ? Math.pow(2, 20 * t - 10) / 2 : (2 - Math.pow(2, -20 * t + 10)) / 2;
            case EASE_IN_BACK -> (BACK_C + 1) * t * t * t - BACK_C * t * t;
            case EASE_OUT_BACK -> 1 + (BACK_C + 1) * Math.pow(t - 1, 3) + BACK_C * Math.pow(t - 1, 2);
            case EASE_OUT_BOUNCE -> bounce(t);
            case EASE_IN_ELASTIC -> -Math.pow(2, 10 * t - 10) * Math.sin((t * 10 - 10.75) * (2 * Math.PI / 3));
            case EASE_OUT_ELASTIC -> Math.pow(2, -10 * t) * Math.sin((t * 10 - 0.75) * (2 * Math.PI / 3)) + 1;
            case EASE_IN_OUT_ELASTIC -> {
                double c5 = 2 * Math.PI / 4.5;
                yield t < 0.5
                        ? -(Math.pow(2, 20 * t - 10) * Math.sin((20 * t - 11.125) * c5)) / 2
                        : (Math.pow(2, -20 * t + 10) * Math.sin((20 * t - 11.125) * c5)) / 2 + 1;
            }
            case SMOOTHSTEP -> t * t * (3 - 2 * t);
            case SMOOTHERSTEP -> t * t * t * (t * (t * 6 - 15) + 10);
            default -> t;
        };
    }

    private static final double BACK_C = 1.70158;

    private static double bounce(double t) {
        double n = 7.5625, d = 2.75;
        if (t < 1 / d) return n * t * t;
        if (t < 2 / d) { t -= 1.5 / d; return n * t * t + 0.75; }
        if (t < 2.5 / d) { t -= 2.25 / d; return n * t * t + 0.9375; }
        t -= 2.625 / d;
        return n * t * t + 0.984375;
    }

    public static float toDelta(long start, int length) {
        return MathHelper.clamp(toDelta(start)/(float) length, 0.0f, 1.0f);
    }

    public static long toDelta(long start) {
        return System.currentTimeMillis() - start;
    }

    public enum Method {
        LINEAR,
        EASE_IN_SINE, EASE_OUT_SINE, EASE_IN_OUT_SINE,
        EASE_IN_CUBIC, EASE_OUT_CUBIC, EASE_IN_OUT_CUBIC,
        EASE_IN_QUAD, EASE_OUT_QUAD, EASE_IN_OUT_QUAD,
        EASE_IN_QUART, EASE_OUT_QUART, EASE_IN_OUT_QUART,
        EASE_IN_EXPO, EASE_OUT_EXPO, EASE_IN_OUT_EXPO,
        EASE_IN_BACK, EASE_OUT_BACK,
        EASE_OUT_BOUNCE,
        EASE_IN_ELASTIC, EASE_OUT_ELASTIC, EASE_IN_OUT_ELASTIC,
        SMOOTHSTEP, SMOOTHERSTEP
    }
}
