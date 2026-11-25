package dev.project.anatorius.mixins;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.modules.impl.visuals.EntityModifierModule;
import dev.project.anatorius.modules.impl.visuals.NoRenderModule;
import dev.project.anatorius.utils.IMinecraft;
import dev.project.anatorius.utils.mixins.ILivingEntity;
import dev.project.anatorius.utils.mixins.ILivingEntityRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, S extends LivingEntityRenderState, M extends EntityModel<? super S>> extends EntityRenderer<T, S> implements ILivingEntityRenderer, IMinecraft {
    @Shadow protected M model;

    @Shadow public abstract int getMixColor(S state);

    @Shadow public abstract float getAnimationCounter(S state);

    @Shadow @Nullable public abstract RenderLayer getRenderLayer(S state, boolean showBody, boolean translucent, boolean showOutline);

    @Shadow public abstract boolean isVisible(S state);

    @Shadow public abstract void setupTransforms(S state, MatrixStack matrices, float animationProgress, float bodyYaw);

    @Shadow public abstract void scale(S state, MatrixStack matrices);

    @Shadow
    private static float clampBodyYaw(LivingEntity entity, float degrees, float tickDelta) {
        return 0;
    }

    @Shadow
    public abstract boolean shouldFlipUpsideDown(T entity);

    public LivingEntityRendererMixin(EntityRendererFactory.Context context) { super(context); }

    @Inject(method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V", at = @At("HEAD"), cancellable = true)
    private void render(S livingEntityRenderState, MatrixStack matrixStack, OrderedRenderCommandQueue queue, CameraRenderState cameraRenderState, CallbackInfo info) {
        NoRenderModule noRender = Anatorius.MODULE_MANAGER.getModule(NoRenderModule.class);
        if (noRender.isToggled() && noRender.corpses.getValue() && livingEntityRenderState.deathTime > 0) {
            info.cancel();
        }
    }

    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/RenderLayer;IIILnet/minecraft/client/texture/Sprite;ILnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;)V"))
    private void render$playerModel(OrderedRenderCommandQueue queue, net.minecraft.client.model.Model<?> model, Object state, MatrixStack matrices,
                                    RenderLayer layer, int light, int overlay, int tint, net.minecraft.client.texture.Sprite sprite, int outline,
                                    net.minecraft.client.render.command.ModelCommandRenderer.CrumblingOverlayCommand crumbling,
                                    com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        dev.project.anatorius.utils.playermodels.PlayerModelMesh mesh = anatorius$playerModel(state);
        if (mesh != null && model instanceof net.minecraft.client.render.entity.model.PlayerEntityModel playerModel
                && state instanceof net.minecraft.client.render.entity.state.PlayerEntityRenderState playerState) {
            playerModel.setAngles(playerState);
            dev.project.anatorius.utils.playermodels.PlayerModelRenderer.submit(mesh, playerModel, matrices, queue, light, overlay, tint);
            return;
        }
        original.call(queue, model, state, matrices, layer, light, overlay, tint, sprite, outline, crumbling);
    }

    @Inject(method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/model/EntityModel;setAngles(Ljava/lang/Object;)V", shift = At.Shift.AFTER))
    private void render$playerModelArms(S state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera, CallbackInfo info) {
        dev.project.anatorius.utils.playermodels.PlayerModelMesh mesh = anatorius$playerModel(state);
        if (mesh != null && this.model instanceof net.minecraft.client.render.entity.model.PlayerEntityModel playerModel) {
            dev.project.anatorius.utils.playermodels.PlayerModelRenderer.moveArmsForItems(mesh, playerModel);
        }
    }

    @com.llamalad7.mixinextras.injector.v2.WrapWithCondition(method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/feature/FeatureRenderer;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/EntityRenderState;FF)V"))
    private boolean render$playerModelFeatures(net.minecraft.client.render.entity.feature.FeatureRenderer<?, ?> feature, MatrixStack matrices,
                                               OrderedRenderCommandQueue queue, int light, net.minecraft.client.render.entity.state.EntityRenderState state,
                                               float limbAngle, float limbDistance) {
        if (anatorius$playerModel(state) == null) return true;
        return !(feature instanceof net.minecraft.client.render.entity.feature.ArmorFeatureRenderer<?, ?, ?>
                || feature instanceof net.minecraft.client.render.entity.feature.CapeFeatureRenderer
                || feature instanceof net.minecraft.client.render.entity.feature.HeadFeatureRenderer<?, ?>
                || feature instanceof net.minecraft.client.render.entity.feature.Deadmau5FeatureRenderer);
    }

    @org.spongepowered.asm.mixin.Unique
    private static dev.project.anatorius.utils.playermodels.PlayerModelMesh anatorius$playerModel(Object state) {
        return state instanceof dev.project.anatorius.utils.mixins.IPlayerModelState s ? s.anatorius$getPlayerModel() : null;
    }

    @org.spongepowered.asm.mixin.injection.ModifyVariable(method = "updateRenderState", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float updateRenderState$tickDelta(float f, T livingEntity) {
        return ((ILivingEntity) livingEntity).anatorius$isStaticPlayerEntity() ? 1.0F : f;
    }

    @Inject(method = "updateRenderState", at = @At("TAIL"))
    private void updateRenderState$rotations(T livingEntity, S livingEntityRenderState, float f, CallbackInfo info) {
        if (livingEntity == mc.player && Anatorius.ROTATION_MANAGER.inRenderTime()) {
            float[] renderRotations = Anatorius.ROTATION_MANAGER.getRenderRotations();
            livingEntityRenderState.bodyYaw = renderRotations[0];
            livingEntityRenderState.relativeHeadYaw = 0.0F;
            livingEntityRenderState.pitch = livingEntityRenderState.flipUpsideDown ? -renderRotations[1] : renderRotations[1];
        }
    }

    @Override
    public void anatorius$render(LivingEntityRenderState livingEntityRenderState, MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider, int i) {
        NoRenderModule noRender = Anatorius.MODULE_MANAGER.getModule(NoRenderModule.class);
        if (noRender.isToggled() && noRender.corpses.getValue() && livingEntityRenderState.deathTime > 0) return;

        matrixStack.push();

        if (livingEntityRenderState.isInPose(EntityPose.SLEEPING)) {
            Direction direction = livingEntityRenderState.sleepingDirection;
            if (direction != null) {
                float f = livingEntityRenderState.standingEyeHeight - 0.1F;
                matrixStack.translate((float)(-direction.getOffsetX()) * f, 0.0F, (float)(-direction.getOffsetZ()) * f);
            }
        }

        float g = livingEntityRenderState.baseScale;
        matrixStack.scale(g, g, g);
        this.setupTransforms((S) livingEntityRenderState, matrixStack, livingEntityRenderState.bodyYaw, g);
        matrixStack.scale(-1.0F, -1.0F, 1.0F);
        this.scale((S) livingEntityRenderState, matrixStack);
        matrixStack.translate(0.0F, -1.501F, 0.0F);
        this.model.setAngles((S) livingEntityRenderState);
        boolean bl = this.isVisible((S) livingEntityRenderState);
        boolean bl2 = !bl && !livingEntityRenderState.invisibleToPlayer;

        RenderLayer renderLayer = this.getRenderLayer((S) livingEntityRenderState, bl, bl2, livingEntityRenderState.hasOutline());
        if (renderLayer != null) {
            VertexConsumer vertexConsumer = vertexConsumerProvider.getBuffer(renderLayer);
            int j = LivingEntityRenderer.getOverlay(livingEntityRenderState, this.getAnimationCounter((S) livingEntityRenderState));
            int k = bl2 ? 654311423 : -1;
            int l = ColorHelper.mix(k, this.getMixColor((S) livingEntityRenderState));
            this.model.render(matrixStack, vertexConsumer, i, j, l);
        }

        matrixStack.pop();
    }
}
