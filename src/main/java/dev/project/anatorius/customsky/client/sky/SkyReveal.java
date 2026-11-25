package dev.project.anatorius.customsky.client.sky;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class SkyReveal {
   private static final float EDGE = 0.14F;
   private static final int MAP_U = 256;
   private static final int MAP_V = 160;
   private static final float[][] MAPS = new float[SkyReveal.Shape.values().length][];

   private SkyReveal() {
   }

   public static float alpha(Shape shape, float progress, float u, float v) {
      if (shape == SkyReveal.Shape.ALL) {
         return 1.0F;
      } else if (progress >= 1.0F) {
         return 1.0F;
      } else if (progress <= 0.0F) {
         return 0.0F;
      } else {
         float edge = edgeFor(shape);
         float threshold = mapAt(shape, u, v);
         float front = progress * (1.0F + edge * 2.0F) - edge;
         return smootherstep(threshold - edge, threshold + edge, front);
      }
   }

   private static float mapAt(Shape shape, float u, float v) {
      float[] map = MAPS[shape.ordinal()];
      if (map == null) {
         map = buildMap(shape);
      }

      float fu = frac(u) * 256.0F;
      float fv = clamp(v) * 159.0F;
      int u0 = (int)fu;
      int v0 = (int)fv;
      float du = fu - (float)u0;
      float dv = fv - (float)v0;
      int u1 = (u0 + 1) % 256;
      int v1 = Math.min(v0 + 1, 159);
      u0 %= 256;
      float a = map[v0 * 256 + u0];
      float b = map[v0 * 256 + u1];
      float c = map[v1 * 256 + u0];
      float d = map[v1 * 256 + u1];
      float top = a + (b - a) * du;
      float bottom = c + (d - c) * du;
      return top + (bottom - top) * dv;
   }

   private static synchronized float[] buildMap(Shape shape) {
      float[] existing = MAPS[shape.ordinal()];
      if (existing != null) {
         return existing;
      } else {
         float[] map = new float['ꀀ'];
         float feather = featherFor(shape);

         for(int y = 0; y < 160; ++y) {
            float v = (float)y / 159.0F;

            for(int x = 0; x < 256; ++x) {
               float u = (float)x / 256.0F;
               float t = threshold(shape, u, v);
               if (feather > 0.0F) {
                  t = clamp(t + (featherNoise(shape, u, v) - 0.5F) * feather);
               }

               map[y * 256 + x] = t;
            }
         }

         MAPS[shape.ordinal()] = map;
         return map;
      }
   }

   private static float edgeFor(Shape shape) {
      float var10000;
      switch (shape.ordinal()) {
         case 1:
         case 2:
            var10000 = 0.09F;
            break;
         case 3:
         case 4:
         case 5:
         case 6:
         case 10:
         case 11:
         case 12:
         case 13:
         case 14:
         default:
            var10000 = 0.14F;
            break;
         case 7:
         case 8:
         case 9:
         case 15:
            var10000 = 0.16F;
      }

      return var10000;
   }

   private static float featherNoise(Shape shape, float u, float v) {
      float var10000;
      switch (shape.ordinal()) {
         case 1:
         case 2:
            var10000 = fbm(u, v, 7, 5.0F, 3);
            break;
         default:
            var10000 = fbm(u, v, 26, 20.0F, 2);
      }

      return var10000;
   }

   private static float featherFor(Shape shape) {
      float var10000;
      switch (shape.ordinal()) {
         case 0:
            var10000 = 0.0F;
            break;
         case 1:
         case 2:
            var10000 = 0.2F;
            break;
         case 3:
         case 4:
         case 5:
         case 6:
         case 7:
         case 8:
         case 9:
         default:
            var10000 = 0.07F;
            break;
         case 10:
         case 11:
         case 12:
         case 13:
            var10000 = 0.04F;
      }

      return var10000;
   }

   private static float threshold(Shape shape, float u, float v) {
      float var10000;
      switch (shape.ordinal()) {
         case 0 -> var10000 = 0.0F;
         case 1 -> var10000 = v;
         case 2 -> var10000 = 1.0F - v;
         case 3 -> var10000 = wrapped(u);
         case 4 -> var10000 = 1.0F - wrapped(u);
         case 5 -> var10000 = (1.0F - v) * 0.7F + wrapped(u) * 0.3F;
         case 6 -> var10000 = (1.0F - v) * 0.7F + (1.0F - wrapped(u)) * 0.3F;
         case 7 -> var10000 = Math.abs(v - 0.5F) * 2.0F;
         case 8 -> var10000 = 1.0F - Math.abs(v - 0.5F) * 2.0F;
         case 9 -> var10000 = frac(u + v * 0.75F);
         case 10 -> var10000 = fbm(u, v, 4, 3.2F, 3);
         case 11 -> var10000 = clamp(v * 0.62F + fbm(u, v, 5, 4.0F, 3) * 0.52F);
         case 12 -> var10000 = clamp((1.0F - v) * 0.62F + fbm(u, v, 5, 4.0F, 3) * 0.52F);
         case 13 -> var10000 = cell(u, v, 18, 11);
         case 14 -> var10000 = frac(u * 8.0F);
         case 15 -> var10000 = clamp(v * 0.55F + frac(u + 0.55F * (float)Math.log((double)Math.max(v, 0.01F))) * 0.45F);
         default -> throw new MatchException((String)null, (Throwable)null);
      }

      return var10000;
   }

   private static float wrapped(float u) {
      float x = frac(u);
      return x <= 0.5F ? x * 2.0F : (1.0F - x) * 2.0F;
   }

   public static float fbm(float u, float v, int cellsU, float cellsV, int octaves) {
      float sum = 0.0F;
      float amplitude = 0.5F;
      float total = 0.0F;
      int periodU = Math.max(1, cellsU);
      float scaleV = cellsV;

      for(int i = 0; i < octaves; ++i) {
         sum += perlin(u * (float)periodU, v * scaleV, periodU) * amplitude;
         total += amplitude;
         periodU *= 2;
         scaleV *= 2.0F;
         amplitude *= 0.5F;
      }

      return clamp(0.5F + sum / total * 0.5F);
   }

   public static float noise(float x, float y) {
      return clamp(0.5F + perlin(x, y, 4096) * 0.7F);
   }

   private static float perlin(float x, float y, int periodX) {
      int xi = floor(x);
      int yi = floor(y);
      float xf = x - (float)xi;
      float yf = y - (float)yi;
      float sx = quintic(xf);
      float sy = quintic(yf);
      float n00 = dot(xi, yi, periodX, xf, yf);
      float n10 = dot(xi + 1, yi, periodX, xf - 1.0F, yf);
      float n01 = dot(xi, yi + 1, periodX, xf, yf - 1.0F);
      float n11 = dot(xi + 1, yi + 1, periodX, xf - 1.0F, yf - 1.0F);
      float top = n00 + (n10 - n00) * sx;
      float bottom = n01 + (n11 - n01) * sx;
      return top + (bottom - top) * sy;
   }

   private static float dot(int gx, int gy, int periodX, float dx, float dy) {
      int h = hash(Math.floorMod(gx, periodX), gy) & 7;
      float var10000;
      switch (h) {
         case 0 -> var10000 = dx;
         case 1 -> var10000 = -dx;
         case 2 -> var10000 = dy;
         case 3 -> var10000 = -dy;
         case 4 -> var10000 = (dx + dy) * 0.7071F;
         case 5 -> var10000 = (dx - dy) * 0.7071F;
         case 6 -> var10000 = (-dx + dy) * 0.7071F;
         default -> var10000 = (-dx - dy) * 0.7071F;
      }

      return var10000;
   }

   private static float cell(float u, float v, int cols, int rows) {
      int cx = Math.floorMod(floor(u * (float)cols), cols);
      int cy = floor(v * (float)rows);
      return (float)(hash(cx, cy) & '\uffff') / 65535.0F;
   }

   private static int hash(int x, int y) {
      int h = x * 374761393 + y * 668265263;
      h = (h ^ h >> 13) * 1274126177;
      return h ^ h >> 16;
   }

   private static int floor(float value) {
      int i = (int)value;
      return value < (float)i ? i - 1 : i;
   }

   private static float quintic(float t) {
      return t * t * t * (t * (t * 6.0F - 15.0F) + 10.0F);
   }

   private static float smootherstep(float edge0, float edge1, float x) {
      if (edge1 <= edge0) {
         return x < edge0 ? 0.0F : 1.0F;
      } else {
         float t = clamp((x - edge0) / (edge1 - edge0));
         return quintic(t);
      }
   }

   private static float frac(float value) {
      return value - (float)Math.floor((double)value);
   }

   private static float clamp(float value) {
      return value < 0.0F ? 0.0F : (value > 1.0F ? 1.0F : value);
   }

   @Environment(EnvType.CLIENT)
   public static enum Shape {
      ALL,
      TOP,
      BOTTOM,
      LEFT,
      RIGHT,
      DIAGONAL,
      DIAGONAL_REVERSE,
      IRIS,
      IRIS_INVERTED,
      SPIRAL,
      NOISE,
      NOISE_TOP,
      NOISE_BOTTOM,
      SHATTER,
      BLINDS,
      VORTEX;

      private static Shape[] $values() {
         return new Shape[]{ALL, TOP, BOTTOM, LEFT, RIGHT, DIAGONAL, DIAGONAL_REVERSE, IRIS, IRIS_INVERTED, SPIRAL, NOISE, NOISE_TOP, NOISE_BOTTOM, SHATTER, BLINDS, VORTEX};
      }
   }
}
