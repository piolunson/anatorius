package dev.project.anatorius.customsky.client.sky;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class SkyClock {
   private static long ticks;
   private static float partial;
   private static long partialAt = -4611686018427387904L;
   private static long partialTick = -1L;

   private SkyClock() {
   }

   public static void tick() {
      ++ticks;
   }

   public static long ticks() {
      return ticks;
   }

   public static float smoothTicks() {
      long now = System.nanoTime();
      if (now - partialAt > 500000L || partialTick != ticks) {
         partial = FadeController.partialTick();
         partialAt = now;
         partialTick = ticks;
      }

      return (float)ticks + partial;
   }

   public static float partial() {
      smoothTicks();
      return partial;
   }
}
