package dev.project.anatorius.customsky.client.sky;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class RiftEffects {
   private static final List<Running> RUNNING = new ArrayList();

   private RiftEffects() {
   }

   public static synchronized void start(Effect effect, int ticks, float strength, boolean loop, int layer) {
      start(effect, ticks, strength, loop, layer, RiftEffects.Options.NONE);
   }

   public static synchronized void start(Effect effect, int ticks, float strength, boolean loop, int layer, Options options) {
      Options o = options == null ? RiftEffects.Options.NONE : options;
      RUNNING.add(new Running(effect, now() + (float)Math.max(0, o.delay()), Math.max(1, ticks), strength, loop, layer, o));
   }

   private static float presence(Running r, float now) {
      float t = (now - r.start()) / (float)r.ticks();
      if (t < 0.0F) {
         return 0.0F;
      } else {
         return r.loop() ? 1.0F : (float)Math.sin((double)Math.min(1.0F, t) * Math.PI);
      }
   }

   public static synchronized int tint(int layer, int base) {
      if (RUNNING.isEmpty()) {
         return base;
      } else {
         float now = now();
         float r = (float)(base >> 16 & 255);
         float g = (float)(base >> 8 & 255);
         float b = (float)(base & 255);

         for(Running run : RUNNING) {
            if (run.options().color() >= 0 && (run.layer() == 0 || run.layer() == layer)) {
               float k = Math.min(1.0F, presence(run, now) * Math.max(0.35F, Math.min(1.0F, run.strength())));
               int c = run.options().color();
               r += ((float)(c >> 16 & 255) - r) * k;
               g += ((float)(c >> 8 & 255) - g) * k;
               b += ((float)(c & 255) - b) * k;
            }
         }

         return Math.round(r) << 16 | Math.round(g) << 8 | Math.round(b);
      }
   }

   public static synchronized float reach(int layer) {
      float out = 1.0F;
      float now = now();

      for(Running run : RUNNING) {
         if (run.layer() == 0 || run.layer() == layer) {
            float k = presence(run, now);
            out = Math.max(out, 1.0F + (run.options().size() - 1.0F) * k);
            if (run.options().size() < 1.0F) {
               out = Math.min(out, 1.0F + (run.options().size() - 1.0F) * k);
            }
         }
      }

      return out;
   }

   public static synchronized void stopAll() {
      RUNNING.clear();
   }

   public static synchronized RiftAnim.Sample apply(RiftAnim.Sample base, int layer) {
      if (RUNNING.isEmpty()) {
         return base;
      } else {
         float now = now();
         RUNNING.removeIf((rx) -> !rx.loop() && now - rx.start() >= (float)rx.ticks());
         if (RUNNING.isEmpty()) {
            return base;
         } else {
            float width = base.width();
            float rim = base.rim();
            float shake = base.shake();
            float branch = base.branch();
            float shock = base.shock();
            float spin = base.spin();
            float flash = base.flash();
            float shards = base.shards();
            float bolts = base.bolts();
            float charge = base.charge();

            for(Running r : RUNNING) {
               if (r.layer() == 0 || r.layer() == layer) {
                  float elapsed = (now - r.start()) / (float)r.ticks();
                  if (!(elapsed < 0.0F)) {
                     float t = elapsed;
                     if (r.loop()) {
                        t = elapsed - (float)Math.floor((double)elapsed);
                     }

                     t = Math.max(0.0F, Math.min(1.0F, t));
                     float k = r.strength();
                     Options o = r.options();
                     float rimBefore = rim;
                     float shakeBefore = shake;
                     float flashBefore = flash;
                     if (o.spin() != 0.0F) {
                        float turns = r.loop() ? elapsed : t * t * (3.0F - 2.0F * t);
                        spin += (float)Math.toRadians((double)o.spin()) * turns;
                     }

                     switch (r.effect().ordinal()) {
                        case 0:
                           flash = Math.max(flash, bump(t, 0.0F, 0.35F) * k);
                           shock = Math.max(shock, Math.min(0.999F, t / 0.6F));
                           shards = Math.max(shards, Math.min(0.999F, t / 0.85F));
                           shake += (1.0F - t) * (1.0F - t) * 3.0F * k;
                           rim += bump(t, 0.0F, 0.4F) * 6.0F * k;
                           break;
                        case 1:
                           branch *= 1.0F + bump(t, 0.0F, 0.6F) * 1.6F * k;
                           shards = Math.max(shards, t < 0.15F ? 0.0F : Math.min(0.999F, (t - 0.15F) / 0.85F));
                           flash = Math.max(flash, bump(t, 0.1F, 0.35F) * 0.7F * k);
                           shake += bump(t, 0.0F, 0.4F) * 0.8F * k;
                           break;
                        case 2:
                           shock = Math.max(shock, Math.min(0.999F, t));
                           rim += bump(t, 0.0F, 0.3F) * 2.0F * k;
                           break;
                        case 3:
                           bolts = Math.max(bolts, Math.min(0.999F, t));
                           float strike = (float)Math.pow(Math.max((double)0.0F, Math.sin((double)t * Math.PI * (double)5.0F)), (double)10.0F) * k;
                           flash = Math.max(flash, strike * 0.5F);
                           rim += strike * 3.0F;
                           shake += strike;
                           break;
                        case 4:
                           float beat = (float)Math.pow(Math.max((double)0.0F, Math.sin((double)t * Math.PI * (double)3.0F)), (double)8.0F) * k;
                           rim += beat * 5.0F;
                           width *= 1.0F + beat * 0.15F;
                           break;
                        case 5:
                           charge = Math.max(charge, (float)Math.sin((double)t * Math.PI) * k);
                           spin += ((float)Math.PI * 2F) * t * t * (3.0F - 2.0F * t);
                           break;
                        case 6:
                           shake += (float)Math.sin((double)t * Math.PI) * 2.5F * k;
                           rim *= 1.0F + (float)Math.abs(Math.sin((double)(now * 1.7F))) * (float)Math.sin((double)t * Math.PI) * k;
                           break;
                        case 7:
                           flash = Math.max(flash, bump(t, 0.0F, 1.0F) * 0.9F * k);
                           rim += bump(t, 0.0F, 1.0F) * 4.0F * k;
                           break;
                        case 8:
                           float shut = Math.min(1.0F, t / 0.55F);
                           width *= 1.0F - 0.7F * (float)Math.sin((double)shut * Math.PI / (double)2.0F) * k;
                           charge = Math.max(charge, shut * k);
                           rim += shut * 2.0F * k;
                           if (t > 0.55F) {
                              float out = (t - 0.55F) / 0.45F;
                              width *= 1.0F + bump(out, 0.0F, 1.0F) * 1.4F * k;
                              flash = Math.max(flash, bump(out, 0.0F, 0.4F) * 1.2F * k);
                              shock = Math.max(shock, Math.min(0.999F, out));
                              shards = Math.max(shards, Math.min(0.999F, out * 0.9F));
                              shake += (1.0F - out) * 3.0F * k;
                           }
                           break;
                        case 9:
                           float build = Math.min(1.0F, t / 0.7F);
                           charge = Math.max(charge, build * build * k);
                           rim += build * build * 7.0F * k;
                           shake += build * build * 2.0F * k;
                           width *= 1.0F - 0.25F * build * k;
                           if (t > 0.7F) {
                              float blow = (t - 0.7F) / 0.3F;
                              flash = Math.max(flash, (1.0F - blow) * 1.5F * k);
                              shock = Math.max(shock, Math.min(0.999F, blow));
                              shards = Math.max(shards, Math.min(0.999F, blow));
                              bolts = Math.max(bolts, Math.min(0.999F, blow * 1.4F));
                              width *= 1.0F + (1.0F - blow) * 0.8F * k;
                           }
                           break;
                        case 10:
                           float lub = (float)Math.pow(Math.max((double)0.0F, Math.sin((double)t * Math.PI * (double)4.0F)), (double)12.0F);
                           float dub = (float)Math.pow(Math.max((double)0.0F, Math.sin((double)(t - 0.07F) * Math.PI * (double)4.0F)), (double)16.0F);
                           float beat10 = (lub + dub * 0.6F) * k;
                           rim += beat10 * 6.0F;
                           width *= 1.0F + beat10 * 0.22F;
                           shake += beat10 * 0.5F;
                           break;
                        case 11:
                           float wave = (float)Math.sin((double)t * Math.PI * (double)6.0F);
                           width *= 1.0F + wave * 0.2F * k;
                           spin += (float)Math.toRadians((double)30.0F) * wave * k;
                           rim += (1.0F + wave) * 1.5F * k;
                           shake += Math.abs(wave) * 0.4F * k;
                           break;
                        case 12:
                           boolean on = (int)(t * 16.0F) % 2 == 0;
                           rim *= on ? 1.0F + 5.0F * k : Math.max(0.0F, 1.0F - 0.8F * k);
                           flash = Math.max(flash, on ? 0.7F * k : 0.0F);
                           width *= on ? 1.0F + 0.08F * k : 1.0F;
                           break;
                        case 13:
                           float pull = (float)Math.sin((double)Math.min(1.0F, t / 0.8F) * Math.PI / (double)2.0F);
                           charge = Math.max(charge, pull * k);
                           bolts = Math.max(bolts, Math.min(0.999F, pull));
                           width *= 1.0F - 0.45F * pull * k;
                           rim += pull * 3.5F * k;
                           if (t > 0.8F) {
                              float go = (t - 0.8F) / 0.2F;
                              flash = Math.max(flash, (1.0F - go) * 1.2F * k);
                              shock = Math.max(shock, Math.min(0.999F, go));
                              width *= 1.0F + go * 0.5F * k;
                           }
                           break;
                        case 14:
                           float stage = t * 3.0F;
                           float f = stage - (float)Math.floor((double)stage);
                           branch *= 1.0F + bump(f, 0.0F, 0.5F) * 1.5F * k;
                           shards = Math.max(shards, Math.min(0.999F, f));
                           shake += bump(f, 0.0F, 0.3F) * 1.8F * k;
                           rim += bump(f, 0.0F, 0.25F) * 4.5F * k;
                           break;
                        case 15:
                           float ease = t * t * (3.0F - 2.0F * t);
                           spin += 12.566371F * ease;
                           float bend = (float)Math.sin((double)t * Math.PI);
                           width *= 1.0F + bend * 0.55F * k;
                           charge = Math.max(charge, bend * 0.8F * k);
                           rim += bend * 2.5F * k;
                           break;
                        case 16:
                           float dark = bump(Math.min(1.0F, t / 0.6F), 0.0F, 1.0F);
                           rim *= Math.max(0.0F, 1.0F - 0.85F * dark * k);
                           width *= Math.max(0.1F, 1.0F - 0.35F * dark * k);
                           if (t > 0.6F) {
                              float back = (t - 0.6F) / 0.4F;
                              rim += bump(back, 0.0F, 1.0F) * 9.0F * k;
                              flash = Math.max(flash, bump(back, 0.0F, 0.5F) * 1.3F * k);
                              width *= 1.0F + bump(back, 0.0F, 1.0F) * 0.3F * k;
                           }
                           break;
                        case 17:
                           float ring = t * 4.0F;
                           shock = Math.max(shock, Math.min(0.999F, ring - (float)Math.floor((double)ring)));
                           rim += (float)Math.pow(Math.max((double)0.0F, Math.sin((double)t * Math.PI * (double)4.0F)), (double)6.0F) * 3.5F * k;
                           shake += (float)Math.pow(Math.max((double)0.0F, Math.sin((double)t * Math.PI * (double)4.0F)), (double)10.0F) * 0.8F * k;
                     }

                     rim = rimBefore + (rim - rimBefore) * o.glow();
                     shake = shakeBefore + (shake - shakeBefore) * o.shake();
                     flash = flashBefore + (flash - flashBefore) * o.flash();
                  }
               }
            }

            return new RiftAnim.Sample(base.opened(), base.from(), width, rim, shake, base.alpha(), branch, shock, spin, flash, shards, bolts, charge);
         }
      }
   }

   private static float bump(float t, float start, float end) {
      return !(t <= start) && !(t >= end) ? (float)Math.sin((double)((t - start) / (end - start)) * Math.PI) : 0.0F;
   }

   private static float now() {
      return SkyClock.smoothTicks();
   }

   @Environment(EnvType.CLIENT)
   public static enum Effect {
      EXPLOSION("explosion"),
      SHATTER("shatter"),
      SHOCKWAVE("shockwave"),
      LIGHTNING("lightning"),
      PULSE("pulse"),
      VORTEX("vortex"),
      TREMOR("tremor"),
      FLARE("flare"),
      COLLAPSE("collapse"),
      SUPERNOVA("supernova"),
      HEARTBEAT("heartbeat"),
      RIPTIDE("riptide"),
      STROBE("strobe"),
      SIPHON("siphon"),
      RUPTURE("rupture"),
      WARP("warp"),
      ECLIPSE("eclipse"),
      CASCADE("cascade");

      private final String id;

      private Effect(String id) {
         this.id = id;
      }

      public String id() {
         return this.id;
      }

      public static Optional<Effect> byId(String id) {
         return Arrays.stream(values()).filter((e) -> e.id.equalsIgnoreCase(id)).findFirst();
      }

      public static List<String> names() {
         List<String> out = new ArrayList();

         for(Effect e : values()) {
            out.add(e.id);
         }

         return out;
      }

      private static Effect[] $values() {
         return new Effect[]{EXPLOSION, SHATTER, SHOCKWAVE, LIGHTNING, PULSE, VORTEX, TREMOR, FLARE, COLLAPSE, SUPERNOVA, HEARTBEAT, RIPTIDE, STROBE, SIPHON, RUPTURE, WARP, ECLIPSE, CASCADE};
      }
   }

   @Environment(EnvType.CLIENT)
   public static record Options(int color, float size, int delay, float spin, float shake, float glow, float flash) {
      public static final Options NONE = new Options(-1, 1.0F, 0, 0.0F, 1.0F, 1.0F, 1.0F);
   }

   @Environment(EnvType.CLIENT)
   private static record Running(Effect effect, float start, int ticks, float strength, boolean loop, int layer, Options options) {
   }
}
