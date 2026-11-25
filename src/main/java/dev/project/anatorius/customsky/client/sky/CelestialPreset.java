package dev.project.anatorius.customsky.client.sky;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public enum CelestialPreset {
   NONE("none"),
   CIRCLE("circle"),
   SHAKE("shake"),
   GLITCH("glitch"),
   SCALE("scale"),
   FLOAT("float"),
   SPIN("spin"),
   PULSE("pulse"),
   SWING("swing"),
   ORBIT("orbit"),
   BOUNCE("bounce"),
   WOBBLE("wobble"),
   FLICKER("flicker");

   private final String id;

   private CelestialPreset(String id) {
      this.id = id;
   }

   public String id() {
      return this.id;
   }

   public static Optional<CelestialPreset> byId(String id) {
      return id == null ? Optional.empty() : Arrays.stream(values()).filter((p) -> p.id.equalsIgnoreCase(id)).findFirst();
   }

   public static List<String> names() {
      List<String> out = new ArrayList();

      for(CelestialPreset p : values()) {
         out.add(p.id);
      }

      return out;
   }

   public Motion motion(float loop) {
      float t = loop - (float)Math.floor((double)loop);
      double angle = (double)t * Math.PI * (double)2.0F;
      Motion var10000;
      switch (this.ordinal()) {
         case 0 -> var10000 = CelestialPreset.Motion.IDLE;
         case 1 -> var10000 = new Motion((float)Math.cos(angle) * 6.0F, (float)Math.sin(angle) * 6.0F, 1.0F, 0.0F, 1.0F);
         case 2 -> var10000 = new Motion(jitter(t, 11) * 1.5F, jitter(t, 29) * 1.5F, 1.0F, 0.0F, 1.0F);
         case 3 -> var10000 = glitch(t);
         case 4 -> var10000 = new Motion(0.0F, 0.0F, 1.0F + (float)Math.sin(angle) * 0.25F, 0.0F, 1.0F);
         case 5 -> var10000 = new Motion(0.0F, (float)Math.sin(angle) * 4.0F, 1.0F, 0.0F, 1.0F);
         case 6 -> var10000 = new Motion(0.0F, 0.0F, 1.0F, t * 360.0F, 1.0F);
         case 7 -> var10000 = new Motion(0.0F, 0.0F, 1.0F + pulse(t) * 0.35F, 0.0F, 0.75F + pulse(t) * 0.25F);
         case 8 -> var10000 = new Motion((float)Math.sin(angle) * 10.0F, (float)Math.abs(Math.sin(angle)) * 2.0F, 1.0F, (float)Math.sin(angle) * 12.0F, 1.0F);
         case 9 -> var10000 = new Motion((float)Math.sin(angle) * 8.0F, (float)Math.sin(angle * (double)2.0F) * 4.0F, 1.0F, 0.0F, 1.0F);
         case 10 -> var10000 = new Motion(0.0F, (float)Math.abs(Math.sin(angle)) * 7.0F, 1.0F, 0.0F, 1.0F);
         case 11 -> var10000 = new Motion(0.0F, 0.0F, 1.0F, (float)Math.sin(angle) * 18.0F, 1.0F);
         case 12 -> var10000 = new Motion(0.0F, 0.0F, 1.0F, 0.0F, (int)(t * 14.0F) % 3 == 0 ? 0.25F : 1.0F);
         default -> throw new MatchException((String)null, (Throwable)null);
      }

      return var10000;
   }

   private static float pulse(float t) {
      float beat = t < 0.5F ? t * 2.0F : (t - 0.5F) * 2.0F;
      float strength = t < 0.5F ? 1.0F : 0.6F;
      return (float)Math.max((double)0.0F, Math.sin((double)beat * Math.PI)) * strength;
   }

   private static float jitter(float t, int salt) {
      int step = (int)(t * 20.0F) * salt;
      return (float)(step * 1103515245 + 12345 & 255) / 127.5F - 1.0F;
   }

   private static Motion glitch(float t) {
      int step = (int)(t * 16.0F);
      boolean hidden = step % 7 == 3;
      float x = jitter(t, 17) * 4.0F;
      float y = jitter(t, 23) * 2.0F;
      return new Motion(x, y, hidden ? 1.1F : 1.0F, 0.0F, hidden ? 0.15F : 1.0F);
   }

   private static CelestialPreset[] $values() {
      return new CelestialPreset[]{NONE, CIRCLE, SHAKE, GLITCH, SCALE, FLOAT, SPIN, PULSE, SWING, ORBIT, BOUNCE, WOBBLE, FLICKER};
   }

   @Environment(EnvType.CLIENT)
   public static record Motion(float offsetX, float offsetY, float scale, float roll, float alpha) {
      public static final Motion IDLE = new Motion(0.0F, 0.0F, 1.0F, 0.0F, 1.0F);
   }
}
