package dev.project.anatorius.utils.minecraft;

import dev.project.anatorius.utils.IMinecraft;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;

public class ElytraHelper implements IMinecraft {
    private static long lastElytraToggle = 0;
    private static boolean wasOnGround = true;
    private static int airTicks = 0;

    public static boolean startGliding() {
        if (mc.player == null) return false;

        if (mc.player.isGliding()) {
            return true;
        }

        boolean onGround = mc.player.isOnGround();
        if (onGround != wasOnGround) {
            wasOnGround = onGround;
            if (!onGround) {
                airTicks = 0;
            }
        }

        if (onGround) {
            return false;
        }

        airTicks++;

        long now = System.currentTimeMillis();
        if (now - lastElytraToggle < 100) {
            return false;
        }

        if (airTicks < 2) {
            return false;
        }

        if (mc.player.getVelocity().y >= 0) {
            return false;
        }

        mc.player.networkHandler.sendPacket(
            new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING)
        );

        lastElytraToggle = now;
        return true;
    }

    public static boolean canSafeJump() {
        if (mc.player == null) return false;

        if (!mc.player.isOnGround()) return false;

        if (mc.player.getAbilities().flying) return true;

        long now = System.currentTimeMillis();
        if (now - lastElytraToggle < 50) return false;

        return true;
    }

    public static void reset() {
        lastElytraToggle = 0;
        wasOnGround = true;
        airTicks = 0;
    }

    public static int getAirTicks() {
        return airTicks;
    }
}
