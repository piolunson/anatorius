package dev.project.anatorius.utils.minecraft;

import dev.project.anatorius.utils.IMinecraft;

public final class FlightInput implements IMinecraft {
    private static int vertical;
    private static double speed = Double.NaN;
    private static int age = Integer.MIN_VALUE;

    private FlightInput() {
    }

    public static void autopilot(int v) {
        autopilot(v, Double.NaN);
    }

    public static void autopilot(int v, double blocksPerTick) {
        if (mc.player == null) return;
        vertical = v;
        speed = blocksPerTick;
        age = mc.player.age;
    }

    public static boolean autopilotActive() {
        return mc.player != null && mc.player.age - age <= 2;
    }

    public static boolean up() {
        return mc.options.jumpKey.isPressed() || (autopilotActive() && vertical > 0);
    }

    public static boolean down() {
        return mc.options.sneakKey.isPressed() || (autopilotActive() && vertical < 0);
    }

    public static double motion(double max) {
        if (mc.options.jumpKey.isPressed()) return max;
        if (mc.options.sneakKey.isPressed()) return -max;
        if (!autopilotActive() || vertical == 0) return 0;
        double s = Double.isNaN(speed) ? max : Math.min(max, speed);
        return vertical > 0 ? s : -s;
    }
}
