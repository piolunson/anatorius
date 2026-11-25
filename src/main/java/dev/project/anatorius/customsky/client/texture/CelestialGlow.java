package dev.project.anatorius.customsky.client.texture;

import java.io.InputStream;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import dev.project.anatorius.customsky.Customskyboxes;

@Environment(EnvType.CLIENT)
public final class CelestialGlow {
   private static final Map<Identifier, Identifier> KEYED = new ConcurrentHashMap();
   private static final String[] MOON_PHASES = new String[]{"full_moon", "waning_gibbous", "third_quarter", "waning_crescent", "new_moon", "waxing_crescent", "first_quarter", "waxing_gibbous"};
   private static final Identifier SUN = Identifier.ofVanilla("textures/environment/celestial/sun.png");
   private static final Identifier MOON_SHEET = Customskyboxes.id("dynamic/glow/vanilla_moon_phases");

   private CelestialGlow() {
   }

   public static void apply(NativeImage image) {
      for(int y = 0; y < image.getHeight(); ++y) {
         for(int x = 0; x < image.getWidth(); ++x) {
            int argb = image.getColorArgb(x, y);
            int a = argb >>> 24 & 255;
            int r = argb >>> 16 & 255;
            int g = argb >>> 8 & 255;
            int b = argb & 255;
            int bright = Math.max(r, Math.max(g, b));
            if (bright != 0 && a != 0) {
               a = a * bright / 255;
               r = Math.min(255, r * 255 / bright);
               g = Math.min(255, g * 255 / bright);
               b = Math.min(255, b * 255 / bright);
               image.setColorArgb(x, y, a << 24 | r << 16 | g << 8 | b);
            } else {
               image.setColorArgb(x, y, 0);
            }
         }
      }
   }

   public static Identifier keyed(Identifier source) {
      Identifier done = (Identifier)KEYED.get(source);
      if (done != null) {
         return done;
      } else {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc == null) {
            return source;
         } else {
            String var10000 = source.getNamespace();
            Identifier target = Customskyboxes.id("dynamic/glow/" + var10000 + "/" + source.getPath());

            try {
               InputStream in = mc.getResourceManager().getResourceOrThrow(source).getInputStream();

               try {
                  NativeImage image = NativeImage.read(in);
                  apply(image);
                  TextureManager var10 = mc.getTextureManager();
                  Objects.requireNonNull(target);
                  var10.registerTexture(target, new NativeImageBackedTexture(target::toString, image));
               } catch (Throwable var8) {
                  if (in != null) {
                     try {
                        in.close();
                     } catch (Throwable var7) {
                        var8.addSuppressed(var7);
                     }
                  }

                  throw var8;
               }

               if (in != null) {
                  in.close();
               }
            } catch (Exception unreadable) {
               Customskyboxes.LOGGER.warn("Could not prepare {} for the sky: {}", source, unreadable.getMessage());
               KEYED.put(source, source);
               return source;
            }

            KEYED.put(source, target);
            return target;
         }
      }
   }

   public static Identifier vanillaSun() {
      return keyed(SUN);
   }

   public static Identifier vanillaMoon() {
      Identifier done = (Identifier)KEYED.get(MOON_SHEET);
      if (done != null) {
         return done;
      } else {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc == null) {
            return MOON_SHEET;
         } else {
            NativeImage sheet = null;

            try {
               for(int i = 0; i < MOON_PHASES.length; ++i) {
                  String var10000 = MOON_PHASES[i];
                  Identifier file = Identifier.ofVanilla("textures/environment/celestial/moon/" + var10000 + ".png");
                  InputStream in = mc.getResourceManager().getResourceOrThrow(file).getInputStream();

                  try {
                     NativeImage phase = NativeImage.read(in);

                     try {
                        int w = phase.getWidth();
                        int h = phase.getHeight();
                        if (sheet == null) {
                           sheet = new NativeImage(w * 4, h * 2, true);
                        }

                        int ox = i % 4 * w;
                        int oy = i / 4 * h;

                        for(int y = 0; y < h; ++y) {
                           for(int x = 0; x < w; ++x) {
                              sheet.setColorArgb(ox + x, oy + y, phase.getColorArgb(x, y));
                           }
                        }
                     } catch (Throwable var15) {
                        if (phase != null) {
                           try {
                              phase.close();
                           } catch (Throwable var14) {
                              var15.addSuppressed(var14);
                           }
                        }

                        throw var15;
                     }

                     if (phase != null) {
                        phase.close();
                     }
                  } catch (Throwable var16) {
                     if (in != null) {
                        try {
                           in.close();
                        } catch (Throwable var13) {
                           var16.addSuppressed(var13);
                        }
                     }

                     throw var16;
                  }

                  if (in != null) {
                     in.close();
                  }
               }

               apply(sheet);
               TextureManager var18 = mc.getTextureManager();
               Identifier var10001 = MOON_SHEET;
               Identifier var10004 = MOON_SHEET;
               Objects.requireNonNull(var10004);
               var18.registerTexture(var10001, new NativeImageBackedTexture(var10004::toString, sheet));
            } catch (Exception unreadable) {
               if (sheet != null) {
                  sheet.close();
               }

               Customskyboxes.LOGGER.warn("Could not put the vanilla moon together: {}", unreadable.getMessage());
            }

            KEYED.put(MOON_SHEET, MOON_SHEET);
            return MOON_SHEET;
         }
      }
   }

   public static void reset() {
      MinecraftClient mc = MinecraftClient.getInstance();

      for(Map.Entry<Identifier, Identifier> e : KEYED.entrySet()) {
         boolean built = !((Identifier)e.getValue()).equals(e.getKey()) || ((Identifier)e.getKey()).equals(MOON_SHEET);
         if (mc != null && built) {
            mc.getTextureManager().destroyTexture((Identifier)e.getValue());
         }
      }

      KEYED.clear();
   }
}
