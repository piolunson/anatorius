package dev.project.anatorius.utils.pathing;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;

public final class BaritoneBridge {
    private static Boolean available;

    private BaritoneBridge() {
    }

    public static boolean available() {
        if (available == null) {
            boolean loaded = false;
            try {
                loaded = FabricLoader.getInstance().isModLoaded("baritone");
            } catch (Throwable ignored) {
            }
            available = loaded;
        }
        return available;
    }

    public static boolean goTo(BlockPos pos) {
        if (!available()) return false;
        try {
            return Impl.goTo(pos);
        } catch (Throwable t) {
            available = false;
            return false;
        }
    }

    public static boolean follow(Entity entity, int radius) {
        if (!available()) return false;
        try {
            return Impl.follow(entity, radius);
        } catch (Throwable t) {
            available = false;
            return false;
        }
    }

    public static boolean elytraTo(BlockPos pos) {
        if (!available()) return false;
        try {
            return Impl.elytraTo(pos);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean elytraActive() {
        if (!available()) return false;
        try {
            return Impl.elytraActive();
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isPathing() {
        if (!available()) return false;
        try {
            return Impl.isPathing();
        } catch (Throwable t) {
            return false;
        }
    }

    public static void stop() {
        if (!available()) return;
        try {
            Impl.stop();
        } catch (Throwable ignored) {
        }
    }

    private static final class Impl {
        private static boolean configured;
        private static BlockPos lastGoal;
        private static Entity lastFollow;

        private static baritone.api.IBaritone baritone() {
            baritone.api.IBaritone b = baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone();
            if (!configured) {
                baritone.api.Settings s = baritone.api.BaritoneAPI.getSettings();
                s.allowBreak.value = false;
                s.allowPlace.value = false;
                s.allowInventory.value = false;
                s.chatDebug.value = false;
                s.logAsToast.value = false;
                s.allowWaterBucketFall.value = false;
                s.assumeWalkOnWater.value = false;
                if (!s.blocksToAvoid.value.contains(net.minecraft.block.Blocks.WATER)) {
                    s.blocksToAvoid.value.add(net.minecraft.block.Blocks.WATER);
                    s.blocksToAvoid.value.add(net.minecraft.block.Blocks.LAVA);
                }
                s.elytraAllowAboveBuildLimit.value = false;
                s.elytraAutoSwap.value = false;
                configured = true;
            }
            return b;
        }

        static boolean goTo(BlockPos pos) {
            baritone.api.IBaritone b = baritone();
            if (pos.equals(lastGoal) && b.getPathingBehavior().isPathing()) return true;
            lastGoal = pos.toImmutable();
            lastFollow = null;
            b.getCustomGoalProcess().setGoalAndPath(new baritone.api.pathing.goals.GoalBlock(pos));
            return true;
        }

        static boolean follow(Entity entity, int radius) {
            baritone.api.IBaritone b = baritone();
            baritone.api.BaritoneAPI.getSettings().followRadius.value = radius;
            if (entity == lastFollow && b.getFollowProcess().isActive()) return true;
            lastFollow = entity;
            lastGoal = null;
            b.getFollowProcess().follow(e -> e == entity);
            return true;
        }

        static boolean elytraTo(BlockPos pos) {
            baritone.api.IBaritone b = baritone();
            baritone.api.process.IElytraProcess elytra = b.getElytraProcess();
            if (!elytra.isLoaded()) return false;
            baritone.api.BaritoneAPI.getSettings().elytraTermsAccepted.value = true;
            baritone.api.BaritoneAPI.getSettings().elytraAutoJump.value = false;
            BlockPos current = elytra.currentDestination();
            if (current != null && current.equals(pos)) return true;
            lastGoal = null;
            lastFollow = null;
            elytra.pathTo(pos);
            return true;
        }

        static boolean elytraActive() {
            return baritone().getElytraProcess().isActive();
        }

        static boolean isPathing() {
            return baritone().getPathingBehavior().isPathing();
        }

        static void stop() {
            lastGoal = null;
            lastFollow = null;
            baritone().getPathingBehavior().cancelEverything();
        }
    }
}
