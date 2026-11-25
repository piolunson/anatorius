package dev.project.anatorius.customsky.client.sky;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class SkyOvercome {
   public static final SkyOvercome NONE = new SkyOvercome(false, false, false, false, false);
   private static final List<String> PARTS = List.of("sun", "moon", "custom", "stars", "clouds");
   private final boolean stars;
   private final boolean sun;
   private final boolean moon;
   private final boolean clouds;
   private final boolean custom;

   private SkyOvercome(boolean stars, boolean sun, boolean moon, boolean clouds, boolean custom) {
      this.stars = stars;
      this.sun = sun;
      this.moon = moon;
      this.clouds = clouds;
      this.custom = custom;
   }

   public boolean covers(SkyboxType type) {
      boolean var10000;
      switch (type) {
         case STARS -> var10000 = this.stars;
         case SUN -> var10000 = this.sun;
         case MOON -> var10000 = this.moon;
         case CLOUDS -> var10000 = this.clouds;
         case CUSTOM -> var10000 = this.custom;
         default -> var10000 = false;
      }

      return var10000;
   }

   public boolean any() {
      return this.stars || this.sun || this.moon || this.custom;
   }

   public boolean clouds() {
      return this.clouds;
   }

   public SkyboxType lastCovered() {
      if (this.custom) {
         return SkyboxType.CUSTOM;
      } else if (this.moon) {
         return SkyboxType.MOON;
      } else {
         return this.sun ? SkyboxType.SUN : SkyboxType.STARS;
      }
   }

   public static SkyOvercome parse(String value) {
      if (value != null && !value.isBlank()) {
         boolean stars = false;
         boolean sun = false;
         boolean moon = false;
         boolean clouds = false;
         boolean custom = false;

         for(String part : value.toLowerCase(Locale.ROOT).split(",")) {
            switch (part.trim()) {
               case "all":
                  stars = true;
                  sun = true;
                  moon = true;
                  clouds = true;
                  custom = true;
                  break;
               case "custom":
                  custom = true;
                  break;
               case "stars":
                  stars = true;
                  break;
               case "sun":
                  sun = true;
                  break;
               case "moon":
                  moon = true;
                  break;
               case "clouds":
                  clouds = true;
               case "none":
                  break;
               default:
                  return null;
            }
         }

         return new SkyOvercome(stars, sun, moon, clouds, custom);
      } else {
         return null;
      }
   }

   public static List<String> names() {
      List<String> out = new ArrayList(PARTS);
      out.add("all");
      return out;
   }

   public static List<String> suggest(String typed) {
      String t = typed == null ? "" : typed.toLowerCase(Locale.ROOT);
      int comma = t.lastIndexOf(44);
      if (comma < 0) {
         return names();
      } else {
         String done = t.substring(0, comma + 1);
         List<String> used = List.of(done.split(","));
         List<String> out = new ArrayList();

         for(String part : PARTS) {
            if (!used.contains(part)) {
               out.add(done + part);
            }
         }

         return out;
      }
   }

   public String toString() {
      if (this.stars && this.sun && this.moon && this.clouds && this.custom) {
         return "all";
      } else {
         List<String> parts = new ArrayList();
         if (this.sun) {
            parts.add("sun");
         }

         if (this.moon) {
            parts.add("moon");
         }

         if (this.stars) {
            parts.add("stars");
         }

         if (this.clouds) {
            parts.add("clouds");
         }

         if (this.custom) {
            parts.add("custom");
         }

         return parts.isEmpty() ? "none" : String.join(",", parts);
      }
   }
}
