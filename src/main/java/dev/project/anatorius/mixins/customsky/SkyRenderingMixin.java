package dev.project.anatorius.mixins.customsky;

import dev.project.anatorius.customsky.CustomSky;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.state.SkyRenderState;
import net.minecraft.world.MoonPhase;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.client.render.SkyRendering;
import net.minecraft.world.dimension.DimensionType.Skybox;
import dev.project.anatorius.customsky.client.sky.CustomStarRenderer;
import dev.project.anatorius.customsky.client.sky.FadeController;
import dev.project.anatorius.customsky.client.sky.SkyDomeRenderer;
import dev.project.anatorius.customsky.client.sky.SkyState;
import dev.project.anatorius.customsky.client.sky.SkyboxEntry;
import dev.project.anatorius.customsky.client.sky.SkyboxManager;
import dev.project.anatorius.customsky.client.sky.SkyboxTextures;
import dev.project.anatorius.customsky.client.sky.SkyboxType;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin({SkyRendering.class})
public abstract class SkyRenderingMixin {
   @Inject(
      method = {"updateRenderState"},
      at = {@At("RETURN")},
      require = 0
   )
   private void cs$afterUpdateRenderState(ClientWorld world, float tickDelta, Camera camera, SkyRenderState state, CallbackInfo ci) {
      if (!CustomSky.active()) return;
      SkyState.sunAngle = state.sunAngle;
      if (world.getDimension().skybox() != Skybox.OVERWORLD && SkyboxTextures.hasAltDimensionSkyOverride()) {
         state.skybox = Skybox.OVERWORLD;
         state.shouldRenderSkyDark = false;
      }

      if (SkyboxTextures.hasSkyDomeOverride()) {
         state.shouldRenderSkyDark = false;
      }
   }

   @Unique
   private void cs$renderStarsInAltDimension(Matrix4f modelView) {
      if (!CustomSky.active()) return;
      SkyboxEntry stars = SkyboxManager.get().resolve(SkyboxType.STARS);
      SkyboxManager.FadingOut fadingStars = SkyboxManager.get().getFadingOut(SkyboxType.STARS);
      if (stars != null || fadingStars != null) {
         if (stars != null) {
            CustomStarRenderer.render(modelView, 1.0F, stars);
         }

         if (fadingStars != null && !FadeController.fadingOutFinished(fadingStars)) {
            float fadeAlpha = FadeController.alphaForFadingOut(fadingStars);
            if (fadeAlpha > 0.0F) {
               SkyboxEntry fe = fadingStars.entry();
               SkyboxEntry fadeEntry = fe.withOpacity(fadeAlpha);
               CustomStarRenderer.render(modelView, 1.0F, fadeEntry);
            }
         }
      }
   }

   @Inject(
      method = {"renderTopSky"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void cs$suppressTopSky(int skyColor, CallbackInfo ci) {
      if (!CustomSky.active()) return;
      boolean altDimension = cs$isAltDimension();
      boolean hasSky = SkyboxTextures.activeEntry(SkyboxType.SKY) != null;
      boolean hasDaySky = SkyboxTextures.activeEntry(SkyboxType.DAY_SKY) != null;
      boolean hasDome = hasSky || hasDaySky || altDimension && SkyboxTextures.activeEntry(SkyboxType.NIGHT_SKY) != null;
      boolean hasStars = SkyboxManager.get().resolve(SkyboxType.STARS) != null;
      if (hasDome || hasStars) {
         if (altDimension) {
            Matrix4f domeMatrix = new Matrix4f();
            if (hasDome) {
               SkyDomeRenderer.renderIfOverride(domeMatrix, 0.0F);
               SkyDomeRenderer.renderCloudPlaneIfOverride(domeMatrix);
            }

            this.cs$renderStarsInAltDimension(domeMatrix);
            if (SkyDomeRenderer.hasCelestialOverride() || SkyDomeRenderer.needsLateDraw()) {
               SkyDomeRenderer.renderCelestialLayers(domeMatrix, (float)((double)SkyState.sunAngle / (Math.PI * 2D)), 0);
            }
         }

         if (hasDome) {
            ci.cancel();
         }
      }
   }

   @Unique
   private static boolean cs$isAltDimension() {
      MinecraftClient mc = MinecraftClient.getInstance();
      return mc != null && mc.world != null && mc.world.getDimension().skybox() != Skybox.OVERWORLD;
   }

   @Inject(
      method = {"renderCelestialBodies(Lnet/minecraft/client/util/math/MatrixStack;FFFLnet/minecraft/world/MoonPhase;FF)V"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void cs$renderSkyDome(MatrixStack ms, float sunAngle, float moonAngle, float starAngle, MoonPhase moonPhase, float rain, float starBrightness, CallbackInfo ci) {
      if (!CustomSky.active()) return;
      if (cs$isAltDimension()) {
         ci.cancel();
      } else {
         Matrix4f domeMatrix = new Matrix4f(ms.peek().getPositionMatrix());
         SkyDomeRenderer.renderIfOverride(domeMatrix, 0.0F);
         SkyDomeRenderer.renderCloudPlaneIfOverride(domeMatrix);
         Matrix4f starMatrix = (new Matrix4f(domeMatrix)).rotate(RotationAxis.POSITIVE_Y.rotationDegrees(-90.0F)).rotate(RotationAxis.POSITIVE_X.rotation(starAngle));
         this.cs$renderDayCycleOffStars(starMatrix);
      }
   }

   @Unique
   private void cs$renderDayCycleOffStars(Matrix4f modelView) {
      if (!CustomSky.active()) return;
      SkyboxEntry stars = SkyboxManager.get().resolve(SkyboxType.STARS);
      SkyboxManager.FadingOut fadingStars = SkyboxManager.get().getFadingOut(SkyboxType.STARS);
      boolean needRender = stars != null && !stars.dayCycle() || fadingStars != null && !FadeController.fadingOutFinished(fadingStars) && !fadingStars.entry().dayCycle();
      if (needRender) {
         if (stars != null && !stars.dayCycle()) {
            CustomStarRenderer.render(modelView, 1.0F, stars);
         }

         if (fadingStars != null && !FadeController.fadingOutFinished(fadingStars) && !fadingStars.entry().dayCycle()) {
            float fadeAlpha = FadeController.alphaForFadingOut(fadingStars);
            if (fadeAlpha > 0.0F) {
               SkyboxEntry fe = fadingStars.entry();
               SkyboxEntry fadeEntry = fe.withOpacity(fadeAlpha);
               CustomStarRenderer.render(modelView, 1.0F, fadeEntry);
            }
         }
      }
   }

   @Inject(
      method = {"renderStars(FLnet/minecraft/client/util/math/MatrixStack;)V"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void cs$replaceVanillaStars(float brightness, MatrixStack ms, CallbackInfo ci) {
      if (!CustomSky.active()) return;
      SkyboxEntry stars = SkyboxManager.get().resolve(SkyboxType.STARS);
      SkyboxManager.FadingOut fadingStars = SkyboxManager.get().getFadingOut(SkyboxType.STARS);
      if (stars != null || fadingStars != null) {
         float effectiveBrightness = brightness;
         if (stars != null && !stars.dayCycle()) {
            effectiveBrightness = 1.0F;
         }

         if (effectiveBrightness > 0.0F) {
            Matrix4f modelView = new Matrix4f(ms.peek().getPositionMatrix());
            if (stars != null) {
               CustomStarRenderer.render(modelView, effectiveBrightness, stars);
            }

            if (fadingStars != null && !FadeController.fadingOutFinished(fadingStars)) {
               float fadeAlpha = FadeController.alphaForFadingOut(fadingStars);
               if (fadeAlpha > 0.0F) {
                  SkyboxEntry fe = fadingStars.entry();
                  float fadeBrightness = fe.dayCycle() ? brightness : 1.0F;
                  if (fadeBrightness > 0.0F) {
                     SkyboxEntry fadeEntry = fe.withOpacity(fadeAlpha);
                     CustomStarRenderer.render(modelView, fadeBrightness, fadeEntry);
                  }
               }
            }
         }

         ci.cancel();
      }
   }

   @Inject(
      method = {"renderSun(FLnet/minecraft/client/util/math/MatrixStack;)V"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void cs$skipVanillaSun(float alpha, MatrixStack ms, CallbackInfo ci) {
      if (!CustomSky.active()) return;
      if (SkyDomeRenderer.hasCelestialOverride()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"renderMoon(Lnet/minecraft/world/MoonPhase;FLnet/minecraft/client/util/math/MatrixStack;)V"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void cs$skipVanillaMoon(MoonPhase phase, float alpha, MatrixStack ms, CallbackInfo ci) {
      if (!CustomSky.active()) return;
      if (SkyDomeRenderer.hasCelestialOverride()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"renderCelestialBodies(Lnet/minecraft/client/util/math/MatrixStack;FFFLnet/minecraft/world/MoonPhase;FF)V"},
      at = {@At("RETURN")},
      require = 0
   )
   private void cs$renderCelestialLayers(MatrixStack ms, float sunAngle, float moonAngle, float starAngle, MoonPhase moonPhase, float rain, float starBrightness, CallbackInfo ci) {
      if (!CustomSky.active()) return;
      if (SkyDomeRenderer.hasCelestialOverride() || SkyDomeRenderer.needsLateDraw()) {
         Matrix4f matrix = new Matrix4f(ms.peek().getPositionMatrix());
         SkyDomeRenderer.renderCelestialLayers(matrix, (float)((double)sunAngle / (Math.PI * 2D)), moonPhase.getIndex());
      }
   }
}
