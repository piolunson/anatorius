package dev.project.anatorius.customsky.client.sky;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import dev.project.anatorius.customsky.client.model.GeoMesh;
import dev.project.anatorius.customsky.client.model.SkyModels;

@Environment(EnvType.CLIENT)
public final class CelestialMesh {
   public static final float DISTANCE = 96.0F;
   public static final float SUN_SIZE = 30.0F;
   public static final float MOON_SIZE = 20.0F;

   private CelestialMesh() {
   }

   public static void build(SkyboxType type, SkyboxEntry entry, float skyAngle, float alpha, SkyAnim.Sample anim, int moonPhase, VertexSink sink) {
      SkyAnim.Sample a = anim == null ? SkyAnim.Sample.IDLE : anim;
      CelestialPreset.Motion preset = entry.preset().motion(FadeController.presetLoop(entry));
      CelestialPreset.Motion shot = SkyEffects.celestialMotion(type);
      float[] angles = baseAngles(type, entry, skyAngle);
      float yaw = angles[0] + preset.offsetX() + shot.offsetX() + a.offsetX() * 30.0F;
      float pitch = angles[1] + preset.offsetY() + shot.offsetY() + a.offsetY() * 30.0F;
      float roll = entry.rotation() + preset.roll() + shot.roll() + a.spin();
      float size = (type == SkyboxType.MOON ? 20.0F : 30.0F) * entry.scale() * preset.scale() * shot.scale() * a.scale();
      float finalAlpha = alpha * preset.alpha() * shot.alpha() * a.alpha() * a.progress();
      finalAlpha = Math.max(0.0F, finalAlpha);
      size = Math.max(0.001F, size);
      double yawRad = Math.toRadians((double)yaw);
      double pitchRad = Math.toRadians((double)pitch);
      float cosPitch = (float)Math.cos(pitchRad);
      float cx = 96.0F * cosPitch * (float)Math.cos(yawRad);
      float cy = 96.0F * (float)Math.sin(pitchRad);
      float cz = 96.0F * cosPitch * (float)Math.sin(yawRad);
      float[] right = normalize(-cz, 0.0F, cx);
      if (right == null) {
         right = new float[]{-1.0F, 0.0F, 0.0F};
      }

      float[] dir = normalize(cx, cy, cz);
      float[] up = cross(right, dir);
      double rollRad = Math.toRadians((double)roll);
      float cosRoll = (float)Math.cos(rollRad);
      float sinRoll = (float)Math.sin(rollRad);
      SkyModels.Loaded model = SkyModels.forEntry(entry);
      if (model != null) {
         float[] rolledUp = new float[]{up[0] * cosRoll - right[0] * sinRoll, up[1] * cosRoll - right[1] * sinRoll, up[2] * cosRoll - right[2] * sinRoll};
         SkyModels.build(type, entry, model, new float[]{cx, cy, cz}, rolledUp, size, finalAlpha, sink);
      } else {
         int argb = argb(finalAlpha);
         float[] uv = uvFor(type, entry, moonPhase);
         emit(sink, cx, cy, cz, right, up, -size, -size, cosRoll, sinRoll, uv[0], uv[3], argb);
         emit(sink, cx, cy, cz, right, up, size, -size, cosRoll, sinRoll, uv[2], uv[3], argb);
         emit(sink, cx, cy, cz, right, up, size, size, cosRoll, sinRoll, uv[2], uv[1], argb);
         emit(sink, cx, cy, cz, right, up, -size, size, cosRoll, sinRoll, uv[0], uv[1], argb);
      }
   }

   public static int vertexCount(SkyboxEntry entry) {
      SkyModels.Loaded model = SkyModels.forEntry(entry);
      return model == null ? 4 : GeoMesh.maxVertices(model.model());
   }

   private static float[] baseAngles(SkyboxType type, SkyboxEntry entry, float skyAngle) {
      if (type == SkyboxType.CUSTOM) {
         return new float[]{entry.hasPosX() ? entry.posX() : 0.0F, entry.hasPosY() ? entry.posY() : 45.0F};
      } else {
         double a = (double)skyAngle * Math.PI * (double)2.0F;
         float x = -((float)Math.sin(a));
         float y = (float)Math.cos(a);
         if (type == SkyboxType.MOON) {
            x = -x;
            y = -y;
         }

         float yaw = (float)Math.toDegrees(Math.atan2((double)0.0F, (double)x));
         float pitch = (float)Math.toDegrees(Math.atan2((double)y, (double)Math.abs(x)));
         if (entry.hasPosX()) {
            yaw += entry.posX();
         }

         if (entry.hasPosY()) {
            pitch *= entry.posY() / 90.0F;
         }

         return new float[]{yaw, pitch};
      }
   }

   private static float[] uvFor(SkyboxType type, SkyboxEntry entry, int moonPhase) {
      if (type != SkyboxType.MOON) {
         return new float[]{0.0F, 0.0F, 1.0F, 1.0F};
      } else {
         int phase = Math.max(0, Math.min(7, moonPhase));
         float u0 = (float)(phase % 4) / 4.0F;
         float v0 = (float)(phase / 4) / 2.0F;
         return new float[]{u0, v0, u0 + 0.25F, v0 + 0.5F};
      }
   }

   private static void emit(VertexSink sink, float cx, float cy, float cz, float[] right, float[] up, float dx, float dy, float cosRoll, float sinRoll, float u, float v, int argb) {
      float rx = dx * cosRoll - dy * sinRoll;
      float ry = dx * sinRoll + dy * cosRoll;
      sink.vertex(cx + right[0] * rx + up[0] * ry, cy + right[1] * rx + up[1] * ry, cz + right[2] * rx + up[2] * ry, u, v, argb);
   }

   private static float[] normalize(float x, float y, float z) {
      float len = (float)Math.sqrt((double)(x * x + y * y + z * z));
      return len < 1.0E-4F ? null : new float[]{x / len, y / len, z / len};
   }

   private static float[] cross(float[] a, float[] b) {
      return new float[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
   }

   private static int argb(float alpha) {
      int a = (int)(Math.max(0.0F, Math.min(1.0F, alpha)) * 255.0F) & 255;
      return a << 24 | 16777215;
   }

   @FunctionalInterface
   @Environment(EnvType.CLIENT)
   public interface VertexSink {
      void vertex(float var1, float var2, float var3, float var4, float var5, int var6);
   }
}
