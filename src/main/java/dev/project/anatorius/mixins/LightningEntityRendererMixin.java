package dev.project.anatorius.mixins;

import com.llamalad7.mixinextras.sugar.Local;
import dev.project.anatorius.Anatorius;
import dev.project.anatorius.modules.impl.visuals.DeathEffectsModule;
import dev.project.anatorius.utils.graphics.TintedVertexConsumer;
import dev.project.anatorius.utils.mixins.ILightningColor;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.LightningEntityRenderer;
import net.minecraft.client.render.entity.state.LightningEntityRenderState;
import net.minecraft.entity.LightningEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightningEntityRenderer.class)
public class LightningEntityRendererMixin {
    @Inject(method = "updateRenderState(Lnet/minecraft/entity/LightningEntity;Lnet/minecraft/client/render/entity/state/LightningEntityRenderState;F)V", at = @At("TAIL"))
    private void updateRenderState(LightningEntity entity, LightningEntityRenderState state, float tickProgress, CallbackInfo info) {
        int color = 0;
        if (entity.getId() == DeathEffectsModule.BOLT_ID && Anatorius.MODULE_MANAGER != null) {
            DeathEffectsModule module = Anatorius.MODULE_MANAGER.getModule(DeathEffectsModule.class);
            if (module != null) color = module.lightningColor.getColor().getRGB();
        }
        ((ILightningColor) state).anatorius$setColor(color);
    }

    @ModifyArg(method = "render(Lnet/minecraft/client/render/entity/state/LightningEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;submitCustom(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/RenderLayer;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue$Custom;)V"),
            index = 2)
    private OrderedRenderCommandQueue.Custom tint(OrderedRenderCommandQueue.Custom original, @Local(argsOnly = true) LightningEntityRenderState state) {
        int color = ((ILightningColor) state).anatorius$getColor();
        if (color == 0) return original;
        return (entry, consumer) -> original.render(entry, new TintedVertexConsumer(consumer, color));
    }
}
