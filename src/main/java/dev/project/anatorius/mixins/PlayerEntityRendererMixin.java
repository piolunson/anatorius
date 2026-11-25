package dev.project.anatorius.mixins;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.utils.IMinecraft;
import net.minecraft.entity.PlayerLikeEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.project.anatorius.utils.mixins.IPlayerModelState;
import dev.project.anatorius.utils.playermodels.PlayerModelMesh;
import dev.project.anatorius.utils.playermodels.PlayerModelRenderer;
import dev.project.anatorius.utils.playermodels.PlayerModels;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

@Mixin(PlayerEntityRenderer.class)
public class PlayerEntityRendererMixin implements IMinecraft {
    @Inject(method = "updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V", at = @At("RETURN"))
    private void updateRenderState$playerModel(PlayerLikeEntity player, PlayerEntityRenderState state, float tickDelta, CallbackInfo info) {
        ((IPlayerModelState) state).anatorius$setPlayerModel(Anatorius.MODULE_MANAGER == null ? null : PlayerModels.forPlayer(player));
    }

    @Inject(method = "updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V", at = @At("RETURN"))
    private void updateRenderState$fakeFly(PlayerLikeEntity player, PlayerEntityRenderState state, float tickDelta, CallbackInfo info) {
        if (!dev.project.anatorius.modules.impl.movement.FakeFlyModule.stands(player)) return;
        state.isGliding = false;
        state.glidingTicks = 0.0F;
        state.limbAmplitudeInverse = 1.0F;
    }

    @Inject(method = "renderArm", at = @At("HEAD"), cancellable = true)
    private void renderArm$playerModel(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, Identifier skinTexture, ModelPart arm,
                                       boolean sleeveVisible, CallbackInfo info) {
        if (Anatorius.MODULE_MANAGER == null) return;
        PlayerModelMesh mesh = PlayerModels.forSelf();
        if (mesh == null) return;
        var model = ((PlayerEntityRenderer<?>) (Object) this).getModel();
        boolean right = arm == model.rightArm;
        arm.resetTransform();
        arm.visible = true;
        arm.roll = right ? 0.1F : -0.1F;
        PlayerModelRenderer.submitArm(mesh, right, arm, matrices, queue, light);
        info.cancel();
    }
}
