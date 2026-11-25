package dev.project.anatorius.modules.impl.movement.elytrafly;

import dev.project.anatorius.events.impl.PacketReceiveEvent;
import dev.project.anatorius.modules.impl.movement.ElytraFlyModule;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class StrictMode extends ElytraFlyMode {
    private long accelerationStart;
    private boolean takeoffPending, awaitingFlight, fireworkLaunchPending;

    public StrictMode(ElytraFlyModule module) {
        super(module);
    }

    @Override
    public void onEnable() {
        if (mc.player.isGliding() && !mc.player.isOnGround()) {
            takeoffPending = true;
            mc.player.stopGliding();
        }
        awaitingFlight = false;
        fireworkLaunchPending = false;
        accelerationStart = System.currentTimeMillis();
    }

    @Override
    public void playerTick() {
        if (mc.player.isOnGround()) takeoffPending = false;
        else if (mc.player.isGliding()) takeoffPending = true;

        boolean requestTakeoff = mc.player.input.playerInput.jump();
        if (module.takeoff.getValue().equals("Timer")) {
            requestTakeoff = !mc.player.isOnGround() && mc.player.getVelocity().y < 0.1;
        } else {
            module.releaseTimer();
        }
        if (requestTakeoff || awaitingFlight) {
            module.updateTakeoff();
            awaitingFlight = !mc.player.isGliding();
        }
    }

    @Override
    public void move(Move move) {
        if (!mc.player.isGliding() && takeoffPending && !mc.player.horizontalCollision && mc.player.getVelocity().y < 0.0) {
            awaitingFlight = true;
        }
        if (!mc.player.isGliding()) return;
        if (!ElytraPhysics.moving()) accelerationStart = System.currentTimeMillis();
        float progress = MathHelper.clamp((System.currentTimeMillis() - accelerationStart) / 500.0f, 0.0f, 1.0f);
        float speed = MathHelper.lerp(progress, module.startSpeed.getValue().floatValue(), module.horizontal.getValue().floatValue());
        double[] motion = ElytraPhysics.applyHorizontal(move, speed);
        mc.player.setVelocity(motion[0], 0.0, motion[1]);
        move.setY(0.0);
    }

    @Override
    public float[] packetLook() {
        float yaw = Float.NaN, pitch = Float.NaN;
        if (module.elytraEquipped() && !mc.player.isOnGround()) {
            boolean adjust = false;
            if (!mc.player.isGliding()) {
                adjust = true;
                fireworkLaunchPending = false;
            } else if (!fireworkLaunchPending) {
                if (!ElytraPhysics.moving()) adjust = true;
                else fireworkLaunchPending = true;
            }
            if (adjust) {
                int offset = mc.player.age % 2 == 0 ? -2 : 2;
                yaw = mc.player.getYaw() + offset;
                pitch = mc.player.getPitch() + offset;
            }
        }
        Vec3d v = mc.player.getVelocity();
        if (mc.player.isGliding() && !mc.player.isOnGround() && v.y < 0.1 && ElytraPhysics.moving()) pitch = -5.0f;
        if (Float.isNaN(yaw) && Float.isNaN(pitch)) return null;
        return new float[]{yaw, pitch};
    }

    @Override
    public void packetReceive(PacketReceiveEvent event) {
        if (event.getPacket() instanceof PlayerPositionLookS2CPacket && mc.player.isGliding() && !mc.player.isOnGround()) {
            accelerationStart = System.currentTimeMillis();
            mc.execute(() -> {
                if (mc.player != null) mc.player.stopGliding();
            });
        }
    }
}
