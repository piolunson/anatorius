package dev.project.anatorius.modules.impl.movement.elytrafly;

import dev.project.anatorius.events.impl.PacketReceiveEvent;
import dev.project.anatorius.modules.impl.movement.ElytraFlyModule;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.Vec3d;

public class ControlMode extends ElytraFlyMode {
    private Vec3d savedVelocity = Vec3d.ZERO;
    private int verticalInputTicks;
    private double flightSpeed;
    private long accelerationStart, verticalAccelerationStart;
    private boolean verticalVelocityApplied;

    public ControlMode(ElytraFlyModule module) {
        super(module);
    }

    @Override
    public void onEnable() {
        flightSpeed = 0.0;
        verticalAccelerationStart = accelerationStart = System.currentTimeMillis();
    }

    private float flightPitch() {
        float pitch = mc.player.getPitch();
        if (verticalInputRequested()) {
            pitch = ElytraPhysics.moving() ? 45.0f : 90.0f;
            if (module.up()) pitch = -pitch;
        }
        return pitch;
    }

    @Override
    public void move(Move move) {
        if (!module.flying() || module.manualVerticalActive()) {
            accelerationStart = System.currentTimeMillis();
            return;
        }
        if (!ElytraPhysics.moving()) accelerationStart = System.currentTimeMillis();

        float verticalSpeed = module.vertical.getValue().floatValue();
        if (module.accelerate.getValue()) {
            flightSpeed = ElytraPhysics.accelerateTo(module.horizontal.getValue().doubleValue(), module.accelMin.getValue().doubleValue(),
                    module.accelTime.getValue().doubleValue(), accelerationStart);
            verticalSpeed = (float) ElytraPhysics.accelerateTo(verticalSpeed, module.verticalAccelMin.getValue().doubleValue(),
                    module.accelTime.getValue().doubleValue(), verticalAccelerationStart);
        } else {
            flightSpeed = module.horizontal.getValue().doubleValue();
        }

        String verticalMode = module.verticalMode.getValue();
        if (verticalInputTicks == 0) {
            if (module.up() && verticalMode.equals("Angled") && flightSpeed >= 2.0) flightSpeed = 2.0;
            double[] motion = ElytraPhysics.applyHorizontal(move, flightSpeed);
            mc.player.setVelocity(motion[0], mc.player.getVelocity().y, motion[1]);
            savedVelocity = mc.player.getVelocity();
            if (mc.player.age % 4 == 0) {
                move.setY(move.y() - 0.0001);
                mc.player.setVelocity(mc.player.getVelocity().add(0.0, -0.0001, 0.0));
            }
        }

        boolean jumping = module.up(), sneaking = module.down();
        if (jumping && module.flying()) {
            if (verticalMode.equals("Angled")) verticalInputTicks++;
            if (verticalMode.equals("Straight")) {
                move.setY(verticalSpeed);
                setVelocityY(verticalSpeed);
            } else if (verticalMode.equals("Angled") && ElytraPhysics.moving()) {
                applyVerticalPitchVelocity();
            }
            return;
        }
        verticalInputTicks = 0;
        if (sneaking) {
            move.setY(-verticalSpeed);
            setVelocityY(-verticalSpeed);
        } else {
            move.setY(0.0);
            setVelocityY(0.0);
            double glide = module.glide.getValue().doubleValue();
            if (!mc.player.verticalCollision && glide != 0.0) {
                mc.player.setVelocity(mc.player.getVelocity().add(0.0, -glide, 0.0));
                move.setY(mc.player.getVelocity().y);
            }
        }
    }

    private void setVelocityY(double y) {
        Vec3d v = mc.player.getVelocity();
        mc.player.setVelocity(v.x, y, v.z);
    }

    @Override
    public void preTick() {
        if (module.manualVerticalActive()) {
            module.holdJump();
            module.holdForward();
        }
    }

    @Override
    public void playerTick() {
        if (!module.flying()) verticalAccelerationStart = System.currentTimeMillis();
        if (!module.up() && !module.down()) verticalAccelerationStart = System.currentTimeMillis();
        if (module.up() && module.flying()) {
            if (!verticalVelocityApplied && module.verticalMode.getValue().equals("Push")) {
                double[] motion = ElytraPhysics.horizontalMotion(mc.player.getYaw(), 1.0f, 0.0f, flightSpeed);
                mc.player.addVelocity(motion[0], 0.0, motion[1]);
            }
            verticalVelocityApplied = true;
            return;
        }
        verticalVelocityApplied = false;
    }

    @Override
    public float[] travelLook() {
        if (!module.flying() || module.manualVerticalActive()) return null;
        float pitch = verticalInputRequested() ? flightPitch() : Float.NaN;
        if (!ElytraPhysics.moving()) return Float.isNaN(pitch) ? null : new float[]{Float.NaN, pitch};
        return new float[]{ElytraPhysics.movementYaw(mc.player.getYaw()), pitch};
    }

    @Override
    public float[] packetLook() {
        if (!module.flying() || module.manualVerticalActive()) return null;
        if (verticalInputRequested()) return new float[]{Float.NaN, flightPitch()};
        if (!ElytraPhysics.moving()) return null;
        float pitch = Float.NaN;
        if (module.spoofPitch.getValue()) pitch = module.spoofedPitch.getValue().floatValue();
        if (verticalInputTicks > 0) pitch = -module.verticalPitch.getValue().floatValue();
        return new float[]{ElytraPhysics.movementYaw(mc.player.getYaw()), pitch};
    }

    @Override
    public void packetReceive(PacketReceiveEvent event) {
        if (event.getPacket() instanceof PlayerPositionLookS2CPacket) accelerationStart = System.currentTimeMillis();
    }

    private boolean verticalInputRequested() {
        if (module.up()) return true;
        return module.down() && module.verticalMode.getValue().equals("Straight") && module.movementSync();
    }

    private void applyVerticalPitchVelocity() {
        float anglePitch = -module.verticalPitch.getValue().floatValue();
        double pitchRadians = Math.toRadians(anglePitch);
        Vec3d velocity = savedVelocity;
        Vec3d look = ElytraPhysics.direction(anglePitch, mc.player.getYaw());
        double horizontalSpeed = velocity.horizontalLength();
        double horizontalLook = look.horizontalLength();
        double lookLength = look.length();
        double cos = Math.cos(pitchRadians);
        double lift = cos * cos * Math.min(1.0, lookLength / 0.4);
        velocity = velocity.add(0.0, 0.08 * (-1.0 + lift * 0.75), 0.0);
        if (pitchRadians < 0.0 && horizontalLook > 0.0) {
            double climb = horizontalSpeed * -Math.sin(pitchRadians) * 0.04;
            velocity = velocity.add(-look.x * climb / horizontalLook, climb * 3.2, -look.z * climb / horizontalLook);
        }
        mc.player.setVelocity(velocity.multiply(0.99, 0.98, 0.99));
        savedVelocity = mc.player.getVelocity();
    }
}
