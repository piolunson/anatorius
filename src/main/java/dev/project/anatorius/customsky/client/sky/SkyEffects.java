package dev.project.anatorius.customsky.client.sky;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;

@Environment(EnvType.CLIENT)
public final class SkyEffects {
   private static final float HANDOVER_TICKS = 10.0F;
   private static final int MAX_PER_TYPE = 8;
   private static final Map<SkyboxType, List<Running>> ACTIVE = new EnumMap(SkyboxType.class);
   private static final Map<SkyboxType, Frame> FRAMES = new EnumMap(SkyboxType.class);

   private SkyEffects() {
   }

   public static void start(SkyboxType type, Effect effect, int ticks, int color, float strength, float size) {
      start(type, effect, ticks, color, strength, size, false, (String)null);
   }

   public static void start(SkyboxType type, Effect effect, int ticks, int color, float strength, float size, boolean loop, String biome) {
      add(type, new Running(effect, (CelestialPreset)null, currentTick(), Math.max(1, ticks), color, strength, Math.max(0.1F, size), loop, biome));
   }

   public static void start(SkyboxType type, Effect effect, int ticks, int color, float strength) {
      start(type, effect, ticks, color, strength, 1.0F);
   }

   public static void start(SkyboxType type, CelestialPreset preset, int ticks, float strength) {
      start(type, preset, ticks, strength, false, (String)null);
   }

   public static void start(SkyboxType type, CelestialPreset preset, int ticks, float strength, boolean loop, String biome) {
      start(type, preset, ticks, strength, loop, biome, 1.0F);
   }

   public static void start(SkyboxType type, CelestialPreset preset, int ticks, float strength, boolean loop, String biome, float size) {
      add(type, new Running((Effect)null, preset, currentTick(), Math.max(1, ticks), 16777215, strength, size, loop, biome));
   }

   private static void add(SkyboxType type, Running started) {
      List<Running> list = (List)ACTIVE.computeIfAbsent(type, (t) -> new ArrayList());

      for(Running r : list) {
         if (r.handedOverTick < 0L && r.effect == started.effect && r.preset == started.preset) {
            r.handedOverTick = started.startTick;
         }
      }

      list.add(started);

      while(list.size() > 8) {
         list.remove(0);
      }
   }

   public static void stop(SkyboxType type) {
      ACTIVE.remove(type);
   }

   public static boolean stop(SkyboxType type, String name) {
      List<Running> list = (List)ACTIVE.get(type);
      if (list == null) {
         return false;
      } else {
         Effect effect = (Effect)SkyEffects.Effect.byId(name).orElse(null);
         CelestialPreset preset = (CelestialPreset)CelestialPreset.byId(name).orElse(null);
         if (effect == null && preset == null) {
            return false;
         } else {
            boolean any = false;
            long now = currentTick();

            for(Running r : list) {
               if (r.handedOverTick < 0L && (effect != null && r.effect == effect || preset != null && r.preset == preset)) {
                  r.handedOverTick = now;
                  any = true;
               }
            }

            return any;
         }
      }
   }

   public static boolean stopEverywhere(String name) {
      boolean any = false;

      for(SkyboxType type : SkyboxType.values()) {
         if (stop(type, name)) {
            any = true;
         }
      }

      return any;
   }

   public static void stopAll() {
      ACTIVE.clear();
   }

   public static boolean isRunning(SkyboxType type) {
      for(Running r : live(type)) {
         if (r.effect != null && r.hereNow()) {
            return true;
         }
      }

      return false;
   }

   private static List<Running> live(SkyboxType type) {
      List<Running> list = (List)ACTIVE.get(type);
      if (list == null) {
         return List.of();
      } else {
         list.removeIf((r) -> !r.loop && progress(r) >= 1.0F || r.handedOverTick >= 0L && weight(r) <= 0.0F);
         if (list.isEmpty()) {
            ACTIVE.remove(type);
            return List.of();
         } else {
            return list;
         }
      }
   }

   private static List<Running> running(SkyboxType type) {
      List<Running> list = (List)ACTIVE.get(type);
      return list == null ? List.of() : list;
   }

   private static float rawProgress(Running r) {
      return Math.max(0.0F, ((float)(currentTick() - r.startTick) + partialTick()) / (float)r.ticks);
   }

   private static float progress(Running r) {
      float raw = Math.max(0.0F, ((float)(currentTick() - r.startTick) + partialTick()) / (float)r.ticks);
      return !r.loop ? raw : raw - (float)Math.floor((double)raw);
   }

   private static float weight(Running r) {
      if (r.handedOverTick < 0L) {
         return 1.0F;
      } else {
         float t = ((float)(currentTick() - r.handedOverTick) + partialTick()) / 10.0F;
         if (t >= 1.0F) {
            return 0.0F;
         } else {
            return t <= 0.0F ? 1.0F : 1.0F - t * t * (3.0F - 2.0F * t);
         }
      }
   }

   public static CelestialPreset.Motion celestialMotion(SkyboxType type) {
      float offsetX = 0.0F;
      float offsetY = 0.0F;
      float scale = 1.0F;
      float roll = 0.0F;
      float alpha = 1.0F;
      boolean any = false;

      for(Running r : live(type)) {
         if (r.preset != null && r.hereNow()) {
            float w = weight(r);
            if (!(w <= 0.0F)) {
               float p = progress(r);
               float amount = (float)Math.sin((double)p * Math.PI) * r.strength * w;
               CelestialPreset.Motion m = r.preset.motion(p);
               if (r.preset == CelestialPreset.SCALE) {
                  float wave = 0.5F + 0.5F * (float)Math.sin((double)p * Math.PI * (double)2.0F - (Math.PI / 2D));
                  scale *= 1.0F + (r.size - 1.0F) * wave * w;
                  any = true;
               } else {
                  offsetX += m.offsetX() * amount;
                  offsetY += m.offsetY() * amount;
                  scale *= 1.0F + (m.scale() - 1.0F) * amount;
                  roll += m.roll() * amount;
                  alpha *= 1.0F - (1.0F - m.alpha()) * amount;
                  any = true;
               }
            }
         }
      }

      return any ? new CelestialPreset.Motion(offsetX, offsetY, scale, roll, alpha) : CelestialPreset.Motion.IDLE;
   }

   public static DomeSample domeSample(SkyboxType type, float u, float v) {
      List<Running> list = running(type);
      if (list.isEmpty()) {
         return SkyEffects.DomeSample.NONE;
      } else {
         float glowR = 0.0F;
         float glowG = 0.0F;
         float glowB = 0.0F;
         float glowAlpha = 0.0F;
         float darken = 1.0F;
         float offsetU = 0.0F;
         float offsetV = 0.0F;

         for(Running r : list) {
            if (r.effect != null && r.hereNow()) {
               float w = weight(r);
               if (!(w <= 0.0F)) {
                  DomeSample s = sample(r, u, v);
                  if (!s.isNone()) {
                     float a = s.glowAlpha() * w;
                     glowR += s.glowR() * a;
                     glowG += s.glowG() * a;
                     glowB += s.glowB() * a;
                     glowAlpha += a;
                     darken *= 1.0F - (1.0F - s.darken()) * w;
                     offsetU += s.offsetU() * w;
                     offsetV += s.offsetV() * w;
                  }
               }
            }
         }

         if (!(glowAlpha <= 0.0F)) {
            float inv = 1.0F / glowAlpha;
            return new DomeSample(glowR * inv, glowG * inv, glowB * inv, Math.min(1.0F, glowAlpha), darken, offsetU, offsetV);
         } else {
            return darken >= 1.0F && offsetU == 0.0F && offsetV == 0.0F ? SkyEffects.DomeSample.NONE : new DomeSample(1.0F, 1.0F, 1.0F, 0.0F, darken, offsetU, offsetV);
         }
      }
   }

   private static DomeSample sample(Running r, float u, float v) {
      float p = progress(r);
      if (p >= 1.0F) {
         return SkyEffects.DomeSample.NONE;
      } else {
         float cr = (float)(r.color >> 16 & 255) / 255.0F;
         float cg = (float)(r.color >> 8 & 255) / 255.0F;
         float cb = (float)(r.color & 255) / 255.0F;
         float k = r.strength;
         float size = r.size;
         float window = (float)Math.sin((double)p * Math.PI);
         DomeSample var10000;
         switch (r.effect.ordinal()) {
            case 0:
               var10000 = band(cr, cg, cb, p, v, 0.16F * size, k, 0.0F);
               break;
            case 1:
               var10000 = band(cr, cg, cb, p, v, 0.1F * size, k, 0.06F);
               break;
            case 2:
               var10000 = blackHole(p, v, u, k, size);
               break;
            case 3:
               var10000 = glitch(p, v, cr, cg, cb, k, size);
               break;
            case 4:
               var10000 = aurora(p, v, u, cr, cg, cb, k, size);
               break;
            case 5:
               var10000 = new DomeSample(cr, cg, cb, window * 0.7F * k, 1.0F, 0.0F, 0.0F);
               break;
            case 6:
               float rings = (float)Math.sin((double)(v * (6.0F / size) - p * 8.0F) * Math.PI);
               float w = Math.max(0.0F, rings) * window;
               var10000 = new DomeSample(cr, cg, cb, w * 0.5F * k, 1.0F, 0.0F, w * 0.01F * k);
               break;
            case 7:
               float turn = r.loop ? rawProgress(r) * 0.35F : window * 0.2F;
               float twist = (1.0F - v) * (1.0F - v) * 0.22F * size * k * (r.loop ? 1.0F : window);
               var10000 = new DomeSample(cr, cg, cb, (r.loop ? 0.12F : window * 0.15F) * k, 1.0F, turn + twist, 0.0F);
               break;
            case 8:
               float turn8 = r.loop ? rawProgress(r) * 0.5F : window * 0.25F;
               var10000 = new DomeSample(cr, cg, cb, (r.loop ? 0.06F : window * 0.1F) * k, 1.0F, turn8 * size, 0.0F);
               break;
            case 9:
               DomeSample ring = band(cr, cg, cb, p, v, 0.07F * size, k * 1.4F, 0.02F);
               var10000 = new DomeSample(ring.glowR(), ring.glowG(), ring.glowB(), ring.glowAlpha() + window * 0.08F * k, ring.darken(), ring.offsetU(), ring.offsetV());
               break;
            case 10:
               float n = SkyReveal.noise(u * 24.0F / size, v * 18.0F / size + p * 12.0F);
               float w10 = Math.max(0.0F, n - 0.55F) * 2.2F * window;
               var10000 = new DomeSample(cr, cg, cb, w10 * 0.5F * k, 1.0F - w10 * 0.2F * k, 0.0F, 0.0F);
               break;
            case 11:
               float beat = (float)Math.max((double)0.0F, Math.sin((double)p * Math.PI * (double)4.0F));
               float strong = p < 0.5F ? 1.0F : 0.55F;
               var10000 = new DomeSample(cr, cg, cb, beat * strong * 0.6F * k, 1.0F, 0.0F, 0.0F);
               break;
            default:
               throw new MatchException((String)null, (Throwable)null);
         }

         return var10000;
      }
   }

   private static DomeSample band(float r, float g, float b, float p, float v, float width, float strength, float pinch) {
      float centre = p * 1.2F - 0.1F;
      float d = Math.abs(v - centre);
      if (d > width) {
         return SkyEffects.DomeSample.NONE;
      } else {
         float w = 1.0F - d / width;
         w *= w;
         float fade = (float)Math.sin((double)Math.min(1.0F, p) * Math.PI);
         float amount = w * fade * strength;
         return new DomeSample(r, g, b, amount, 1.0F - amount * 0.25F, 0.0F, pinch == 0.0F ? 0.0F : (v - centre) * pinch * w * strength);
      }
   }

   private static DomeSample blackHole(float p, float v, float u, float strength, float size) {
      float w = (float)Math.sin((double)p * Math.PI);
      float reach = Math.max(0.12F, 0.45F * size);
      float pull = (1.0F - Math.min(1.0F, v / reach)) * (1.0F - Math.min(1.0F, v / reach)) * w * strength;
      float dark = Math.max(0.0F, 1.0F - v * (5.0F / size)) * w * strength;
      return new DomeSample(0.0F, 0.0F, 0.0F, 0.0F, Math.max(0.05F, 1.0F - dark), (float)Math.sin((double)u * Math.PI * (double)2.0F) * pull * 0.05F, -pull * 0.12F);
   }

   private static DomeSample glitch(float p, float v, float r, float g, float b, float strength, float size) {
      int band = (int)(v * (14.0F / size));
      int step = (int)(p * 24.0F);
      int hash = band * 73856093 ^ step * 19349663;
      boolean torn = (hash & 7) < 3;
      if (!torn) {
         return SkyEffects.DomeSample.NONE;
      } else {
         float shift = ((float)(hash >> 3 & 255) / 255.0F - 0.5F) * 0.08F * strength * size;
         return new DomeSample(r, g, b, 0.2F * strength, 0.85F, shift, 0.0F);
      }
   }

   private static DomeSample aurora(float p, float v, float u, float r, float g, float b, float strength, float size) {
      float top = 0.55F * size;
      if (v > top) {
         return SkyEffects.DomeSample.NONE;
      } else {
         double wave = Math.sin((double)u * Math.PI * (double)6.0F + (double)p * Math.PI * (double)4.0F) * (double)0.5F + (double)0.5F;
         float height = 1.0F - v / top;
         float w = (float)wave * height * (float)Math.sin((double)p * Math.PI);
         return new DomeSample(r, g, b, w * 0.5F * strength, 1.0F, 0.0F, (float)wave * 0.01F * strength);
      }
   }

   public static Frame frame(SkyboxType type) {
      if (type == null) {
         return null;
      } else {
         List<Running> list = live(type);
         boolean any = false;

         for(Running r : list) {
            if (r.effect != null && r.hereNow()) {
               any = true;
               break;
            }
         }

         if (!any) {
            FRAMES.remove(type);
            return null;
         } else {
            Frame frame = (Frame)FRAMES.computeIfAbsent(type, (t) -> new Frame());
            double now = (double)((float)currentTick() + partialTick());
            if (frame.stamp != now) {
               frame.stamp = now;
               frame.rebuild(type);
            }

            return frame;
         }
      }
   }

   private static long currentTick() {
      return SkyClock.ticks();
   }

   private static float partialTick() {
      MinecraftClient mc = MinecraftClient.getInstance();
      return mc == null ? 0.0F : mc.getRenderTickCounter().getTickProgress(false);
   }

   @Environment(EnvType.CLIENT)
   public static enum Effect {
      PULSE("pulse"),
      SHOCKWAVE("shockwave"),
      BLACK_HOLE("black_hole"),
      GLITCH("glitch"),
      AURORA("aurora"),
      FLASH("flash"),
      RIPPLE("ripple"),
      VORTEX("vortex"),
      ROTATION("rotation"),
      NOVA("nova"),
      STATIC("static"),
      HEARTBEAT("heartbeat");

      private final String id;

      private Effect(String id) {
         this.id = id;
      }

      public String id() {
         return this.id;
      }

      public static Optional<Effect> byId(String id) {
         return id == null ? Optional.empty() : Arrays.stream(values()).filter((e) -> e.id.equalsIgnoreCase(id)).findFirst();
      }

      public static List<String> names() {
         List<String> out = new ArrayList();

         for(Effect e : values()) {
            out.add(e.id);
         }

         return out;
      }

      private static Effect[] $values() {
         return new Effect[]{PULSE, SHOCKWAVE, BLACK_HOLE, GLITCH, AURORA, FLASH, RIPPLE, VORTEX, ROTATION, NOVA, STATIC, HEARTBEAT};
      }
   }

   @Environment(EnvType.CLIENT)
   public static record DomeSample(float glowR, float glowG, float glowB, float glowAlpha, float darken, float offsetU, float offsetV) {
      public static final DomeSample NONE = new DomeSample(1.0F, 1.0F, 1.0F, 0.0F, 1.0F, 0.0F, 0.0F);

      public boolean isNone() {
         return this.glowAlpha <= 0.0F && this.darken >= 1.0F && this.offsetU == 0.0F && this.offsetV == 0.0F;
      }
   }

   @Environment(EnvType.CLIENT)
   private static final class Running {
      final Effect effect;
      final CelestialPreset preset;
      final long startTick;
      final int ticks;
      final int color;
      final float strength;
      final float size;
      final boolean loop;
      final String biome;
      long handedOverTick = -1L;

      Running(Effect effect, CelestialPreset preset, long startTick, int ticks, int color, float strength, float size, boolean loop, String biome) {
         this.effect = effect;
         this.preset = preset;
         this.startTick = startTick;
         this.ticks = ticks;
         this.color = color;
         this.strength = strength;
         this.size = size;
         this.loop = loop;
         this.biome = biome;
      }

      boolean hereNow() {
         if (this.biome == null) {
            return true;
         } else {
            String current = SkyboxManager.currentBiomeText();
            return current != null && current.equals(this.biome);
         }
      }
   }

   @Environment(EnvType.CLIENT)
   public static final class Frame {
      public static final int GLOW_R = 0;
      public static final int GLOW_G = 1;
      public static final int GLOW_B = 2;
      public static final int GLOW_A = 3;
      public static final int DARKEN = 4;
      public static final int OFFSET_U = 5;
      public static final int OFFSET_V = 6;
      public static final int SLOTS = 7;
      private static final int LU = 48;
      private static final int LV = 64;
      private final float[] data = new float[21504];
      private double stamp = Double.NaN;

      private void rebuild(SkyboxType type) {
         for(int y = 0; y < 64; ++y) {
            float v = (float)y / 63.0F;

            for(int x = 0; x < 48; ++x) {
               float u = (float)x / 48.0F;
               DomeSample s = SkyEffects.domeSample(type, u, v);
               int i = (y * 48 + x) * 7;
               this.data[i + 0] = s.glowR();
               this.data[i + 1] = s.glowG();
               this.data[i + 2] = s.glowB();
               this.data[i + 3] = s.glowAlpha();
               this.data[i + 4] = s.darken();
               this.data[i + 5] = s.offsetU();
               this.data[i + 6] = s.offsetV();
            }
         }
      }

      public void sampleInto(float u, float v, float[] out) {
         float fu = (u - (float)Math.floor((double)u)) * 48.0F;
         float fv = (v < 0.0F ? 0.0F : (v > 1.0F ? 1.0F : v)) * 63.0F;
         int u0 = (int)fu;
         int v0 = (int)fv;
         float du = fu - (float)u0;
         float dv = fv - (float)v0;
         u0 %= 48;
         int u1 = (u0 + 1) % 48;
         int v1 = Math.min(v0 + 1, 63);
         int a = (v0 * 48 + u0) * 7;
         int b = (v0 * 48 + u1) * 7;
         int c = (v1 * 48 + u0) * 7;
         int d = (v1 * 48 + u1) * 7;

         for(int i = 0; i < 7; ++i) {
            float top = this.data[a + i] + (this.data[b + i] - this.data[a + i]) * du;
            float bottom = this.data[c + i] + (this.data[d + i] - this.data[c + i]) * du;
            out[i] = top + (bottom - top) * dv;
         }
      }
   }
}
