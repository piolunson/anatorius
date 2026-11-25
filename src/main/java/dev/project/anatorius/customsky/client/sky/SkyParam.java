package dev.project.anatorius.customsky.client.sky;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import dev.project.anatorius.customsky.client.model.SkyModels;

@Environment(EnvType.CLIENT)
public enum SkyParam {
   SCALE("scale", SkyParam.Kind.FLOAT, 0.01F, 100.0F, 1.0F),
   OPACITY("opacity", SkyParam.Kind.FLOAT, 0.0F, 1.0F, 1.0F),
   FADE_IN("fadein", SkyParam.Kind.INT, 0.0F, 72000.0F, 0.0F),
   FADE_OUT("fadeout", SkyParam.Kind.INT, 0.0F, 72000.0F, 0.0F),
   GIF_SPEED("gif_speed", SkyParam.Kind.FLOAT, 0.0F, 100.0F, 1.0F),
   SPEED("speed", SkyParam.Kind.FLOAT, 0.0F, 100.0F, 1.0F),
   ROTATION("rotation", SkyParam.Kind.FLOAT, -360.0F, 360.0F, 0.0F),
   LAYER("layer", SkyParam.Kind.INT, 1.0F, 8.0F, 1.0F),
   POS_X("posX", SkyParam.Kind.POS_X, -360.0F, 360.0F, 0.0F),
   POS_Y("posY", SkyParam.Kind.POS_Y, -90.0F, 90.0F, 0.0F),
   DIRECTION("direction", SkyParam.Kind.WEATHER_DIR, -360.0F, 360.0F, 0.0F),
   TILT("tilt", SkyParam.Kind.FLOAT, 0.0F, 80.0F, 25.0F),
   AMOUNT("amount", SkyParam.Kind.INT, 1.0F, 10.0F, 1.0F),
   DAY_CYCLE("daycycle", SkyParam.Kind.BOOL, 0.0F, 1.0F, 1.0F),
   SKY_COLOR("skycolor", SkyParam.Kind.BOOL, 0.0F, 1.0F, 0.0F),
   ANIM_IN("anim_in", SkyParam.Kind.ANIM_IN, 0.0F, 0.0F, 0.0F),
   ANIM_IN_TIME("anim_in_time", SkyParam.Kind.INT, 1.0F, 72000.0F, 40.0F),
   ANIM_OUT("anim_out", SkyParam.Kind.ANIM_OUT, 0.0F, 0.0F, 0.0F),
   ANIM_OUT_TIME("anim_out_time", SkyParam.Kind.INT, 1.0F, 72000.0F, 40.0F),
   ANIMATION_PRESET("animation_preset", SkyParam.Kind.CELESTIAL, 0.0F, 0.0F, 0.0F),
   ANIMATION_TIME("animation_time", SkyParam.Kind.INT, 1.0F, 72000.0F, 80.0F),
   FORCE_OVERCOME("force_overcome", SkyParam.Kind.OVERCOME, 0.0F, 0.0F, 0.0F),
   SMOOTH_EDGES("smooth_edges", SkyParam.Kind.BOOL, 0.0F, 1.0F, 1.0F),
   RIFT_SHAPE("type", SkyParam.Kind.RIFT_SHAPE, 0.0F, 0.0F, 0.0F),
   RADIUS("radius", SkyParam.Kind.FLOAT, 1.0F, 100.0F, 35.0F),
   COLOR("color", SkyParam.Kind.COLOR, 0.0F, 0.0F, 0.0F),
   BACKGROUND("background", SkyParam.Kind.TEXTURE, 0.0F, 0.0F, 0.0F),
   LENGTH("length", SkyParam.Kind.FLOAT, 0.05F, 8.0F, 1.8F),
   MODEL("model", SkyParam.Kind.MODEL, 0.0F, 0.0F, 0.0F),
   MODEL_SCALE("model_scale", SkyParam.Kind.FLOAT, 0.05F, 10.0F, 1.0F),
   MODEL_TEXTURE("model_texture", SkyParam.Kind.TEXTURE, 0.0F, 0.0F, 0.0F),
   MODEL_POS_X("modelposX", SkyParam.Kind.FLOAT, -90.0F, 90.0F, 0.0F),
   MODEL_POS_Y("modelposY", SkyParam.Kind.FLOAT, -90.0F, 90.0F, 0.0F);

   public static final int MAX_LAYER = 8;
   private final String key;
   private final Kind kind;
   private final float min;
   private final float max;
   private final float defaultValue;

   private SkyParam(String key, Kind kind, float min, float max, float defaultValue) {
      this.key = key;
      this.kind = kind;
      this.min = min;
      this.max = max;
      this.defaultValue = defaultValue;
   }

   public String key() {
      return this.key;
   }

   public Kind kind() {
      return this.kind;
   }

   public float min() {
      return this.min;
   }

   public float max() {
      return this.max;
   }

   public float defaultValue() {
      return this.defaultValue;
   }

   public boolean appliesTo(SkyboxType type) {
      boolean var10000;
      switch (this.ordinal()) {
         case 0:
            var10000 = type.hasScale();
            break;
         case 1:
         case 2:
         case 3:
            var10000 = true;
            break;
         case 4:
            var10000 = type.isTextureBased() && !type.isRift();
            break;
         case 5:
            var10000 = type.isWeather() || type == SkyboxType.CLOUDS;
            break;
         case 6:
            var10000 = type.isDome() || type.isCelestial() || type.isRift();
            break;
         case 7:
            var10000 = type.hasLayers();
            break;
         case 8:
         case 9:
            var10000 = type.isCelestial() || type.isRift();
            break;
         case 10:
         case 11:
            var10000 = type.isWeather();
            break;
         case 12:
         case 13:
            var10000 = type == SkyboxType.STARS;
            break;
         case 14:
            var10000 = type == SkyboxType.CLOUDS;
            break;
         case 15:
         case 16:
         case 17:
         case 18:
            var10000 = type.isDome() || type.isCelestial() || type.isRift() || type == SkyboxType.STARS;
            break;
         case 19:
         case 20:
            var10000 = type.isCelestial();
            break;
         case 21:
            var10000 = type.isDome();
            break;
         case 22:
            var10000 = type.isDome();
            break;
         case 23:
         case 24:
         case 25:
         case 26:
         case 27:
            var10000 = type.isRift();
            break;
         case 28:
         case 30:
            var10000 = type.isCelestial() || type.isRift();
            break;
         case 29:
         case 31:
         case 32:
            var10000 = type.isRift();
            break;
         default:
            throw new MatchException((String)null, (Throwable)null);
      }

      return var10000;
   }

   public static List<SkyParam> forType(SkyboxType type) {
      List<SkyParam> out = new ArrayList();

      for(SkyParam p : values()) {
         if (p.appliesTo(type)) {
            out.add(p);
         }
      }

      return out;
   }

   public static Optional<SkyParam> byKey(String key) {
      return key == null ? Optional.empty() : Arrays.stream(values()).filter((p) -> p.key.equalsIgnoreCase(key)).findFirst();
   }

   private static List<String> withNumbers(List<String> names, String... numbers) {
      List<String> out = new ArrayList(names);
      out.addAll(List.of(numbers));
      return out;
   }

   public List<String> suggestions() {
      List var10000;
      switch (this.kind.ordinal()) {
         case 0:
         case 1:
            var10000 = List.of("1");
            break;
         case 2:
            var10000 = List.of("true", "false");
            break;
         case 3:
            var10000 = withNumbers(SkyDirection.horizontalNames(), "0", "45", "90", "135", "180", "225", "270", "315");
            break;
         case 4:
            var10000 = withNumbers(SkyDirection.verticalNames(), "90", "75", "60", "45", "30", "15", "0", "-15");
            break;
         case 5:
            var10000 = SkyDirection.weatherNames();
            break;
         case 6:
            var10000 = SkyAnim.names(true);
            break;
         case 7:
            var10000 = SkyAnim.names(false);
            break;
         case 8:
            var10000 = CelestialPreset.names();
            break;
         case 9:
            var10000 = SkyOvercome.names();
            break;
         case 10:
            var10000 = RiftShape.names();
            break;
         case 11:
            var10000 = withNumbers(SkyParams.colorNames(), "#ff3030", "#66aaff", "#ffcc00", "#9933ff");
            break;
         case 12:
            var10000 = List.of("<texture>");
            break;
         case 13:
            var10000 = SkyModels.names();
            break;
         default:
            throw new MatchException((String)null, (Throwable)null);
      }

      return var10000;
   }

   private static SkyParam[] $values() {
      return new SkyParam[]{SCALE, OPACITY, FADE_IN, FADE_OUT, GIF_SPEED, SPEED, ROTATION, LAYER, POS_X, POS_Y, DIRECTION, TILT, AMOUNT, DAY_CYCLE, SKY_COLOR, ANIM_IN, ANIM_IN_TIME, ANIM_OUT, ANIM_OUT_TIME, ANIMATION_PRESET, ANIMATION_TIME, FORCE_OVERCOME, SMOOTH_EDGES, RIFT_SHAPE, RADIUS, COLOR, BACKGROUND, LENGTH, MODEL, MODEL_SCALE, MODEL_TEXTURE, MODEL_POS_X, MODEL_POS_Y};
   }

   @Environment(EnvType.CLIENT)
   public static enum Kind {
      FLOAT,
      INT,
      BOOL,
      POS_X,
      POS_Y,
      WEATHER_DIR,
      ANIM_IN,
      ANIM_OUT,
      CELESTIAL,
      OVERCOME,
      RIFT_SHAPE,
      COLOR,
      TEXTURE,
      MODEL;

      private static Kind[] $values() {
         return new Kind[]{FLOAT, INT, BOOL, POS_X, POS_Y, WEATHER_DIR, ANIM_IN, ANIM_OUT, CELESTIAL, OVERCOME, RIFT_SHAPE, COLOR, TEXTURE, MODEL};
      }
   }
}
