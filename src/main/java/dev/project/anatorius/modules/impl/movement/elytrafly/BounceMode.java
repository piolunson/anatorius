package dev.project.anatorius.modules.impl.movement.elytrafly;

import dev.project.anatorius.events.impl.PacketReceiveEvent;
import dev.project.anatorius.events.impl.PacketSendEvent;
import dev.project.anatorius.modules.impl.movement.ElytraFlyModule;
import dev.project.anatorius.utils.minecraft.EntityUtils;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.s2c.play.DeathMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.Queue;

public class BounceMode extends ElytraFlyMode {
    private final Queue<Packet<?>> queuedPackets = new ArrayDeque<>();
    private long retriedAt;
    private int bounceCooldownTicks, bounceInputTicks;
    private boolean bypassAtTickStart, flyingAtTickStart, diagonalPending;
    private float automaticPitch = 90.0f;

    public BounceMode(ElytraFlyModule module) {
        super(module);
    }

    private void flushQueuedPackets() {
        if (mc.player == null || mc.getNetworkHandler() == null) {
            queuedPackets.clear();
            return;
        }
        while (!queuedPackets.isEmpty()) {
            Packet<?> packet = queuedPackets.poll();
            if (packet == null) break;
            module.sendUncaught(packet);
        }
    }

    @Override
    public void onEnable() {
        bounceInputTicks = 0;
        diagonalPending = module.diagonal.getValue() && module.bouncePacket.getValue();
    }

    @Override
    public void onDisable() {
        mc.player.setSprinting(false);
        flushQueuedPackets();
    }

    private boolean canApply() {
        return bypassAtTickStart || module.elytraEquipped();
    }

    private boolean inLiquidOrWeb() {
        return mc.player.isTouchingWater() || mc.player.isInLava() || EntityUtils.isInWeb(mc.player);
    }

    @Override
    public void preTick() {
        if (diagonalPending) return;
        flyingAtTickStart = module.flying();
        if (canApply() && !module.itemUseSlowing()) {
            if (bounceInputTicks != 2) module.holdJump();
            else module.releaseJump();
            module.holdForward();
            module.holdSprint();
            mc.player.setSprinting(true);
            bounceInputTicks++;
        } else {
            bounceInputTicks = 0;
        }

        bypassAtTickStart = module.durabilityBypassActive();
        if (module.vanillaGliding() && module.groundedTicks() > 5) {
            mc.player.stopGliding();
            return;
        }
        if (inLiquidOrWeb() || module.itemUseSlowing() || !canApply()) return;
        if (!module.flying() && module.groundedTicks() < 3 && !bypassAtTickStart) {
            mc.player.stopGliding();
            mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
        }
        if (!mc.player.horizontalCollision && ElytraPhysics.moving()) {
            mc.player.setSprinting(true);
            if (module.vanillaGliding()) mc.player.setSneaking(false);
        }
        if (module.pitchLock.getValue() && !module.silent.getValue()) mc.player.setPitch(pitch());
    }

    @Override
    public void packetSend(PacketSendEvent event) {
        if (diagonalPending || !flyingAtTickStart || !canApply()) return;
        if (module.compensate.getValue() && !module.itemUseSlowing()
                && !(event.getPacket() instanceof ClientCommandC2SPacket) && !(event.getPacket() instanceof CommonPongC2SPacket)) {
            queuedPackets.add(event.getPacket());
            event.setCancelled(true);
        }
    }

    @Override
    public void move(Move move) {
        if (!module.bouncePacket.getValue()) return;
        if (diagonalPending) {
            Vec3d target = bounceTarget();
            Vec3d position = mc.player.getEntityPos();
            double distance = Math.hypot(target.x - position.x, target.z - position.z);
            if (MathHelper.clamp(distance, 0.0, 0.1) < 0.1) {
                diagonalPending = false;
                move.setHorizontal(0.0, 0.0);
                mc.player.setPosition(target.x, mc.player.getY(), target.z);
            } else {
                Vec3d step = target.subtract(position).normalize().multiply(0.11);
                move.setHorizontal(step.x, step.z);
            }
            return;
        }
        if (!module.setbackWithin(200L) && mc.player.getHungerManager().getFoodLevel() > 6 && module.flying()
                && mc.player.isOnGround()) {
            double speed = module.speedKmh();
            if (speed < module.maxBoost.getValue().intValue() && speed >= 97.0) move.setY(0.0);
        }
    }

    @Override
    public void playerTick() {
        if (!diagonalPending) {
            if (module.itemUseSlowing()) return;
            if (module.bouncePacket.getValue() && module.diagonal.getValue()) {
                float diagonalYaw = Math.round((mc.player.getYaw() + 1.0f) / 45.0f) * 45.0f;
                if (diagonalYaw % 90.0f == 0.0f) diagonalYaw += 45.0f;
                mc.player.setYaw(diagonalYaw);
            }
            if (!inLiquidOrWeb()) {
                if (module.compensate.getValue()) {
                    if (bounceCooldownTicks == 0) flushQueuedPackets();
                } else if (!queuedPackets.isEmpty()) {
                    flushQueuedPackets();
                }
                return;
            }
            flushQueuedPackets();
        } else {
            flushQueuedPackets();
        }
    }

    @Override
    public void clientTick() {
        if (mc.player.isGliding()) return;
        long now = System.currentTimeMillis();
        if (now - retriedAt >= 10L && bounceInputTicks >= 4) {
            retriedAt = now;
            module.tryStartNormalFlight();
        }
    }

    @Override
    public float[] travelLook() {
        if (!canApply() || diagonalPending || !module.pitchLock.getValue() || module.itemUseSlowing()) return null;
        if (module.autoPitch.getValue()) automaticPitch = mc.player.getVelocity().y > -0.2 ? 90.0f : 2.0f;
        return new float[]{Float.NaN, pitch()};
    }

    @Override
    public float[] packetLook() {
        if (diagonalPending) {
            Vec3d target = bounceTarget();
            Vec3d eye = mc.player.getEyePos();
            double dx = target.x - eye.x, dy = target.y - eye.y, dz = target.z - eye.z;
            float yaw = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
            float pitch = (float) MathHelper.wrapDegrees(-Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))));
            return new float[]{yaw, pitch};
        }

        double dx = mc.player.getX() - mc.player.lastX, dy = mc.player.getY() - mc.player.lastY, dz = mc.player.getZ() - mc.player.lastZ;
        Vec3d projected = mc.player.getEntityPos().add(dx, dy, dz);
        boolean free = mc.world.isSpaceEmpty(mc.player, Box.of(projected, 0.2, 0.2, 0.2));
        if (free && !mc.player.isOnGround()) {
            if (bounceCooldownTicks > 0) bounceCooldownTicks--;
        } else {
            bounceCooldownTicks = 2;
        }

        if (!module.itemUseSlowing() && canApply() && module.pitchLock.getValue() && module.silent.getValue()) {
            return new float[]{Float.NaN, pitch()};
        }
        return null;
    }

    @Override
    public void packetReceive(PacketReceiveEvent event) {
        if (event.getPacket() instanceof DeathMessageS2CPacket || event.getPacket() instanceof PlayerPositionLookS2CPacket) {
            mc.execute(this::flushQueuedPackets);
        }
    }

    private float pitch() {
        return module.autoPitch.getValue() ? automaticPitch : module.pitch.getValue().floatValue();
    }

    public boolean isDiagonalPending() {
        return diagonalPending;
    }

    private Vec3d bounceTarget() {
        Direction open = openHorizontalSide();
        BlockPos position = BlockPos.ofFloored(mc.player.getX(), mc.player.getY() + 0.5, mc.player.getZ());
        return open == null ? Vec3d.ofBottomCenter(position) : Vec3d.ofBottomCenter(position.offset(open));
    }

    private Direction openHorizontalSide() {
        BlockPos position = mc.player.getBlockPos();
        int blocked = 0;
        Direction lastOpen = null;
        for (Direction side : Direction.Type.HORIZONTAL) {
            if (!mc.world.getBlockState(position.offset(side)).isReplaceable()) blocked++;
            else lastOpen = side;
        }
        return blocked == 0 ? null : lastOpen;
    }
}
