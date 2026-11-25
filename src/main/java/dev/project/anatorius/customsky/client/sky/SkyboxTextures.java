package dev.project.anatorius.customsky.client.sky;

import java.util.EnumMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import dev.project.anatorius.customsky.client.texture.AnimatedTextureRegistry;

@Environment(EnvType.CLIENT)
public final class SkyboxTextures {
   private static final Map<SkyboxType, Float> currentScale = new EnumMap(SkyboxType.class);
   private static final Map<SkyboxType, Long> lastNanoTimeMap = new EnumMap(SkyboxType.class);
   private static final float ANIM_SPEED = 6.0F;

   private SkyboxTextures() {
   }

   public static Identifier activeTexture(SkyboxType type) {
      SkyboxEntry e = SkyboxManager.get().resolve(type);
      return e == null ? null : AnimatedTextureRegistry.get().resolve(type, e.texture());
   }

   public static boolean smoothEdges(SkyboxType type) {
      SkyboxEntry active = activeEntry(type);
      if (active != null) {
         return active.smoothEdges();
      } else {
         SkyboxManager.FadingOut fading = SkyboxManager.get().getFadingOut(type);
         return fading == null || fading.entry().smoothEdges();
      }
   }

   public static SkyboxEntry activeEntry(SkyboxType type) {
      return SkyboxManager.get().resolve(type);
   }

   public static boolean hasSkyDomeOverride() {
      return activeEntry(SkyboxType.SKY) != null || activeEntry(SkyboxType.DAY_SKY) != null || activeEntry(SkyboxType.NIGHT_SKY) != null;
   }

   public static boolean hasAltDimensionSkyOverride() {
      return hasSkyDomeOverride() || activeEntry(SkyboxType.STARS) != null;
   }

   public static float activeAlpha(SkyboxType type) {
      return FadeController.alphaForActive(SkyboxManager.get().resolve(type));
   }

   public static float activeScale(SkyboxType type) {
      SkyboxEntry e = SkyboxManager.get().resolve(type);
      float target = e != null ? e.scale() : 1.0F;
      if (type != SkyboxType.SUN && type != SkyboxType.MOON) {
         return target;
      } else {
         long now = System.nanoTime();
         Long prev = (Long)lastNanoTimeMap.get(type);
         float dt = 0.0F;
         if (prev != null) {
            dt = (float)(now - prev) / 1.0E9F;
            dt = Math.min(dt, 0.1F);
         }

         lastNanoTimeMap.put(type, now);
         Float cur = (Float)currentScale.get(type);
         if (cur == null) {
            currentScale.put(type, target);
            return target;
         } else {
            float diff = target - cur;
            if (Math.abs(diff) < 0.001F) {
               currentScale.put(type, target);
               return target;
            } else {
               float factor = 1.0F - (float)Math.exp((double)(-6.0F * dt));
               float newScale = cur + diff * factor;
               currentScale.put(type, newScale);
               return newScale;
            }
         }
      }
   }
}
