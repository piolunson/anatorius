package dev.project.anatorius.customsky.client.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class GeoModel {
   public final int textureWidth;
   public final int textureHeight;
   public final List<Bone> roots = new ArrayList();
   public final Map<String, Bone> bones = new HashMap();
   public final float[] min = new float[]{Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
   public final float[] max = new float[]{-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
   public static final String[] FACES = new String[]{"north", "east", "south", "west", "up", "down"};

   private GeoModel(int textureWidth, int textureHeight) {
      this.textureWidth = textureWidth;
      this.textureHeight = textureHeight;
   }

   public static GeoModel parse(JsonObject root) {
      JsonObject geo;
      if (root.has("minecraft:geometry")) {
         geo = root.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
      } else {
         geo = null;

         for(Map.Entry<String, JsonElement> e : root.entrySet()) {
            if (((String)e.getKey()).startsWith("geometry.") && ((JsonElement)e.getValue()).isJsonObject()) {
               geo = ((JsonElement)e.getValue()).getAsJsonObject();
               break;
            }
         }

         if (geo == null) {
            throw new IllegalArgumentException("no geometry in the file");
         }
      }

      JsonObject desc = geo.has("description") ? geo.getAsJsonObject("description") : geo;
      int tw = desc.has("texture_width") ? desc.get("texture_width").getAsInt() : 64;
      int th = desc.has("texture_height") ? desc.get("texture_height").getAsInt() : 64;
      GeoModel model = new GeoModel(tw, th);
      Map<String, String> parents = new HashMap();
      List<Bone> order = new ArrayList();
      if (geo.has("bones")) {
         for(JsonElement el : geo.getAsJsonArray("bones")) {
            JsonObject b = el.getAsJsonObject();
            String name = b.get("name").getAsString();
            Bone bone = new Bone(name, flipPivot(vec(b, "pivot", 0.0F)), flipRotation(vec(b, "rotation", 0.0F)));
            if (b.has("parent")) {
               parents.put(name, b.get("parent").getAsString());
            }

            if (b.has("cubes")) {
               for(JsonElement c : b.getAsJsonArray("cubes")) {
                  bone.cubes.add(cube(c.getAsJsonObject(), model));
               }
            }

            model.bones.put(name, bone);
            order.add(bone);
         }
      }

      for(Bone bone : order) {
         Bone parent = (Bone)model.bones.get(parents.get(bone.name));
         if (parent != null) {
            parent.children.add(bone);
         } else {
            model.roots.add(bone);
         }
      }

      if (model.min[0] > model.max[0]) {
         for(int i = 0; i < 3; ++i) {
            model.min[i] = -8.0F;
            model.max[i] = 8.0F;
         }
      }

      return model;
   }

   private static Cube cube(JsonObject c, GeoModel model) {
      float[] origin = vec(c, "origin", 0.0F);
      float[] size = vec(c, "size", 0.0F);
      float inflate = c.has("inflate") ? c.get("inflate").getAsFloat() : 0.0F;
      boolean mirror = c.has("mirror") && c.get("mirror").getAsBoolean();
      float[] from = new float[]{-(origin[0] + size[0]) - inflate, origin[1] - inflate, origin[2] - inflate};
      float[] to = new float[]{-origin[0] + inflate, origin[1] + size[1] + inflate, origin[2] + size[2] + inflate};

      for(int i = 0; i < 3; ++i) {
         model.min[i] = Math.min(model.min[i], from[i]);
         model.max[i] = Math.max(model.max[i], to[i]);
      }

      float[][] uv = new float[6][];
      JsonElement uvEl = c.get("uv");
      if (uvEl != null && uvEl.isJsonArray()) {
         boxUv(uv, uvEl.getAsJsonArray().get(0).getAsFloat(), uvEl.getAsJsonArray().get(1).getAsFloat(), size, mirror);
      } else if (uvEl != null && uvEl.isJsonObject()) {
         JsonObject faces = uvEl.getAsJsonObject();

         for(int f = 0; f < 6; ++f) {
            if (faces.has(FACES[f])) {
               JsonObject face = faces.getAsJsonObject(FACES[f]);
               float[] at = vec2(face, "uv");
               float[] span = face.has("uv_size") ? vec2(face, "uv_size") : new float[]{size[f != 1 && f != 3 ? 0 : 2], size[f >= 4 ? 2 : 1]};
               uv[f] = f >= 4 ? new float[]{at[0] + span[0], at[1] + span[1], at[0], at[1]} : new float[]{at[0], at[1], at[0] + span[0], at[1] + span[1]};
            }
         }
      } else {
         boxUv(uv, 0.0F, 0.0F, size, mirror);
      }

      return new Cube(from, to, flipPivot(vec(c, "pivot", 0.0F)), flipRotation(vec(c, "rotation", 0.0F)), uv);
   }

   private static void boxUv(float[][] uv, float u, float v, float[] size, boolean mirror) {
      float w = (float)Math.floor((double)size[0]);
      float h = (float)Math.floor((double)size[1]);
      float d = (float)Math.floor((double)size[2]);
      uv[0] = new float[]{u + d, v + d, u + d + w, v + d + h};
      uv[1] = new float[]{u, v + d, u + d, v + d + h};
      uv[2] = new float[]{u + d + w + d, v + d, u + d + w + d + w, v + d + h};
      uv[3] = new float[]{u + d + w, v + d, u + d + w + d, v + d + h};
      uv[4] = new float[]{u + d + w, v + d, u + d, v};
      uv[5] = new float[]{u + d + w + w, v, u + d + w, v + d};
      if (mirror) {
         for(float[] f : uv) {
            float t = f[0];
            f[0] = f[2];
            f[2] = t;
         }

         float[] east = uv[1];
         uv[1] = uv[3];
         uv[3] = east;
      }
   }

   private static float[] flipPivot(float[] p) {
      return new float[]{-p[0], p[1], p[2]};
   }

   private static float[] flipRotation(float[] r) {
      return new float[]{-r[0], -r[1], r[2]};
   }

   private static float[] vec(JsonObject o, String key, float fallback) {
      float[] out = new float[]{fallback, fallback, fallback};
      if (o.has(key) && o.get(key).isJsonArray()) {
         JsonArray a = o.getAsJsonArray(key);

         for(int i = 0; i < 3 && i < a.size(); ++i) {
            out[i] = a.get(i).getAsFloat();
         }

         return out;
      } else {
         return out;
      }
   }

   private static float[] vec2(JsonObject o, String key) {
      float[] out = new float[]{0.0F, 0.0F};
      if (o.has(key) && o.get(key).isJsonArray()) {
         JsonArray a = o.getAsJsonArray(key);

         for(int i = 0; i < 2 && i < a.size(); ++i) {
            out[i] = a.get(i).getAsFloat();
         }

         return out;
      } else {
         return out;
      }
   }

   @Environment(EnvType.CLIENT)
   public static final class Bone {
      public final String name;
      public final float[] pivot;
      public final float[] rotation;
      public final List<Cube> cubes = new ArrayList();
      public final List<Bone> children = new ArrayList();

      Bone(String name, float[] pivot, float[] rotation) {
         this.name = name;
         this.pivot = pivot;
         this.rotation = rotation;
      }
   }

   @Environment(EnvType.CLIENT)
   public static record Cube(float[] from, float[] to, float[] pivot, float[] rotation, float[][] uv) {
   }
}
