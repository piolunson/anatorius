package dev.project.anatorius.utils.minecraft;

import dev.project.anatorius.events.impl.PlayerMoveEvent;
import dev.project.anatorius.mixins.accessors.Vec3dAccessor;
import dev.project.anatorius.utils.IMinecraft;
import dev.project.anatorius.utils.rotations.RotationUtils;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector2d;

public class MovementUtils implements IMinecraft {
    public static double DEFAULT_SPEED = 0.2873;

    public static Vector2d forward(double speed) {
        float forward = mc.player.input.getMovementInput().y;
        float sideways = mc.player.input.getMovementInput().x;
        float yaw = mc.player.getYaw();

        if (forward == 0.0f && sideways == 0.0f) return new Vector2d(0, 0);
        if (forward != 0.0f) {
            if (sideways >= 1.0f) {
                yaw += ((forward > 0.0f) ? -45 : 45);
                sideways = 0.0f;
            } else if (sideways <= -1.0f) {
                yaw += ((forward > 0.0f) ? 45 : -45);
                sideways = 0.0f;
            }

            if (forward > 0.0f) forward = 1.0f;
            else if (forward < 0.0f) forward = -1.0f;
        }

        double motionX = Math.cos(Math.toRadians(yaw + 90.0f));
        double motionZ = Math.sin(Math.toRadians(yaw + 90.0f));

        return new Vector2d(forward * speed * motionX + sideways * speed * motionZ, forward * speed * motionZ - sideways * speed * motionX);
    }

    public static float getMoveYaw(float playerYaw) {
        double forward = 0.0D;
        double sideways = 0.0D;

        if (mc.options.forwardKey.isPressed()) forward += 1.0D;
        if (mc.options.backKey.isPressed()) forward -= 1.0D;
        if (mc.options.leftKey.isPressed()) sideways += 1.0D;
        if (mc.options.rightKey.isPressed()) sideways -= 1.0D;

        if (forward == 0.0D && sideways == 0.0D) return playerYaw;

        double yawRad = Math.toRadians(playerYaw);
        double sin = Math.sin(yawRad);
        double cos = Math.cos(yawRad);
        double motionX = forward * -sin + sideways * cos;
        double motionZ = forward * cos + sideways * sin;

        return (float) Math.toDegrees(Math.atan2(-motionX, motionZ));
    }

    public static double[] straightForward(double speed) {
        return new double[]{speed * Math.cos(Math.toRadians(mc.player.getYaw() + 90.0f)), speed * Math.sin(Math.toRadians(mc.player.getYaw() + 90.0f))};
    }

    public static double getPotionSpeed(double speed) {
        if (mc.player.hasStatusEffect(StatusEffects.SPEED)) speed *= 1.0 + 0.2 * (mc.player.getStatusEffect(StatusEffects.SPEED).getAmplifier() + 1);
        if (mc.player.hasStatusEffect(StatusEffects.SLOWNESS)) speed /= 1.0 + 0.2 * (mc.player.getStatusEffect(StatusEffects.SLOWNESS).getAmplifier() + 1);

        return speed;
    }

    public static double getPotionJump(double jump) {
        if (mc.player.hasStatusEffect(StatusEffects.JUMP_BOOST)) jump += (mc.player.getStatusEffect(StatusEffects.JUMP_BOOST).getAmplifier() + 1) * 0.1f;

        return jump;
    }

    public static boolean isMoving() {
        return mc.player.sidewaysSpeed != 0.0f || mc.player.forwardSpeed != 0.0f;
    }

    public static void moveTowards(PlayerMoveEvent event, Vec3d vec3d, double speed) {
        double angle = Math.toRadians(RotationUtils.getRotations(vec3d, false)[0]);
        double x = -Math.sin(angle) * speed;
        double z = Math.cos(angle) * speed;
        double[] difference = new double[] {vec3d.x - mc.player.getX(), vec3d.z - mc.player.getZ()};

        event.setMovement(new Vec3d(Math.abs(x) < Math.abs(difference[0]) ? x : difference[0], event.getMovement().getY(), event.getMovement().getZ()));
        event.setMovement(new Vec3d(event.getMovement().getX(), event.getMovement().getY(), Math.abs(z) < Math.abs(difference[1]) ? z : difference[1]));
        ((Vec3dAccessor) mc.player.getVelocity()).setX(0);
        ((Vec3dAccessor) mc.player.getVelocity()).setZ(0);
        event.setCancelled(true);
    }
}
