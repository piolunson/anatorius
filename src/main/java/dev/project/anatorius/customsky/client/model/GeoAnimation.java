package dev.project.anatorius.customsky.client.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import dev.project.anatorius.customsky.Customskyboxes;

@Environment(EnvType.CLIENT)
public final class GeoAnimation {
   public final String name;
   public final Loop loop;
   public final float length;
   private final Map<String, Channel[]> bones = new HashMap();

   private GeoAnimation(String name, Loop loop, float length) {
      this.name = name;
      this.loop = loop;
      this.length = length;
   }

   public static Map<String, GeoAnimation> parseAll(JsonObject root) {
      Map<String, GeoAnimation> out = new LinkedHashMap();
      if (!root.has("animations")) {
         return out;
      } else {
         for(Map.Entry<String, JsonElement> e : root.getAsJsonObject("animations").entrySet()) {
            try {
               out.put((String)e.getKey(), parse((String)e.getKey(), ((JsonElement)e.getValue()).getAsJsonObject()));
            } catch (RuntimeException broken) {
               Customskyboxes.LOGGER.warn("Skipping animation {}: {}", e.getKey(), broken.toString());
            }
         }

         return out;
      }
   }

   private static GeoAnimation parse(String name, JsonObject o) {
      Loop loop = GeoAnimation.Loop.ONCE;
      if (o.has("loop")) {
         JsonElement l = o.get("loop");
         if (l.getAsJsonPrimitive().isBoolean()) {
            loop = l.getAsBoolean() ? GeoAnimation.Loop.LOOP : GeoAnimation.Loop.ONCE;
         } else if ("hold_on_last_frame".equals(l.getAsString())) {
            loop = GeoAnimation.Loop.HOLD;
         }
      }

      float length = o.has("animation_length") ? o.get("animation_length").getAsFloat() : -1.0F;
      GeoAnimation anim = new GeoAnimation(name, loop, length);
      float longest = 0.0F;
      if (o.has("bones")) {
         for(Map.Entry<String, JsonElement> b : o.getAsJsonObject("bones").entrySet()) {
            JsonObject channels = ((JsonElement)b.getValue()).getAsJsonObject();
            Channel[] c = new Channel[]{GeoAnimation.Channel.parse(channels.get("rotation"), 0.0F), GeoAnimation.Channel.parse(channels.get("position"), 0.0F), GeoAnimation.Channel.parse(channels.get("scale"), 1.0F)};

            for(Channel ch : c) {
               if (ch != null) {
                  longest = Math.max(longest, ch.lastTime());
               }
            }

            anim.bones.put((String)b.getKey(), c);
         }
      }

      return anim.length <= 0.0F ? (new GeoAnimation(name, loop, Math.max(longest, 0.05F))).copyBones(anim) : anim;
   }

   private GeoAnimation copyBones(GeoAnimation from) {
      this.bones.putAll(from.bones);
      return this;
   }

   public float localTime(float seconds) {
      if (this.loop == GeoAnimation.Loop.LOOP) {
         return this.length <= 0.0F ? 0.0F : seconds % this.length;
      } else {
         return Math.min(seconds, this.length);
      }
   }

   public boolean pose(String bone, float seconds, Molang.Scope scope, Pose out) {
      Channel[] c = (Channel[])this.bones.get(bone);
      if (c == null) {
         return false;
      } else {
         float t = this.localTime(seconds);
         scope.animTime = (double)t;
         if (c[0] != null) {
            c[0].sample(t, scope, out.rotation);
         }

         if (c[1] != null) {
            c[1].sample(t, scope, out.position);
         }

         if (c[2] != null) {
            c[2].sample(t, scope, out.scale);
         }

         return true;
      }
   }

   @Environment(EnvType.CLIENT)
   public static enum Loop {
      ONCE,
      HOLD,
      LOOP;

      private static Loop[] $values() {
         return new Loop[]{ONCE, HOLD, LOOP};
      }
   }

   @Environment(EnvType.CLIENT)
   public static final class Pose {
      public final float[] rotation = new float[3];
      public final float[] position = new float[3];
      public final float[] scale = new float[]{1.0F, 1.0F, 1.0F};
   }

   @Environment(EnvType.CLIENT)
   private static record Key(float time, Molang.Expr[] pre, Molang.Expr[] post, boolean smooth, String easing) {
   }

   @Environment(EnvType.CLIENT)
   private static final class Channel {
      final List<Key> keys = new ArrayList();

      float lastTime() {
         return this.keys.isEmpty() ? 0.0F : ((Key)this.keys.get(this.keys.size() - 1)).time;
      }

      static Channel parse(JsonElement el, float fallback) {
         if (el != null && !el.isJsonNull()) {
            Channel ch = new Channel();
            if (el.isJsonObject() && !isKeyObject(el.getAsJsonObject())) {
               for(Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
                  float time;
                  try {
                     time = Float.parseFloat((String)e.getKey());
                  } catch (NumberFormatException var7) {
                     continue;
                  }

                  ch.keys.add(key(time, (JsonElement)e.getValue(), fallback));
               }

               ch.keys.sort((a, b) -> Float.compare(a.time, b.time));
            } else {
               ch.keys.add(key(0.0F, el, fallback));
            }

            return ch.keys.isEmpty() ? null : ch;
         } else {
            return null;
         }
      }

      private static boolean isKeyObject(JsonObject o) {
         return o.has("vector") || o.has("pre") || o.has("post");
      }

      private static Key key(float time, JsonElement v, float fallback) {
         if (!v.isJsonObject()) {
            Molang.Expr[] vec = vector(v, fallback);
            return new Key(time, vec, vec, false, "linear");
         } else {
            JsonObject o = v.getAsJsonObject();
            boolean smooth = o.has("lerp_mode") && "catmullrom".equals(o.get("lerp_mode").getAsString());
            String easing = o.has("easing") ? o.get("easing").getAsString() : "linear";
            if (o.has("vector")) {
               Molang.Expr[] vec = vector(o.get("vector"), fallback);
               return new Key(time, vec, vec, smooth, easing);
            } else {
               Molang.Expr[] post = vector(o.has("post") ? o.get("post") : o.get("pre"), fallback);
               Molang.Expr[] pre = o.has("pre") ? vector(o.get("pre"), fallback) : post;
               return new Key(time, pre, post, smooth, easing);
            }
         }
      }

      private static Molang.Expr[] vector(JsonElement v, float fallback) {
         Molang.Expr[] out = new Molang.Expr[3];
         if (v != null && v.isJsonArray()) {
            JsonArray a = v.getAsJsonArray();

            for(int i = 0; i < 3; ++i) {
               out[i] = i < a.size() ? value(a.get(i)) : Molang.constant((double)fallback);
            }
         } else {
            Molang.Expr one = v == null ? Molang.constant((double)fallback) : value(v);
            out[0] = out[1] = out[2] = one;
         }

         return out;
      }

      private static Molang.Expr value(JsonElement e) {
         return e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? Molang.constant(e.getAsDouble()) : Molang.parse(e.getAsString());
      }

      void sample(float t, Molang.Scope scope, float[] out) {
         int n = this.keys.size();
         if (n != 1 && !(t <= ((Key)this.keys.get(0)).time)) {
            if (t >= ((Key)this.keys.get(n - 1)).time) {
               eval(((Key)this.keys.get(n - 1)).post, scope, out);
            } else {
               int i;
               for(i = 0; i < n - 2 && t >= ((Key)this.keys.get(i + 1)).time; ++i) {
               }

               Key a = (Key)this.keys.get(i);
               Key b = (Key)this.keys.get(i + 1);
               float span = b.time - a.time;
               float f = span <= 0.0F ? 1.0F : (t - a.time) / span;
               f = (float)GeoAnimation.Easing.apply(b.easing, (double)f);
               float[] va = new float[3];
               float[] vb = new float[3];
               eval(a.post, scope, va);
               eval(b.pre, scope, vb);
               if (!a.smooth && !b.smooth) {
                  for(int k = 0; k < 3; ++k) {
                     out[k] = va[k] + (vb[k] - va[k]) * f;
                  }
               } else {
                  float[] v0 = new float[3];
                  float[] v3 = new float[3];
                  eval(((Key)this.keys.get(Math.max(0, i - 1))).post, scope, v0);
                  eval(((Key)this.keys.get(Math.min(n - 1, i + 2))).pre, scope, v3);

                  for(int k = 0; k < 3; ++k) {
                     out[k] = catmullRom(v0[k], va[k], vb[k], v3[k], f);
                  }
               }
            }
         } else {
            eval(((Key)this.keys.get(0)).pre, scope, out);
         }
      }

      private static void eval(Molang.Expr[] v, Molang.Scope scope, float[] out) {
         for(int k = 0; k < 3; ++k) {
            out[k] = (float)v[k].eval(scope);
         }
      }

      private static float catmullRom(float p0, float p1, float p2, float p3, float t) {
         float t2 = t * t;
         float t3 = t2 * t;
         return 0.5F * (2.0F * p1 + (-p0 + p2) * t + (2.0F * p0 - 5.0F * p1 + 4.0F * p2 - p3) * t2 + (-p0 + 3.0F * p1 - 3.0F * p2 + p3) * t3);
      }
   }

   @Environment(EnvType.CLIENT)
   private static final class Easing {
      static double apply(String name, double t) {
         if (name == null) {
            return t;
         } else {
            String n = name.toLowerCase();
            if (n.equals("step")) {
               return t < (double)1.0F ? (double)0.0F : (double)1.0F;
            } else {
               boolean in = n.startsWith("easein") && !n.startsWith("easeinout");
               boolean out = n.startsWith("easeout");
               boolean inOut = n.startsWith("easeinout");
               if (!in && !out && !inOut) {
                  return t;
               } else {
                  String kind = n.substring(inOut ? 9 : (in ? 6 : 7));
                  if (in) {
                     return ease(kind, t);
                  } else if (out) {
                     return (double)1.0F - ease(kind, (double)1.0F - t);
                  } else {
                     return t < (double)0.5F ? ease(kind, t * (double)2.0F) / (double)2.0F : (double)1.0F - ease(kind, ((double)1.0F - t) * (double)2.0F) / (double)2.0F;
                  }
               }
            }
         }
      }

      private static double ease(String kind, double t) {
         double var10000;
         switch (kind) {
            case "sine" -> var10000 = (double)1.0F - Math.cos(t * Math.PI / (double)2.0F);
            case "quad" -> var10000 = t * t;
            case "cubic" -> var10000 = t * t * t;
            case "quart" -> var10000 = t * t * t * t;
            case "quint" -> var10000 = t * t * t * t * t;
            case "expo" -> var10000 = t == (double)0.0F ? (double)0.0F : Math.pow((double)2.0F, (double)10.0F * t - (double)10.0F);
            case "circ" -> var10000 = (double)1.0F - Math.sqrt((double)1.0F - t * t);
            case "back" -> var10000 = 2.70158 * t * t * t - 1.70158 * t * t;
            case "elastic" -> var10000 = t != (double)0.0F && t != (double)1.0F ? -Math.pow((double)2.0F, (double)10.0F * t - (double)10.0F) * Math.sin((t * (double)10.0F - (double)10.75F) * 2.0943951023931953) : t;
            case "bounce" -> var10000 = (double)1.0F - bounceOut((double)1.0F - t);
            default -> var10000 = t;
         }

         return var10000;
      }

      private static double bounceOut(double t) {
         double n = (double)7.5625F;
         double d = (double)2.75F;
         if (t < (double)1.0F / d) {
            return n * t * t;
         } else if (t < (double)2.0F / d) {
            double var8;
            return n * (var8 = t - (double)1.5F / d) * var8 + (double)0.75F;
         } else {
            double var6;
            double var7;
            return t < (double)2.5F / d ? n * (var6 = t - (double)2.25F / d) * var6 + (double)0.9375F : n * (var7 = t - (double)2.625F / d) * var7 + (double)0.984375F;
         }
      }
   }
}
