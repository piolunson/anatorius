package dev.project.anatorius.mixins.customsky;

import dev.project.anatorius.customsky.CustomSky;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.render.CloudRenderer;
import dev.project.anatorius.customsky.client.sky.SkyDomeRenderer;
import dev.project.anatorius.customsky.client.sky.SkyboxManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin({CloudRenderer.class})
public abstract class CloudRendererMixin {
   @Inject(
      method = {"renderClouds(ILnet/minecraft/client/option/CloudRenderMode;FLnet/minecraft/util/math/Vec3d;JF)V"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void cs$cancelVanillaClouds(int color, CloudRenderMode mode, float cloudHeight, Vec3d camPos, long time, float tickDelta, CallbackInfo ci) {
      if (!CustomSky.active()) return;
      if (SkyboxManager.get().cloudsToDraw() != null || SkyDomeRenderer.cloudsOvercome() > 0.25F) {
         ci.cancel();
      }
   }
}
