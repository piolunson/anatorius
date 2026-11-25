package dev.project.anatorius.customsky.client.sky;

import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class SkyAnchor {
   private static double lastX;
   private static double lastY;
   private static double lastZ;
   private static double currentX;
   private static double currentY;
   private static double currentZ;
   private static boolean known;
   private static final Map<SkyboxEntry, double[]> ANCHORS = new WeakHashMap();

   private SkyAnchor() {
   }

   public static void set(double x, double y, double z) {
      if (!known) {
         currentX = x;
         currentY = y;
         currentZ = z;
      }

      lastX = currentX;
      lastY = currentY;
      lastZ = currentZ;
      currentX = x;
      currentY = y;
      currentZ = z;
      known = true;
   }

   public static boolean known() {
      return known;
   }

   public static double playerX() {
      return lerp(lastX, currentX);
   }

   public static double playerY() {
      return lerp(lastY, currentY);
   }

   public static double playerZ() {
      return lerp(lastZ, currentZ);
   }

   private static double lerp(double from, double to) {
      float t = FadeController.partialTick();
      if (t < 0.0F) {
         t = 0.0F;
      }

      if (t > 1.0F) {
         t = 1.0F;
      }

      return from + (to - from) * (double)t;
   }

   public static double elevation(SkyboxEntry entry, float height) {
      synchronized(ANCHORS) {
         return ((double[])ANCHORS.computeIfAbsent(entry, (e) -> {
            double rise = (double)height - currentY;
            double angle = Math.atan2(rise, Math.max((double)32.0F, (double)height * 0.3));
            return new double[]{Math.max((double)0.0F, Math.min(Math.toRadians((double)85.0F), angle))};
         }))[0];
      }
   }
}
