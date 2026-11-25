package dev.project.anatorius.customsky.client.sky;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public enum SkyAnim {
   NONE("none", SkyAnim.Motion.NONE, SkyReveal.Shape.ALL, SkyAnim.Target.ANY, true, true),
   FADE_IN("fade_in", SkyAnim.Motion.FADE, SkyReveal.Shape.ALL, SkyAnim.Target.ANY, true, false),
   ZOOM_IN("zoom_in", SkyAnim.Motion.ZOOM, SkyReveal.Shape.ALL, SkyAnim.Target.ANY, true, false),
   SPIN_IN("spin_in", SkyAnim.Motion.SPIN, SkyReveal.Shape.ALL, SkyAnim.Target.ANY, true, false),
   DROP_IN("drop_in", SkyAnim.Motion.DROP, SkyReveal.Shape.ALL, SkyAnim.Target.DOME, true, false),
   RIPPLE_IN("ripple_in", SkyAnim.Motion.RIPPLE, SkyReveal.Shape.IRIS, SkyAnim.Target.DOME, true, false),
   TOP_MERGE_IN("top_merge_in", SkyAnim.Motion.NONE, SkyReveal.Shape.TOP, SkyAnim.Target.DOME, true, false),
   BOTTOM_MERGE_IN("bottom_merge_in", SkyAnim.Motion.NONE, SkyReveal.Shape.BOTTOM, SkyAnim.Target.DOME, true, false),
   LEFT_WIPE_IN("left_wipe_in", SkyAnim.Motion.NONE, SkyReveal.Shape.LEFT, SkyAnim.Target.DOME, true, false),
   RIGHT_WIPE_IN("right_wipe_in", SkyAnim.Motion.NONE, SkyReveal.Shape.RIGHT, SkyAnim.Target.DOME, true, false),
   DIAGONAL_IN("diagonal_in", SkyAnim.Motion.NONE, SkyReveal.Shape.DIAGONAL, SkyAnim.Target.DOME, true, false),
   DIAGONAL_REVERSE_IN("diagonal_reverse_in", SkyAnim.Motion.NONE, SkyReveal.Shape.DIAGONAL_REVERSE, SkyAnim.Target.DOME, true, false),
   CURTAIN_IN("curtain_in", SkyAnim.Motion.CURTAIN, SkyReveal.Shape.TOP, SkyAnim.Target.DOME, true, false),
   BLINDS_IN("blinds_in", SkyAnim.Motion.NONE, SkyReveal.Shape.BLINDS, SkyAnim.Target.DOME, true, false),
   IRIS_IN("iris_in", SkyAnim.Motion.NONE, SkyReveal.Shape.IRIS, SkyAnim.Target.DOME, true, false),
   IRIS_CLOSE_IN("iris_close_in", SkyAnim.Motion.NONE, SkyReveal.Shape.IRIS_INVERTED, SkyAnim.Target.DOME, true, false),
   SPIRAL_IN("spiral_in", SkyAnim.Motion.NONE, SkyReveal.Shape.SPIRAL, SkyAnim.Target.DOME, true, false),
   DISSOLVE_IN("dissolve_in", SkyAnim.Motion.NONE, SkyReveal.Shape.NOISE, SkyAnim.Target.DOME, true, false),
   CONSUME_TOP_IN("consume_top_in", SkyAnim.Motion.NONE, SkyReveal.Shape.NOISE_TOP, SkyAnim.Target.DOME, true, false),
   CONSUME_BOTTOM_IN("consume_bottom_in", SkyAnim.Motion.NONE, SkyReveal.Shape.NOISE_BOTTOM, SkyAnim.Target.DOME, true, false),
   SHATTER_IN("shatter_in", SkyAnim.Motion.NONE, SkyReveal.Shape.SHATTER, SkyAnim.Target.DOME, true, false),
   VORTEX_IN("vortex_in", SkyAnim.Motion.SPIN, SkyReveal.Shape.VORTEX, SkyAnim.Target.DOME, true, false),
   SMOKE_IN("smoke_in", SkyAnim.Motion.DRIFT, SkyReveal.Shape.NOISE, SkyAnim.Target.DOME, true, false),
   RANDOM_SCALE_IN("random_scale_in", SkyAnim.Motion.RANDOM_SCALE, SkyReveal.Shape.ALL, SkyAnim.Target.STARS, true, false),
   STAR_FALL_IN("star_fall_in", SkyAnim.Motion.STAR_FALL, SkyReveal.Shape.ALL, SkyAnim.Target.STARS, true, false),
   TWINKLE_IN("twinkle_in", SkyAnim.Motion.TWINKLE, SkyReveal.Shape.ALL, SkyAnim.Target.STARS, true, false),
   FADE_OUT("fade_out", SkyAnim.Motion.FADE, SkyReveal.Shape.ALL, SkyAnim.Target.ANY, false, false),
   ZOOM_OUT("zoom_out", SkyAnim.Motion.ZOOM, SkyReveal.Shape.ALL, SkyAnim.Target.ANY, false, false),
   SPIN_OUT("spin_out", SkyAnim.Motion.SPIN, SkyReveal.Shape.ALL, SkyAnim.Target.ANY, false, false),
   DROP_OUT("drop_out", SkyAnim.Motion.DROP, SkyReveal.Shape.ALL, SkyAnim.Target.DOME, false, false),
   RIPPLE_OUT("ripple_out", SkyAnim.Motion.RIPPLE, SkyReveal.Shape.IRIS, SkyAnim.Target.DOME, false, false),
   TOP_MERGE_OUT("top_merge_out", SkyAnim.Motion.NONE, SkyReveal.Shape.TOP, SkyAnim.Target.DOME, false, false),
   BOTTOM_MERGE_OUT("bottom_merge_out", SkyAnim.Motion.NONE, SkyReveal.Shape.BOTTOM, SkyAnim.Target.DOME, false, false),
   LEFT_WIPE_OUT("left_wipe_out", SkyAnim.Motion.NONE, SkyReveal.Shape.LEFT, SkyAnim.Target.DOME, false, false),
   RIGHT_WIPE_OUT("right_wipe_out", SkyAnim.Motion.NONE, SkyReveal.Shape.RIGHT, SkyAnim.Target.DOME, false, false),
   DIAGONAL_OUT("diagonal_out", SkyAnim.Motion.NONE, SkyReveal.Shape.DIAGONAL, SkyAnim.Target.DOME, false, false),
   DIAGONAL_REVERSE_OUT("diagonal_reverse_out", SkyAnim.Motion.NONE, SkyReveal.Shape.DIAGONAL_REVERSE, SkyAnim.Target.DOME, false, false),
   CURTAIN_OUT("curtain_out", SkyAnim.Motion.CURTAIN, SkyReveal.Shape.TOP, SkyAnim.Target.DOME, false, false),
   BLINDS_OUT("blinds_out", SkyAnim.Motion.NONE, SkyReveal.Shape.BLINDS, SkyAnim.Target.DOME, false, false),
   IRIS_OUT("iris_out", SkyAnim.Motion.NONE, SkyReveal.Shape.IRIS, SkyAnim.Target.DOME, false, false),
   IRIS_CLOSE_OUT("iris_close_out", SkyAnim.Motion.NONE, SkyReveal.Shape.IRIS_INVERTED, SkyAnim.Target.DOME, false, false),
   SPIRAL_OUT("spiral_out", SkyAnim.Motion.NONE, SkyReveal.Shape.SPIRAL, SkyAnim.Target.DOME, false, false),
   DISSOLVE_OUT("dissolve_out", SkyAnim.Motion.NONE, SkyReveal.Shape.NOISE, SkyAnim.Target.DOME, false, false),
   CONSUME_TOP_OUT("consume_top_out", SkyAnim.Motion.NONE, SkyReveal.Shape.NOISE_TOP, SkyAnim.Target.DOME, false, false),
   CONSUME_BOTTOM_OUT("consume_bottom_out", SkyAnim.Motion.NONE, SkyReveal.Shape.NOISE_BOTTOM, SkyAnim.Target.DOME, false, false),
   SHATTER_OUT("shatter_out", SkyAnim.Motion.NONE, SkyReveal.Shape.SHATTER, SkyAnim.Target.DOME, false, false),
   VORTEX_OUT("vortex_out", SkyAnim.Motion.SPIN, SkyReveal.Shape.VORTEX, SkyAnim.Target.DOME, false, false),
   SMOKE_OUT("smoke_out", SkyAnim.Motion.DRIFT, SkyReveal.Shape.NOISE, SkyAnim.Target.DOME, false, false),
   RANDOM_SCALE_OUT("random_scale_out", SkyAnim.Motion.RANDOM_SCALE, SkyReveal.Shape.ALL, SkyAnim.Target.STARS, false, false),
   STAR_FALL_OUT("star_fall_out", SkyAnim.Motion.STAR_FALL, SkyReveal.Shape.ALL, SkyAnim.Target.STARS, false, false),
   TWINKLE_OUT("twinkle_out", SkyAnim.Motion.TWINKLE, SkyReveal.Shape.ALL, SkyAnim.Target.STARS, false, false);

   private final String id;
   private final Motion motion;
   private final SkyReveal.Shape reveal;
   private final Target target;
   private final boolean entrance;
   private final boolean none;

   private SkyAnim(String id, Motion motion, SkyReveal.Shape reveal, Target target, boolean entrance, boolean none) {
      this.id = id;
      this.motion = motion;
      this.reveal = reveal;
      this.target = target;
      this.entrance = entrance;
      this.none = none;
   }

   public String id() {
      return this.id;
   }

   public boolean isNone() {
      return this.none;
   }

   public boolean isEntrance() {
      return this.entrance;
   }

   public boolean starsOnly() {
      return this.target == SkyAnim.Target.STARS;
   }

   public static Optional<SkyAnim> byId(String id) {
      return id == null ? Optional.empty() : Arrays.stream(values()).filter((a) -> a.id.equalsIgnoreCase(id)).findFirst();
   }

   public static List<String> names(boolean entrance) {
      List<String> out = new ArrayList();

      for(SkyAnim a : values()) {
         if (a.none || a.entrance == entrance) {
            out.add(a.id);
         }
      }

      return out;
   }

   public boolean fits(SkyboxType type) {
      boolean var10000;
      switch (this.target.ordinal()) {
         case 0 -> var10000 = true;
         case 1 -> var10000 = type.isDome();
         case 2 -> var10000 = type == SkyboxType.STARS;
         default -> throw new MatchException((String)null, (Throwable)null);
      }

      return var10000;
   }

   public Sample sample(float progress) {
      float t = Math.max(0.0F, Math.min(1.0F, progress));
      if (this.none) {
         return SkyAnim.Sample.IDLE;
      } else {
         float p = this.entrance ? t : 1.0F - t;
         float eased = ease(p);
         float arrived = easeOut(p);
         float alpha = 1.0F;
         float scale = 1.0F;
         float offsetX = 0.0F;
         float offsetY = 0.0F;
         float spin = 0.0F;
         float scatter = 1.0F;
         switch (this.motion.ordinal()) {
            case 0:
            default:
               break;
            case 1:
               alpha = eased;
               break;
            case 2:
               alpha = eased;
               scale = 0.55F + arrived * 0.45F;
               break;
            case 3:
               alpha = eased;
               scale = 0.7F + arrived * 0.3F;
               spin = (1.0F - arrived) * 210.0F;
               break;
            case 4:
               alpha = eased;
               offsetY = (1.0F - arrived) * 0.85F;
               break;
            case 5:
               offsetY = (1.0F - arrived) * 0.45F;
               break;
            case 6:
               scale = 1.0F + (float)Math.sin((double)p * Math.PI * (double)3.0F) * 0.06F * (1.0F - p);
               break;
            case 7:
               offsetX = (1.0F - arrived) * 0.22F;
               offsetY = (1.0F - arrived) * 0.12F;
               break;
            case 8:
               scale = arrived;
               scatter = eased;
               break;
            case 9:
               alpha = eased;
               offsetY = (1.0F - arrived) * 0.55F;
               scatter = eased;
               break;
            case 10:
               alpha = eased;
               scale = 0.75F + arrived * 0.25F;
               scatter = eased;
         }

         return new Sample(alpha, scale, offsetX, offsetY, spin, this.reveal, eased, scatter);
      }
   }

   private static float ease(float t) {
      return t * t * t * (t * (t * 6.0F - 15.0F) + 10.0F);
   }

   private static float easeOut(float t) {
      float inv = 1.0F - t;
      return 1.0F - inv * inv * inv;
   }

   public static float scatterAlpha(float scatter, int pieceHash) {
      if (scatter >= 1.0F) {
         return 1.0F;
      } else if (scatter <= 0.0F) {
         return 0.0F;
      } else {
         float slot = (float)(pieceHash * 1664525 + 1013904223 & '\uffff') / 65535.0F;
         float local = scatter * 1.35F - slot * 0.35F;
         return Math.max(0.0F, Math.min(1.0F, local));
      }
   }

   private static SkyAnim[] $values() {
      return new SkyAnim[]{NONE, FADE_IN, ZOOM_IN, SPIN_IN, DROP_IN, RIPPLE_IN, TOP_MERGE_IN, BOTTOM_MERGE_IN, LEFT_WIPE_IN, RIGHT_WIPE_IN, DIAGONAL_IN, DIAGONAL_REVERSE_IN, CURTAIN_IN, BLINDS_IN, IRIS_IN, IRIS_CLOSE_IN, SPIRAL_IN, DISSOLVE_IN, CONSUME_TOP_IN, CONSUME_BOTTOM_IN, SHATTER_IN, VORTEX_IN, SMOKE_IN, RANDOM_SCALE_IN, STAR_FALL_IN, TWINKLE_IN, FADE_OUT, ZOOM_OUT, SPIN_OUT, DROP_OUT, RIPPLE_OUT, TOP_MERGE_OUT, BOTTOM_MERGE_OUT, LEFT_WIPE_OUT, RIGHT_WIPE_OUT, DIAGONAL_OUT, DIAGONAL_REVERSE_OUT, CURTAIN_OUT, BLINDS_OUT, IRIS_OUT, IRIS_CLOSE_OUT, SPIRAL_OUT, DISSOLVE_OUT, CONSUME_TOP_OUT, CONSUME_BOTTOM_OUT, SHATTER_OUT, VORTEX_OUT, SMOKE_OUT, RANDOM_SCALE_OUT, STAR_FALL_OUT, TWINKLE_OUT};
   }

   @Environment(EnvType.CLIENT)
   private static enum Motion {
      NONE,
      FADE,
      ZOOM,
      SPIN,
      DROP,
      CURTAIN,
      RIPPLE,
      DRIFT,
      RANDOM_SCALE,
      STAR_FALL,
      TWINKLE;

      private static Motion[] $values() {
         return new Motion[]{NONE, FADE, ZOOM, SPIN, DROP, CURTAIN, RIPPLE, DRIFT, RANDOM_SCALE, STAR_FALL, TWINKLE};
      }
   }

   @Environment(EnvType.CLIENT)
   private static enum Target {
      ANY,
      DOME,
      STARS;

      private static Target[] $values() {
         return new Target[]{ANY, DOME, STARS};
      }
   }

   @Environment(EnvType.CLIENT)
   public static record Sample(float alpha, float scale, float offsetX, float offsetY, float spin, SkyReveal.Shape reveal, float progress, float scatter) {
      public static final Sample IDLE;

      public float alphaAt(float u, float v) {
         return this.alpha * SkyReveal.alpha(this.reveal, this.progress, u, v);
      }

      static {
         IDLE = new Sample(1.0F, 1.0F, 0.0F, 0.0F, 0.0F, SkyReveal.Shape.ALL, 1.0F, 1.0F);
      }
   }
}
