package dev.project.anatorius.customsky.client.sky;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;

@Environment(EnvType.CLIENT)
public final class FadeController {
   private FadeController() {
   }

   public static float alphaForActive(SkyboxEntry entry) {
      if (entry == null) {
         return 0.0F;
      } else {
         float op = Math.max(0.0F, Math.min(1.0F, entry.opacity()));
         if (entry.fadeInTicks() <= 0) {
            return op;
         } else {
            long elapsed = Math.max(0L, currentTick() - entry.appliedAtTick());
            return Math.min(1.0F, (float)elapsed / (float)entry.fadeInTicks()) * op;
         }
      }
   }

   public static float alphaForFadingOut(SkyboxManager.FadingOut fading) {
      if (fading == null) {
         return 0.0F;
      } else {
         float op = Math.max(0.0F, Math.min(1.0F, fading.entry().opacity()));
         int ticks = fading.entry().fadeOutTicks();
         if (ticks > 0) {
            long elapsed = Math.max(0L, currentTick() - fading.startTick());
            return Math.max(0.0F, (1.0F - (float)elapsed / (float)ticks) * op);
         } else {
            return fading.entry().animOut().isNone() && fading.entry().riftAnimOut().isNone() ? 0.0F : op;
         }
      }
   }

   public static boolean fadingOutFinished(SkyboxManager.FadingOut fading) {
      if (fading == null) {
         return true;
      } else {
         long elapsed = currentTick() - fading.startTick();
         int fade = fading.entry().fadeOutTicks();
         boolean hasExit = !fading.entry().animOut().isNone() || !fading.entry().riftAnimOut().isNone();
         int anim = hasExit ? fading.entry().animOutTime() : 0;
         return elapsed >= (long)Math.max(fade, anim);
      }
   }

   public static RiftAnim.Sample riftSample(SkyboxEntry entry) {
      if (entry == null) {
         return RiftAnim.Sample.IDLE;
      } else {
         RiftAnim anim = entry.riftAnimIn();
         if (anim.isNone()) {
            return RiftAnim.Sample.IDLE;
         } else {
            int ticks = Math.max(1, entry.animInTime());
            float progress = ((float)(currentTick() - entry.appliedAtTick()) + partialTick()) / (float)ticks;
            return progress >= 1.0F ? RiftAnim.Sample.IDLE : anim.sample(progress);
         }
      }
   }

   public static RiftAnim.Sample riftSample(SkyboxManager.FadingOut fading) {
      if (fading == null) {
         return RiftAnim.Sample.IDLE;
      } else {
         RiftAnim anim = fading.entry().riftAnimOut();
         if (anim.isNone()) {
            return RiftAnim.Sample.IDLE;
         } else {
            int ticks = Math.max(1, fading.entry().animOutTime());
            float progress = ((float)(currentTick() - fading.startTick()) + partialTick()) / (float)ticks;
            return anim.sample(Math.min(1.0F, progress));
         }
      }
   }

   public static SkyAnim.Sample animSample(SkyboxEntry entry) {
      if (entry == null) {
         return SkyAnim.Sample.IDLE;
      } else {
         SkyAnim anim = entry.animIn();
         if (anim.isNone()) {
            return SkyAnim.Sample.IDLE;
         } else {
            int ticks = Math.max(1, entry.animInTime());
            float progress = ((float)(currentTick() - entry.appliedAtTick()) + partialTick()) / (float)ticks;
            return progress >= 1.0F ? SkyAnim.Sample.IDLE : anim.sample(progress);
         }
      }
   }

   public static SkyAnim.Sample animSample(SkyboxManager.FadingOut fading) {
      if (fading == null) {
         return SkyAnim.Sample.IDLE;
      } else {
         SkyAnim anim = fading.entry().animOut();
         if (anim.isNone()) {
            return SkyAnim.Sample.IDLE;
         } else {
            int ticks = Math.max(1, fading.entry().animOutTime());
            float progress = ((float)(currentTick() - fading.startTick()) + partialTick()) / (float)ticks;
            return anim.sample(Math.min(1.0F, progress));
         }
      }
   }

   public static float presetLoop(SkyboxEntry entry) {
      if (entry != null && entry.preset() != CelestialPreset.NONE) {
         int ticks = Math.max(1, entry.animationTime());
         float t = ((float)(currentTick() - entry.appliedAtTick()) + partialTick()) / (float)ticks;
         return t - (float)Math.floor((double)t);
      } else {
         return 0.0F;
      }
   }

   private static long currentTick() {
      return SkyClock.ticks();
   }

   public static float partialTick() {
      MinecraftClient mc = MinecraftClient.getInstance();
      return mc == null ? 0.0F : mc.getRenderTickCounter().getTickProgress(false);
   }
}
