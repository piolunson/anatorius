package dev.project.anatorius.utils.items3d;

import dev.project.anatorius.modules.impl.visuals.Items3DModule;
import dev.project.anatorius.utils.IMinecraft;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.TorchBlock;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

public final class ItemMotion implements IMinecraft {
    public static final ItemMotion INSTANCE = new ItemMotion();

    public enum Profile { DEFAULT, SWORD, AXE, TOOL, MACE, LANTERN, TORCH, TOTEM, CRYSTAL }

    private record Pose(float tx, float ty, float tz, float rx, float ry, float rz) {
        static final Pose ZERO = new Pose(0, 0, 0, 0, 0, 0);

        Pose lerp(Pose o, float t) {
            return new Pose(MathHelper.lerp(t, tx, o.tx), MathHelper.lerp(t, ty, o.ty), MathHelper.lerp(t, tz, o.tz),
                    MathHelper.lerp(t, rx, o.rx), MathHelper.lerp(t, ry, o.ry), MathHelper.lerp(t, rz, o.rz));
        }
    }

    private static Pose poseOf(Profile p) {
        return switch (p) {
            case SWORD -> new Pose(0f, 0.01f, 0f, -6f, 4f, 3f);
            case AXE -> new Pose(0f, -0.03f, 0.02f, 10f, 0f, -4f);
            case TOOL -> new Pose(0f, -0.01f, 0f, 5f, 0f, 0f);
            case MACE -> new Pose(0f, -0.04f, 0.03f, 14f, 0f, -6f);
            case LANTERN -> new Pose(0f, -0.06f, 0f, -8f, 0f, 0f);
            case TORCH -> new Pose(0f, 0.02f, 0f, -4f, 0f, 2f);
            case TOTEM -> new Pose(0f, 0.03f, 0f, 0f, 12f, 0f);
            case CRYSTAL -> new Pose(0f, 0.04f, 0f, 0f, 0f, 0f);
            default -> Pose.ZERO;
        };
    }

    public static Profile profileOf(ItemStack stack) {
        if (stack.isIn(ItemTags.SWORDS)) return Profile.SWORD;
        if (stack.isIn(ItemTags.AXES)) return Profile.AXE;
        if (stack.isIn(ItemTags.PICKAXES) || stack.isIn(ItemTags.SHOVELS) || stack.isIn(ItemTags.HOES)) return Profile.TOOL;
        if (stack.isOf(Items.MACE)) return Profile.MACE;
        if (stack.getItem() instanceof BlockItem bi) {
            if (bi.getBlock() instanceof LanternBlock) return Profile.LANTERN;
            if (bi.getBlock() instanceof TorchBlock) return Profile.TORCH;
        }
        if (stack.isOf(Items.TOTEM_OF_UNDYING)) return Profile.TOTEM;
        if (stack.isOf(Items.END_CRYSTAL)) return Profile.CRYSTAL;
        return Profile.DEFAULT;
    }

    private static final float STEP = 1f / 240f;
    private static final float LOW_STEP = 1f / 120f;
    private static final float MAX_FRAME = 0.1f;

    private long lastNanos;
    private boolean primed;
    private float lastYaw, lastPitch;
    private double lastVx, lastVy, lastVz;
    private boolean wasOnGround = true;

    private float inYaw, inYawV, inPitch, inPitchV;
    private float bobPhase, bobAmp;
    private float land, landV;
    private float penRoll, penRollV, penPitch, penPitchV;
    private float crystalTicks, floatTime;
    private long clockNanos;
    private boolean degraded;
    private long degradedSince, recoveredSince;

    private final Profile[] profile = {Profile.DEFAULT, Profile.DEFAULT};
    private final Pose[] from = {Pose.ZERO, Pose.ZERO};
    private final float[] blend = {1f, 1f};

    public void reset() {
        primed = false;
        clockNanos = 0L;
        inYaw = inYawV = inPitch = inPitchV = 0;
        bobAmp = 0;
        land = landV = 0;
        penRoll = penRollV = penPitch = penPitchV = 0;
        degraded = false;
        profile[0] = profile[1] = Profile.DEFAULT;
        from[0] = from[1] = Pose.ZERO;
        blend[0] = blend[1] = 1f;
    }

    public boolean degraded() {
        return degraded;
    }

    public float crystalTicks() { return crystalTicks; }
    public float floatTime() { return floatTime; }

    public void advanceClock(Items3DModule m) {
        long now = System.nanoTime();
        if (clockNanos != 0L) {
            float dt = Math.min(MAX_FRAME, (now - clockNanos) / 1.0e9f);
            float speed = m.speed();
            crystalTicks += dt * 20f * Items3DModule.CRYSTAL_SPEED * speed;
            floatTime += dt * speed;
            if (crystalTicks > 1.0e6f) crystalTicks -= 1.0e6f;
            if (floatTime > 1.0e5f) floatTime -= 1.0e5f;
        }
        clockNanos = now;
    }

    public void update(Items3DModule m, ClientPlayerEntity player, float tickDelta) {
        long now = System.nanoTime();
        if (primed && now - lastNanos < 250_000L) return;
        float dt = primed ? Math.min(MAX_FRAME, (now - lastNanos) / 1.0e9f) : 0f;
        lastNanos = now;

        float yaw = player.getYaw(tickDelta), pitch = player.getPitch(tickDelta);
        Vec3d v = player.getVelocity();
        if (!primed) {
            lastYaw = yaw; lastPitch = pitch;
            lastVx = v.x; lastVy = v.y; lastVz = v.z;
            primed = true;
            return;
        }
        updateProtection(m, now);
        advanceClock(m);
        if (dt <= 0f) return;

        float yawRate = MathHelper.wrapDegrees(yaw - lastYaw) / dt, pitchRate = (pitch - lastPitch) / dt;
        lastYaw = yaw;
        lastPitch = pitch;

        double ax = (v.x - lastVx) * 20.0, az = (v.z - lastVz) * 20.0;
        double rad = Math.toRadians(yaw);
        float aSide = (float) (ax * Math.cos(rad) + az * Math.sin(rad));
        float aFwd = (float) (-ax * Math.sin(rad) + az * Math.cos(rad));
        boolean onGround = player.isOnGround();
        if (onGround && !wasOnGround && lastVy < -0.2) landV += (float) Math.min(1.2, -lastVy) * 1.4f;
        wasOnGround = onGround;
        lastVx = v.x; lastVy = v.y; lastVz = v.z;

        float horizontal = (float) Math.sqrt(v.x * v.x + v.z * v.z);
        float targetAmp = MathHelper.clamp(horizontal / 0.28f, 0f, 1.3f) * (onGround ? 1f : 0.3f);
        bobAmp += (targetAmp - bobAmp) * (1f - (float) Math.exp(-6f * dt));
        bobPhase = (bobPhase + dt * MathHelper.TAU * (1.4f + 0.9f * bobAmp)) % (MathHelper.TAU * 64f);

        float k = m.physicsStrength.getValue().floatValue() / 100f;
        float inTargetYaw = MathHelper.clamp(-yawRate * 0.012f * k, -9f, 9f);
        float inTargetPitch = MathHelper.clamp(-pitchRate * 0.010f * k, -7f, 7f);
        float lanternK = Items3DModule.PENDULUM_STRENGTH * k / 0.20f;
        float damp = 1.2f + 7f * Items3DModule.DAMPING;
        float maxRad = (float) Math.toRadians(Items3DModule.MAX_ROTATION_DEG);

        float step = degraded ? LOW_STEP : STEP;
        for (float t = 0; t < dt; t += step) {
            float h = Math.min(step, dt - t);
            inYawV += (120f * (inTargetYaw - inYaw) - 18f * inYawV) * h; inYaw += inYawV * h;
            inPitchV += (120f * (inTargetPitch - inPitch) - 18f * inPitchV) * h; inPitch += inPitchV * h;
            landV += (-90f * land - 11f * landV) * h; land += landV * h;
            float drive = MathHelper.sin(bobPhase * 0.5f) * bobAmp * 30f;
            penRollV += (-28f * MathHelper.sin(penRoll) - damp * penRollV + lanternK * (aSide * 2.2f + yawRate * 0.02f + drive)) * h;
            penRoll = MathHelper.clamp(penRoll + penRollV * h, -maxRad, maxRad);
            penPitchV += (-28f * MathHelper.sin(penPitch) - damp * penPitchV + lanternK * (-aFwd * 2.2f + pitchRate * 0.015f)) * h;
            penPitch = MathHelper.clamp(penPitch + penPitchV * h, -maxRad, maxRad);
        }
        if (Math.abs(penRoll) >= maxRad) penRollV *= 0.5f;
        if (Math.abs(penPitch) >= maxRad) penPitchV *= 0.5f;

        for (int hand = 0; hand < 2; hand++) {
            blend[hand] = Math.min(1f, blend[hand] + dt / 0.18f);
        }
    }

    private void updateProtection(Items3DModule m, long now) {
        if (!Items3DModule.LOW_FPS_PROTECTION) { degraded = false; return; }
        int fps = mc.getCurrentFps(), target = Items3DModule.TARGET_FPS;
        if (!degraded) {
            if (fps < target) { if (degradedSince == 0) degradedSince = now; } else degradedSince = 0;
            if (degradedSince != 0 && now - degradedSince > 1_500_000_000L) { degraded = true; recoveredSince = 0; }
        } else {
            if (fps > target * 1.15f) { if (recoveredSince == 0) recoveredSince = now; } else recoveredSince = 0;
            if (recoveredSince != 0 && now - recoveredSince > 3_000_000_000L) { degraded = false; degradedSince = 0; }
        }
    }

    public void apply(Items3DModule m, MatrixStack matrices, ItemStack stack, boolean mainHand, Arm arm) {
        int hand = mainHand ? 0 : 1;
        Profile p = profileOf(stack);
        if (p != profile[hand]) {
            from[hand] = currentPose(hand);
            profile[hand] = p;
            blend[hand] = Items3DModule.SMOOTH_TRANSITIONS ? 0f : 1f;
        }
        float side = arm == Arm.RIGHT ? 1f : -1f;
        float sway = Items3DModule.HAND_SWAY;

        if (Items3DModule.REALISTIC_HOLDING) {
            Pose pose = currentPose(hand);
            matrices.translate(pose.tx * side, pose.ty, pose.tz);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pose.rx));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(pose.ry * side));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(pose.rz * side));
        }

        float bx = MathHelper.sin(bobPhase) * 0.012f * bobAmp * sway;
        float by = -Math.abs(MathHelper.cos(bobPhase)) * 0.010f * bobAmp * sway;
        matrices.translate(bx * side, by, 0f);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(MathHelper.sin(bobPhase) * 1.6f * bobAmp * sway * side));

        if (m.physics.getValue()) {
            matrices.translate(0f, -land * 0.05f, 0f);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(inYaw));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(inPitch));
        }

        if (p == Profile.LANTERN && Items3DModule.LANTERN_PHYSICS) {
            matrices.multiply(RotationAxis.POSITIVE_Z.rotation(penRoll * side));
            matrices.multiply(RotationAxis.POSITIVE_X.rotation(penPitch));
        } else if (p == Profile.TOTEM) {
            matrices.translate(0f, MathHelper.sin(floatTime * 2.1f) * 0.012f, 0f);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(MathHelper.sin(floatTime * 1.3f) * 4f * side));
        }
    }

    private Pose currentPose(int hand) {
        Pose target = poseOf(profile[hand]);
        float t = blend[hand];
        if (t >= 1f) return target;
        float e = 1f - (1f - t) * (1f - t) * (1f - t);
        return from[hand].lerp(target, e);
    }

    public float lanternRollDegrees() {
        return (float) Math.toDegrees(penRoll);
    }
}
