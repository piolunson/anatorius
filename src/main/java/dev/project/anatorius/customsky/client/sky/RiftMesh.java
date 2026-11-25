package dev.project.anatorius.customsky.client.sky;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class RiftMesh {
   private static final int SEGMENTS = 110;
   private static final int FRACTURES = 11;
   private static final int FRACTURE_SEGMENTS = 16;
   private static final int SHOCK_SEGMENTS = 96;
   private static final int RING = 240;
   private static final float DISTANCE = 94.0F;
   private static final float TAU = ((float)Math.PI * 2F);
   private static final float QUARTER = ((float)Math.PI / 2F);
   private static OutlineKey lastOutlineKey;
   private static Outline lastOutline;
   private static RingKey lastRingKey;
   private static Ring lastRing;
   private static List<float[]> capture;
   private static final int TRIG = 8192;
   private static final float[] SINE = new float[8194];

   private RiftMesh() {
   }

   public static void build(SkyboxEntry entry, RiftAnim.Sample anim, float alpha, boolean inside, VertexSink sink) {
      anim = RiftEffects.apply(anim, entry.layer());
      float a = alpha * anim.alpha();
      if (!(a <= 0.002F)) {
         RiftShape shape = entry.riftShape();
         float radius = (float)Math.toRadians((double)Math.min(100.0F, Math.max(1.0F, entry.riftRadius())));
         float halfLen = radius * shape.length() * 1.6F;
         float open = Math.max(0.05F, entry.riftLength()) * 0.5F;
         Placement p = spun(place(entry), anim.spin());
         float from = clamp(anim.from());
         float grown = clamp(anim.opened());
         float low = from - from * grown;
         float high = from + (1.0F - from) * grown;
         if (!(high - low < 0.004F)) {
            float[] tint = rgb(RiftEffects.tint(entry.layer(), entry.riftColor()));
            float effectReach = RiftEffects.reach(entry.layer());
            if (shape.isClosed()) {
               buildClosed(sink, p, shape, radius, anim, from, low, high, tint, a, inside);
               if (!inside) {
                  effects(sink, p, anim, radius, radius * effectReach, tint, a);
               }
            } else {
               Outline o = outline(shape, radius, halfLen, open, anim, low, high);
               if (inside) {
                  window(sink, p, o, radius, halfLen, a);
               } else {
                  halo(sink, p, o, radius, anim, tint, a);
                  edges(sink, p, o, radius, anim, tint, a);
                  fractures(sink, p, o, shape, radius, halfLen, anim, tint, a);
                  if (anim.shock() > 0.0F && anim.shock() < 1.0F) {
                     shockwave(sink, p, anim, radius, halfLen, tint, a);
                  }

                  effects(sink, p, anim, radius, Math.max(radius, halfLen) * effectReach, tint, a);
               }
            }
         }
      }
   }

   private static void effects(VertexSink sink, Placement p, RiftAnim.Sample anim, float radius, float reach, float[] tint, float a) {
      if (anim.charge() > 0.002F) {
         chargeRings(sink, p, anim, reach, tint, a);
      }

      if (anim.bolts() > 0.002F && anim.bolts() < 1.0F) {
         bolts(sink, p, anim, radius, reach, tint, a);
      }

      if (anim.shards() > 0.002F && anim.shards() < 1.0F) {
         shards(sink, p, anim, radius, reach, tint, a);
      }

      if (anim.flash() > 0.002F) {
         flash(sink, p, anim, reach, tint, a);
      }
   }

   private static void flash(VertexSink sink, Placement p, RiftAnim.Sample anim, float reach, float[] tint, float a) {
      float f = clamp(anim.flash());
      float r = reach * (1.1F + 1.9F * f);
      int hot = argb(Math.min(1.0F, a * f), 1.0F, mix(tint[1], 1.0F, 0.75F), mix(tint[2], 1.0F, 0.65F));
      int clear = argb(0.0F, tint[0], tint[1], tint[2]);
      int n = 48;

      for(int i = 0; i < n; ++i) {
         float a0 = (float)i * ((float)Math.PI * 2F) / (float)n;
         float a1 = (float)(i + 1) * ((float)Math.PI * 2F) / (float)n;
         emit(sink, p, 0.0F, 0.0F, 0.5F, 0.5F, hot);
         emit(sink, p, r * (float)Math.cos((double)a0), r * (float)Math.sin((double)a0), 1.0F, 0.5F, clear);
         emit(sink, p, r * (float)Math.cos((double)a1), r * (float)Math.sin((double)a1), 1.0F, 0.5F, clear);
         emit(sink, p, 0.0F, 0.0F, 0.5F, 0.5F, hot);
      }
   }

   private static void chargeRings(VertexSink sink, Placement p, RiftAnim.Sample anim, float reach, float[] tint, float a) {
      float time = SkyClock.smoothTicks() * 0.05F;
      float c = clamp(anim.charge());
      int segments = 72;

      for(int k = 0; k < 4; ++k) {
         float phase = (float)((double)(time * (0.8F + c) + (float)k / 4.0F) % (double)1.0F);
         float ringR = reach * (0.35F + 2.4F * (1.0F - phase));
         float band = reach * (0.025F + 0.05F * phase);
         float strength = a * c * 4.0F * phase * (1.0F - phase);
         float twist = (1.0F - phase) * 2.2F + (float)k;
         int hot = argb(Math.min(1.0F, strength), mix(tint[0], 1.0F, 0.4F), mix(tint[1], 1.0F, 0.5F), mix(tint[2], 1.0F, 0.4F));
         int clear = argb(0.0F, tint[0], tint[1], tint[2]);

         for(int i = 0; i < segments; ++i) {
            float a0 = (float)i * ((float)Math.PI * 2F) / (float)segments + twist;
            float a1 = (float)(i + 1) * ((float)Math.PI * 2F) / (float)segments + twist;
            float arm0 = 0.35F + 0.65F * (float)Math.pow((double)0.5F + (double)0.5F * Math.cos((double)(a0 * 2.0F - twist)), (double)3.0F);
            float arm1 = 0.35F + 0.65F * (float)Math.pow((double)0.5F + (double)0.5F * Math.cos((double)(a1 * 2.0F - twist)), (double)3.0F);
            int c0 = scaleAlpha(hot, arm0);
            int c1 = scaleAlpha(hot, arm1);
            float ci0 = (float)Math.cos((double)a0);
            float si0 = (float)Math.sin((double)a0);
            float ci1 = (float)Math.cos((double)a1);
            float si1 = (float)Math.sin((double)a1);
            emit(sink, p, (ringR - band) * ci0, (ringR - band) * si0, 0.0F, 0.5F, clear);
            emit(sink, p, ringR * ci0, ringR * si0, 0.5F, 0.5F, c0);
            emit(sink, p, ringR * ci1, ringR * si1, 0.5F, 0.5F, c1);
            emit(sink, p, (ringR - band) * ci1, (ringR - band) * si1, 0.0F, 0.5F, clear);
            emit(sink, p, ringR * ci0, ringR * si0, 0.5F, 0.5F, c0);
            emit(sink, p, (ringR + band) * ci0, (ringR + band) * si0, 1.0F, 0.5F, clear);
            emit(sink, p, (ringR + band) * ci1, (ringR + band) * si1, 1.0F, 0.5F, clear);
            emit(sink, p, ringR * ci1, ringR * si1, 0.5F, 0.5F, c1);
         }
      }
   }

   private static void bolts(VertexSink sink, Placement p, RiftAnim.Sample anim, float radius, float reach, float[] tint, float a) {
      int count = 5;
      int steps = 24;
      float b = clamp(anim.bolts());
      float time = SkyClock.smoothTicks() * 0.05F;

      for(int k = 0; k < count; ++k) {
         float at = ((float)k + 0.5F) / (float)count;
         float local = (b - (at - 0.08F)) / 0.16F;
         if (!(local <= 0.0F) && !(local >= 1.6F)) {
            float grow = clamp(local / 0.25F);
            float life = local < 0.25F ? 1.0F : clamp(1.0F - (local - 0.25F) / 1.35F);
            life *= 0.8F + 0.2F * (float)Math.sin((double)(time * 55.0F + (float)k * 2.1F));
            if (!(life <= 0.0F)) {
               long seed = 7919L * (long)(k + 1);
               float angle = hash01(seed, 1) * ((float)Math.PI * 2F);
               float from = reach * (2.4F + hash01(seed, 2) * 1.2F);
               float sx = from * (float)Math.cos((double)angle);
               float sy = from * (float)Math.sin((double)angle);
               float crackle = time * 12.0F;
               long frame = (long)Math.floor((double)crackle);
               float blend = smooth01(crackle - (float)frame);
               float[] xs = new float[steps + 1];
               float[] ys = new float[steps + 1];

               for(int i = 0; i <= steps; ++i) {
                  float t = (float)i / (float)steps;
                  float jag0 = hash01(seed + frame * 31L, i) - 0.5F;
                  float jag1 = hash01(seed + (frame + 1L) * 31L, i) - 0.5F;
                  float jag = (jag0 + (jag1 - jag0) * blend) * reach * 0.45F * (float)Math.sin((double)t * Math.PI);
                  xs[i] = sx * (1.0F - t) - sy / from * jag;
                  ys[i] = sy * (1.0F - t) + sx / from * jag;
               }

               float width = radius * (0.012F + 0.014F * life);
               int core = argb(Math.min(1.0F, a * life * 1.4F), 1.0F, mix(tint[1], 1.0F, 0.85F), mix(tint[2], 1.0F, 0.85F));
               int glow = argb(Math.min(1.0F, a * life * 0.55F), tint[0], tint[1], tint[2]);
               float drawn = grow * (float)steps;
               strip(sink, p, xs, ys, drawn, width * 4.0F, glow);
               strip(sink, p, xs, ys, drawn, width, core);

               for(int f = 0; f < 2; ++f) {
                  int startAt = 5 + (int)(hash01(seed, 20 + f) * (float)(steps - 12));
                  if (!(drawn < (float)(startAt + 1))) {
                     int forkSteps = 7;
                     float[] fx = new float[forkSteps + 1];
                     float[] fy = new float[forkSteps + 1];
                     float dirX = xs[startAt + 1] - xs[startAt - 1];
                     float dirY = ys[startAt + 1] - ys[startAt - 1];
                     float len = (float)Math.sqrt((double)(dirX * dirX + dirY * dirY)) + 1.0E-5F;
                     float turn = (hash01(seed, 30 + f) < 0.5F ? -1.0F : 1.0F) * (0.5F + hash01(seed, 40 + f) * 0.4F);
                     float cx = (float)Math.cos((double)turn);
                     float sn = (float)Math.sin((double)turn);
                     float ux = (dirX * cx - dirY * sn) / len;
                     float uy = (dirX * sn + dirY * cx) / len;
                     float forkLen = from * (0.12F + hash01(seed, 50 + f) * 0.1F);

                     for(int i = 0; i <= forkSteps; ++i) {
                        float t = (float)i / (float)forkSteps;
                        float jag = (hash01(seed + frame * 13L + (long)f, i) - 0.5F) * forkLen * 0.3F * t;
                        fx[i] = xs[startAt] + ux * forkLen * t - uy * jag;
                        fy[i] = ys[startAt] + uy * forkLen * t + ux * jag;
                     }

                     float forkDrawn = Math.min((float)forkSteps, (drawn - (float)startAt) * 1.5F);
                     strip(sink, p, fx, fy, forkDrawn, width * 2.2F, scaleAlpha(glow, 0.7F));
                     strip(sink, p, fx, fy, forkDrawn, width * 0.55F, scaleAlpha(core, 0.8F));
                  }
               }
            }
         }
      }
   }

   private static void strip(VertexSink sink, Placement p, float[] xs, float[] ys, float drawn, float width, int argb) {
      int n = xs.length - 1;
      int last = Math.min(n, (int)Math.ceil((double)drawn));
      if (last >= 1) {
         float[] nx = new float[last + 1];
         float[] ny = new float[last + 1];
         float[] px = new float[last + 1];
         float[] py = new float[last + 1];

         for(int i = 0; i <= last; ++i) {
            float x = xs[i];
            float y = ys[i];
            if (i == last && drawn < (float)last) {
               float f = drawn - (float)(last - 1);
               x = xs[last - 1] + (xs[last] - xs[last - 1]) * f;
               y = ys[last - 1] + (ys[last] - ys[last - 1]) * f;
            }

            px[i] = x;
            py[i] = y;
         }

         for(int i = 0; i <= last; ++i) {
            int a0 = Math.max(0, i - 1);
            int a1 = Math.min(last, i + 1);
            float dx = px[a1] - px[a0];
            float dy = py[a1] - py[a0];
            float len = (float)Math.sqrt((double)(dx * dx + dy * dy)) + 1.0E-6F;
            float t = (float)i / (float)n;
            float w = width * (0.35F + 0.65F * (float)Math.sin((double)Math.min(1.0F, t * 1.15F + 0.08F) * Math.PI * (double)0.5F + (double)0.0F)) * (1.0F - 0.55F * t);
            nx[i] = -dy / len * w;
            ny[i] = dx / len * w;
         }

         for(int i = 0; i < last; ++i) {
            emit(sink, p, px[i] - nx[i], py[i] - ny[i], 0.0F, 0.5F, argb);
            emit(sink, p, px[i] + nx[i], py[i] + ny[i], 1.0F, 0.5F, argb);
            emit(sink, p, px[i + 1] + nx[i + 1], py[i + 1] + ny[i + 1], 1.0F, 0.5F, argb);
            emit(sink, p, px[i + 1] - nx[i + 1], py[i + 1] - ny[i + 1], 0.0F, 0.5F, argb);
         }
      }
   }

   private static float smooth01(float t) {
      return t * t * (3.0F - 2.0F * t);
   }

   private static void shards(VertexSink sink, Placement p, RiftAnim.Sample anim, float radius, float reach, float[] tint, float a) {
      float s = clamp(anim.shards());
      float fade = (1.0F - s) * (1.0F - s);
      int count = 34;

      for(int k = 0; k < count; ++k) {
         long seed = 104729L * (long)(k + 1);
         float angle = hash01(seed, 1) * ((float)Math.PI * 2F);
         float speed = 0.5F + hash01(seed, 2);
         float dist = reach * (0.2F + 2.8F * easeOutQuad(s) * speed);
         float cx = dist * (float)Math.cos((double)angle);
         float cy = dist * (float)Math.sin((double)angle);
         float size = radius * (0.05F + hash01(seed, 3) * 0.1F) * (1.0F - 0.5F * s);
         float turn = hash01(seed, 4) * ((float)Math.PI * 2F) + s * (3.0F + hash01(seed, 5) * 8.0F);
         int edge = argb(Math.min(1.0F, a * fade * 1.3F), 1.0F, mix(tint[1], 1.0F, 0.6F), mix(tint[2], 1.0F, 0.5F));
         int body = argb(Math.min(1.0F, a * fade * 0.7F), tint[0], tint[1], tint[2]);
         float[] xs = new float[3];
         float[] ys = new float[3];

         for(int c = 0; c < 3; ++c) {
            float ang = turn + (float)c * ((float)Math.PI * 2F) / 3.0F + (hash01(seed, 10 + c) - 0.5F) * 0.9F;
            float len = size * (0.6F + hash01(seed, 20 + c) * 0.8F);
            xs[c] = cx + len * (float)Math.cos((double)ang);
            ys[c] = cy + len * (float)Math.sin((double)ang);
         }

         emit(sink, p, xs[0], ys[0], 0.5F, 0.5F, edge);
         emit(sink, p, xs[1], ys[1], 0.5F, 0.5F, body);
         emit(sink, p, xs[2], ys[2], 0.5F, 0.5F, edge);
         emit(sink, p, xs[2], ys[2], 0.5F, 0.5F, edge);
      }
   }

   private static void quad(VertexSink sink, Placement p, float x0, float y0, float x1, float y1, float wx, float wy, float w, int argb) {
      emit(sink, p, x0 - wx * w, y0 - wy * w, 0.0F, 0.5F, argb);
      emit(sink, p, x0 + wx * w, y0 + wy * w, 1.0F, 0.5F, argb);
      emit(sink, p, x1 + wx * w, y1 + wy * w, 1.0F, 0.5F, argb);
      emit(sink, p, x1 - wx * w, y1 - wy * w, 0.0F, 0.5F, argb);
   }

   private static int scaleAlpha(int argb, float k) {
      int alpha = Math.round((float)(argb >>> 24 & 255) * clamp(k));
      return alpha << 24 | argb & 16777215;
   }

   private static float easeOutQuad(float t) {
      return 1.0F - (1.0F - t) * (1.0F - t);
   }

   private static Placement spun(Placement p, float spin) {
      if (spin == 0.0F) {
         return p;
      } else {
         double roll = Math.atan2((double)p.sinRoll(), (double)p.cosRoll()) + (double)spin;
         return new Placement(p.basis(), (float)Math.cos(roll), (float)Math.sin(roll));
      }
   }

   private static Outline outline(RiftShape shape, float radius, float halfLen, float open, RiftAnim.Sample anim, float low, float high) {
      OutlineKey key = new OutlineKey(shape, radius, halfLen, open, anim, low, high, SkyClock.smoothTicks());
      if (key.equals(lastOutlineKey)) {
         return lastOutline;
      } else {
         lastOutline = makeOutline(shape, radius, halfLen, open, anim, low, high);
         lastOutlineKey = key;
         return lastOutline;
      }
   }

   private static Outline makeOutline(RiftShape shape, float radius, float halfLen, float open, RiftAnim.Sample anim, float low, float high) {
      int n = 110;
      float[] along = new float[n + 1];
      float[] mid = new float[n + 1];
      float[] half = new float[n + 1];
      float[] ss = new float[n + 1];
      float time = SkyClock.smoothTicks() * 0.05F;
      float rough = shape.roughness();
      float shake = anim.shake();

      for(int i = 0; i <= n; ++i) {
         float s = low + (high - low) * (float)i / (float)n;
         ss[i] = s;
         along[i] = (s - 0.5F) * 2.0F * halfLen;
         float hw = radius * shape.halfWidth(s) * anim.width() * open;
         float wander = wave(s * 2.6F, 7L) * radius * 0.05F * rough + wave(s * 11.0F, 19L) * radius * 0.018F * rough;
         float jitter = shake == 0.0F ? 0.0F : (float)Math.sin((double)(time * 13.0F + s * 21.0F)) * radius * 0.02F * shake;
         mid[i] = wander + jitter;
         half[i] = Math.max(0.0F, hw * (1.0F + rough * (0.07F * wave(s * 9.0F + time * 0.1F, 31L) + 0.04F * wave(s * 23.0F, 53L))));
      }

      return new Outline(n, along, mid, half, ss);
   }

   private static void window(VertexSink sink, Placement p, Outline o, float radius, float halfLen, float a) {
      int plain = argb(a, 1.0F, 1.0F, 1.0F);
      float uScale = 1.0F / Math.max(1.0E-4F, radius * 2.4F);
      float vScale = 1.0F / Math.max(1.0E-4F, halfLen * 2.1F);

      for(int i = 0; i < o.n(); ++i) {
         float l0 = o.mid()[i] - o.half()[i];
         float r0 = o.mid()[i] + o.half()[i];
         float l1 = o.mid()[i + 1] - o.half()[i + 1];
         float r1 = o.mid()[i + 1] + o.half()[i + 1];
         float y0 = o.along()[i];
         float y1 = o.along()[i + 1];
         emit(sink, p, l0, y0, 0.5F + l0 * uScale, 0.5F - y0 * vScale, plain);
         emit(sink, p, r0, y0, 0.5F + r0 * uScale, 0.5F - y0 * vScale, plain);
         emit(sink, p, r1, y1, 0.5F + r1 * uScale, 0.5F - y1 * vScale, plain);
         emit(sink, p, l1, y1, 0.5F + l1 * uScale, 0.5F - y1 * vScale, plain);
      }
   }

   private static void halo(VertexSink sink, Placement p, Outline o, float radius, RiftAnim.Sample anim, float[] tint, float a) {
      float reach = radius * 0.4F;
      float strength = Math.min(0.8F, anim.rim() * 0.15F) * a;
      if (!(strength <= 0.002F)) {
         int clear = argb(0.0F, tint[0], tint[1], tint[2]);

         for(int side = -1; side <= 1; side += 2) {
            for(int i = 0; i < o.n(); ++i) {
               float in0 = o.mid()[i] + (float)side * o.half()[i];
               float in1 = o.mid()[i + 1] + (float)side * o.half()[i + 1];
               float w0 = reach * (0.25F + 0.75F * profile(o.s()[i]));
               float w1 = reach * (0.25F + 0.75F * profile(o.s()[i + 1]));
               int c0 = argb(strength * profile(o.s()[i]), tint[0], tint[1], tint[2]);
               int c1 = argb(strength * profile(o.s()[i + 1]), tint[0], tint[1], tint[2]);
               emit(sink, p, in0, o.along()[i], 0.5F, 0.5F, c0);
               emit(sink, p, in0 + (float)side * w0, o.along()[i], 1.0F, 0.5F, clear);
               emit(sink, p, in1 + (float)side * w1, o.along()[i + 1], 1.0F, 0.5F, clear);
               emit(sink, p, in1, o.along()[i + 1], 0.5F, 0.5F, c1);
            }
         }
      }
   }

   private static void edges(VertexSink sink, Placement p, Outline o, float radius, RiftAnim.Sample anim, float[] tint, float a) {
      float time = SkyClock.smoothTicks() * 0.05F;
      float rim = anim.rim();
      float g = mix(tint[1], 1.0F, 0.55F);
      float bl = mix(tint[2], 1.0F, 0.4F);
      int clear = argb(0.0F, tint[0], tint[1], tint[2]);

      for(int side = -1; side <= 1; side += 2) {
         for(int i = 0; i < o.n(); ++i) {
            int[] c = new int[2];
            float[] lip = new float[2];
            float[] out = new float[2];

            for(int k = 0; k < 2; ++k) {
               int j = i + k;
               float s = o.s()[j];
               float pulse = (float)Math.pow(Math.max((double)0.0F, Math.sin((double)((s * 2.6F - time * 0.55F) * ((float)Math.PI * 2F)))), (double)14.0F) + (float)Math.pow(Math.max((double)0.0F, Math.sin((double)((s * 1.7F + time * 0.33F) * ((float)Math.PI * 2F)))), (double)18.0F);
               float glow = rim * (0.45F + 0.55F * profile(s)) * (1.0F + pulse * 1.6F);
               lip[k] = o.mid()[j] + (float)side * o.half()[j];
               out[k] = Math.max(o.half()[j] * 0.3F, radius * 0.016F) * (0.5F + 0.7F * profile(s)) * (1.0F + pulse);
               c[k] = argb(Math.min(1.0F, a * glow), 1.0F, g, bl);
            }

            int fade0 = argb(Math.min(1.0F, a * rim * 0.35F * profile(o.s()[i])), tint[0], tint[1], tint[2]);
            int fade1 = argb(Math.min(1.0F, a * rim * 0.35F * profile(o.s()[i + 1])), tint[0], tint[1], tint[2]);
            emit(sink, p, lip[0], o.along()[i], 0.5F, 0.5F, c[0]);
            emit(sink, p, lip[0] + (float)side * out[0] * 0.4F, o.along()[i], 0.7F, 0.5F, fade0);
            emit(sink, p, lip[1] + (float)side * out[1] * 0.4F, o.along()[i + 1], 0.7F, 0.5F, fade1);
            emit(sink, p, lip[1], o.along()[i + 1], 0.5F, 0.5F, c[1]);
            emit(sink, p, lip[0] + (float)side * out[0] * 0.4F, o.along()[i], 0.7F, 0.5F, fade0);
            emit(sink, p, lip[0] + (float)side * out[0], o.along()[i], 1.0F, 0.5F, clear);
            emit(sink, p, lip[1] + (float)side * out[1], o.along()[i + 1], 1.0F, 0.5F, clear);
            emit(sink, p, lip[1] + (float)side * out[1] * 0.4F, o.along()[i + 1], 0.7F, 0.5F, fade1);
         }
      }
   }

   private static void fractures(VertexSink sink, Placement p, Outline o, RiftShape shape, float radius, float halfLen, RiftAnim.Sample anim, float[] tint, float a) {
      float grown = anim.branch();
      if (!(grown <= 0.01F)) {
         float time = SkyClock.smoothTicks() * 0.05F;
         float strength = a * Math.min(1.3F, anim.rim()) * 0.8F;
         float g = mix(tint[1], 1.0F, 0.5F);
         float bl = mix(tint[2], 1.0F, 0.35F);

         for(int f = 0; f < 11; ++f) {
            long seed = 131L * (long)(f + 3);
            boolean top = f % 2 == 0;
            float startS = top ? 1.0F - hash01(seed, 1) * 0.1F : hash01(seed, 1) * 0.1F;
            float startY = (startS - 0.5F) * 2.0F * halfLen;
            float startX = radius * shape.halfWidth(startS) * anim.width() * (hash01(seed, 9) - 0.5F) * 1.2F;
            float reach = halfLen * (0.1F + hash01(seed, 2) * 0.22F) * grown;
            float lean = (hash01(seed, 3) - 0.5F) * 0.5F;
            float width = radius * (0.005F + hash01(seed, 4) * 0.008F);
            float flicker = 0.55F + 0.45F * (float)Math.sin((double)(time * (1.7F + hash01(seed, 5) * 2.0F) + (float)f));
            float prevX = startX;
            float prevY = startY;
            float prevW = width;

            for(int i = 1; i <= 16; ++i) {
               float t = (float)i / 16.0F;
               float y = startY + (top ? 1.0F : -1.0F) * reach * t;
               float x = startX + lean * reach * t + wave(t * 4.0F + (float)f, seed) * radius * 0.05F * t + wave(t * 11.0F + (float)f, seed * 7L) * radius * 0.022F * t;
               float w = width * 4.0F * t * (1.0F - t);
               float fade = (1.0F - t) * strength * flicker;
               int c0 = argb(Math.min(1.0F, fade * 1.1F), 1.0F, g, bl);
               int c1 = argb(Math.min(1.0F, fade * 0.9375F), tint[0], tint[1], tint[2]);
               emit(sink, p, prevX - prevW, prevY, 0.0F, 0.5F, c0);
               emit(sink, p, prevX + prevW, prevY, 1.0F, 0.5F, c0);
               emit(sink, p, x + w, y, 1.0F, 0.5F, c1);
               emit(sink, p, x - w, y, 0.0F, 0.5F, c1);
               prevX = x;
               prevY = y;
               prevW = w;
            }
         }
      }
   }

   private static void shockwave(VertexSink sink, Placement p, RiftAnim.Sample anim, float radius, float halfLen, float[] tint, float a) {
      float s = clamp(anim.shock());
      float reach = Math.max(radius, halfLen) * 1.9F;
      float ring = s * reach;
      float thickness = Math.max(radius * 0.05F, reach * 0.05F);
      float inner = Math.max(0.0F, ring - thickness);
      if (!(ring - inner < radius * 0.01F)) {
         float fade = (float)Math.sin((double)Math.min(1.0F, s * 1.1F) * Math.PI) * a;
         if (!(fade <= 0.004F)) {
            int hot = argb(Math.min(1.0F, fade), 1.0F, mix(tint[1], 1.0F, 0.5F), mix(tint[2], 1.0F, 0.35F));
            int clear = argb(0.0F, tint[0], tint[1], tint[2]);

            for(int i = 0; i < 96; ++i) {
               float a0 = (float)i / 96.0F * ((float)Math.PI * 2F);
               float a1 = (float)(i + 1) / 96.0F * ((float)Math.PI * 2F);
               float c0 = (float)Math.cos((double)a0);
               float s0 = (float)Math.sin((double)a0);
               float c1 = (float)Math.cos((double)a1);
               float s1 = (float)Math.sin((double)a1);
               emit(sink, p, inner * c0, inner * s0, 0.0F, 0.5F, clear);
               emit(sink, p, ring * c0, ring * s0, 1.0F, 0.5F, hot);
               emit(sink, p, ring * c1, ring * s1, 1.0F, 0.5F, hot);
               emit(sink, p, inner * c1, inner * s1, 0.0F, 0.5F, clear);
            }
         }
      }
   }

   private static void buildClosed(VertexSink sink, Placement p, RiftShape shape, float radius, RiftAnim.Sample anim, float from, float low, float high, float[] tint, float a, boolean inside) {
      float size = radius * (0.3F + 0.7F * anim.width());
      float hole = Math.max(clamp((anim.width() - 0.012F) / 0.35F), clamp(anim.opened() * 1.4F) * 0.9F);
      Ring r = ring(shape, size, anim, from, low, high);
      if (inside) {
         if (hole > 0.002F) {
            closedWindow(sink, p, r, size, a * hole);
         }
      } else {
         closedHalo(sink, p, r, size, anim, tint, a);
         closedEdges(sink, p, r, radius, size, anim, tint, a);
         closedFractures(sink, p, shape, radius, size, anim, from, low, high, tint, a);
         if (anim.shock() > 0.0F && anim.shock() < 1.0F) {
            shockwave(sink, p, anim, radius, radius, tint, a);
         }
      }
   }

   private static float angleOf(float u, float from) {
      return ((float)Math.PI / 2F) + (u - from) * ((float)Math.PI * 2F);
   }

   private static float outlineRadius(RiftShape shape, float size, float angle, RiftAnim.Sample anim) {
      float time = SkyClock.smoothTicks() * 0.05F;
      float rough = shape.roughness();
      float f = angle / ((float)Math.PI * 2F);
      float bumps;
      if (shape.isJagged()) {
         float crackle = time * 3.0F;
         long frame = (long)Math.floor((double)crackle);
         float blend = smooth01(crackle - (float)frame);
         float now = 0.18F * loopNoise(f, 13, 977L + frame * 31L, false) + 0.09F * loopNoise(f, 37, 433L + frame * 17L, false);
         float next = 0.18F * loopNoise(f, 13, 977L + (frame + 1L) * 31L, false) + 0.09F * loopNoise(f, 37, 433L + (frame + 1L) * 17L, false);
         bumps = now + (next - now) * blend;
      } else {
         bumps = 0.18F * loopNoise(f, 9, 31L, true) + 0.08F * loopNoise(f, 23, 53L, true) + 0.05F * (float)Math.sin((double)(4.0F * angle + time * 0.6F));
      }

      float jitter = anim.shake() == 0.0F ? 0.0F : (float)Math.sin((double)(time * 13.0F + angle * 5.0F)) * 0.02F * anim.shake();
      return size * shape.radiusAt(angle, time) * Math.max(0.05F, 1.0F + rough * bumps + jitter);
   }

   private static Ring ring(RiftShape shape, float size, RiftAnim.Sample anim, float from, float low, float high) {
      RingKey key = new RingKey(shape, size, anim, from, low, high, SkyClock.smoothTicks());
      if (key.equals(lastRingKey)) {
         return lastRing;
      } else {
         lastRing = makeRing(shape, size, anim, from, low, high);
         lastRingKey = key;
         return lastRing;
      }
   }

   private static Ring makeRing(RiftShape shape, float size, RiftAnim.Sample anim, float from, float low, float high) {
      int ring = size < (float)Math.toRadians((double)30.0F) ? 160 : 240;
      int n = Math.max(8, Math.round((float)ring * (high - low)));
      float[] x = new float[n + 1];
      float[] y = new float[n + 1];
      float[] outX = new float[n + 1];
      float[] outY = new float[n + 1];
      float[] angle = new float[n + 1];

      for(int i = 0; i <= n; ++i) {
         float th = angleOf(low + (high - low) * (float)i / (float)n, from);
         float c = (float)Math.cos((double)th);
         float s = (float)Math.sin((double)th);
         float d = outlineRadius(shape, size, th, anim);
         angle[i] = th;
         x[i] = d * c;
         y[i] = -d * s;
         outX[i] = c;
         outY[i] = -s;
      }

      return new Ring(n, x, y, outX, outY, angle);
   }

   private static void closedWindow(VertexSink sink, Placement p, Ring r, float size, float a) {
      int plain = argb(a, 1.0F, 1.0F, 1.0F);
      float scale = 1.0F / Math.max(1.0E-4F, size * 2.2F);

      for(int i = 0; i < r.n(); ++i) {
         float x0 = r.x()[i];
         float y0 = r.y()[i];
         float x1 = r.x()[i + 1];
         float y1 = r.y()[i + 1];
         emit(sink, p, 0.0F, 0.0F, 0.5F, 0.5F, plain);
         emit(sink, p, x0, y0, 0.5F + x0 * scale, 0.5F - y0 * scale, plain);
         emit(sink, p, x1, y1, 0.5F + x1 * scale, 0.5F - y1 * scale, plain);
         emit(sink, p, x1, y1, 0.5F + x1 * scale, 0.5F - y1 * scale, plain);
      }
   }

   private static void closedHalo(VertexSink sink, Placement p, Ring r, float size, RiftAnim.Sample anim, float[] tint, float a) {
      float reach = size * 0.35F;
      float strength = Math.min(0.8F, anim.rim() * 0.15F) * a;
      if (!(strength <= 0.002F)) {
         float time = SkyClock.smoothTicks() * 0.05F;
         int clear = argb(0.0F, tint[0], tint[1], tint[2]);

         for(int i = 0; i < r.n(); ++i) {
            float sw0 = 0.75F + 0.25F * (float)Math.sin((double)(3.0F * r.angle()[i] + time * 0.4F));
            float sw1 = 0.75F + 0.25F * (float)Math.sin((double)(3.0F * r.angle()[i + 1] + time * 0.4F));
            int c0 = argb(strength * sw0, tint[0], tint[1], tint[2]);
            int c1 = argb(strength * sw1, tint[0], tint[1], tint[2]);
            emit(sink, p, r.x()[i], r.y()[i], 0.5F, 0.5F, c0);
            emit(sink, p, r.x()[i] + r.outX()[i] * reach * sw0, r.y()[i] + r.outY()[i] * reach * sw0, 1.0F, 0.5F, clear);
            emit(sink, p, r.x()[i + 1] + r.outX()[i + 1] * reach * sw1, r.y()[i + 1] + r.outY()[i + 1] * reach * sw1, 1.0F, 0.5F, clear);
            emit(sink, p, r.x()[i + 1], r.y()[i + 1], 0.5F, 0.5F, c1);
         }
      }
   }

   private static void closedEdges(VertexSink sink, Placement p, Ring r, float radius, float size, RiftAnim.Sample anim, float[] tint, float a) {
      float time = SkyClock.smoothTicks() * 0.05F;
      float rim = anim.rim();
      float g = mix(tint[1], 1.0F, 0.55F);
      float bl = mix(tint[2], 1.0F, 0.4F);
      int clear = argb(0.0F, tint[0], tint[1], tint[2]);
      float base = Math.max(size * 0.035F, radius * 0.016F);

      for(int i = 0; i < r.n(); ++i) {
         int[] c = new int[2];
         int[] fade = new int[2];
         float[] out = new float[2];

         for(int k = 0; k < 2; ++k) {
            float f = r.angle()[i + k] / ((float)Math.PI * 2F);
            float pulse = (float)Math.pow(Math.max((double)0.0F, Math.sin((double)((f * 3.0F - time * 0.55F) * ((float)Math.PI * 2F)))), (double)14.0F) + (float)Math.pow(Math.max((double)0.0F, Math.sin((double)((f * 2.0F + time * 0.33F) * ((float)Math.PI * 2F)))), (double)18.0F);
            float glow = rim * 0.8F * (1.0F + pulse * 1.6F);
            out[k] = base * (1.0F + pulse);
            c[k] = argb(Math.min(1.0F, a * glow), 1.0F, g, bl);
            fade[k] = argb(Math.min(1.0F, a * rim * 0.3F), tint[0], tint[1], tint[2]);
         }

         float x0 = r.x()[i];
         float y0 = r.y()[i];
         float x1 = r.x()[i + 1];
         float y1 = r.y()[i + 1];
         float ox0 = r.outX()[i];
         float oy0 = r.outY()[i];
         float ox1 = r.outX()[i + 1];
         float oy1 = r.outY()[i + 1];
         emit(sink, p, x0, y0, 0.5F, 0.5F, c[0]);
         emit(sink, p, x0 + ox0 * out[0] * 0.4F, y0 + oy0 * out[0] * 0.4F, 0.7F, 0.5F, fade[0]);
         emit(sink, p, x1 + ox1 * out[1] * 0.4F, y1 + oy1 * out[1] * 0.4F, 0.7F, 0.5F, fade[1]);
         emit(sink, p, x1, y1, 0.5F, 0.5F, c[1]);
         emit(sink, p, x0 + ox0 * out[0] * 0.4F, y0 + oy0 * out[0] * 0.4F, 0.7F, 0.5F, fade[0]);
         emit(sink, p, x0 + ox0 * out[0], y0 + oy0 * out[0], 1.0F, 0.5F, clear);
         emit(sink, p, x1 + ox1 * out[1], y1 + oy1 * out[1], 1.0F, 0.5F, clear);
         emit(sink, p, x1 + ox1 * out[1] * 0.4F, y1 + oy1 * out[1] * 0.4F, 0.7F, 0.5F, fade[1]);
      }
   }

   private static void closedFractures(VertexSink sink, Placement p, RiftShape shape, float radius, float size, RiftAnim.Sample anim, float from, float low, float high, float[] tint, float a) {
      float grown = anim.branch();
      if (!(grown <= 0.01F)) {
         float time = SkyClock.smoothTicks() * 0.05F;
         float strength = a * Math.min(1.3F, anim.rim()) * 0.8F;
         float g = mix(tint[1], 1.0F, 0.5F);
         float bl = mix(tint[2], 1.0F, 0.35F);

         for(int f = 0; f < 11; ++f) {
            long seed = 131L * (long)(f + 3);
            float th = angleOf(low + (high - low) * hash01(seed, 1), from);
            float d = outlineRadius(shape, size, th, anim);
            float dirX = (float)Math.cos((double)th);
            float dirY = -((float)Math.sin((double)th));
            float startX = dirX * d;
            float startY = dirY * d;
            float lean = (hash01(seed, 3) - 0.5F) * 0.6F;
            float cl = (float)Math.cos((double)lean);
            float sl = (float)Math.sin((double)lean);
            float fx = dirX * cl - dirY * sl;
            float fy = dirX * sl + dirY * cl;
            float sideX = -fy;
            float sideY = fx;
            float reach = size * (0.2F + hash01(seed, 2) * 0.45F) * grown;
            float width = radius * (0.005F + hash01(seed, 4) * 0.008F);
            float flicker = 0.55F + 0.45F * (float)Math.sin((double)(time * (1.7F + hash01(seed, 5) * 2.0F) + (float)f));
            float prevX = startX;
            float prevY = startY;
            float prevW = width;

            for(int i = 1; i <= 16; ++i) {
               float t = (float)i / 16.0F;
               float zig = wave(t * 4.0F + (float)f, seed) * size * 0.06F * t + wave(t * 11.0F + (float)f, seed * 7L) * size * 0.025F * t;
               float x = startX + fx * reach * t + sideX * zig;
               float y = startY + fy * reach * t + sideY * zig;
               float w = width * 4.0F * t * (1.0F - t);
               float fadeOut = (1.0F - t) * strength * flicker;
               int c0 = argb(Math.min(1.0F, fadeOut * 1.1F), 1.0F, g, bl);
               int c1 = argb(Math.min(1.0F, fadeOut * 0.9375F), tint[0], tint[1], tint[2]);
               emit(sink, p, prevX - sideX * prevW, prevY - sideY * prevW, 0.0F, 0.5F, c0);
               emit(sink, p, prevX + sideX * prevW, prevY + sideY * prevW, 1.0F, 0.5F, c0);
               emit(sink, p, x + sideX * w, y + sideY * w, 1.0F, 0.5F, c1);
               emit(sink, p, x - sideX * w, y - sideY * w, 0.0F, 0.5F, c1);
               prevX = x;
               prevY = y;
               prevW = w;
            }
         }
      }
   }

   public static float[][] modelFrame(SkyboxEntry entry, RiftAnim.Sample anim, float alpha) {
      anim = RiftEffects.apply(anim, entry.layer());
      float a = alpha * anim.alpha() * clamp(anim.opened() * 1.4F);
      if (a <= 0.002F) {
         return null;
      } else {
         Placement p = spun(place(entry), anim.spin());
         float[] f = p.basis().forward();
         float[] r = p.basis().right();
         float[] u = p.basis().up();
         float[] up = new float[]{-(u[0] * p.cosRoll() - r[0] * p.sinRoll()), -(u[1] * p.cosRoll() - r[1] * p.sinRoll()), -(u[2] * p.cosRoll() - r[2] * p.sinRoll())};
         float radius = (float)Math.toRadians((double)Math.min(100.0F, Math.max(1.0F, entry.riftRadius())));
         float dist = 79.9F;
         float size = dist * (float)Math.tan((double)Math.min(radius, 1.2F)) * 0.7F * entry.modelScale();
         float[] across = new float[]{r[0] * p.cosRoll() + u[0] * p.sinRoll(), r[1] * p.cosRoll() + u[1] * p.sinRoll(), r[2] * p.cosRoll() + u[2] * p.sinRoll()};
         float shiftX = dist * (float)Math.tan(Math.toRadians((double)entry.modelPosX()));
         float shiftY = dist * (float)Math.tan(Math.toRadians((double)entry.modelPosY()));
         float[] center = new float[]{f[0] * dist + across[0] * shiftX + up[0] * shiftY, f[1] * dist + across[1] * shiftX + up[1] * shiftY, f[2] * dist + across[2] * shiftX + up[2] * shiftY};
         return new float[][]{center, up, {size, a}};
      }
   }

   public static void clipToHole(SkyboxEntry entry, RiftAnim.Sample anim, float alpha, List<float[]> quads, VertexSink out) {
      List<float[]> corners = new ArrayList();
      capture = corners;

      try {
         build(entry, anim, alpha, true, (x, y, z, u, vx, argb) -> {
         });
      } finally {
         capture = null;
      }

      ArrayList<float[]> pieces = new ArrayList<>();

      for(int i = 0; i + 3 < corners.size(); i += 4) {
         float[] piece = new float[8];
         float area = 0.0F;

         for(int k = 0; k < 4; ++k) {
            piece[k * 2] = ((float[])corners.get(i + k))[0];
            piece[k * 2 + 1] = ((float[])corners.get(i + k))[1];
         }

         for(int k = 0; k < 4; ++k) {
            int n = (k + 1) % 4;
            area += piece[k * 2] * piece[n * 2 + 1] - piece[n * 2] * piece[k * 2 + 1];
         }

         if (Math.abs(area) > 1.0E-9F) {
            pieces.add(piece);
         }
      }

      if (!pieces.isEmpty()) {
         Basis b = place(entry).basis();
         float[] f = b.forward();
         float[] r = b.right();
         float[] up = b.up();

         for(int q = 0; q + 3 < quads.size(); q += 4) {
            float[][] poly = new float[4][];
            float minX = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE;
            float minY = Float.MAX_VALUE;
            float maxY = -Float.MAX_VALUE;

            for(int k = 0; k < 4; ++k) {
               float[] v = (float[])quads.get(q + k);
               float len = (float)Math.sqrt((double)(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]));
               if (len < 1.0E-5F) {
                  len = 1.0F;
               }

               float dx = v[0] / len;
               float dy = v[1] / len;
               float dz = v[2] / len;
               float ay = (float)Math.asin((double)Math.max(-1.0F, Math.min(1.0F, dx * up[0] + dy * up[1] + dz * up[2])));
               float ax = (float)Math.atan2((double)(dx * r[0] + dy * r[1] + dz * r[2]), (double)(dx * f[0] + dy * f[1] + dz * f[2]));
               poly[k] = new float[]{v[0], v[1], v[2], v[3], v[4], ax, ay};
               minX = Math.min(minX, ax);
               maxX = Math.max(maxX, ax);
               minY = Math.min(minY, ay);
               maxY = Math.max(maxY, ay);
            }

            int argb = Float.floatToRawIntBits(((float[])quads.get(q))[5]);

            for(float[] piece : pieces) {
               float pMinX = Math.min(Math.min(piece[0], piece[2]), Math.min(piece[4], piece[6]));
               float pMaxX = Math.max(Math.max(piece[0], piece[2]), Math.max(piece[4], piece[6]));
               float pMinY = Math.min(Math.min(piece[1], piece[3]), Math.min(piece[5], piece[7]));
               float pMaxY = Math.max(Math.max(piece[1], piece[3]), Math.max(piece[5], piece[7]));
               if (!(pMaxX < minX) && !(pMinX > maxX) && !(pMaxY < minY) && !(pMinY > maxY)) {
                  List<float[]> left = clip(Arrays.asList(poly), piece);

                  for(int k = 1; k + 1 < left.size(); ++k) {
                     float[] a = (float[])left.get(0);
                     float[] c = (float[])left.get(k);
                     float[] d = (float[])left.get(k + 1);
                     out.vertex(a[0], a[1], a[2], a[3], a[4], argb);
                     out.vertex(c[0], c[1], c[2], c[3], c[4], argb);
                     out.vertex(d[0], d[1], d[2], d[3], d[4], argb);
                     out.vertex(d[0], d[1], d[2], d[3], d[4], argb);
                  }
               }
            }
         }
      }
   }

   private static List<float[]> clip(List<float[]> poly, float[] piece) {
      float area = 0.0F;

      for(int k = 0; k < 4; ++k) {
         int n = (k + 1) % 4;
         area += piece[k * 2] * piece[n * 2 + 1] - piece[n * 2] * piece[k * 2 + 1];
      }

      float side = Math.signum(area);
      List<float[]> cur = poly;

      for(int e = 0; e < 4 && !cur.isEmpty(); ++e) {
         float ex = piece[e * 2];
         float ey = piece[e * 2 + 1];
         float fx = piece[(e + 1) % 4 * 2];
         float fy = piece[(e + 1) % 4 * 2 + 1];
         if (!(Math.abs(fx - ex) + Math.abs(fy - ey) < 1.0E-9F)) {
            List<float[]> next = new ArrayList();

            for(int i = 0; i < cur.size(); ++i) {
               float[] p = (float[])cur.get(i);
               float[] q = (float[])cur.get((i + 1) % cur.size());
               float dp = side * ((fx - ex) * (p[6] - ey) - (fy - ey) * (p[5] - ex));
               float dq = side * ((fx - ex) * (q[6] - ey) - (fy - ey) * (q[5] - ex));
               if (dp >= 0.0F) {
                  next.add(p);
               }

               if (dp >= 0.0F != dq >= 0.0F) {
                  float t = dp / (dp - dq);
                  float[] m = new float[7];

                  for(int k = 0; k < 7; ++k) {
                     m[k] = p[k] + (q[k] - p[k]) * t;
                  }

                  next.add(m);
               }
            }

            cur = next;
         }
      }

      return cur.size() >= 3 ? cur : List.of();
   }

   private static Placement place(SkyboxEntry entry) {
      float roll = (float)Math.toRadians((double)entry.rotation());
      float yaw = (float)Math.toRadians(entry.hasPosX() ? (double)entry.posX() : (double)0.0F);
      float pitch = (float)Math.toRadians(entry.hasPosY() ? (double)entry.posY() : (double)90.0F);
      return new Placement(basis(yaw, pitch), (float)Math.cos((double)roll), (float)Math.sin((double)roll));
   }

   private static Basis basis(float yaw, float pitch) {
      float cosPitch = (float)Math.cos((double)pitch);
      float[] forward = normalize(cosPitch * (float)Math.cos((double)yaw), (float)Math.sin((double)pitch), cosPitch * (float)Math.sin((double)yaw));
      float[] right = normalize(forward[2], 0.0F, -forward[0]);
      if (right == null) {
         right = new float[]{1.0F, 0.0F, 0.0F};
      }

      return new Basis(forward, right, cross(right, forward), 1.0F, 0.0F);
   }

   private static void emit(VertexSink sink, Placement p, float lx, float ly, float u, float v, int argb) {
      float ax = lx * p.cosRoll() - ly * p.sinRoll();
      float ay = lx * p.sinRoll() + ly * p.cosRoll();
      if (capture != null) {
         capture.add(new float[]{ax, ay});
      }

      Basis b = p.basis();
      float cosX = fastCos(ax);
      float sinX = fastSin(ax);
      float cosY = fastCos(ay);
      float sinY = fastSin(ay);
      float[] f = b.forward();
      float[] r = b.right();
      float[] up = b.up();
      float dx = (f[0] * cosX + r[0] * sinX) * cosY + up[0] * sinY;
      float dy = (f[1] * cosX + r[1] * sinX) * cosY + up[1] * sinY;
      float dz = (f[2] * cosX + r[2] * sinX) * cosY + up[2] * sinY;
      sink.vertex(94.0F * dx, 94.0F * dy, 94.0F * dz, u, v, argb);
   }

   public static int quadCount(boolean inside) {
      return inside ? Math.max(110, 240) + 4 : Math.max(1320, 720) + 176 + 96 + 8 + 48 + 576 + 160 + 34;
   }

   private static float profile(float s) {
      return (float)Math.pow(Math.sin((double)clamp(s) * Math.PI), 0.55);
   }

   private static float wave(float x, long seed) {
      int i = (int)Math.floor((double)x);
      float t = x - (float)i;
      float a = hash01(seed, i) * 2.0F - 1.0F;
      float b = hash01(seed, i + 1) * 2.0F - 1.0F;
      float e = t * t * (3.0F - 2.0F * t);
      return a + (b - a) * e;
   }

   private static float loopNoise(float f, int cells, long seed, boolean smooth) {
      float x = (f % 1.0F + 1.0F) % 1.0F * (float)cells;
      int i = (int)Math.floor((double)x);
      float t = x - (float)i;
      float a = hash01(seed, i % cells) * 2.0F - 1.0F;
      float b = hash01(seed, (i + 1) % cells) * 2.0F - 1.0F;
      float e = smooth ? t * t * (3.0F - 2.0F * t) : t;
      return a + (b - a) * e;
   }

   private static float fastSin(float a) {
      float t = a * 1303.7972F;
      t -= (float)Math.floor((double)(t / 8192.0F)) * 8192.0F;
      int i = (int)t;
      if (i >= 8192) {
         i = 8191;
      }

      float f = t - (float)i;
      return SINE[i] + (SINE[i + 1] - SINE[i]) * f;
   }

   private static float fastCos(float a) {
      return fastSin(a + ((float)Math.PI / 2F));
   }

   private static float hash01(long seed, int salt) {
      long h = seed * 6364136223846793005L + (long)salt * 1442695040888963407L;
      h ^= h >>> 29;
      h *= -4658895280553007687L;
      h ^= h >>> 32;
      return (float)(h >>> 11 & 65535L) / 65535.0F;
   }

   private static int shade(float alpha, float light) {
      return argb(alpha, light, light, light);
   }

   private static float mix(float a, float b, float t) {
      return a + (b - a) * t;
   }

   private static float[] rgb(int color) {
      return new float[]{(float)(color >> 16 & 255) / 255.0F, (float)(color >> 8 & 255) / 255.0F, (float)(color & 255) / 255.0F};
   }

   private static float[] normalize(float x, float y, float z) {
      float len = (float)Math.sqrt((double)(x * x + y * y + z * z));
      return len < 1.0E-5F ? null : new float[]{x / len, y / len, z / len};
   }

   private static float[] cross(float[] a, float[] b) {
      return new float[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
   }

   private static int argb(float alpha, float r, float g, float b) {
      int a = (int)(clamp(alpha) * 255.0F) & 255;
      int ri = (int)(clamp(r) * 255.0F) & 255;
      int gi = (int)(clamp(g) * 255.0F) & 255;
      int bi = (int)(clamp(b) * 255.0F) & 255;
      return a << 24 | ri << 16 | gi << 8 | bi;
   }

   private static float clamp(float v) {
      return v < 0.0F ? 0.0F : (v > 1.0F ? 1.0F : v);
   }

   static {
      for(int i = 0; i < SINE.length; ++i) {
         SINE[i] = (float)Math.sin((double)i * (Math.PI * 2D) / (double)8192.0F);
      }
   }

   @Environment(EnvType.CLIENT)
   private static record Basis(float[] forward, float[] right, float[] up, float cosRoll, float sinRoll) {
   }

   @Environment(EnvType.CLIENT)
   private static record Outline(int n, float[] along, float[] mid, float[] half, float[] s) {
   }

   @Environment(EnvType.CLIENT)
   private static record OutlineKey(RiftShape shape, float radius, float halfLen, float open, RiftAnim.Sample anim, float low, float high, float time) {
   }

   @Environment(EnvType.CLIENT)
   private static record Ring(int n, float[] x, float[] y, float[] outX, float[] outY, float[] angle) {
   }

   @Environment(EnvType.CLIENT)
   private static record RingKey(RiftShape shape, float size, RiftAnim.Sample anim, float from, float low, float high, float time) {
   }

   @Environment(EnvType.CLIENT)
   private static record Placement(Basis basis, float cosRoll, float sinRoll) {
   }

   @FunctionalInterface
   @Environment(EnvType.CLIENT)
   public interface VertexSink {
      void vertex(float var1, float var2, float var3, float var4, float var5, int var6);
   }
}
