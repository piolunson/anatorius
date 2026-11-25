package dev.project.anatorius.mixins;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.modules.impl.visuals.EntityModifierModule;
import dev.project.anatorius.utils.mixins.IEndCrystalEntityRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.model.EndCrystalEntityModel;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EndCrystalEntityRenderer;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.render.entity.state.EndCrystalEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(EndCrystalEntityRenderer.class)
public class EndCrystalEntityRendererMixin implements IEndCrystalEntityRenderer {
    @Mutable @Shadow @Final private static RenderLayer END_CRYSTAL;

    @Shadow @Final private static Identifier TEXTURE;
    @Shadow @Final private EndCrystalEntityModel model;

    @Override
    public EndCrystalEntityModel anatorius$getModel() {
        return model;
    }

    @Inject(method = "render(Lnet/minecraft/client/render/entity/state/EndCrystalEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V", at = @At("HEAD"))
    private void render$HEAD(EndCrystalEntityRenderState endCrystalEntityRenderState, MatrixStack matrixStack, OrderedRenderCommandQueue queue, CameraRenderState cameraRenderState, CallbackInfo info) {
        if (Anatorius.MODULE_MANAGER.getModule(EntityModifierModule.class).isToggled() && Anatorius.MODULE_MANAGER.getModule(EntityModifierModule.class).crystals.getValue()) {
            END_CRYSTAL = net.minecraft.client.render.RenderLayers.entityTranslucent(TEXTURE);
            return;
        }

        END_CRYSTAL = net.minecraft.client.render.RenderLayers.entityCutoutNoCull(TEXTURE);
    }

    @ModifyArgs(method = "render(Lnet/minecraft/client/render/entity/state/EndCrystalEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;scale(FFF)V", ordinal = 0))
    private void render$scale(Args args) {
        if (Anatorius.MODULE_MANAGER.getModule(EntityModifierModule.class).isToggled() && Anatorius.MODULE_MANAGER.getModule(EntityModifierModule.class).crystals.getValue()) {
            args.set(0, 2.0F * Anatorius.MODULE_MANAGER.getModule(EntityModifierModule.class).crystalScale.getValue().floatValue());
            args.set(1, 2.0F * Anatorius.MODULE_MANAGER.getModule(EntityModifierModule.class).crystalScale.getValue().floatValue());
            args.set(2, 2.0F * Anatorius.MODULE_MANAGER.getModule(EntityModifierModule.class).crystalScale.getValue().floatValue());
        }
    }

    @Inject(method = "getYOffset", at = @At(value = "HEAD"), cancellable = true)
    private static void getYOffset(float f, CallbackInfoReturnable<Float> info) {
        if (Anatorius.MODULE_MANAGER.getModule(EntityModifierModule.class).isToggled() && Anatorius.MODULE_MANAGER.getModule(EntityModifierModule.class).crystals.getValue()) {
            float bounce = MathHelper.sin(f * 0.2F) / 2.0F + 0.5F;
            bounce = (bounce * bounce + bounce) * 0.4F * Anatorius.MODULE_MANAGER.getModule(EntityModifierModule.class).crystalBounce.getValue().floatValue();

            info.setReturnValue(bounce - 1.4F);
        }
    }
}
