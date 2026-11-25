package dev.project.anatorius.modules.impl.movement;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.PacketReceiveEvent;
import dev.project.anatorius.events.impl.PacketSendEvent;
import dev.project.anatorius.events.impl.PlayerMoveEvent;
import dev.project.anatorius.events.impl.SendMovementEvent;

import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.settings.impl.BooleanSetting;
import dev.project.anatorius.settings.impl.ModeSetting;
import dev.project.anatorius.settings.impl.NumberSetting;
import dev.project.anatorius.utils.minecraft.MovementUtils;
import net.minecraft.client.gui.screen.world.LevelLoadingScreen;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector2d;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@RegisterModule(name = "PacketFly", description = "Allows you to fly by manipulating movement packets.", category = Module.Category.MOVEMENT)
public class PacketFlyModule extends Module {
    public ModeSetting mode = new ModeSetting("Mode", "The method used for packet flying.", "Fast", new String[]{"Fast", "Factor", "Rubber", "Limit"});
    public ModeSetting type = new ModeSetting("Type", "The packet manipulation type.", "Preserve", new String[]{"Preserve", "Up", "Down", "Bounds"});
    public ModeSetting phase = new ModeSetting("Phase", "Phase through blocks.", "Full", new String[]{"Full", "Off", "Semi"});
    public BooleanSetting limit = new BooleanSetting("Limit", "Limits packet sending.", true);

    public BooleanSetting antiKick = new BooleanSetting("AntiKick", "Prevents getting kicked for flying.", true);
    public NumberSetting interval = new NumberSetting("Interval", "AntiKick interval.", new BooleanSetting.Visibility(antiKick, true), 4.0, 1.0, 50.0);
    public NumberSetting upInterval = new NumberSetting("UpInterval", "Up movement interval.", new BooleanSetting.Visibility(antiKick, true), 20.0, 1.0, 50.0);
    public NumberSetting antiKickOffset = new NumberSetting("AntiKickOffset", "AntiKick offset value.", new BooleanSetting.Visibility(antiKick, true), 0.04, 0.008, 1.0);

    public NumberSetting speed = new NumberSetting("Speed", "Movement speed.", 1.0, 0.0, 10.0);
    public NumberSetting upSpeed = new NumberSetting("UpSpeed", "Vertical movement speed.", 0.062, 0.001, 0.1);
    public NumberSetting timer = new NumberSetting("Timer", "Timer multiplier.", 1.0, 0.1, 5.0);
    public NumberSetting increaseTicks = new NumberSetting("IncreaseTicks", "Factor increase ticks.", 1.0, 1.0, 20.0);
    public NumberSetting factor = new NumberSetting("Factor", "Speed factor multiplier.", 1.0, 1.0, 10.0);
    public NumberSetting offset = new NumberSetting("Offset", "Position offset value.", new ModeSetting.Visibility(type, "Up", "Down"), 1337.0, 1.0, 1337.0);

    private final ConcurrentHashMap<Integer, Teleport> teleports = new ConcurrentHashMap<>();
    private final ArrayList<PlayerMoveC2SPacket> movePackets = new ArrayList<>();
    private int ticks, factorTicks, teleportId = -1;
    private boolean flip = false;

    @Override
    public void onEnable() {
        teleportId = -1;
        if (mc.player != null && mc.world != null) {
            ticks = 0;
            teleportId = 0;
            movePackets.clear();
            teleports.clear();
        }
        factorTicks = 0;
    }

    @Override
    public void onDisable() {
        Anatorius.WORLD_MANAGER.setTimerMultiplier(1.0f);
    }

    public boolean getTickCounter(int n) {
        ++ticks;
        if (ticks >= n) {
            ticks = 0;
            return true;
        }
        return false;
    }

    private int getWorldBorder() {
        if (mc.isInSingleplayer()) {
            return 1;
        }
        int n = ThreadLocalRandom.current().nextInt(29000000);
        if (ThreadLocalRandom.current().nextBoolean()) {
            return n;
        }
        return -n;
    }

    public Vec3d getVectorByMode(Vec3d vec3d, Vec3d vec3d2) {
        Vec3d vec3d3 = vec3d.add(vec3d2);
        switch (type.getValue()) {
            case "Preserve" -> vec3d3 = vec3d3.add(getWorldBorder(), 0.0, getWorldBorder());
            case "Up" -> vec3d3 = vec3d3.add(0.0, offset.getValue().doubleValue(), 0.0);
            case "Down" -> vec3d3 = vec3d3.add(0.0, -offset.getValue().doubleValue(), 0.0);
            case "Bounds" -> vec3d3 = new Vec3d(vec3d3.x, mc.player.getY() <= 10.0 ? 255.0 : 1.0, vec3d3.z);
        }
        return vec3d3;
    }

    public void sendPackets(Vec3d vec3d, boolean confirm) {
        Vec3d motion = mc.player.getEntityPos().add(vec3d);
        Vec3d rubberBand = getVectorByMode(vec3d, motion);

        PlayerMoveC2SPacket motionPacket = new PlayerMoveC2SPacket.PositionAndOnGround(motion.x, motion.y, motion.z, mc.player.isOnGround(), mc.player.horizontalCollision);
        movePackets.add(motionPacket);
        mc.player.networkHandler.sendPacket(motionPacket);

        PlayerMoveC2SPacket rubberBandPacket = new PlayerMoveC2SPacket.PositionAndOnGround(rubberBand.x, rubberBand.y, rubberBand.z, mc.player.isOnGround(), mc.player.horizontalCollision);
        movePackets.add(rubberBandPacket);
        mc.player.networkHandler.sendPacket(rubberBandPacket);

        if (confirm) {
            mc.player.networkHandler.sendPacket(new TeleportConfirmC2SPacket(++teleportId));
            teleports.put(teleportId, new Teleport(motion.x, motion.y, motion.z, System.currentTimeMillis()));
        }
    }

    @SubscribeEvent
    public void onPacketReceive(PacketReceiveEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (event.getPacket() instanceof PlayerPositionLookS2CPacket pac) {
            int packetTeleportId = pac.teleportId();
            Teleport teleport = teleports.remove(packetTeleportId);
            if (
                    mc.player.isAlive()
                            && mc.world.isChunkLoaded((int) mc.player.getX() >> 4, (int) mc.player.getZ() >> 4)
                            && !(mc.currentScreen instanceof LevelLoadingScreen)
                            && !mode.getValue().equals("Rubber")
                            && teleport != null
                            && teleport.x == pac.change().position().getX()
                            && teleport.y == pac.change().position().getY()
                            && teleport.z == pac.change().position().getZ()
            ) {
                event.setCancelled(true);
                return;
            }
            teleportId = packetTeleportId;
        }
    }

    @SubscribeEvent
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacket() instanceof PlayerMoveC2SPacket) {
            if (movePackets.contains((PlayerMoveC2SPacket) event.getPacket())) {
                movePackets.remove((PlayerMoveC2SPacket) event.getPacket());
                return;
            }
            event.setCancelled(true);
        }
    }

    @SubscribeEvent
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!event.isCancelled()) {
            if (!mode.getValue().equals("Rubber") && teleportId == 0)
                return;

            event.setCancelled(true);
            event.setX(mc.player.getVelocity().x);
            event.setY(mc.player.getVelocity().y);
            event.setZ(mc.player.getVelocity().z);

            if (!phase.getValue().equals("Off") && (phase.getValue().equals("Semi") || mc.world.getBlockCollisions(mc.player, mc.player.getBoundingBox().expand(-0.0625, -0.0625, -0.0625)).iterator().hasNext())) {
                mc.player.noClip = true;
            }
        }
    }

    @SubscribeEvent
    public void onSendMovement(SendMovementEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (timer.getValue().doubleValue() != 1.0)
            Anatorius.WORLD_MANAGER.setTimerMultiplier(timer.getValue().floatValue());

        mc.player.setVelocity(0.0, 0.0, 0.0);

        if (!mode.getValue().equals("Rubber") && teleportId == 0) {
            if (getTickCounter(4))
                sendPackets(Vec3d.ZERO, false);
            return;
        }

        boolean insideBlock = mc.world.getBlockCollisions(mc.player, mc.player.getBoundingBox().expand(-0.0625, -0.0625, -0.0625)).iterator().hasNext();

        double upMotion = 0;

        if (mc.options.jumpKey.isPressed() && (insideBlock || !MovementUtils.isMoving())) {
            if (antiKick.getValue() && !insideBlock)
                upMotion = getTickCounter(mode.getValue().equals("Rubber") ? upInterval.getValue().intValue() / 2 : upInterval.getValue().intValue()) ? -upSpeed.getValue().doubleValue() / 2.0 : upSpeed.getValue().doubleValue();
            else
                upMotion = upSpeed.getValue().doubleValue();
        } else if (mc.options.sneakKey.isPressed())
            upMotion = -upSpeed.getValue().doubleValue();
        else if (antiKick.getValue() && !insideBlock)
            upMotion = getTickCounter(interval.getValue().intValue()) ? -antiKickOffset.getValue().doubleValue() : 0.0;

        if (phase.getValue().equals("Full") && insideBlock && MovementUtils.isMoving() && upMotion != 0.0)
            upMotion = mc.options.jumpKey.isPressed() ? upMotion / 2.5 : upMotion / 1.5;

        Vector2d motion = MovementUtils.forward(phase.getValue().equals("Full") && insideBlock ? 0.034444444444444444 : speed.getValue().doubleValue() * 0.26);

        int factorInt = 1;
        if (mode.getValue().equals("Factor") && mc.player.age % increaseTicks.getValue().intValue() == 0) {
            factorInt = (int) Math.floor(factor.getValue().doubleValue());
            factorTicks++;
            if (factorTicks > (int) (20.0 / ((factor.getValue().doubleValue() - factorInt) * 20.0))) {
                factorInt += 1;
                factorTicks = 0;
            }
        }

        for (int i = 1; i <= factorInt; ++i) {
            if (mode.getValue().equals("Limit")) {
                if (mc.player.age % 2 == 0) {
                    if (flip && upMotion >= 0.0) {
                        flip = false;
                        upMotion = -upSpeed.getValue().doubleValue() / 2.0;
                    }
                    mc.player.setVelocity(motion.x * i, upMotion * i, motion.y * i);
                    sendPackets(mc.player.getVelocity(), !limit.getValue());
                    continue;
                }
                if (!(upMotion < 0.0)) continue;
                flip = true;
                continue;
            }
            mc.player.setVelocity(motion.x * i, upMotion * i, motion.y * i);
            sendPackets(mc.player.getVelocity(), !mode.getValue().equals("Rubber"));
        }

        teleports.entrySet().removeIf(entry -> System.currentTimeMillis() - entry.getValue().time > 30000);
    }

    @Override
    public String getMetaData() {
        return mode.getValue();
    }

    public record Teleport(double x, double y, double z, long time) {}
}
