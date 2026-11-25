package dev.project.anatorius.modules.impl.movement.elytrafly;

import dev.project.anatorius.modules.impl.movement.ElytraFlyModule;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class GrimMode extends ElytraFlyMode {
    private boolean movementStarted, negativeYawJitter, jitterPhase;

    public GrimMode(ElytraFlyModule module) {
        super(module);
    }

    @Override
    public void move(Move move) {
        if (module.collisionBelow()) movementStarted = false;
        if (idleAirborne() && durabilityFlight() && module.stopMotion.getValue().equals("Freeze")) move.setVelocity(Vec3d.ZERO);
    }

    @Override
    public void clientTick() {
        module.releaseTimer();
        if (module.stopMotion.getValue().equals("Timer") && durabilityFlight()) {
            movementStarted = true;
            if (idleAirborne()) module.requestTimer(0.02f);
        }
    }

    @Override
    public void preTick() {
        if (module.collisionBelow()) return;
        if (module.elytraEquipped() && module.infiniteDurability.getValue().equals("None") && !module.flying()) module.startGliding();
        if (!durabilityFlight()) return;

        float playerYaw = mc.player.getYaw();
        float yaw = ElytraPhysics.moving() ? ElytraPhysics.movementYaw(playerYaw) : playerYaw;
        yaw += (negativeYawJitter ? -1 : 1) * ElytraPhysics.JITTER_STEP;
        negativeYawJitter = !negativeYawJitter;

        float pitch = -1.0f;
        if (module.up()) {
            pitch = ElytraPhysics.moving() ? -45.0f : -90.0f;
            if (!ElytraPhysics.moving()) yaw = MathHelper.wrapDegrees(yaw + 180.0f);
        }
        if (module.down()) {
            if (module.responsiveDescend.getValue()) pitch = ElytraPhysics.moving() ? mc.player.getPitch() : 90.0f;
            else pitch = ElytraPhysics.moving() ? 45.0f : 90.0f;
        }
        if (module.stopMotion.getValue().equals("Jitter") && idleAirborne()) {
            if (jitterPhase) yaw = MathHelper.wrapDegrees(yaw + 180.0f);
            jitterPhase = !jitterPhase;
        }
        request(yaw, pitch);
    }

    private boolean idleAirborne() {
        boolean input = ElytraPhysics.moving() || module.up() || module.down() || movementKeyDown();
        return !module.collisionBelow() && !input;
    }

    private boolean durabilityFlight() {
        return module.infiniteDurability.getValue().equals("None") ? module.flying() : !module.collisionBelow();
    }

    private boolean movementKeyDown() {
        return mc.options.forwardKey.isPressed() || mc.options.backKey.isPressed() || mc.options.rightKey.isPressed()
                || mc.options.leftKey.isPressed() || mc.options.sneakKey.isPressed() || mc.options.jumpKey.isPressed();
    }
}
