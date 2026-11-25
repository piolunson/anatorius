package dev.project.anatorius.customsky.client.sky;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class WeatherDrift {
   private static final float EASE = 0.06F;
   private static float currentSpeed = 1.0F;
   private static double clock;
   private static long lastTick = Long.MIN_VALUE;

   private WeatherDrift() {
   }

   public static boolean active() {
      return entry() != null;
   }

   public static float driftX(double y) {
      SkyboxEntry e = entry();
      return e == null ? 0.0F : (float)(strength(e) * Math.cos(Math.toRadians((double)e.weatherDirection())) * y);
   }

   public static float driftZ(double y) {
      SkyboxEntry e = entry();
      return e == null ? 0.0F : (float)(strength(e) * Math.sin(Math.toRadians((double)e.weatherDirection())) * y);
   }

   private static double strength(SkyboxEntry entry) {
      double tilt = Math.max((double)0.0F, Math.min((double)80.0F, (double)entry.weatherTilt()));
      return Math.tan(Math.toRadians(tilt));
   }

   public static void tick(long gameTime) {
      if (gameTime != lastTick) {
         lastTick = gameTime;
         currentSpeed += (targetSpeed() - currentSpeed) * 0.06F;
         if (Math.abs(currentSpeed - targetSpeed()) < 0.002F) {
            currentSpeed = targetSpeed();
         }

         clock += (double)currentSpeed;
      }
   }

   public static boolean speedActive() {
      return currentSpeed < 0.999F || targetSpeed() < 0.999F;
   }

   public static int ticks() {
      return (int)(clock % (double)Integer.MAX_VALUE);
   }

   public static float partial(float original) {
      return (float)(clock - Math.floor(clock)) + original * currentSpeed;
   }

   private static float targetSpeed() {
      SkyboxEntry rain = SkyboxManager.get().resolve(SkyboxType.RAIN);
      if (rain != null) {
         return clamp(rain.speed());
      } else {
         SkyboxEntry snow = SkyboxManager.get().resolve(SkyboxType.SNOW);
         return snow != null ? clamp(snow.speed()) : 1.0F;
      }
   }

   private static float clamp(float speed) {
      return speed < 0.0F ? 0.0F : (speed > 1.0F ? 1.0F : speed);
   }

   private static SkyboxEntry entry() {
      SkyboxEntry rain = SkyboxManager.get().resolve(SkyboxType.RAIN);
      if (rain != null && !Float.isNaN(rain.weatherDirection())) {
         return rain;
      } else {
         SkyboxEntry snow = SkyboxManager.get().resolve(SkyboxType.SNOW);
         return snow != null && !Float.isNaN(snow.weatherDirection()) ? snow : null;
      }
   }
}
