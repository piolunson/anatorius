package dev.project.anatorius.modules.impl.movement.elytrafly;

import dev.project.anatorius.modules.impl.movement.ElytraFlyModule;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.ThreadLocalRandom;

public class BoostMode extends ElytraFlyMode {
    private long lastGlidingAt;
    private boolean pitchInitialized;
    private float targetPitch;
    private float movementYaw;

    public BoostMode(ElytraFlyModule module) {
        super(module);
    }

    private float computeMovementYaw() {
        if (ElytraPhysics.moving() && module.responsive.getValue()) return ElytraPhysics.movementYaw(mc.player.getYaw());
        return mc.player.getYaw();
    }

    private boolean earlyNcpBoost() {
        return module.ncpBoost.getValue() && mc.player.getGlidingTicks() <= 5;
    }

    @Override
    public void move(Move move) {
        float limit = module.speedLimit.getValue().floatValue();
        if (limit > 200.0f && earlyNcpBoost()) limit = 200.0f;
        if (module.speedLimit.isAtMaxLabel() && !earlyNcpBoost()) return;
        Vec3d velocity = move.velocity();
        double kmh = Math.hypot(velocity.x, velocity.z) * 20.0 * 3.6;
        if (kmh > limit) {
            Vec3d limited = velocity.normalize().multiply(limit / 3.6 / 20.0);
            move.setHorizontal(limited.x, limited.z);
        }
    }

    @Override
    public void preTick() {
        if (module.autoJump.getValue() && module.vanillaGliding()) module.holdJump();
        movementYaw = computeMovementYaw();
        pitchInitialized = false;
        if (mc.player.isTouchingWater()) return;
        if (module.is2b2t() && mc.player.isUsingItem()) return;

        boolean takeoffRequested = mc.player.input.playerInput.jump() && !module.takeoff.getValue().equals("None");
        if (module.takeoff.getValue().equals("Timer")) {
            takeoffRequested = !mc.player.isOnGround() && mc.player.getVelocity().y < 0.1;
        } else {
            module.releaseTimer();
        }
        long now = System.currentTimeMillis();
        if (module.vanillaGliding()) lastGlidingAt = now;
        if ((takeoffRequested || now - lastGlidingAt < 1500L) && !module.vanillaGliding() && !module.collisionBelow() && module.elytraEquipped()) {
            module.startGliding();
        }

        if (!module.flying()) return;
        float radians = (float) Math.toRadians(movementYaw);
        float magnitude = module.boost.getValue().floatValue() * 0.1f;
        if (module.boost.isAtMaxLabel() && module.is2b2t()) {
            magnitude = 0.0205f;
            if (targetPitch <= 0.0f && module.setbackWithin(10_000L)) magnitude = 0.008f;
        }
        if (earlyNcpBoost()) magnitude = 3.0f;
        if (module.ncpBoost.getValue()) updateBoostPitch();
        if (pitchInitialized) return;
        if (module.inLava.getValue() && mc.player.isInLava()) magnitude = 0.2f * module.lavaSpeed.getValue().floatValue();

        boolean forward = module.factorize.getValue() ? targetPitch > 0.0f
                : mc.options.forwardKey.isPressed() || module.always.getValue();
        if (!forward) {
            if (mc.options.backKey.isPressed()) {
                mc.player.addVelocity(MathHelper.sin(radians) * magnitude, 0.0, MathHelper.cos(radians) * -magnitude);
            }
            return;
        }
        mc.player.addVelocity(MathHelper.sin(radians) * -magnitude, 0.0, MathHelper.cos(radians) * magnitude);
    }

    @Override
    public void clientTick() {
        if (module.ncpBoost.getValue()) return;
        if (module.flying()) updateBoostPitch();
    }

    private void updateBoostPitch() {
        float jitter = module.movementSync() ? ElytraPhysics.JITTER_STEP * (-3 + ThreadLocalRandom.current().nextInt(6)) : 0.0f;
        if (ElytraPhysics.moving()) request(movementYaw, mc.player.getPitch());

        if (!module.movementSync()) {
            targetPitch = mc.player.getPitch();
        } else if (Math.abs(targetPitch - mc.player.getPitch()) < 15.0f) {
            targetPitch = mc.player.getPitch();
        } else {
            targetPitch += targetPitch > mc.player.getPitch() ? -10.0f : 10.0f;
        }

        if (module.responsive.getValue()) {
            float desired = targetPitch + jitter;
            if (mc.player.input.playerInput.jump()) desired = (module.movementSync() ? -45.0f : -60.0f) + jitter;
            else if (mc.player.input.playerInput.sneak()) desired = 60.0f + jitter;
            desired = MathHelper.clamp(desired, -90.0f, 90.0f);
            if (mc.player.getPitch() != desired) {
                request(movementYaw, desired);
                targetPitch = desired;
            }
        }

        if (targetPitch < 0.0f && module.ncpBoost.getValue()) {
            int flightTicks = module.durabilityBypassActive() ? mc.player.age : mc.player.getGlidingTicks();
            if (flightTicks % 6 > 2 || (flightTicks >= 1 && flightTicks < 20)) {
                pitchInitialized = true;
                return;
            }
            request(movementYaw, 0.0f);
        }
    }
}
