package dev.project.anatorius.modules.impl.movement.elytrafly;

import dev.project.anatorius.modules.impl.movement.ElytraFlyModule;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.util.math.Vec3d;

public class PacketMode extends ElytraFlyMode {
    private long accelerationStart, lastMovedAt;
    private float flightSpeed;

    public PacketMode(ElytraFlyModule module) {
        super(module);
    }

    @Override
    public void onEnable() {
        flightSpeed = 0.0f;
        accelerationStart = System.currentTimeMillis();
    }

    @Override
    public void move(Move move) {
        if (!module.packetFlightActive()) return;
        if (!ElytraPhysics.moving()) accelerationStart = System.currentTimeMillis();
        if (module.accelerate.getValue()) {
            flightSpeed = (float) ElytraPhysics.accelerateTo(module.horizontal.getValue().doubleValue(), module.accelMin.getValue().doubleValue(),
                    module.accelTime.getValue().doubleValue(), accelerationStart);
        } else {
            flightSpeed = module.horizontal.getValue().floatValue();
        }
        double[] motion = ElytraPhysics.applyHorizontal(move, flightSpeed);
        float glide = -module.glide.getValue().floatValue();
        mc.player.setVelocity(motion[0], glide, motion[1]);
        move.setY(glide);
        if (module.down()) {
            move.setY(-0.5);
            Vec3d v = mc.player.getVelocity();
            mc.player.setVelocity(v.x, -0.5, v.z);
        }
        mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
        if (module.antiAfk.getValue() && move.x() == 0.0 && move.z() == 0.0 && System.currentTimeMillis() - lastMovedAt >= 2500L) {
            double radians = Math.toRadians(mc.player.age % 360);
            move.setHorizontal(Math.sin(radians) * 0.03, Math.cos(radians) * 0.03);
            return;
        }
        if (ElytraPhysics.moving()) lastMovedAt = System.currentTimeMillis();
    }
}
