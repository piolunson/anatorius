package dev.project.anatorius.utils.minecraft;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.modules.impl.core.AntiCheatModule;
import dev.project.anatorius.utils.IMinecraft;

public class BlockBudget implements IMinecraft {
    private int placedThisTick;
    private long windowStart;
    private int windowCount;

    public void newTick() {
        placedThisTick = 0;
        long now = System.currentTimeMillis();
        if (now - windowStart >= 100L) {
            windowStart = now;
            windowCount = 0;
        }
    }

    public void reset() {
        placedThisTick = 0;
        windowStart = 0;
        windowCount = 0;
    }

    public boolean canPlace(boolean instant, int blocksPerTick) {
        if (instant) {
            if (isGrimMode() && isMoving()) return placedThisTick < 1;
            return true;
        }

        if (isGrimMode() && isMoving() && placedThisTick >= 1) return false;
        if (blocksPerTick > 0 && placedThisTick >= blocksPerTick) return false;
        return windowCount < (isGrimMode() ? 4 : 8);
    }

    public void consume() {
        placedThisTick++;
        windowCount++;
    }

    public int placed() {
        return placedThisTick;
    }

    private static boolean isMoving() {
        if (mc.player == null) return false;
        double vx = mc.player.getVelocity().x;
        double vz = mc.player.getVelocity().z;
        return (vx * vx + vz * vz) > 0.000025;
    }

    private static boolean isGrimMode() {
        AntiCheatModule antiCheat = Anatorius.MODULE_MANAGER.getModule(AntiCheatModule.class);
        return antiCheat != null && antiCheat.movementSync.getValue();
    }
}
