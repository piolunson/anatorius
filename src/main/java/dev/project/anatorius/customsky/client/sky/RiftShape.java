package dev.project.anatorius.customsky.client.sky;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public enum RiftShape {
   CRACK("crack", 0.75F, 1.0F, 1.0F),
   SLASH("slash", 0.35F, 0.45F, 1.6F),
   TEAR("tear", 1.0F, 1.9F, 0.85F),
   EYE("eye", 1.0F, 0.35F, 0.6F),
   GATE("gate", 0.55F, 0.25F, 1.35F),
   CIRCLE("circle", 1.0F, 0.12F, 1.0F),
   OVAL("oval", 1.0F, 0.12F, 1.0F),
   SQUARE("square", 1.0F, 0.08F, 1.0F),
   DIAMOND("diamond", 1.0F, 0.08F, 1.0F),
   TRIANGLE("triangle", 1.0F, 0.08F, 1.0F),
   PENTAGON("pentagon", 1.0F, 0.08F, 1.0F),
   HEXAGON("hexagon", 1.0F, 0.08F, 1.0F),
   OCTAGON("octagon", 1.0F, 0.08F, 1.0F),
   STAR("star", 1.0F, 0.1F, 1.0F),
   STAR6("star6", 1.0F, 0.1F, 1.0F),
   STAR8("star8", 1.0F, 0.1F, 1.0F),
   SPARKLE("sparkle", 1.0F, 0.08F, 1.0F),
   SUN("sun", 1.0F, 0.1F, 1.0F),
   FLOWER("flower", 1.0F, 0.15F, 1.0F),
   HEART("heart", 1.0F, 0.1F, 1.0F),
   CROSS("cross", 1.0F, 0.08F, 1.0F),
   GEAR("gear", 1.0F, 0.06F, 1.0F),
   DROP("drop", 1.0F, 0.1F, 1.0F),
   BLOB("blob", 1.0F, 0.2F, 1.0F),
   SHATTER("shatter", 1.0F, 1.0F, 1.0F),
   CRESCENT("crescent", 1.0F, 0.1F, 1.0F),
   CLOVER("clover", 1.0F, 0.12F, 1.0F),
   INFINITY("infinity", 1.0F, 0.12F, 1.0F),
   SQUIRCLE("squircle", 1.0F, 0.08F, 1.0F),
   BURST("burst", 1.0F, 0.12F, 1.0F),
   STAR4("star4", 1.0F, 0.15F, 1.0F),
   STAR12("star12", 1.0F, 0.12F, 1.0F),
   KEYHOLE("keyhole", 1.0F, 0.1F, 1.0F),
   HOURGLASS("hourglass", 1.0F, 0.1F, 1.0F),
   EGG("egg", 1.0F, 0.08F, 1.0F),
   ARCH("arch", 1.0F, 0.1F, 1.0F),
   CLAW("claw", 1.0F, 0.25F, 1.0F);

   private final String id;
   private final float width;
   private final float roughness;
   private final float length;
   private static final float TAU = ((float)Math.PI * 2F);
   private static final float QUARTER = ((float)Math.PI / 2F);
   private static final float[] HEART_OUTLINE = buildHeart(720);

   private RiftShape(String id, float width, float roughness, float length) {
      this.id = id;
      this.width = width;
      this.roughness = roughness;
      this.length = length;
   }

   public String id() {
      return this.id;
   }

   public float roughness() {
      return this.roughness;
   }

   public float length() {
      return this.length;
   }

   public float halfWidth(float t) {
      float s = Math.max(0.0F, Math.min(1.0F, t));
      float var10000;
      switch (this.ordinal()) {
         case 0 -> var10000 = this.width * (float)Math.pow(Math.sin((double)s * Math.PI), 0.65) * (1.0F - 0.25F * s);
         case 1 -> var10000 = this.width * (float)Math.pow(Math.sin((double)s * Math.PI), 1.6);
         case 2 -> var10000 = this.width * (float)Math.pow(Math.sin((double)s * Math.PI), 0.45);
         case 3 -> var10000 = this.width * (float)Math.sin((double)s * Math.PI);
         case 4 -> var10000 = this.width * Math.min(1.0F, (float)Math.sin((double)s * Math.PI) * 2.2F);
         default -> var10000 = this.width * (float)Math.sin((double)s * Math.PI);
      }

      return var10000;
   }

   public boolean isClosed() {
      boolean var10000;
      switch (this.ordinal()) {
         case 0:
         case 1:
         case 2:
         case 3:
         case 4:
            var10000 = false;
            break;
         default:
            var10000 = true;
      }

      return var10000;
   }

   public boolean isJagged() {
      return this == SHATTER;
   }

   public float radiusAt(float angle, float time) {
      float a = (angle % ((float)Math.PI * 2F) + ((float)Math.PI * 2F)) % ((float)Math.PI * 2F);
      float var10000;
      switch (this.ordinal()) {
         case 5:
         case 24:
            var10000 = 1.0F;
            break;
         case 6:
            var10000 = ellipse(a, 0.62F);
            break;
         case 7:
            var10000 = polygon(a, 4, ((float)Math.PI / 4F));
            break;
         case 8:
            var10000 = polygon(a, 4, 0.0F);
            break;
         case 9:
            var10000 = polygon(a, 3, ((float)Math.PI / 2F));
            break;
         case 10:
            var10000 = polygon(a, 5, ((float)Math.PI / 2F));
            break;
         case 11:
            var10000 = polygon(a, 6, 0.0F);
            break;
         case 12:
            var10000 = polygon(a, 8, ((float)Math.PI / 8F));
            break;
         case 13:
            var10000 = star(a, 5, 0.42F);
            break;
         case 14:
            var10000 = star(a, 6, 0.58F);
            break;
         case 15:
            var10000 = star(a, 8, 0.55F);
            break;
         case 16:
            var10000 = sparkle(a);
            break;
         case 17:
            var10000 = star(a, 16, 0.8F);
            break;
         case 18:
            var10000 = flower(a, 6);
            break;
         case 19:
            var10000 = heart(a);
            break;
         case 20:
            var10000 = cross(a, 0.34F);
            break;
         case 21:
            var10000 = gear(a, 10);
            break;
         case 22:
            var10000 = drop(a);
            break;
         case 23:
            var10000 = blob(a, time);
            break;
         case 25:
            var10000 = crescent(a);
            break;
         case 26:
            var10000 = 0.62F + 0.38F * Math.abs((float)Math.cos((double)(2.0F * a)));
            break;
         case 27:
            var10000 = lemniscate(a);
            break;
         case 28:
            var10000 = superellipse(a, 4.0F);
            break;
         case 29:
            var10000 = star(a, 18, 0.62F);
            break;
         case 30:
            var10000 = star(a, 4, 0.3F);
            break;
         case 31:
            var10000 = star(a, 12, 0.68F);
            break;
         case 32:
            var10000 = keyhole(a);
            break;
         case 33:
            var10000 = hourglass(a);
            break;
         case 34:
            var10000 = egg(a);
            break;
         case 35:
            var10000 = arch(a);
            break;
         case 36:
            var10000 = claw(a);
            break;
         default:
            var10000 = 1.0F;
      }

      return var10000;
   }

   private static float polygon(float a, int sides, float corner) {
      float step = ((float)Math.PI * 2F) / (float)sides;
      float half = step / 2.0F;
      float t = ((a - corner) % step + step) % step;
      return (float)(Math.cos((double)half) / Math.cos((double)(t - half)));
   }

   private static float star(float a, int points, float inner) {
      float step = ((float)Math.PI * 2F) / (float)points;
      float half = step / 2.0F;
      float t = ((a - ((float)Math.PI / 2F)) % step + step) % step;
      float phi = t > half ? step - t : t;
      float nx = inner * (float)Math.cos((double)half);
      float ny = inner * (float)Math.sin((double)half);
      float denom = ny * (float)Math.cos((double)phi) - (nx - 1.0F) * (float)Math.sin((double)phi);
      return denom <= 1.0E-5F ? 1.0F : ny / denom;
   }

   private static float sparkle(float a) {
      double p = 0.55;
      double c = Math.abs(Math.cos((double)a));
      double s = Math.abs(Math.sin((double)a));
      double r = Math.pow(Math.pow(c, p) + Math.pow(s, p), (double)-1.0F / p);
      return (float)(0.06 + 0.94 * r);
   }

   private static float flower(float a, int petals) {
      float wave = Math.abs((float)Math.cos((double)((float)petals * 0.5F * (a - ((float)Math.PI / 2F)))));
      return 0.5F + 0.5F * (float)Math.sqrt((double)wave);
   }

   private static float crescent(float a) {
      float bite = Math.max(0.0F, -((float)Math.cos((double)a)));
      return Math.max(0.12F, 1.0F - 0.85F * (float)Math.pow((double)bite, 0.55));
   }

   private static float lemniscate(float a) {
      float c = (float)Math.cos((double)(2.0F * a));
      return Math.max(0.1F, (float)Math.sqrt((double)Math.max(0.0F, c)) * 0.95F + 0.08F);
   }

   private static float superellipse(float a, float n) {
      double c = Math.pow(Math.abs(Math.cos((double)a)), (double)n);
      double s = Math.pow(Math.abs(Math.sin((double)a)), (double)n);
      return (float)Math.pow(c + s, (double)-1.0F / (double)n);
   }

   private static float keyhole(float a) {
      float up = (float)Math.sin((double)a);
      if (up >= 0.0F) {
         return 1.0F;
      } else {
         float side = Math.abs((float)Math.cos((double)a));
         return Math.min(1.0F, 0.3F / Math.max(0.3F, side));
      }
   }

   private static float hourglass(float a) {
      float side = Math.abs((float)Math.cos((double)a));
      return Math.max(0.1F, Math.min(1.0F, 0.42F / Math.max(0.42F, side)));
   }

   private static float egg(float a) {
      float up = (float)Math.sin((double)a);
      return ellipse(a, 0.78F) * (1.0F - 0.14F * up);
   }

   private static float arch(float a) {
      float up = (float)Math.sin((double)a);
      return up > 0.2F ? ellipse(a, 0.7F) : Math.min(ellipse(a, 0.7F), 0.75F / Math.max(0.2F, Math.abs(up)));
   }

   private static float claw(float a) {
      float sweep = (float)Math.sin((double)(a * 0.5F));
      return Math.max(0.1F, 0.25F + 0.75F * sweep * sweep * (0.6F + 0.4F * (float)Math.cos((double)a)));
   }

   private static float ellipse(float a, float wide) {
      float c = (float)Math.cos((double)a) / wide;
      float s = (float)Math.sin((double)a);
      return 1.0F / (float)Math.sqrt((double)(c * c + s * s));
   }

   private static float cross(float a, float arm) {
      float c = Math.max(1.0E-4F, Math.abs((float)Math.cos((double)a)));
      float s = Math.max(1.0E-4F, Math.abs((float)Math.sin((double)a)));
      float upright = Math.min(arm / c, 1.0F / s);
      float across = Math.min(arm / s, 1.0F / c);
      return Math.max(upright, across) / (float)Math.sqrt((double)(1.0F + arm * arm));
   }

   private static float gear(float a, int teeth) {
      float wave = (float)Math.cos((double)((float)teeth * (a - ((float)Math.PI / 2F))));
      float tooth = Math.max(0.0F, Math.min(1.0F, wave * 2.5F + 0.5F));
      return 0.8F + 0.2F * tooth;
   }

   private static float drop(float a) {
      float off = Math.abs(((a - ((float)Math.PI / 2F) + (float)Math.PI) % ((float)Math.PI * 2F) + ((float)Math.PI * 2F)) % ((float)Math.PI * 2F) - (float)Math.PI);
      float round = 0.62F;
      if ((double)off > (Math.PI / 2D)) {
         return round;
      } else {
         float k = off / ((float)Math.PI / 2F);
         float tip = 1.0F / (1.0F + (1.0F / round - 1.0F) * (float)Math.pow((double)k, 0.8));
         return Math.max(round, tip);
      }
   }

   private static float blob(float a, float time) {
      float r = 1.0F + 0.1F * (float)Math.sin((double)(3.0F * a + time * 0.35F)) + 0.07F * (float)Math.sin((double)(5.0F * a - time * 0.55F + 1.7F)) + 0.04F * (float)Math.sin((double)(8.0F * a + time * 0.8F + 0.4F));
      return r / 1.21F;
   }

   private static float heart(float a) {
      float f = a / ((float)Math.PI * 2F) * (float)HEART_OUTLINE.length;
      int i = (int)Math.floor((double)f);
      float t = f - (float)i;
      float r0 = HEART_OUTLINE[(i % HEART_OUTLINE.length + HEART_OUTLINE.length) % HEART_OUTLINE.length];
      float r1 = HEART_OUTLINE[((i + 1) % HEART_OUTLINE.length + HEART_OUTLINE.length) % HEART_OUTLINE.length];
      return r0 + (r1 - r0) * t;
   }

   private static float[] buildHeart(int bins) {
      float[] out = new float[bins];
      boolean[] hit = new boolean[bins];
      float centreY = -2.5F;
      int samples = 8192;
      float max = 0.0F;

      for(int k = 0; k < samples; ++k) {
         double t = (double)k * Math.PI * (double)2.0F / (double)samples;
         double x = (double)16.0F * Math.pow(Math.sin(t), (double)3.0F);
         double y = (double)13.0F * Math.cos(t) - (double)5.0F * Math.cos((double)2.0F * t) - (double)2.0F * Math.cos((double)3.0F * t) - Math.cos((double)4.0F * t) - (double)centreY;
         float r = (float)Math.sqrt(x * x + y * y);
         double ang = Math.atan2(y, x);
         int bin = (int)Math.floor((ang % (Math.PI * 2D) + (Math.PI * 2D)) % (Math.PI * 2D) / (Math.PI * 2D) * (double)bins);
         bin = Math.min(bins - 1, Math.max(0, bin));
         out[bin] = hit[bin] ? Math.min(out[bin], r) : r;
         hit[bin] = true;
         max = Math.max(max, r);
      }

      for(int i = 0; i < bins; ++i) {
         if (!hit[i]) {
            int lo = i;

            int hi;
            for(hi = i; !hit[(lo % bins + bins) % bins]; --lo) {
            }

            while(!hit[hi % bins]) {
               ++hi;
            }

            float rl = out[(lo % bins + bins) % bins];
            float rh = out[hi % bins];
            out[i] = rl + (rh - rl) * (float)(i - lo) / (float)(hi - lo);
         }
      }

      for(int i = 0; i < bins; ++i) {
         out[i] /= max;
      }

      return out;
   }

   public static Optional<RiftShape> byId(String id) {
      return id == null ? Optional.empty() : Arrays.stream(values()).filter((s) -> s.id.equalsIgnoreCase(id)).findFirst();
   }

   public static List<String> names() {
      List<String> out = new ArrayList();

      for(RiftShape s : values()) {
         out.add(s.id);
      }

      return out;
   }

   private static RiftShape[] $values() {
      return new RiftShape[]{CRACK, SLASH, TEAR, EYE, GATE, CIRCLE, OVAL, SQUARE, DIAMOND, TRIANGLE, PENTAGON, HEXAGON, OCTAGON, STAR, STAR6, STAR8, SPARKLE, SUN, FLOWER, HEART, CROSS, GEAR, DROP, BLOB, SHATTER, CRESCENT, CLOVER, INFINITY, SQUIRCLE, BURST, STAR4, STAR12, KEYHOLE, HOURGLASS, EGG, ARCH, CLAW};
   }
}
