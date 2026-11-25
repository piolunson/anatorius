package dev.project.anatorius.modules.impl.movement;

import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.PacketReceiveEvent;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.modules.impl.movement.elytrafly.ElytraPhysics;
import dev.project.anatorius.settings.impl.BooleanSetting;
import dev.project.anatorius.settings.impl.NumberSetting;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.AbstractBoatEntity;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.Vec3d;

@RegisterModule(name = "EntityControl", description = "Lets you control and speed up anything you ride.", category = Module.Category.MOVEMENT)
public class EntityControlModule extends Module {
    private static EntityControlModule INSTANCE;

    public BooleanSetting horseJump = new BooleanSetting("HorseJump", "Horses and other jumping mounts always jump at full strength, no charging.", true);
    public BooleanSetting copyYaw = new BooleanSetting("CopyYaw", "Turns the vehicle to where you look.", false);

    public BooleanSetting entitySpeed = new BooleanSetting("EntitySpeed", "Speed", "Sets how fast your vehicle moves.", true);
    public NumberSetting horizontal = new NumberSetting("Horizontal", "How fast the vehicle moves, in blocks per tick (20 ticks = 1 second).", new BooleanSetting.Visibility(entitySpeed, true), 1.0, 0.1, 19.9);
    public BooleanSetting accelerate = new BooleanSetting("Accelerate", "The speed builds up when you start moving instead of being full at once.", new BooleanSetting.Visibility(entitySpeed, true), false);
    public BooleanSetting stopOnWall = new BooleanSetting("StopOnWall", "Doesn't push the vehicle while it runs into a wall (the server would set you back).", new BooleanSetting.Visibility(entitySpeed, true), true);
    public BooleanSetting fly = new BooleanSetting("Fly", "The vehicle flies: jump climbs, Sprint sinks (sneak would get you off). Boats lose their gravity.", new BooleanSetting.Visibility(entitySpeed, true), false);
    public NumberSetting vertical = new NumberSetting("Vertical", "How fast it climbs and sinks, in blocks per tick.", new BooleanSetting.Visibility(fly, true), 1.0, 0.0, 10.0);
    public NumberSetting glide = new NumberSetting("Glide", "How fast it sinks while you hold neither key, in blocks per tick. 0 hovers in place.", new BooleanSetting.Visibility(fly, true), 0.0, 0.0, 2.0);

    private static final double HOVER_JITTER = 0.04125;
    private boolean negativeJitter;
    private long accelerationStart;

    public EntityControlModule() {
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        accelerationStart = System.currentTimeMillis();
    }

    @Override
    public void onDisable() {
        if (mc.player != null && mc.player.getVehicle() instanceof AbstractBoatEntity boat) boat.setNoGravity(false);
    }

    @SubscribeEvent
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacket() instanceof PlayerPositionLookS2CPacket) accelerationStart = System.currentTimeMillis();
    }

    public static boolean saddles() {
        return INSTANCE != null && INSTANCE.isToggled();
    }

    public static boolean fullJumps() {
        return INSTANCE != null && INSTANCE.isToggled() && INSTANCE.horseJump.getValue();
    }

    public static Vec3d vehicleMotion(Entity vehicle, Vec3d motion) {
        if (INSTANCE == null || !INSTANCE.isToggled() || mc.player == null) return null;
        if (mc.player.getVehicle() != vehicle || vehicle.getControllingPassenger() != mc.player) return null;
        return INSTANCE.motion(vehicle, motion);
    }

    private Vec3d motion(Entity vehicle, Vec3d motion) {
        if (!ElytraPhysics.moving()) accelerationStart = System.currentTimeMillis();
        if (copyYaw.getValue()) vehicle.setYaw(mc.player.getYaw());
        if (!entitySpeed.getValue()) return null;
        if (stopOnWall.getValue() && vehicle.horizontalCollision) return null;

        double speed = Math.min(horizontal.getValue().doubleValue(), 19.99);
        if (accelerate.getValue()) speed = ElytraPhysics.accelerateTo(speed, 0.1, speed, accelerationStart);
        double[] horizontalMotion = ElytraPhysics.horizontalMotion(mc.player.getYaw(), speed);

        double y = motion.y;
        if (fly.getValue()) {
            if (vehicle instanceof AbstractBoatEntity boat) boat.setNoGravity(true);
            double verticalSpeed = vertical.getValue().doubleValue();
            boolean descend = mc.options.sprintKey.isPressed() && !(mc.currentScreen instanceof ChatScreen);
            if (mc.options.jumpKey.isPressed()) y = verticalSpeed;
            else if (descend) y = -verticalSpeed;
            else if (glide.getValue().doubleValue() > 0.0) y = -glide.getValue().doubleValue();
            else y = negativeJitter ? -HOVER_JITTER : HOVER_JITTER;
        }
        negativeJitter = !negativeJitter;
        return new Vec3d(horizontalMotion[0], y, horizontalMotion[1]);
    }

    @Override
    public String getMetaData() {
        return entitySpeed.getValue() ? String.valueOf(horizontal.getValue()) : "";
    }
}
