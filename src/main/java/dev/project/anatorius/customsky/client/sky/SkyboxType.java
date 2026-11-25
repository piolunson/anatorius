package dev.project.anatorius.customsky.client.sky;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public enum SkyboxType {
   SKY("sky", true),
   DAY_SKY("day_sky", true),
   NIGHT_SKY("night_sky", true),
   STARS("stars", false),
   SUN("sun", true),
   MOON("moon", true),
   CLOUDS("clouds", true),
   RAIN("rain", true),
   SNOW("snow", true),
   RIFT("rift", true),
   CUSTOM("custom", true);

   private final String id;
   private final boolean textureBased;

   private SkyboxType(String id, boolean textureBased) {
      this.id = id;
      this.textureBased = textureBased;
   }

   public String id() {
      return this.id;
   }

   public String subfolder() {
      return this.id;
   }

   public boolean isTextureBased() {
      return this.textureBased;
   }

   public boolean hasScale() {
      return this == SUN || this == MOON || this == STARS || this == CUSTOM;
   }

   public boolean isDome() {
      return this == SKY || this == DAY_SKY || this == NIGHT_SKY;
   }

   public boolean isCelestial() {
      return this == SUN || this == MOON || this == CUSTOM;
   }

   public boolean hasLayers() {
      return this.isCelestial() || this.isRift();
   }

   public boolean isRift() {
      return this == RIFT;
   }

   public boolean isWeather() {
      return this == RAIN || this == SNOW;
   }

   public static Optional<SkyboxType> fromId(String id) {
      return id == null ? Optional.empty() : Arrays.stream(values()).filter((t) -> t.id.equalsIgnoreCase(id)).findFirst();
   }

   public static List<String> allIds() {
      return Arrays.stream(values()).map(SkyboxType::id).toList();
   }

   private static SkyboxType[] $values() {
      return new SkyboxType[]{SKY, DAY_SKY, NIGHT_SKY, STARS, SUN, MOON, CLOUDS, RAIN, SNOW, RIFT, CUSTOM};
   }
}
