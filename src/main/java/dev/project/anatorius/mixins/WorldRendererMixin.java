package dev.project.anatorius.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.impl.RenderShaderEvent;
import dev.project.anatorius.modules.impl.visuals.BlockHighlightModule;
import dev.project.anatorius.modules.impl.visuals.FreecamModule;
import dev.project.anatorius.modules.impl.visuals.NoRenderModule;
import dev.project.anatorius.modules.impl.visuals.ShadersModule;
import dev.project.anatorius.utils.graphics.WorldChamsChain;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.DefaultFramebufferSet;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.memory.ObjectAllocator;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
    @Inject(method = "getLightmapCoordinates(Lnet/minecraft/client/render/WorldRenderer$BrightnessGetter;Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;)I", at = @At("HEAD"), cancellable = true)
    private static void xrayLight(WorldRenderer.BrightnessGetter brightnessGetter, net.minecraft.world.BlockRenderView world, net.minecraft.block.BlockState state, net.minecraft.util.math.BlockPos pos, CallbackInfoReturnable<Integer> info) {
        if (dev.project.anatorius.modules.impl.visuals.XrayModule.isActive()) info.setReturnValue(15728880);
    }

    @Inject(method = "updateCamera", at = @At("HEAD"))
    private void captureFrustum(Camera camera, Frustum frustum, boolean spectator, CallbackInfo ci) {
        dev.project.anatorius.utils.graphics.Renderer3D.capturedFrustum = frustum;
    }

    @Inject(method = "render", at = @At(value = "HEAD"))
    private void render(ObjectAllocator allocator, RenderTickCounter tickCounter, boolean renderBlockOutline, Camera camera, Matrix4f positionMatrix, Matrix4f basicProjectionMatrix, Matrix4f projectionMatrix, GpuBufferSlice fogBuffer, Vector4f fogColor, boolean renderSky, CallbackInfo info, @Local(argsOnly = true, ordinal = 0) LocalBooleanRef blockOutline) {
        if (Anatorius.MODULE_MANAGER != null && Anatorius.MODULE_MANAGER.getModule(BlockHighlightModule.class).isToggled()) {
            blockOutline.set(false);
        }
    }

    @ModifyArg(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;updateCamera(Lnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/Frustum;Z)V"), index = 2)
    private boolean render$setupTerrain(boolean spectator) {
        return Anatorius.MODULE_MANAGER.getModule(FreecamModule.class).isToggled() || spectator;
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/profiler/Profiler;swap(Ljava/lang/String;)V", ordinal = 0, shift = At.Shift.BEFORE))
    private void render$swap(ObjectAllocator allocator, RenderTickCounter tickCounter, boolean renderBlockOutline, Camera camera, Matrix4f positionMatrix, Matrix4f basicProjectionMatrix, Matrix4f projectionMatrix, GpuBufferSlice fogBuffer, Vector4f fogColor, boolean renderSky, CallbackInfo info) {
        Anatorius.EVENT_HANDLER.post(new RenderShaderEvent());
    }

    @Inject(method = "hasBlindnessOrDarkness(Lnet/minecraft/client/render/Camera;)Z", at = @At("HEAD"), cancellable = true)
    private void hasBlindnessOrDarkness(Camera camera, CallbackInfoReturnable<Boolean> info) {
        if (Anatorius.MODULE_MANAGER.getModule(NoRenderModule.class).isToggled() && Anatorius.MODULE_MANAGER.getModule(NoRenderModule.class).blindness.getValue()) {
            info.setReturnValue(false);
        }
    }

    @Inject(method = "onResized", at = @At("TAIL"))
    private void onResized(int width, int height, CallbackInfo info) {
        if (Anatorius.SHADER_MANAGER != null) {
            Anatorius.SHADER_MANAGER.resize(width, height);
        }
    }

    @Inject(method = "canDrawEntityOutlines", at = @At("HEAD"), cancellable = true)
    private void anatorius$forceOutlines(CallbackInfoReturnable<Boolean> cir) {
        if (Anatorius.MODULE_MANAGER == null) return;
        ShadersModule module = Anatorius.MODULE_MANAGER.getModule(ShadersModule.class);
        if (module != null && module.wantsChams()) cir.setReturnValue(true);
    }

    @ModifyExpressionValue(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/ShaderLoader;loadPostEffect(Lnet/minecraft/util/Identifier;Ljava/util/Set;)Lnet/minecraft/client/gl/PostEffectProcessor;"))
    private PostEffectProcessor anatorius$swapOutlineChain(PostEffectProcessor original) {
        if (Anatorius.MODULE_MANAGER == null) return original;
        ShadersModule module = Anatorius.MODULE_MANAGER.getModule(ShadersModule.class);
        if (module == null || !module.wantsChams()) return original;

        PostEffectProcessor chams = WorldChamsChain.get(DefaultFramebufferSet.MAIN_AND_ENTITY_OUTLINE,
                module, module.getColorA(), 0);
        return chams != null ? chams : original;
    }
}
