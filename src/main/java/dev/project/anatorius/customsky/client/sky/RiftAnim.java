package dev.project.anatorius.customsky.client.sky;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public enum RiftAnim {
   NONE("none", true, true),
   RIFT_OPEN("rift_open", true, false),
   RIFT_UNZIP("rift_unzip", true, false),
   RIFT_BURST("rift_burst", true, false),
   RIFT_SLASH("rift_slash", true, false),
   RIFT_FLICKER("rift_flicker", true, false),
   RIFT_SHATTER("rift_shatter", true, false),
   RIFT_VORTEX("rift_vortex", true, false),
   RIFT_LIGHTNING("rift_lightning", true, false),
   RIFT_AWAKEN("rift_awaken", true, false),
   RIFT_BLOOM("rift_bloom", true, false),
   RIFT_SUPERNOVA("rift_supernova", true, false),
   RIFT_GLITCH("rift_glitch", true, false),
   RIFT_IGNITE("rift_ignite", true, false),
   RIFT_PORTAL("rift_portal", true, false),
   RIFT_CLOSE("rift_close", false, false),
   RIFT_ZIP("rift_zip", false, false),
   RIFT_COLLAPSE("rift_collapse", false, false),
   RIFT_SEAL("rift_seal", false, false),
   RIFT_SNUFF("rift_snuff", false, false),
   RIFT_UNSHATTER("rift_unshatter", false, false),
   RIFT_IMPLODE("rift_implode", false, false),
   RIFT_STRIKE("rift_strike", false, false),
   RIFT_SLUMBER("rift_slumber", false, false),
   RIFT_WITHER("rift_wither", false, false),
   RIFT_BLACKHOLE("rift_blackhole", false, false),
   RIFT_GLITCH_OUT("rift_glitch_out", false, false),
   RIFT_BURNOUT("rift_burnout", false, false),
   RIFT_PORTAL_CLOSE("rift_portal_close", false, false);

   private final String id;
   private final boolean entrance;
   private final boolean none;

   private RiftAnim(String id, boolean entrance, boolean none) {
      this.id = id;
      this.entrance = entrance;
      this.none = none;
   }

   public String id() {
      return this.id;
   }

   public boolean isNone() {
      return this.none;
   }

   public boolean isEntrance() {
      return this.entrance;
   }

   public static Optional<RiftAnim> byId(String id) {
      return id == null ? Optional.empty() : Arrays.stream(values()).filter((a) -> a.id.equalsIgnoreCase(id)).findFirst();
   }

   public static List<String> names(boolean entrance) {
      List<String> out = new ArrayList();

      for(RiftAnim a : values()) {
         if (a.none || a.entrance == entrance) {
            out.add(a.id);
         }
      }

      return out;
   }

   public Sample sample(float progress) {
      if (this.none) {
         return idle();
      } else {
         float t = Math.max(0.0F, Math.min(1.0F, progress));
         float p = this.entrance ? t : 1.0F - t;
         Sample var10000;
         switch (this.ordinal()) {
            case 0:
               var10000 = idle();
               break;
            case 1:
            case 15:
               var10000 = beats(p, 0.5F, 0.22F, 0.1F, 0.48F, 0.4F, 0.7F, 1.0F);
               break;
            case 2:
            case 16:
               var10000 = beats(p, 0.0F, 0.28F, 0.14F, 0.55F, 0.46F, 0.76F, 1.0F);
               break;
            case 3:
            case 17:
               var10000 = beats(p, 0.5F, 0.12F, 0.06F, 0.26F, 0.2F, 0.52F, 2.2F);
               break;
            case 4:
            case 18:
               var10000 = slash(p);
               break;
            case 5:
            case 19:
               Sample base = beats(p, 0.5F, 0.24F, 0.12F, 0.5F, 0.45F, 0.75F, 1.0F);
               float stutter = p < 0.5F && (int)(p * 13.0F) % 2 == 0 ? 0.2F : 1.0F;
               var10000 = new Sample(base.opened(), base.from(), base.width(), base.rim() * stutter, base.shake() + (1.0F - stutter) * 0.5F, base.alpha() * stutter, base.branch(), base.shock());
               break;
            case 6:
            case 20:
               var10000 = shatter(p);
               break;
            case 7:
            case 21:
               var10000 = vortex(p);
               break;
            case 8:
            case 22:
               var10000 = lightning(p);
               break;
            case 9:
            case 23:
               var10000 = awaken(p);
               break;
            case 10:
            case 24:
               var10000 = bloom(p);
               break;
            case 11:
            case 25:
               var10000 = supernova(p);
               break;
            case 12:
            case 26:
               var10000 = glitch(p);
               break;
            case 13:
            case 27:
               var10000 = ignite(p);
               break;
            case 14:
            case 28:
               var10000 = portal(p);
               break;
            default:
               throw new MatchException((String)null, (Throwable)null);
         }

         return var10000;
      }
   }

   private static Sample beats(float p, float from, float lineUntil, float branchFrom, float branchUntil, float tearFrom, float tearUntil, float power) {
      float line = easeOut(ramp(p, 0.0F, lineUntil));
      float branch = easeOut(ramp(p, branchFrom, branchUntil));
      float tear = ramp(p, tearFrom, tearUntil);
      float open = tear <= 0.0F ? 0.0F : overshoot(tear);
      float width = 0.012F + open * 0.988F;
      float drawing = p < lineUntil ? (float)Math.sin((double)(p / Math.max(0.001F, lineUntil)) * Math.PI) : 0.0F;
      float ripping = (float)Math.sin((double)Math.min(1.0F, tear) * Math.PI);
      float rim = 1.0F + drawing * 4.5F + ripping * 5.0F * power;
      float shake = (drawing * 0.4F + ripping * 1.1F) * power;
      float shock = ramp(p, tearFrom, Math.min(1.0F, tearFrom + (tearUntil - tearFrom) * 2.2F));
      return new Sample(line, from, width, rim, shake, Math.min(1.0F, p * 8.0F), branch, shock);
   }

   private static Sample shatter(float p) {
      Sample b = beats(p, 0.5F, 0.12F, 0.04F, 0.5F, 0.56F, 0.72F, 1.6F);
      float branch = b.branch() * 2.4F;
      float flash = bump(p, 0.54F, 0.62F, 0.8F);
      float shards = ramp(p, 0.55F, 1.0F);
      float shake = b.shake() + (p < 0.56F ? 0.25F * ramp(p, 0.1F, 0.55F) : 0.0F);
      return (new Sample(b.opened(), b.from(), b.width(), b.rim(), shake, b.alpha(), branch, b.shock())).with(0.0F, flash, shards, 0.0F, 0.0F);
   }

   private static Sample vortex(float p) {
      Sample b = beats(p, 0.5F, 0.42F, 0.3F, 0.6F, 0.5F, 0.8F, 1.2F);
      float charge = p < 0.55F ? ramp(p, 0.0F, 0.15F) : 1.0F - ramp(p, 0.55F, 0.65F);
      float spin = 12.566371F * (1.0F - easeOut(ramp(p, 0.0F, 0.85F)));
      float flash = bump(p, 0.48F, 0.56F, 0.75F);
      return (new Sample(b.opened(), b.from(), b.width(), b.rim(), b.shake(), b.alpha(), b.branch(), b.shock())).with(spin, flash, 0.0F, 0.0F, charge * (p < 0.55F ? 1.0F : 1.0F));
   }

   private static Sample lightning(float p) {
      Sample b = beats(p, 0.5F, 0.2F, 0.08F, 0.6F, 0.6F, 0.76F, 2.0F);
      float bolts = ramp(p, 0.0F, 0.64F);
      float strikes = (float)Math.pow(Math.max((double)0.0F, Math.sin((double)bolts * Math.PI * (double)5.0F)), (double)10.0F) * 0.5F;
      float flash = Math.max(p < 0.6F ? strikes : 0.0F, bump(p, 0.6F, 0.68F, 1.0F));
      float rim = b.rim() * (1.0F + strikes * 3.0F);
      return (new Sample(b.opened(), b.from(), b.width(), rim, b.shake() + strikes, b.alpha(), b.branch(), b.shock())).with(0.0F, flash, 0.0F, bolts, 0.0F);
   }

   private static Sample awaken(float p) {
      float line = easeOut(ramp(p, 0.0F, 0.3F));
      float beat = (float)Math.pow(Math.max((double)0.0F, Math.sin((double)ramp(p, 0.3F, 0.66F) * Math.PI * (double)4.0F)), (double)8.0F);
      float open = smooth(ramp(p, 0.62F, 0.98F));
      float width = 0.012F + open * 0.988F;
      float rim = 1.0F + beat * 6.0F + (float)Math.sin((double)open * Math.PI) * 2.0F;
      float flash = beat * 0.35F + bump(p, 0.62F, 0.75F, 0.35F);
      return (new Sample(line, 0.5F, width, rim, 0.0F, Math.min(1.0F, p * 5.0F), ramp(p, 0.2F, 0.7F), 0.0F)).with(0.0F, flash, 0.0F, 0.0F, beat * 0.6F);
   }

   private static Sample slash(float p) {
      float line = easeOut(ramp(p, 0.0F, 0.08F));
      float tear = ramp(p, 0.8F, 0.88F);
      float open = tear <= 0.0F ? 0.0F : overshoot(tear);
      float width = 0.012F + open * 0.988F;
      float glint = bump(p, 0.0F, 0.12F, 1.0F);
      float simmer = p > 0.1F && p < 0.8F ? 0.6F + 0.4F * (float)Math.sin((double)(p * 60.0F)) : 0.0F;
      float rip = (float)Math.sin((double)Math.min(1.0F, tear) * Math.PI);
      float rim = 1.0F + glint * 7.0F + simmer * 1.5F + rip * 7.0F;
      float shake = rip * 3.0F;
      float shock = ramp(p, 0.8F, 0.95F);
      float flash = Math.max(glint * 0.45F, bump(p, 0.8F, 0.9F, 0.9F));
      return (new Sample(line, 0.0F, width, rim, shake, Math.min(1.0F, p * 12.0F), ramp(p, 0.78F, 0.9F), shock)).with(0.0F, flash, 0.0F, 0.0F, 0.0F);
   }

   private static Sample bloom(float p) {
      float grow = ramp(p, 0.0F, 0.85F);
      float open = overshoot(grow);
      float width = 0.012F + open * 0.988F;
      float spin = ((float)Math.PI * 1.5F) * (1.0F - easeOut(grow));
      float flash = bump(p, 0.05F, 0.4F, 0.4F);
      return (new Sample(1.0F, 0.5F, width, 1.0F + (float)Math.sin((double)grow * Math.PI) * 3.0F, 0.0F, Math.min(1.0F, p * 6.0F), ramp(p, 0.4F, 1.0F), 0.0F)).with(spin, flash, 0.0F, 0.0F, 0.0F);
   }

   private static Sample supernova(float p) {
      float flash = p < 0.12F ? ramp(p, 0.0F, 0.06F) : 1.0F - ramp(p, 0.12F, 0.55F);
      float settle = easeOut(ramp(p, 0.08F, 0.9F));
      float width = 2.6F - 1.6F * settle;
      float shock = ramp(p, 0.06F, 0.5F);
      float rim = 1.0F + (1.0F - settle) * 6.0F;
      float shards = ramp(p, 0.08F, 0.7F);
      return (new Sample(1.0F, 0.5F, width, rim, (1.0F - settle) * 1.5F, Math.min(1.0F, p * 20.0F), 1.0F, shock)).with(0.0F, flash, shards, 0.0F, (1.0F - settle) * 0.5F);
   }

   private static Sample glitch(float p) {
      Sample b = beats(p, 0.5F, 0.3F, 0.1F, 0.5F, 0.55F, 0.85F, 1.0F);
      long frame = (long)Math.floor((double)(p * 40.0F));
      float r1 = hash(frame, 3);
      float r2 = hash(frame, 7);
      float r3 = hash(frame, 11);
      boolean settled = p > 0.85F;
      float on = !settled && !(r1 > 0.35F) ? 0.1F : 1.0F;
      float width = settled ? b.width() : b.width() * (0.4F + r2 * 1.4F);
      float opened = settled ? b.opened() : Math.min(1.0F, b.opened() * (0.5F + r3));
      float spin = settled ? 0.0F : (r2 - 0.5F) * 0.6F;
      float shake = settled ? 0.0F : 1.5F + r3 * 2.0F;
      return (new Sample(opened, b.from(), width, b.rim() * (0.6F + r1 * 2.0F), shake, b.alpha() * on, b.branch(), b.shock())).with(spin, settled ? 0.0F : r1 * 0.3F, 0.0F, 0.0F, 0.0F);
   }

   private static Sample ignite(float p) {
      float line = ramp(p, 0.0F, 0.55F);
      float burning = p < 0.6F ? 1.0F : 0.0F;
      float tear = ramp(p, 0.55F, 0.85F);
      float open = tear <= 0.0F ? 0.0F : overshoot(tear);
      float width = 0.012F + open * 0.988F + burning * 0.03F;
      float rim = 1.0F + burning * 5.0F + (float)Math.sin((double)Math.min(1.0F, tear) * Math.PI) * 6.0F;
      float flash = bump(p, 0.55F, 0.7F, 0.6F);
      return (new Sample(line, 0.0F, width, rim, burning * 0.3F, Math.min(1.0F, p * 10.0F), ramp(p, 0.3F, 0.8F), 0.0F)).with(0.0F, flash, 0.0F, 0.0F, 0.0F);
   }

   private static Sample portal(float p) {
      float charge = 1.0F - ramp(p, 0.5F, 0.75F);
      float pulse = (float)Math.pow(Math.max((double)0.0F, Math.sin((double)p * Math.PI * (double)6.0F)), (double)6.0F) * (p < 0.5F ? 1.0F : 0.0F);
      float open = smooth(ramp(p, 0.45F, 0.9F));
      float width = 0.012F + open * 0.988F;
      float flash = bump(p, 0.45F, 0.6F, 0.55F);
      return (new Sample(easeOut(ramp(p, 0.0F, 0.3F)), 0.5F, width, 1.0F + pulse * 5.0F + open * 1.5F, 0.0F, Math.min(1.0F, p * 6.0F), ramp(p, 0.5F, 1.0F), ramp(p, 0.47F, 0.8F))).with(0.0F, flash, 0.0F, 0.0F, charge);
   }

   private static float hash(long frame, int salt) {
      long h = frame * 6364136223846793005L + (long)salt * 1442695040888963407L;
      h ^= h >>> 31;
      h *= -4658895280553007687L;
      h ^= h >>> 29;
      return (float)(h >>> 11 & 65535L) / 65535.0F;
   }

   private static float bump(float p, float start, float end, float peak) {
      return !(p <= start) && !(p >= end) ? (float)Math.sin((double)((p - start) / (end - start)) * Math.PI) * peak : 0.0F;
   }

   private static float smooth(float t) {
      return t * t * (3.0F - 2.0F * t);
   }

   public static Sample idle() {
      float t = SkyClock.smoothTicks() * 0.05F;
      float breath = 1.0F + (float)Math.sin((double)(t * 0.7F)) * 0.06F + (float)Math.sin((double)(t * 1.9F)) * 0.025F;
      float swell = 1.0F + (float)Math.pow(Math.max((double)0.0F, Math.sin((double)(t * 0.45F))), (double)6.0F) * 1.8F;
      float flare = (float)Math.pow(Math.max((double)0.0F, Math.sin((double)(t * 0.17F + 1.3F))), (double)24.0F) * 3.5F;
      float stutter = (int)(t * 3.0F) % 41 == 0 ? 2.2F : 1.0F;
      float jitter = 0.05F + (float)Math.pow(Math.max((double)0.0F, Math.sin((double)(t * 0.31F))), (double)8.0F) * 0.5F;
      return new Sample(1.0F, 0.5F, breath, (swell + flare) * stutter, jitter, 1.0F, 1.0F, 0.0F);
   }

   private static float ramp(float p, float start, float end) {
      if (end <= start) {
         return p >= end ? 1.0F : 0.0F;
      } else {
         return Math.max(0.0F, Math.min(1.0F, (p - start) / (end - start)));
      }
   }

   private static float overshoot(float t) {
      float e = easeOut(t);
      float wobble = (float)Math.exp((double)(-t * 6.0F)) * (float)Math.sin((double)(t * 16.0F)) * 0.18F;
      return Math.max(0.0F, e + wobble);
   }

   private static float easeOut(float t) {
      float inv = 1.0F - Math.max(0.0F, Math.min(1.0F, t));
      return 1.0F - inv * inv * inv;
   }

   private static RiftAnim[] $values() {
      return new RiftAnim[]{NONE, RIFT_OPEN, RIFT_UNZIP, RIFT_BURST, RIFT_SLASH, RIFT_FLICKER, RIFT_SHATTER, RIFT_VORTEX, RIFT_LIGHTNING, RIFT_AWAKEN, RIFT_BLOOM, RIFT_SUPERNOVA, RIFT_GLITCH, RIFT_IGNITE, RIFT_PORTAL, RIFT_CLOSE, RIFT_ZIP, RIFT_COLLAPSE, RIFT_SEAL, RIFT_SNUFF, RIFT_UNSHATTER, RIFT_IMPLODE, RIFT_STRIKE, RIFT_SLUMBER, RIFT_WITHER, RIFT_BLACKHOLE, RIFT_GLITCH_OUT, RIFT_BURNOUT, RIFT_PORTAL_CLOSE};
   }

   @Environment(EnvType.CLIENT)
   public static record Sample(float opened, float from, float width, float rim, float shake, float alpha, float branch, float shock, float spin, float flash, float shards, float bolts, float charge) {
      public static final Sample IDLE = new Sample(1.0F, 0.5F, 1.0F, 1.0F, 0.0F, 1.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);

      Sample(float opened, float from, float width, float rim, float shake, float alpha, float branch, float shock) {
         this(opened, from, width, rim, shake, alpha, branch, shock, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
      }

      Sample with(float spin, float flash, float shards, float bolts, float charge) {
         return new Sample(this.opened, this.from, this.width, this.rim, this.shake, this.alpha, this.branch, this.shock, spin, flash, shards, bolts, charge);
      }
   }
}
