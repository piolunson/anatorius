package dev.project.anatorius.modules.impl.movement.elytrafly;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

public final class ElytraPhysics {
    public static final float JITTER_STEP = (float) (Math.pow(0.2, 3.0) * 8.0 * 0.15 - 0.001);

    private ElytraPhysics() {}

    private static MinecraftClient mc() {
        return MinecraftClient.getInstance();
    }

    private static Vec2f input() {
        return mc().player.input.getMovementInput();
    }

    public static boolean moving() {
        if (mc().player == null || mc().player.input == null) return false;
        Vec2f in = input();
        return in.y != 0.0f || in.x != 0.0f;
    }

    public static double[] horizontalMotion(float yaw, double speed) {
        Vec2f in = input();
        return horizontalMotion(yaw, in.y, in.x, speed);
    }

    public static double[] horizontalMotion(float yaw, float forward, float strafe, double speed) {
        if (forward != 0.0f) {
            if (strafe > 0.0f) yaw += forward > 0.0f ? -45.0f : 45.0f;
            else if (strafe < 0.0f) yaw += forward > 0.0f ? 45.0f : -45.0f;
            strafe = 0.0f;
            forward = forward > 0.0f ? 1.0f : -1.0f;
        }
        double radians = Math.toRadians(yaw);
        double x = forward * speed * -Math.sin(radians) + strafe * speed * Math.cos(radians);
        double z = forward * speed * Math.cos(radians) - strafe * speed * -Math.sin(radians);
        return new double[]{x, z};
    }

    public static float movementYaw(float yaw) {
        double[] motion = horizontalMotion(yaw, 1.0);
        return (float) (Math.toDegrees(Math.atan2(motion[1], motion[0])) - 90.0);
    }

    public static double accelerateTo(double target, double initial, double durationSeconds, long startedAtMillis) {
        if (initial >= target) return target;
        double fraction = (System.currentTimeMillis() - startedAtMillis) / (durationSeconds * 1000.0);
        double value = initial + (target - initial) * MathHelper.clamp(fraction, 0.0, 1.0);
        return Math.min(value, target);
    }

    public static double[] applyHorizontal(ElytraFlyMode.Move move, double speed) {
        if (!moving()) {
            move.setHorizontal(0.0, 0.0);
            return new double[]{0.0, 0.0};
        }
        double[] motion = horizontalMotion(mc().player.getYaw(), speed);
        move.setHorizontal(motion[0], motion[1]);
        return motion;
    }

    public static Vec3d direction(float pitch, float yaw) {
        return Vec3d.fromPolar(pitch, yaw);
    }
}
