package dev.project.anatorius.customsky.client.model;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import dev.project.anatorius.customsky.Customskyboxes;
import dev.project.anatorius.customsky.client.sky.CelestialMesh;
import dev.project.anatorius.customsky.client.sky.SkyboxEntry;
import dev.project.anatorius.customsky.client.sky.SkyboxType;

@Environment(EnvType.CLIENT)
public final class SkyModels {
   private static final Map<String, Optional<Loaded>> CACHE = new ConcurrentHashMap();
   private static final Map<String, Playing> PLAYING = new ConcurrentHashMap();
   private static final Map<String, Optional<Identifier>> CHOSEN = new ConcurrentHashMap();
   private static final Identifier BLANK = Customskyboxes.id("textures/rift_rim.png");
   private static volatile List<String> names;
   private static final String[] TEXTURE_FOLDERS = new String[]{"textures/geo", "geo", "textures/entity", "textures/models"};
   private static final long LOADED_AT = System.nanoTime();

   private SkyModels() {
   }

   public static void reset() {
      CACHE.clear();
      CHOSEN.clear();
      names = null;
   }

   public static List<String> names() {
      List<String> found = names;
      if (found != null) {
         return found;
      } else {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc == null) {
            return List.of();
         } else {
            List<String> out = new ArrayList();

            try {
               for(Identifier id : mc.getResourceManager().findResources("geo", (idx) -> idx.getPath().endsWith(".geo.json")).keySet()) {
                  String file = id.getPath().substring(id.getPath().lastIndexOf(47) + 1);
                  String name = file.substring(0, file.length() - ".geo.json".length());
                  out.add("customskyboxes".equals(id.getNamespace()) ? name : id.getNamespace() + ":" + name);
               }
            } catch (RuntimeException failed) {
               Customskyboxes.LOGGER.warn("Could not list sky models: {}", failed.toString());
            }

            Collections.sort(out);
            names = out;
            return out;
         }
      }
   }

   public static Loaded forEntry(SkyboxEntry entry) {
      String name = entry == null ? null : entry.model();
      return name == null ? null : get(name);
   }

   public static Identifier textureFor(SkyboxEntry entry) {
      Loaded loaded = forEntry(entry);
      if (loaded == null) {
         return null;
      } else {
         String chosen = entry.modelTexture();
         if (chosen != null) {
            Identifier id = (Identifier)((Optional)CHOSEN.computeIfAbsent(chosen, (v) -> Optional.ofNullable(chosenTexture(v)))).orElse(null);
            if (id != null) {
               return id;
            }
         }

         return loaded.texture();
      }
   }

   public static Loaded get(String name) {
      return (Loaded)((Optional)CACHE.computeIfAbsent(name.toLowerCase(), SkyModels::load)).orElse(null);
   }

   private static Optional<Loaded> load(String name) {
      String ns = "customskyboxes";
      String path = name;
      int colon = name.indexOf(58);
      if (colon > 0) {
         ns = name.substring(0, colon);
         path = name.substring(colon + 1);
      }

      try {
         JsonObject geo = readJson(id(ns, "geo/" + path + ".geo.json"));
         if (geo == null) {
            Customskyboxes.LOGGER.warn("No model {} (looked for {}:geo/{}.geo.json)", new Object[]{name, ns, path});
            return Optional.empty();
         } else {
            GeoModel model = GeoModel.parse(geo);
            Map<String, GeoAnimation> animations = new LinkedHashMap();
            JsonObject anims = readJson(id(ns, "animations/" + path + ".animation.json"));
            if (anims != null) {
               animations.putAll(GeoAnimation.parseAll(anims));
            }

            Identifier texture = findTexture(ns, path);
            if (texture == null) {
               Customskyboxes.LOGGER.warn("No texture for model {}: put it in {}:textures/geo/{}.png, or pick one with model_texture=", new Object[]{name, ns, path});
               texture = BLANK;
            }

            Customskyboxes.LOGGER.info("Loaded sky model {}: {} bones, {} animations", new Object[]{name, model.bones.size(), animations.size()});
            return Optional.of(new Loaded(model, texture, animations));
         }
      } catch (RuntimeException broken) {
         Customskyboxes.LOGGER.warn("Could not read model {}: {}", name, broken.toString());
         return Optional.empty();
      }
   }

   private static Identifier findTexture(String ns, String path) {
      for(String folder : TEXTURE_FOLDERS) {
         Identifier id = id(ns, folder + "/" + path + ".png");
         if (id != null && exists(id)) {
            return id;
         }
      }

      String bare = path.substring(path.lastIndexOf(47) + 1).toLowerCase();

      for(String folder : TEXTURE_FOLDERS) {
         Identifier best = null;

         for(Identifier id : pngs(ns, folder)) {
            String file = fileName(id);
            if ((file.startsWith(bare) || bare.startsWith(file)) && (best == null || file.length() < fileName(best).length())) {
               best = id;
            }
         }

         if (best != null) {
            return best;
         }
      }

      return null;
   }

   private static String fileName(Identifier id) {
      String p = id.getPath();
      String file = p.substring(p.lastIndexOf(47) + 1).toLowerCase();
      return file.endsWith(".png") ? file.substring(0, file.length() - 4) : file;
   }

   private static List<Identifier> pngs(String ns, String folder) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc == null) {
         return List.of();
      } else {
         try {
            return new ArrayList(mc.getResourceManager().findResources(folder, (id) -> id.getNamespace().equals(ns) && id.getPath().endsWith(".png")).keySet());
         } catch (RuntimeException var4) {
            return List.of();
         }
      }
   }

   public static List<String> textureNames() {
      List<String> out = new ArrayList();

      for(Identifier id : pngs("customskyboxes", "textures/geo")) {
         out.add(fileName(id) + ".png");
      }

      Collections.sort(out);
      return out;
   }

   private static Identifier chosenTexture(String value) {
      String v = value.endsWith(".png") ? value : value + ".png";
      Identifier id = v.contains(":") ? Identifier.tryParse(v) : id("customskyboxes", "textures/geo/" + v);
      return id != null && exists(id) ? id : null;
   }

   public static List<String> animationNames(Loaded loaded) {
      List<String> out = new ArrayList();
      if (loaded == null) {
         return out;
      } else {
         for(String full : loaded.animations().keySet()) {
            out.add(shortName(full));
         }

         return out;
      }
   }

   public static List<String> animationNames(List<SkyboxEntry> entries) {
      List<String> out = new ArrayList();

      for(SkyboxEntry e : entries) {
         for(String n : animationNames(forEntry(e))) {
            if (!out.contains(n)) {
               out.add(n);
            }
         }
      }

      out.add("stop");
      return out;
   }

   public static GeoAnimation find(Loaded loaded, String name) {
      if (loaded != null && name != null) {
         GeoAnimation exact = (GeoAnimation)loaded.animations().get(name);
         if (exact != null) {
            return exact;
         } else {
            for(Map.Entry<String, GeoAnimation> e : loaded.animations().entrySet()) {
               if (shortName((String)e.getKey()).equalsIgnoreCase(name)) {
                  return (GeoAnimation)e.getValue();
               }
            }

            return null;
         }
      } else {
         return null;
      }
   }

   private static String shortName(String full) {
      int dot = full.lastIndexOf(46);
      return dot >= 0 ? full.substring(dot + 1) : full;
   }

   public static void play(SkyboxType type, int layer, String animation) {
      PLAYING.put(type.id() + "#" + layer, new Playing(animation, System.nanoTime()));
   }

   private static GeoAnimation fallback(Loaded loaded) {
      GeoAnimation idle = find(loaded, "idle");
      if (idle != null) {
         return idle;
      } else {
         for(GeoAnimation a : loaded.animations().values()) {
            if (a.loop == GeoAnimation.Loop.LOOP) {
               return a;
            }
         }

         return null;
      }
   }

   public static void build(SkyboxType type, SkyboxEntry entry, Loaded loaded, float[] center, float[] up, float size, float alpha, CelestialMesh.VertexSink sink) {
      Map var10000 = PLAYING;
      String var10001 = type.id();
      Playing playing = (Playing)var10000.get(var10001 + "#" + entry.layer());
      GeoAnimation anim;
      long since;
      if (playing != null) {
         anim = "stop".equalsIgnoreCase(playing.animation()) ? null : find(loaded, playing.animation());
         since = playing.startedAt();
      } else {
         anim = fallback(loaded);
         since = LOADED_AT;
      }

      float seconds = (float)(System.nanoTime() - since) / 1.0E9F;
      if (playing != null && anim != null && anim.loop == GeoAnimation.Loop.ONCE && seconds > anim.length) {
         GeoAnimation idle = fallback(loaded);
         long now = System.nanoTime();
         if (idle != null && idle != anim) {
            PLAYING.put(type.id() + "#" + entry.layer(), new Playing(idle.name, now));
            anim = idle;
            seconds = 0.0F;
         } else {
            var10000 = PLAYING;
            var10001 = type.id();
            var10000.remove(var10001 + "#" + entry.layer());
            anim = idle;
            seconds = (float)(now - LOADED_AT) / 1.0E9F;
         }
      }

      GeoMesh.build(loaded.model(), anim, seconds, center, up, size, alpha, sink);
   }

   private static Identifier id(String ns, String path) {
      return Identifier.tryParse(ns + ":" + path);
   }

   private static boolean exists(Identifier id) {
      MinecraftClient mc = MinecraftClient.getInstance();
      return mc != null && mc.getResourceManager().getResource(id).isPresent();
   }

   private static JsonObject readJson(Identifier id) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc != null && id != null) {
         Optional<Resource> resource = mc.getResourceManager().getResource(id);
         if (resource.isEmpty()) {
            return null;
         } else {
            try {
               InputStream in = ((Resource)resource.get()).getInputStream();

               JsonObject var4;
               try {
                  var4 = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
               } catch (Throwable var7) {
                  if (in != null) {
                     try {
                        in.close();
                     } catch (Throwable var6) {
                        var7.addSuppressed(var6);
                     }
                  }

                  throw var7;
               }

               if (in != null) {
                  in.close();
               }

               return var4;
            } catch (Exception unreadable) {
               throw new IllegalArgumentException(String.valueOf(id) + ": " + unreadable.getMessage(), unreadable);
            }
         }
      } else {
         return null;
      }
   }

   @Environment(EnvType.CLIENT)
   public static record Loaded(GeoModel model, Identifier texture, Map<String, GeoAnimation> animations) {
   }

   @Environment(EnvType.CLIENT)
   private static record Playing(String animation, long startedAt) {
   }
}
