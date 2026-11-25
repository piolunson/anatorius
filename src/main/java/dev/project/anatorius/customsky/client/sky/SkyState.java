package dev.project.anatorius.customsky.client.sky;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class SkyState {
   public static volatile float sunAngle;

   private SkyState() {
   }

   public static float daylight() {
      return Math.min(1.0F, Math.max(0.0F, (float)Math.cos((double)sunAngle) * 2.0F + 0.5F));
   }
}
