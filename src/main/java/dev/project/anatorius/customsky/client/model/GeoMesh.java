package dev.project.anatorius.customsky.client.model;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import dev.project.anatorius.customsky.client.sky.CelestialMesh;
import org.joml.Matrix4f;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public final class GeoMesh {
   private static final float DEPTH = 0.35F;
   private static final Vector3f VIEW = new Vector3f();
   private static final float[] MIDDLE = new float[3];
   private static float middleDistance;
   private static final Vector3f LIGHT = (new Vector3f(0.3F, 1.0F, 0.55F)).normalize();

   private GeoMesh() {
   }

   public static int maxVertices(GeoModel model) {
      int cubes = 0;

      for(GeoModel.Bone bone : model.bones.values()) {
         cubes += bone.cubes.size();
      }

      return Math.max(4, cubes * 6 * 4);
   }

   public static void build(GeoModel model, GeoAnimation anim, float seconds, float[] center, float[] upHint, float size, float alpha, CelestialMesh.VertexSink sink) {
      Vector3f back = (new Vector3f(center[0], center[1], center[2])).normalize();
      Vector3f up = new Vector3f(upHint[0], upHint[1], upHint[2]);
      up.sub((new Vector3f(back)).mul(up.dot(back))).normalize();
      Vector3f east = (new Vector3f(up)).cross(back);
      float extent = 0.0F;
      float[] mid = new float[3];

      for(int i = 0; i < 3; ++i) {
         mid[i] = (model.min[i] + model.max[i]) / 2.0F;
         extent = Math.max(extent, (model.max[i] - model.min[i]) / 2.0F);
      }

      float fit = size / Math.max(extent, 0.001F);
      VIEW.set(back);
      MIDDLE[0] = center[0];
      MIDDLE[1] = center[1];
      MIDDLE[2] = center[2];
      middleDistance = Math.max(1.0F, (float)Math.sqrt((double)(center[0] * center[0] + center[1] * center[1] + center[2] * center[2])));
      Matrix4f place = (new Matrix4f(east.x * fit, east.y * fit, east.z * fit, 0.0F, up.x * fit, up.y * fit, up.z * fit, 0.0F, back.x * fit, back.y * fit, back.z * fit, 0.0F, center[0], center[1], center[2], 1.0F)).translate(-mid[0], -mid[1], -mid[2]);
      List<Face> faces = new ArrayList();
      Molang.Scope scope = new Molang.Scope();
      scope.lifeTime = (double)seconds;

      for(GeoModel.Bone root : model.roots) {
         bone(model, root, new Matrix4f(), anim, seconds, scope, place, faces);
      }

      faces.sort((ax, b) -> Float.compare(b.depth, ax.depth));
      if (faces.isEmpty()) {
         for(int i = 0; i < 4; ++i) {
            sink.vertex(center[0], center[1], center[2], 0.0F, 0.0F, 0);
         }
      } else {
         int a = (int)(Math.max(0.0F, Math.min(1.0F, alpha)) * 255.0F) & 255;

         for(Face f : faces) {
            int c = (int)(255.0F * f.light) & 255;
            int argb = a << 24 | c << 16 | c << 8 | c;

            for(int i = 0; i < 4; ++i) {
               sink.vertex(f.xyz[i * 3], f.xyz[i * 3 + 1], f.xyz[i * 3 + 2], f.uv[i * 2], f.uv[i * 2 + 1], argb);
            }
         }
      }
   }

   private static void bone(GeoModel model, GeoModel.Bone bone, Matrix4f parent, GeoAnimation anim, float seconds, Molang.Scope scope, Matrix4f place, List<Face> faces) {
      float[] rot = (float[])bone.rotation.clone();
      float[] pos = new float[3];
      float[] scale = new float[]{1.0F, 1.0F, 1.0F};
      if (anim != null) {
         GeoAnimation.Pose pose = new GeoAnimation.Pose();
         if (anim.pose(bone.name, seconds, scope, pose)) {
            rot[0] -= pose.rotation[0];
            rot[1] -= pose.rotation[1];
            rot[2] += pose.rotation[2];
            pos[0] = -pose.position[0];
            pos[1] = pose.position[1];
            pos[2] = pose.position[2];
            scale = pose.scale;
         }
      }

      float[] p = bone.pivot;
      Matrix4f m = (new Matrix4f(parent)).translate(pos[0], pos[1], pos[2]).translate(p[0], p[1], p[2]).rotateZ((float)Math.toRadians((double)rot[2])).rotateY((float)Math.toRadians((double)rot[1])).rotateX((float)Math.toRadians((double)rot[0])).scale(scale[0], scale[1], scale[2]).translate(-p[0], -p[1], -p[2]);

      for(GeoModel.Cube cube : bone.cubes) {
         cube(model, cube, m, place, faces);
      }

      for(GeoModel.Bone child : bone.children) {
         bone(model, child, m, anim, seconds, scope, place, faces);
      }
   }

   private static void cube(GeoModel model, GeoModel.Cube cube, Matrix4f bone, Matrix4f place, List<Face> faces) {
      float[] cp = cube.pivot();
      float[] cr = cube.rotation();
      Matrix4f local = new Matrix4f(bone);
      if (cr[0] != 0.0F || cr[1] != 0.0F || cr[2] != 0.0F) {
         local.translate(cp[0], cp[1], cp[2]).rotateZ((float)Math.toRadians((double)cr[2])).rotateY((float)Math.toRadians((double)cr[1])).rotateX((float)Math.toRadians((double)cr[0])).translate(-cp[0], -cp[1], -cp[2]);
      }

      Matrix4f world = (new Matrix4f(place)).mul(local);
      float[] a = cube.from();
      float[] b = cube.to();
      float x0 = a[0];
      float y0 = a[1];
      float z0 = a[2];
      float x1 = b[0];
      float y1 = b[1];
      float z1 = b[2];
      float[][] corners = new float[][]{{x1, y1, z0, x0, y1, z0, x0, y0, z0, x1, y0, z0}, {x1, y1, z1, x1, y1, z0, x1, y0, z0, x1, y0, z1}, {x0, y1, z1, x1, y1, z1, x1, y0, z1, x0, y0, z1}, {x0, y1, z0, x0, y1, z1, x0, y0, z1, x0, y0, z0}, {x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1}, {x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0}};
      float tw = (float)model.textureWidth;
      float th = (float)model.textureHeight;
      Vector3f v = new Vector3f();

      for(int f = 0; f < 6; ++f) {
         float[] uv = cube.uv()[f];
         if (uv != null) {
            float[] in = corners[f];
            float[] out = new float[12];
            Vector3f inModel = new Vector3f();
            float[] localPts = new float[12];

            for(int i = 0; i < 4; ++i) {
               local.transformPosition(in[i * 3], in[i * 3 + 1], in[i * 3 + 2], inModel);
               localPts[i * 3] = inModel.x;
               localPts[i * 3 + 1] = inModel.y;
               localPts[i * 3 + 2] = inModel.z;
               world.transformPosition(in[i * 3], in[i * 3 + 1], in[i * 3 + 2], v);
               out[i * 3] = v.x;
               out[i * 3 + 1] = v.y;
               out[i * 3 + 2] = v.z;
            }

            Vector3f normal = normal(out);
            if (normal != null && !(normal.dot(VIEW) >= 0.0F)) {
               float cx = (out[0] + out[3] + out[6] + out[9]) / 4.0F - MIDDLE[0];
               float cy = (out[1] + out[4] + out[7] + out[10]) / 4.0F - MIDDLE[1];
               float cz = (out[2] + out[5] + out[8] + out[11]) / 4.0F - MIDDLE[2];
               float faceDepth = cx * VIEW.x + cy * VIEW.y + cz * VIEW.z;

               for(int i = 0; i < 4; ++i) {
                  warp(out, i * 3);
               }

               Vector3f modelNormal = normal(localPts);
               float light = modelNormal == null ? 1.0F : 0.55F + 0.45F * Math.max(0.0F, modelNormal.dot(LIGHT));
               float u0 = uv[0] / tw;
               float v0 = uv[1] / th;
               float u1 = uv[2] / tw;
               float v1 = uv[3] / th;
               faces.add(new Face(out, new float[]{u0, v0, u1, v0, u1, v1, u0, v1}, faceDepth, light));
            }
         }
      }
   }

   private static void warp(float[] p, int at) {
      float dx = p[at] - MIDDLE[0];
      float dy = p[at + 1] - MIDDLE[1];
      float dz = p[at + 2] - MIDDLE[2];
      float along = dx * VIEW.x + dy * VIEW.y + dz * VIEW.z;
      float k = (middleDistance + along * 0.35F) / middleDistance;
      p[at] = (MIDDLE[0] + dx - along * VIEW.x) * k;
      p[at + 1] = (MIDDLE[1] + dy - along * VIEW.y) * k;
      p[at + 2] = (MIDDLE[2] + dz - along * VIEW.z) * k;
   }

   private static Vector3f normal(float[] p) {
      Vector3f down = new Vector3f(p[9] - p[0], p[10] - p[1], p[11] - p[2]);
      Vector3f across = new Vector3f(p[3] - p[0], p[4] - p[1], p[5] - p[2]);
      Vector3f n = down.cross(across);
      if (n.lengthSquared() < 1.0E-12F) {
         down.set(p[6] - p[3], p[7] - p[4], p[8] - p[5]);
         across.set(p[0] - p[3], p[1] - p[4], p[2] - p[5]);
         n = across.cross(down);
         if (n.lengthSquared() < 1.0E-12F) {
            return null;
         }
      }

      return n.normalize();
   }

   @Environment(EnvType.CLIENT)
   private static record Face(float[] xyz, float[] uv, float depth, float light) {
   }
}
