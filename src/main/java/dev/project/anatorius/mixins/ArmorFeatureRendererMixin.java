package dev.project.anatorius.mixins;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.modules.impl.visuals.NoRenderModule;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ArmorFeatureRenderer.class)
public abstract class ArmorFeatureRendererMixin<S extends BipedEntityRenderState, M extends BipedEntityModel<S>, A extends BipedEntityModel<S>> {
    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/BipedEntityRenderState;FF)V", at = @At("HEAD"), cancellable = true)
    private void renderArmor(MatrixStack matrixStack, OrderedRenderCommandQueue queue, int i, S bipedEntityRenderState, float f, float g, CallbackInfo info) {
        NoRenderModule module = Anatorius.MODULE_MANAGER.getModule(NoRenderModule.class);
        if (module.isToggled() && module.armor.getValue()) {
            info.cancel();
        }
    }
}
