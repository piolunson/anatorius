package dev.project.anatorius.customsky.client.sky;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class SkyDirection {
   private static final Map<String, Float> HORIZONTAL = new LinkedHashMap();
   private static final Map<String, Float> VERTICAL = new LinkedHashMap();
   private static final Map<String, Float> WEATHER = new LinkedHashMap();

   private SkyDirection() {
   }

   public static List<String> horizontalNames() {
      return List.copyOf(HORIZONTAL.keySet());
   }

   public static List<String> verticalNames() {
      return List.copyOf(VERTICAL.keySet());
   }

   public static List<String> weatherNames() {
      return List.copyOf(WEATHER.keySet());
   }

   public static Float parse(SkyParam.Kind kind, String text) {
      Map var10000;
      switch (kind) {
         case POS_X -> var10000 = HORIZONTAL;
         case POS_Y -> var10000 = VERTICAL;
         case WEATHER_DIR -> var10000 = WEATHER;
         default -> var10000 = null;
      }

      Map<String, Float> table = var10000;
      if (table != null && text != null && !text.isBlank()) {
         Float named = (Float)table.get(text.toUpperCase());
         if (named != null) {
            return named;
         } else {
            try {
               return Float.parseFloat(text);
            } catch (NumberFormatException var5) {
               return null;
            }
         }
      } else {
         return null;
      }
   }

   public static String name(SkyParam.Kind kind, float degrees) {
      Map var10000;
      switch (kind) {
         case POS_X -> var10000 = HORIZONTAL;
         case POS_Y -> var10000 = VERTICAL;
         case WEATHER_DIR -> var10000 = WEATHER;
         default -> var10000 = Map.of();
      }

      Map<String, Float> table = var10000;

      for(Map.Entry<String, Float> e : table.entrySet()) {
         if (Float.compare((Float)e.getValue(), degrees) == 0) {
            return (String)e.getKey();
         }
      }

      return String.valueOf(degrees);
   }

   public static boolean isStraight(float degrees) {
      return Float.isNaN(degrees);
   }

   static {
      HORIZONTAL.put("EAST", 0.0F);
      HORIZONTAL.put("SOUTH_EAST", 45.0F);
      HORIZONTAL.put("SOUTH", 90.0F);
      HORIZONTAL.put("SOUTH_WEST", 135.0F);
      HORIZONTAL.put("WEST", 180.0F);
      HORIZONTAL.put("NORTH_WEST", 225.0F);
      HORIZONTAL.put("NORTH", 270.0F);
      HORIZONTAL.put("NORTH_EAST", 315.0F);
      VERTICAL.put("HORIZON", 0.0F);
      VERTICAL.put("LOW", 20.0F);
      VERTICAL.put("MIDDLE", 45.0F);
      VERTICAL.put("HIGH", 70.0F);
      VERTICAL.put("TOP", 90.0F);
      VERTICAL.put("BELOW", -25.0F);
      WEATHER.put("STRAIGHT", Float.NaN);
      WEATHER.put("NORTH", 270.0F);
      WEATHER.put("NORTH_EAST", 315.0F);
      WEATHER.put("EAST", 0.0F);
      WEATHER.put("SOUTH_EAST", 45.0F);
      WEATHER.put("SOUTH", 90.0F);
      WEATHER.put("SOUTH_WEST", 135.0F);
      WEATHER.put("WEST", 180.0F);
      WEATHER.put("NORTH_WEST", 225.0F);
   }
}
