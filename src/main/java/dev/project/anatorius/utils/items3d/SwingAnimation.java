package dev.project.anatorius.utils.items3d;

import dev.project.anatorius.modules.impl.visuals.Items3DModule;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

public final class SwingAnimation {
    private SwingAnimation() { }

    public static boolean apply(Items3DModule m, MatrixStack matrices, Arm arm, float swingProgress, ItemStack stack) {
        ItemMotion.Profile profile = ItemMotion.profileOf(stack);
        if (profile != ItemMotion.Profile.SWORD && profile != ItemMotion.Profile.AXE && profile != ItemMotion.Profile.TOOL
                && profile != ItemMotion.Profile.MACE) return false;
        float p = MathHelper.clamp(swingProgress * Math.max(1f, Items3DModule.SWING_SPEED), 0f, 1f);
        int i = arm == Arm.RIGHT ? 1 : -1;

        switch (profile) {
            case SWORD -> {
                float f = MathHelper.sin(p * p * MathHelper.PI);
                float g = MathHelper.sin(MathHelper.sqrt(p) * MathHelper.PI);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(i * (45f + f * -32f)));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(i * g * -34f));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(g * -72f));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(i * -45f));
            }
            case AXE, MACE -> {
                float q = (float) Math.pow(p, 1.35);
                float g = MathHelper.sin(MathHelper.sqrt(q) * MathHelper.PI);
                float windUp = MathHelper.sin(MathHelper.clamp(p / 0.18f, 0f, 1f) * MathHelper.PI) * (1f - q);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(i * (45f + g * -14f)));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(i * g * -12f));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(g * -100f + windUp * 14f));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(i * -45f));
            }
            default -> {
                float f = MathHelper.sin(p * p * MathHelper.PI);
                float g = MathHelper.sin(MathHelper.sqrt(p) * MathHelper.PI);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(i * (45f + f * -10f)));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(i * g * -8f));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(g * -62f));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(i * -45f));
            }
        }
        return true;
    }
}
