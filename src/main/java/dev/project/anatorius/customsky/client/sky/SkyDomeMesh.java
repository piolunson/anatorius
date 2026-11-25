package dev.project.anatorius.customsky.client.sky;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class SkyDomeMesh {
   public static final int SEGMENTS = 128;
   public static final int RINGS = 76;
   public static final int SEAM_BLEND_SEGMENTS = 10;
   private static final int COARSE_STEP = 2;
   private static final float[] SIN_THETA = new float[77];
   private static final float[] COS_THETA = new float[77];
   private static final float[] SIN_PHI = new float[129];
   private static final float[] COS_PHI = new float[129];

   private SkyDomeMesh() {
   }

   public static int quadCount() {
      return 11248;
   }

   public static void build(float radius, float rotationDegrees, float alpha, VertexSink sink) {
      build(radius, rotationDegrees, (SkyboxType)null, SkyAnim.Sample.IDLE, alpha, false, sink);
   }

   public static void build(float radius, float rotationDegrees, SkyboxType type, SkyAnim.Sample anim, float alpha, VertexSink sink) {
      build(radius, rotationDegrees, type, anim, alpha, false, sink);
   }

   public static void buildGlow(float radius, float rotationDegrees, SkyboxType type, SkyAnim.Sample anim, float alpha, VertexSink sink) {
      build(radius, rotationDegrees, type, anim, alpha, true, sink);
   }

   private static void build(float radius, float rotationDegrees, SkyboxType type, SkyAnim.Sample anim, float alpha, boolean glowPass, VertexSink sink) {
      SkyAnim.Sample a = anim == null ? SkyAnim.Sample.IDLE : anim;
      float r = radius * a.scale();
      double rotation = Math.toRadians((double)(rotationDegrees + a.spin()));
      float cosRot = (float)Math.cos(rotation);
      float sinRot = (float)Math.sin(rotation);
      float offsetX = a.offsetX() * radius;
      float offsetY = a.offsetY() * radius;
      SkyEffects.Frame fx = SkyEffects.frame(type);
      float[] fxOut = new float[7];
      boolean plainAnim = a.reveal() == SkyReveal.Shape.ALL || a.progress() >= 1.0F;
      int step = plainAnim && a.scatter() >= 1.0F ? 2 : 1;
      int rows = 76 / step;
      int cols = 128 / step;
      int n = (rows + 1) * (cols + 1);
      float[] px = new float[n];
      float[] py = new float[n];
      float[] pz = new float[n];
      float[] pu = new float[n];
      float[] pv = new float[n];
      float[] pa = new float[n];
      int[] prgb = new int[n];

      for(int i = 0; i <= rows; ++i) {
         int ring = i * step;
         float sinTheta = SIN_THETA[ring];
         float y = r * COS_THETA[ring] + offsetY;
         float v0 = (float)ring / 76.0F;

         for(int j = 0; j <= cols; ++j) {
            int seg = j * step;
            int k = i * (cols + 1) + j;
            float baseX = sinTheta * COS_PHI[seg];
            float baseZ = sinTheta * SIN_PHI[seg];
            px[k] = r * (baseX * cosRot - baseZ * sinRot) + offsetX;
            py[k] = y;
            pz[k] = r * (baseX * sinRot + baseZ * cosRot);
            float u = (float)seg / 128.0F;
            float v = v0;
            float vertexAlpha = plainAnim ? a.alpha() : a.alphaAt(u, v0);
            float cr = 1.0F;
            float cg = 1.0F;
            float cb = 1.0F;
            if (fx != null) {
               fx.sampleInto(u, v0, fxOut);
               u += fxOut[5];
               v = Math.max(0.0F, Math.min(1.0F, v0 + fxOut[6]));
               if (glowPass) {
                  cr = fxOut[0];
                  cg = fxOut[1];
                  cb = fxOut[2];
                  vertexAlpha *= fxOut[3];
               } else {
                  cr = cg = cb = fxOut[4];
               }
            } else if (glowPass) {
               vertexAlpha = 0.0F;
            }

            pu[k] = u;
            pv[k] = v;
            pa[k] = alpha * vertexAlpha;
            prgb[k] = to255(cr) << 16 | to255(cg) << 8 | to255(cb);
         }
      }

      for(int i = 0; i < rows; ++i) {
         for(int j = 0; j < cols; ++j) {
            float pieceAlpha = a.scatter() < 1.0F ? SkyAnim.scatterAlpha(a.scatter(), i * step * 73 + j * step) : 1.0F;
            if (!(pieceAlpha <= 0.0F)) {
               int k00 = i * (cols + 1) + j;
               int k10 = k00 + cols + 1;
               emitCached(sink, px, py, pz, pu, pv, pa, prgb, k00, pieceAlpha);
               emitCached(sink, px, py, pz, pu, pv, pa, prgb, k10, pieceAlpha);
               emitCached(sink, px, py, pz, pu, pv, pa, prgb, k10 + 1, pieceAlpha);
               emitCached(sink, px, py, pz, pu, pv, pa, prgb, k00 + 1, pieceAlpha);
            }
         }
      }

      if (!glowPass && (type == null || SkyboxTextures.smoothEdges(type))) {
         for(int ring = 0; ring < 76; ring += step) {
            for(int s = 0; s < 10; s += step) {
               quad(sink, r, cosRot, sinRot, offsetX, offsetY, ring, 128 - step - s, step, true, a, alpha, false, fx, fxOut, plainAnim);
               quad(sink, r, cosRot, sinRot, offsetX, offsetY, ring, s, step, true, a, alpha, false, fx, fxOut, plainAnim);
            }
         }
      }
   }

   private static void quad(VertexSink sink, float radius, float cosRot, float sinRot, float offsetX, float offsetY, int ring, int seg, int step, boolean seamOverlay, SkyAnim.Sample anim, float alpha, boolean glowPass, SkyEffects.Frame fx, float[] fxOut, boolean plainAnim) {
      float pieceAlpha = anim.scatter() < 1.0F ? SkyAnim.scatterAlpha(anim.scatter(), ring * 73 + seg) : 1.0F;
      if (!(pieceAlpha <= 0.0F)) {
         corner(sink, radius, cosRot, sinRot, offsetX, offsetY, ring, seg, seamOverlay, anim, alpha, pieceAlpha, glowPass, fx, fxOut, plainAnim);
         corner(sink, radius, cosRot, sinRot, offsetX, offsetY, ring + step, seg, seamOverlay, anim, alpha, pieceAlpha, glowPass, fx, fxOut, plainAnim);
         corner(sink, radius, cosRot, sinRot, offsetX, offsetY, ring + step, seg + step, seamOverlay, anim, alpha, pieceAlpha, glowPass, fx, fxOut, plainAnim);
         corner(sink, radius, cosRot, sinRot, offsetX, offsetY, ring, seg + step, seamOverlay, anim, alpha, pieceAlpha, glowPass, fx, fxOut, plainAnim);
      }
   }

   private static void corner(VertexSink sink, float radius, float cosRot, float sinRot, float offsetX, float offsetY, int ring, int seg, boolean seamOverlay, SkyAnim.Sample anim, float alpha, float pieceAlpha, boolean glowPass, SkyEffects.Frame fx, float[] fxOut, boolean plainAnim) {
      float sinTheta = SIN_THETA[ring];
      float baseX = sinTheta * COS_PHI[seg];
      float baseZ = sinTheta * SIN_PHI[seg];
      float x = radius * (baseX * cosRot - baseZ * sinRot) + offsetX;
      float y = radius * COS_THETA[ring] + offsetY;
      float z = radius * (baseX * sinRot + baseZ * cosRot);
      float u = (float)seg / 128.0F;
      float v = (float)ring / 76.0F;
      float vertexAlpha;
      if (!plainAnim) {
         vertexAlpha = pieceAlpha * anim.alphaAt(u, v);
      } else {
         vertexAlpha = pieceAlpha * anim.alpha();
      }

      if (seamOverlay) {
         int fromSeam = Math.min(seg, 128 - seg);
         float t = 1.0F - (float)fromSeam / 10.0F;
         vertexAlpha *= 0.5F * t * t * (3.0F - 2.0F * t);
         u = 1.0F - u;
      }

      float colourR = 1.0F;
      float colourG = 1.0F;
      float colourB = 1.0F;
      if (fx != null) {
         fx.sampleInto(u, v, fxOut);
         u += fxOut[5];
         v = Math.max(0.0F, Math.min(1.0F, v + fxOut[6]));
         if (glowPass) {
            colourR = fxOut[0];
            colourG = fxOut[1];
            colourB = fxOut[2];
            vertexAlpha *= fxOut[3];
         } else {
            colourR = colourG = colourB = fxOut[4];
         }
      } else if (glowPass) {
         vertexAlpha = 0.0F;
      }

      sink.vertex(x, y, z, u, v, argb(alpha * vertexAlpha, colourR, colourG, colourB));
   }

   private static void emitCached(VertexSink sink, float[] x, float[] y, float[] z, float[] u, float[] v, float[] a, int[] rgb, int k, float pieceAlpha) {
      int alpha = (int)(Math.max(0.0F, Math.min(1.0F, a[k] * pieceAlpha)) * 255.0F) & 255;
      sink.vertex(x[k], y[k], z[k], u[k], v[k], alpha << 24 | rgb[k]);
   }

   private static int to255(float c) {
      return (int)(Math.max(0.0F, Math.min(1.0F, c)) * 255.0F) & 255;
   }

   private static int argb(float alpha, float r, float g, float b) {
      int a = (int)(Math.max(0.0F, Math.min(1.0F, alpha)) * 255.0F) & 255;
      int ri = (int)(Math.max(0.0F, Math.min(1.0F, r)) * 255.0F) & 255;
      int gi = (int)(Math.max(0.0F, Math.min(1.0F, g)) * 255.0F) & 255;
      int bi = (int)(Math.max(0.0F, Math.min(1.0F, b)) * 255.0F) & 255;
      return a << 24 | ri << 16 | gi << 8 | bi;
   }

   static {
      for(int ring = 0; ring <= 76; ++ring) {
         double theta = Math.PI * (double)ring / (double)76.0F;
         SIN_THETA[ring] = (float)Math.sin(theta);
         COS_THETA[ring] = (float)Math.cos(theta);
      }

      for(int seg = 0; seg <= 128; ++seg) {
         double phi = (Math.PI * 2D) * (double)seg / (double)128.0F;
         SIN_PHI[seg] = (float)Math.sin(phi);
         COS_PHI[seg] = (float)Math.cos(phi);
      }
   }

   @FunctionalInterface
   @Environment(EnvType.CLIENT)
   public interface VertexSink {
      void vertex(float var1, float var2, float var3, float var4, float var5, int var6);
   }
}
