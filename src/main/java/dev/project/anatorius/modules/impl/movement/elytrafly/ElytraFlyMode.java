package dev.project.anatorius.modules.impl.movement.elytrafly;

import dev.project.anatorius.events.impl.PacketReceiveEvent;
import dev.project.anatorius.events.impl.PacketSendEvent;
import dev.project.anatorius.events.impl.PlayerMoveEvent;
import dev.project.anatorius.modules.impl.movement.ElytraFlyModule;
import dev.project.anatorius.utils.IMinecraft;
import net.minecraft.util.math.Vec3d;

public abstract class ElytraFlyMode implements IMinecraft {
    protected final ElytraFlyModule module;

    protected ElytraFlyMode(ElytraFlyModule module) {
        this.module = module;
    }

    public void onEnable() {}

    public void onDisable() {}

    public void preTick() {}

    public void clientTick() {}

    public void playerTick() {}

    public void move(Move move) {}

    private float requestYaw = Float.NaN, requestPitch = Float.NaN;

    public void clearRequest() {
        requestYaw = Float.NaN;
        requestPitch = Float.NaN;
    }

    protected void request(float yaw, float pitch) {
        requestYaw = yaw;
        requestPitch = pitch;
    }

    protected float[] requested() {
        if (Float.isNaN(requestYaw) && Float.isNaN(requestPitch)) return null;
        return new float[]{requestYaw, requestPitch};
    }

    public float[] travelLook() {
        return requested();
    }

    public float[] packetLook() {
        return requested();
    }

    public void packetSend(PacketSendEvent event) {}

    public void packetReceive(PacketReceiveEvent event) {}

    public static final class Move {
        private final PlayerMoveEvent event;

        public Move(PlayerMoveEvent event) {
            this.event = event;
        }

        public Vec3d velocity() {
            return new Vec3d(event.getX(), event.getY(), event.getZ());
        }

        public double x() { return event.getX(); }

        public double y() { return event.getY(); }

        public double z() { return event.getZ(); }

        public void setY(double y) {
            event.setY(y);
            event.setCancelled(true);
        }

        public void setHorizontal(double x, double z) {
            event.setX(x);
            event.setZ(z);
            event.setCancelled(true);
        }

        public void setVelocity(Vec3d velocity) {
            event.setX(velocity.x);
            event.setY(velocity.y);
            event.setZ(velocity.z);
            event.setCancelled(true);
        }
    }
}
