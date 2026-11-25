package dev.project.anatorius.modules.impl.movement.fakefly;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.impl.PlayerMoveEvent;
import dev.project.anatorius.modules.impl.combat.AutoMaceModule;
import dev.project.anatorius.modules.impl.movement.FakeFlyModule;
import dev.project.anatorius.modules.impl.exploit.TimerModule;
import dev.project.anatorius.modules.impl.movement.elytrafly.ElytraPhysics;
import dev.project.anatorius.utils.IMinecraft;
import dev.project.anatorius.utils.minecraft.FlightInput;
import net.minecraft.util.math.MathHelper;

public class NewGrimHandler implements IMinecraft {
    private final FakeFlyModule module;
    private boolean movementStarted;
    private boolean negativeYawJitter;
    private boolean pitchJitterPhase;
    private boolean timerHeld;

    public NewGrimHandler(FakeFlyModule module) {
        this.module = module;
    }

    public void onDisable() {
        releaseTimer();
        movementStarted = false;
    }

    public void onMove(PlayerMoveEvent event) {
        if (InfDurability.collisionBelow()) movementStarted = false;
        if (idleAirborne() && hasDurabilityFlightCondition() && module.stopMotion.getValue().equals("Static")) {
            if (AutoMaceModule.busy()) return;
            event.setX(0.0);
            event.setY(0.0);
            event.setZ(0.0);
        }
    }

    public void onClientTick() {
        releaseTimer();
        if (AutoMaceModule.busy()) return;
        if (module.stopMotion.getValue().equals("Timer") && hasDurabilityFlightCondition()) {
            movementStarted = true;
            if (idleAirborne()) requestTimer(0.02f);
        }
    }

    public void rotationTick() {
        if (InfDurability.collisionBelow()) return;
        if (AutoMaceModule.busy()) return;
        if (!hasDurabilityFlightCondition()) return;

        float playerYaw = mc.player.getYaw();
        boolean moving = ElytraPhysics.moving();
        float yaw = moving ? ElytraPhysics.movementYaw(playerYaw) : playerYaw;
        yaw += (negativeYawJitter ? -1 : 1) * ElytraPhysics.JITTER_STEP;
        negativeYawJitter = !negativeYawJitter;

        float pitch = -1.0f;
        if (FlightInput.up()) {
            pitch = moving ? -45.0f : -90.0f;
            if (!moving) yaw = MathHelper.wrapDegrees(yaw + 180.0f);
        }
        if (FlightInput.down()) {
            if (module.responsiveDescend.getValue()) pitch = moving ? mc.player.getPitch() : 90.0f;
            else pitch = moving ? 45.0f : 90.0f;
        }
        if (module.stopMotion.getValue().equals("Jitter") && idleAirborne()) {
            if (pitchJitterPhase) yaw = MathHelper.wrapDegrees(yaw + 180.0f);
            pitchJitterPhase = !pitchJitterPhase;
        }
        module.rotateGlide(yaw, pitch);
    }

    private boolean hasDurabilityFlightCondition() {
        return !InfDurability.collisionBelow();
    }

    private boolean idleAirborne() {
        boolean inputRequested = ElytraPhysics.moving() || FlightInput.up() || FlightInput.down() || movementKeyDown();
        return !InfDurability.collisionBelow() && !inputRequested;
    }

    private boolean movementKeyDown() {
        return mc.options.forwardKey.isPressed() || mc.options.backKey.isPressed() || mc.options.rightKey.isPressed()
                || mc.options.leftKey.isPressed() || mc.options.sneakKey.isPressed() || mc.options.jumpKey.isPressed();
    }

    private void requestTimer(float multiplier) {
        timerHeld = true;
        Anatorius.WORLD_MANAGER.setTimerMultiplier(multiplier);
    }

    private void releaseTimer() {
        if (!timerHeld) return;
        timerHeld = false;
        TimerModule timer = Anatorius.MODULE_MANAGER.getModule(TimerModule.class);
        Anatorius.WORLD_MANAGER.setTimerMultiplier(timer != null && timer.isToggled() ? timer.multiplier.getValue().floatValue() : 1.0f);
    }
}
