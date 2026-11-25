package dev.project.anatorius.utils.rotations;

import dev.project.anatorius.utils.IMinecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class RotationUtils implements IMinecraft {
    public static float[] getRotations(Entity entity) {
        return getRotations(entity.getX(), entity.getY() + entity.getEyeHeight(entity.getPose()) / 2.0, entity.getZ(), false);
    }

    public static float[] getRotations(Vec3d vec3d, boolean b) {
        return getRotations(vec3d.x, vec3d.y, vec3d.z, false);
    }

    public static float[] getRotations(double x, double y, double z, boolean b) {
        return getRotations(mc.player, x, y, z);
    }

    public static float[] getRotations(Entity entity, double x, double y, double z) {
        Vec3d vec3d = entity.getEntityPos().add(0, entity.getEyeHeight(entity.getPose()), 0);

        double deltaX = x - vec3d.x;
        double deltaY = (y - vec3d.y) * -1.0;
        double deltaZ = z - vec3d.z;

        double distance = MathHelper.sqrt((float) (deltaX * deltaX + deltaZ * deltaZ));

        float yaw = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0);
        float pitch = (float) MathHelper.clamp(MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(deltaY, distance))), -90f, 90f);

        return new float[]{yaw, pitch};
    }

    public static float getGCD() {
        double sensitivity = mc.options.getMouseSensitivity().getValue();
        double f = sensitivity * 0.6 + 0.2;
        return (float) (f * f * f * 8.0 * 0.15);
    }

    public static float[] applyGCD(float targetYaw, float targetPitch, float prevYaw, float prevPitch) {
        float gcd = getGCD();
        if (gcd <= 0f) return new float[]{targetYaw, MathHelper.clamp(targetPitch, -90f, 90f)};

        float deltaYaw = MathHelper.wrapDegrees(targetYaw - prevYaw);
        float deltaPitch = targetPitch - prevPitch;

        deltaYaw -= deltaYaw % gcd;
        deltaPitch -= deltaPitch % gcd;

        return new float[]{prevYaw + deltaYaw, MathHelper.clamp(prevPitch + deltaPitch, -90f, 90f)};
    }

    public static float[] getRotations(Direction direction) {
        return switch (direction) {
            case DOWN -> new float[]{mc.player.getYaw(), 90.0f};
            case UP -> new float[]{mc.player.getYaw(), -90.0f};
            case NORTH -> new float[]{180.0f, mc.player.getPitch()};
            case SOUTH -> new float[]{0.0f, mc.player.getPitch()};
            case WEST -> new float[]{90.0f, mc.player.getPitch()};
            case EAST -> new float[]{-90.0f, mc.player.getPitch()};
        };
    }
}
