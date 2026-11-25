package dev.project.anatorius.mixins.customsky;

import dev.project.anatorius.customsky.CustomSky;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WeatherRendering;
import dev.project.anatorius.customsky.client.sky.DriftingVertexConsumer;
import dev.project.anatorius.customsky.client.sky.SkyboxSwap;
import dev.project.anatorius.customsky.client.sky.WeatherDrift;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Environment(EnvType.CLIENT)
@Mixin({WeatherRendering.class})
public abstract class WeatherRenderingMixin {
   @WrapOperation(
      method = {"renderPrecipitation(Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/client/render/state/WeatherRenderState;)V"},
      at = {@At(
   value = "INVOKE",
   target = "Lnet/minecraft/client/render/RenderLayers;weather(Lnet/minecraft/util/Identifier;Z)Lnet/minecraft/client/render/RenderLayer;"
)},
      require = 0
   )
   private static RenderLayer cs$swapWeatherTexture(Identifier id, boolean depth, Operation<RenderLayer> original) {
      return (RenderLayer)original.call(new Object[]{CustomSky.active() ? SkyboxSwap.swap(id) : id, depth});
   }

   @ModifyVariable(
      method = {"renderPrecipitation(Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/client/render/state/WeatherRenderState;)V"},
      at = @At("HEAD"),
      argsOnly = true,
      ordinal = 0,
      require = 0
   )
   private static VertexConsumerProvider cs$leanWeatherBuffers(VertexConsumerProvider original) {
      return !CustomSky.active() || !WeatherDrift.active() ? original : (layer) -> new DriftingVertexConsumer(original.getBuffer(layer));
   }
}
