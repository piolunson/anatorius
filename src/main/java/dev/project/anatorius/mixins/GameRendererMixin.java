package dev.project.anatorius.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.impl.RenderEntityEvent;
import dev.project.anatorius.events.impl.RenderShaderEvent;
import dev.project.anatorius.events.impl.RenderWorldEvent;
import dev.project.anatorius.modules.impl.miscellaneous.FOVModifierModule;
import dev.project.anatorius.modules.impl.visuals.AspectRatioModule;
import dev.project.anatorius.modules.impl.visuals.FreecamModule;
import dev.project.anatorius.modules.impl.visuals.NoRenderModule;
import dev.project.anatorius.modules.impl.visuals.ZoomModule;
import dev.project.anatorius.utils.graphics.Renderer2D;
import dev.project.anatorius.utils.graphics.Renderer3D;
import dev.project.anatorius.utils.minecraft.WorldUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.profiler.Profilers;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Shadow @Final private MinecraftClient client;

    @Shadow private float viewDistanceBlocks;

    @Inject(method = "renderWorld", at = @At("HEAD"))
    private void renderWorld$HEAD(RenderTickCounter tickCounter, CallbackInfo info) {
        Renderer3D.prepare();
    }

    @Inject(method = "renderWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;render(Lnet/minecraft/client/util/memory/ObjectAllocator;Lnet/minecraft/client/render/RenderTickCounter;ZLnet/minecraft/client/render/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V", shift = At.Shift.AFTER))
    private void renderWorld$swap(RenderTickCounter tickCounter, CallbackInfo info, @Local(ordinal = 1) Matrix4f matrix4f3, @Local(ordinal = 0) float tickDelta, @Local MatrixStack matrixStack) {
        RenderSystem.getModelViewStack().pushMatrix();

        RenderSystem.getModelViewStack().mul(matrix4f3);
        RenderSystem.getModelViewStack().mul(matrixStack.peek().getPositionMatrix().invert());

        Anatorius.EVENT_HANDLER.post(new RenderWorldEvent(matrixStack, tickDelta));

        Renderer3D.drawDepthTested(Renderer3D.DEPTH_QUADS, Renderer3D.DEPTH_DEBUG_LINES, false);
        Renderer3D.drawDepthTested(Renderer3D.DEPTH_SHINE_QUADS, Renderer3D.DEPTH_SHINE_DEBUG_LINES, true);
        Renderer3D.draw(Renderer3D.QUADS, Renderer3D.DEBUG_LINES, false);
        Renderer3D.draw(Renderer3D.SHINE_QUADS, Renderer3D.SHINE_DEBUG_LINES, true);

        Anatorius.EVENT_HANDLER.post(new RenderWorldEvent.Post(matrixStack, tickDelta));
        Anatorius.EVENT_HANDLER.post(new RenderEntityEvent.Post());

        RenderSystem.getModelViewStack().popMatrix();
    }

    @Inject(method = "renderWorld", at = @At("TAIL"))
    private void renderWorld$TAIL(RenderTickCounter renderTickCounter, CallbackInfo info) {
        Anatorius.EVENT_HANDLER.post(new RenderShaderEvent.Post());
    }

    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
    private void tiltViewWhenHurt(CallbackInfo info) {
        if (Anatorius.MODULE_MANAGER.getModule(NoRenderModule.class).isToggled() && Anatorius.MODULE_MANAGER.getModule(NoRenderModule.class).hurtCamera.getValue()) {
            info.cancel();
        }
    }

    @Inject(method = "updateCrosshairTarget", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getCrosshairTarget(FLnet/minecraft/entity/Entity;)Lnet/minecraft/util/hit/HitResult;"), cancellable = true)
    private void updateCrosshairTarget(float tickDelta, CallbackInfo info) {
        if (Anatorius.MODULE_MANAGER.getModule(FreecamModule.class).isToggled()) {
            Profilers.get().pop();
            client.crosshairTarget = WorldUtils.getRaytraceTarget(Anatorius.MODULE_MANAGER.getModule(FreecamModule.class).getFreeYaw(), Anatorius.MODULE_MANAGER.getModule(FreecamModule.class).getFreePitch(), Anatorius.MODULE_MANAGER.getModule(FreecamModule.class).getFreeX(), Anatorius.MODULE_MANAGER.getModule(FreecamModule.class).getFreeY(), Anatorius.MODULE_MANAGER.getModule(FreecamModule.class).getFreeZ());
            info.cancel();
        }
    }

    @Inject(method = "showFloatingItem", at = @At("HEAD"), cancellable = true)
    private void showFloatingItem(ItemStack floatingItem, CallbackInfo info) {
        if (Anatorius.MODULE_MANAGER.getModule(NoRenderModule.class).isToggled() && Anatorius.MODULE_MANAGER.getModule(NoRenderModule.class).totemAnimation.getValue()) {
            info.cancel();
        }
    }

    @Inject(method = "renderWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;render(Lnet/minecraft/client/util/memory/ObjectAllocator;Lnet/minecraft/client/render/RenderTickCounter;ZLnet/minecraft/client/render/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V", shift = At.Shift.AFTER))
    private void renderWorld(RenderTickCounter tickCounter, CallbackInfo info, @Local(ordinal = 1) Matrix4f positionMatrix, @Local(ordinal = 0) Matrix4f projectionMatrix) {
        Renderer2D.LAST_PROJECTION_MATRIX.set(projectionMatrix);
        Renderer2D.LAST_MODEL_MATRIX.set(RenderSystem.getModelViewMatrix());
        Renderer2D.LAST_WORLD_MATRIX.set(positionMatrix);
    }

    @Inject(method = "getFov", at = @At("TAIL"), cancellable = true)
    private void getFOV(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Float> info) {
        FOVModifierModule module = Anatorius.MODULE_MANAGER.getModule(FOVModifierModule.class);
        if(module.isToggled()) {
            if(info.getReturnValue() == 70 && !module.items.getValue()) return;
            info.setReturnValue(module.fov.getValue().floatValue());
        }
        if (changingFov) {
            double zoom = ZoomModule.zoom();
            if (zoom != 1.0) info.setReturnValue((float) (info.getReturnValue() / zoom));
        }
    }

    @Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
    private void renderHand(float tickProgress, boolean sleeping, Matrix4f positionMatrix, CallbackInfo info) {
        if (ZoomModule.hidesHand()) info.cancel();
    }

    @Inject(method = "getBasicProjectionMatrix",at = @At("TAIL"), cancellable = true)
    public void getBasicProjectionMatrix(float fovDegrees, CallbackInfoReturnable<Matrix4f> info) {
        if (Anatorius.MODULE_MANAGER.getModule(AspectRatioModule.class).isToggled()) {
            MatrixStack matrixStack = new MatrixStack();
            matrixStack.peek().getPositionMatrix().identity();

            matrixStack.peek().getPositionMatrix().mul(new Matrix4f().setPerspective((float)(fovDegrees * 0.01745329238474369), Anatorius.MODULE_MANAGER.getModule(AspectRatioModule.class).ratio.getValue().floatValue(), 0.05f, viewDistanceBlocks * 4.0f));
            info.setReturnValue(matrixStack.peek().getPositionMatrix());
        }
    }
}
