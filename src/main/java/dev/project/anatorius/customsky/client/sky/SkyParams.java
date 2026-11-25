package dev.project.anatorius.customsky.client.sky;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import dev.project.anatorius.customsky.client.model.SkyModels;
import dev.project.anatorius.customsky.client.resource.ResourcePackScanner;

@Environment(EnvType.CLIENT)
public final class SkyParams {
   public static final SkyParams EMPTY = new SkyParams(new EnumMap(SkyParam.class));
   private final Map<SkyParam, Object> values;
   private static final LinkedHashMap<String, Integer> COLORS = new LinkedHashMap();

   private SkyParams(Map<SkyParam, Object> values) {
      this.values = values;
   }

   public boolean has(SkyParam param) {
      return this.values.containsKey(param);
   }

   public float getFloat(SkyParam param) {
      Object v = this.values.get(param);
      float var10000;
      if (v instanceof Float f) {
         var10000 = f;
      } else {
         var10000 = param.defaultValue();
      }

      return var10000;
   }

   public int getInt(SkyParam param) {
      return Math.round(this.getFloat(param));
   }

   public boolean getBool(SkyParam param) {
      return this.getFloat(param) >= 0.5F;
   }

   public SkyAnim getAnim(SkyParam param) {
      Object v = this.values.get(param);
      SkyAnim var10000;
      if (v instanceof SkyAnim a) {
         var10000 = a;
      } else {
         var10000 = SkyAnim.NONE;
      }

      return var10000;
   }

   public SkyOvercome getOvercome() {
      Object v = this.values.get(SkyParam.FORCE_OVERCOME);
      SkyOvercome var10000;
      if (v instanceof SkyOvercome o) {
         var10000 = o;
      } else {
         var10000 = SkyOvercome.NONE;
      }

      return var10000;
   }

   public RiftShape getRiftShape() {
      Object v = this.values.get(SkyParam.RIFT_SHAPE);
      RiftShape var10000;
      if (v instanceof RiftShape r) {
         var10000 = r;
      } else {
         var10000 = RiftShape.CRACK;
      }

      return var10000;
   }

   public RiftAnim getRiftAnim(SkyParam param) {
      Object v = this.values.get(param);
      RiftAnim var10000;
      if (v instanceof RiftAnim a) {
         var10000 = a;
      } else {
         var10000 = RiftAnim.NONE;
      }

      return var10000;
   }

   public String getText(SkyParam param) {
      Object v = this.values.get(param);
      String var10000;
      if (v instanceof String text) {
         var10000 = text;
      } else {
         var10000 = null;
      }

      return var10000;
   }

   public CelestialPreset getPreset() {
      Object v = this.values.get(SkyParam.ANIMATION_PRESET);
      CelestialPreset var10000;
      if (v instanceof CelestialPreset p) {
         var10000 = p;
      } else {
         var10000 = CelestialPreset.NONE;
      }

      return var10000;
   }

   public SkyParams with(SkyParam param, Object value) {
      Map<SkyParam, Object> copy = new EnumMap(this.values);
      if (value == null) {
         copy.remove(param);
      } else {
         copy.put(param, value);
      }

      return new SkyParams(copy);
   }

   public Map<String, String> toMap() {
      Map<String, String> out = new LinkedHashMap();

      for(SkyParam p : SkyParam.values()) {
         Object v = this.values.get(p);
         if (v != null) {
            out.put(p.key(), print(p, v));
         }
      }

      return out;
   }

   public boolean isEmpty() {
      return this.values.isEmpty();
   }

   private static String print(SkyParam param, Object value) {
      if (value instanceof SkyAnim a) {
         return a.id();
      } else if (value instanceof CelestialPreset p) {
         return p.id();
      } else if (value instanceof RiftShape r) {
         return r.id();
      } else if (value instanceof RiftAnim r) {
         return r.id();
      } else if (value instanceof SkyOvercome o) {
         return o.toString();
      } else if (value instanceof String text) {
         return text;
      } else if (param.kind() == SkyParam.Kind.COLOR && value instanceof Float c) {
         return String.format("#%06x", Math.round(c) & 16777215);
      } else {
         float var10000;
         if (value instanceof Float n) {
            var10000 = n;
         } else {
            var10000 = 0.0F;
         }

         float f = var10000;
         String var11;
         switch (param.kind()) {
            case BOOL:
               var11 = f >= 0.5F ? "true" : "false";
               break;
            case INT:
               var11 = String.valueOf(Math.round(f));
               break;
            case POS_X:
            case POS_Y:
            case WEATHER_DIR:
               var11 = SkyDirection.name(param.kind(), f);
               break;
            default:
               var11 = trimFloat(f);
         }

         return var11;
      }
   }

   private static String trimFloat(float f) {
      return (double)f == Math.rint((double)f) ? String.valueOf((int)f) : String.valueOf(f);
   }

   public static Result parse(SkyboxType type, String raw) {
      Map<SkyParam, Object> out = new EnumMap(SkyParam.class);
      if (raw != null && !raw.isBlank()) {
         for(String token : raw.trim().split("\\s+")) {
            if (!token.isEmpty()) {
               int eq = token.indexOf(61);
               if (eq <= 0) {
                  return new Result((SkyParams)null, "Arguments look like name=value, so " + token + " does not work");
               }

               String key = token.substring(0, eq);
               String value = token.substring(eq + 1);
               SkyParam param = legacy(type, (SkyParam)SkyParam.byKey(key).orElse(null));
               if (param == null) {
                  return new Result((SkyParams)null, "Unknown argument " + key);
               }

               if (!param.appliesTo(type)) {
                  String var12 = type.id();
                  return new Result((SkyParams)null, var12 + " does not take " + param.key());
               }

               if (out.containsKey(param)) {
                  return new Result((SkyParams)null, param.key() + " is set twice");
               }

               Object parsed = parseValue(type, param, value);
               if (parsed == null) {
                  String var10003 = param.key();
                  return new Result((SkyParams)null, "Bad value for " + var10003 + ": " + value);
               }

               out.put(param, parsed);
            }
         }

         return new Result(new SkyParams(out), (String)null);
      } else {
         return new Result(new SkyParams(out), (String)null);
      }
   }

   private static SkyParam legacy(SkyboxType type, SkyParam param) {
      return param == SkyParam.SPEED && !param.appliesTo(type) && SkyParam.GIF_SPEED.appliesTo(type) ? SkyParam.GIF_SPEED : param;
   }

   public static List<String> colorNames() {
      return new ArrayList(COLORS.keySet());
   }

   public static Integer namedColor(String name) {
      return name == null ? null : (Integer)COLORS.get(name.toUpperCase(Locale.ROOT));
   }

   private static Integer parseColor(String value) {
      String v = value.trim();
      if (v.startsWith("#")) {
         try {
            return Integer.parseInt(v.substring(1), 16) & 16777215;
         } catch (NumberFormatException var3) {
            return null;
         }
      } else {
         return (Integer)COLORS.get(v.toUpperCase(Locale.ROOT));
      }
   }

   private static Object parseValue(SkyboxType type, SkyParam param, String value) {
      switch (param.kind()) {
         case BOOL:
            if (value.equalsIgnoreCase("true")) {
               return 1.0F;
            } else {
               if (value.equalsIgnoreCase("false")) {
                  return 0.0F;
               }

               return null;
            }
         case INT:
         default:
            try {
               return clamp(param, Float.parseFloat(value));
            } catch (NumberFormatException var5) {
               return null;
            }
         case POS_X:
         case POS_Y:
         case WEATHER_DIR:
            Float degrees = SkyDirection.parse(param.kind(), value);
            if (degrees == null) {
               return null;
            } else {
               if (Float.isNaN(degrees)) {
                  return degrees;
               }

               return clamp(param, degrees);
            }
         case ANIM_IN:
         case ANIM_OUT:
            if (type.isRift()) {
               RiftAnim riftAnim = (RiftAnim)RiftAnim.byId(value).orElse(null);
               if (riftAnim == null) {
                  return null;
               } else {
                  boolean wantsIn = param == SkyParam.ANIM_IN;
                  if (!riftAnim.isNone() && riftAnim.isEntrance() != wantsIn) {
                     return null;
                  }

                  return riftAnim;
               }
            } else {
               SkyAnim anim = (SkyAnim)SkyAnim.byId(value).orElse(null);
               if (anim != null && anim.fits(type)) {
                  boolean wantsEntrance = param == SkyParam.ANIM_IN;
                  if (!anim.isNone() && anim.isEntrance() != wantsEntrance) {
                     return null;
                  }

                  return anim;
               }

               return null;
            }
         case CELESTIAL:
            return CelestialPreset.byId(value).orElse(null);
         case OVERCOME:
            return SkyOvercome.parse(value);
         case RIFT_SHAPE:
            return RiftShape.byId(value).orElse(null);
         case COLOR:
            Integer rgb = parseColor(value);
            return rgb == null ? null : (float)rgb;
         case TEXTURE:
         case MODEL:
            return value.isBlank() ? null : value;
      }
   }

   private static Float clamp(SkyParam param, float value) {
      return !(value < param.min()) && !(value > param.max()) ? value : null;
   }

   public static List<String> suggest(SkyboxType type, String rawSoFar) {
      String text = rawSoFar == null ? "" : rawSoFar;
      int lastSpace = text.lastIndexOf(32);
      String current = lastSpace < 0 ? text : text.substring(lastSpace + 1);
      String before = lastSpace < 0 ? "" : text.substring(0, lastSpace);
      int eq = current.indexOf(61);
      if (eq < 0) {
         List<String> used = usedKeys(before);
         List<String> out = new ArrayList();

         for(SkyParam p : SkyParam.forType(type)) {
            if (!used.contains(p.key().toLowerCase()) && p.key().toLowerCase().startsWith(current.toLowerCase())) {
               out.add(p.key() + "=");
            }
         }

         return out;
      } else {
         SkyParam param = (SkyParam)SkyParam.byKey(current.substring(0, eq)).orElse(null);
         if (param != null && param.appliesTo(type)) {
            String partial = current.substring(eq + 1).toLowerCase();
            List<String> out = new ArrayList();
            List<String> options = param.suggestions();
            if (param == SkyParam.BACKGROUND) {
               options = backgroundNames();
            }

            if (param.kind() == SkyParam.Kind.OVERCOME) {
               options = SkyOvercome.suggest(partial);
            }

            if (param == SkyParam.MODEL_TEXTURE) {
               options = SkyModels.textureNames();
            }

            if (type.isRift() && param == SkyParam.ANIM_IN) {
               options = RiftAnim.names(true);
            }

            if (type.isRift() && param == SkyParam.ANIM_OUT) {
               options = RiftAnim.names(false);
            }

            for(String value : options) {
               if (param.kind() == SkyParam.Kind.ANIM_IN || param.kind() == SkyParam.Kind.ANIM_OUT) {
                  SkyAnim anim = (SkyAnim)SkyAnim.byId(value).orElse(null);
                  if (anim != null && !anim.fits(type)) {
                     continue;
                  }
               }

               if (value.toLowerCase().startsWith(partial)) {
                  String var10001 = param.key();
                  out.add(var10001 + "=" + value);
               }
            }

            return out;
         } else {
            return List.of();
         }
      }
   }

   private static List<String> backgroundNames() {
      List<String> out = new ArrayList();

      for(SkyboxType folder : new SkyboxType[]{SkyboxType.RIFT, SkyboxType.SKY, SkyboxType.NIGHT_SKY}) {
         for(String name : ResourcePackScanner.get().list(folder)) {
            if (!out.contains(name)) {
               out.add(name);
            }
         }
      }

      return out;
   }

   public static List<String> usedBefore(String rawSoFar) {
      String text = rawSoFar == null ? "" : rawSoFar;
      int lastSpace = text.lastIndexOf(32);
      return usedKeys(lastSpace < 0 ? "" : text.substring(0, lastSpace));
   }

   public static String currentWord(String rawSoFar) {
      String text = rawSoFar == null ? "" : rawSoFar;
      int lastSpace = text.lastIndexOf(32);
      return lastSpace < 0 ? text : text.substring(lastSpace + 1);
   }

   private static List<String> usedKeys(String text) {
      if (text != null && !text.isBlank()) {
         List<String> out = new ArrayList();

         for(String token : text.trim().split("\\s+")) {
            int eq = token.indexOf(61);
            if (eq > 0) {
               out.add(token.substring(0, eq).toLowerCase());
            }
         }

         return out;
      } else {
         return Collections.emptyList();
      }
   }

   public static SkyParams fromMap(SkyboxType type, Map<String, String> saved) {
      Map<SkyParam, Object> out = new EnumMap(SkyParam.class);
      saved.forEach((key, value) -> Optional.ofNullable(legacy(type, (SkyParam)SkyParam.byKey(key).orElse(null))).ifPresent((param) -> {
            if (param.appliesTo(type)) {
               Object parsed = parseValue(type, param, value);
               if (parsed != null) {
                  out.put(param, parsed);
               }
            }
         }));
      return new SkyParams(out);
   }

   static {
      COLORS.put("RED", 16724016);
      COLORS.put("CRIMSON", 14423100);
      COLORS.put("ORANGE", 16746496);
      COLORS.put("AMBER", 16756736);
      COLORS.put("GOLD", 16766720);
      COLORS.put("YELLOW", 16768324);
      COLORS.put("LIME", 10944256);
      COLORS.put("GREEN", 4521864);
      COLORS.put("EMERALD", 51320);
      COLORS.put("TEAL", 45988);
      COLORS.put("CYAN", 65535);
      COLORS.put("AQUA", 8388564);
      COLORS.put("SKY", 6737151);
      COLORS.put("ICE", 12578815);
      COLORS.put("BLUE", 5601279);
      COLORS.put("INDIGO", 4930559);
      COLORS.put("VIOLET", 9395199);
      COLORS.put("PURPLE", 11158783);
      COLORS.put("LAVENDER", 13213951);
      COLORS.put("MAGENTA", 16711935);
      COLORS.put("PINK", 16742314);
      COLORS.put("ROSE", 16727423);
      COLORS.put("WHITE", 16777215);
      COLORS.put("SILVER", 12632256);
      COLORS.put("GRAY", 8421504);
      COLORS.put("BLACK", 0);
   }

   @Environment(EnvType.CLIENT)
   public static record Result(SkyParams params, String error) {
      public boolean ok() {
         return this.error == null;
      }
   }
}
