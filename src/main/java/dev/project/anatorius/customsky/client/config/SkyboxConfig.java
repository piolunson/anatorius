package dev.project.anatorius.customsky.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import dev.project.anatorius.customsky.Customskyboxes;
import dev.project.anatorius.customsky.client.sky.SkyParams;
import dev.project.anatorius.customsky.client.sky.SkyboxEntry;
import dev.project.anatorius.customsky.client.sky.SkyboxManager;
import dev.project.anatorius.customsky.client.sky.SkyboxType;

@Environment(EnvType.CLIENT)
public final class SkyboxConfig {
   private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().create();

   private SkyboxConfig() {
   }

   public static Path configPath() {
      return MinecraftClient.getInstance().runDirectory.toPath().resolve("config").resolve("customskyboxes.json");
   }

   public static void save() {
      Path p = configPath();
      JsonObject root = new JsonObject();
      JsonObject global = new JsonObject();
      SkyboxManager.get().snapshotGlobal().forEach((key, entry) -> global.add(key.toString(), serialize(entry)));
      root.add("global", global);
      JsonObject biomes = new JsonObject();
      SkyboxManager.get().snapshotBiomes().forEach((biome, m) -> {
         JsonObject bo = new JsonObject();
         m.forEach((key, entry) -> bo.add(key.toString(), serialize(entry)));
         biomes.add(biome.toString(), bo);
      });
      root.add("biomes", biomes);

      try {
         Files.createDirectories(p.getParent());
         Writer w = Files.newBufferedWriter(p);

         try {
            GSON.toJson(root, w);
         } catch (Throwable var8) {
            if (w != null) {
               try {
                  w.close();
               } catch (Throwable var7) {
                  var8.addSuppressed(var7);
               }
            }

            throw var8;
         }

         if (w != null) {
            w.close();
         }
      } catch (IOException e) {
         Customskyboxes.LOGGER.error("Failed to save config", e);
      }
   }

   public static void load() {
      Path p = configPath();
      if (Files.exists(p, new LinkOption[0])) {
         try {
            Reader r = Files.newBufferedReader(p);

            label117: {
               try {
                  JsonObject root = (JsonObject)GSON.fromJson(r, JsonObject.class);
                  if (root == null) {
                     break label117;
                  }

                  SkyboxManager mgr = SkyboxManager.get();
                  if (root.has("global")) {
                     JsonObject global = root.getAsJsonObject("global");

                     for(String key : global.keySet()) {
                        SkyboxType type = typeOf(key);
                        if (type != null) {
                           SkyboxEntry e = deserialize(type, global.getAsJsonObject(key));
                           if (e != null) {
                              mgr.setGlobal(type, e);
                           }
                        }
                     }
                  }

                  if (root.has("biomes")) {
                     JsonObject biomes = root.getAsJsonObject("biomes");
                     Iterator var18 = biomes.keySet().iterator();

                     label79:
                     while(true) {
                        String bkey;
                        Identifier biomeId;
                        while(true) {
                           if (!var18.hasNext()) {
                              break label79;
                           }

                           bkey = (String)var18.next();

                           try {
                              biomeId = Identifier.of(bkey);
                              break;
                           } catch (Exception var14) {
                           }
                        }

                        JsonObject types = biomes.getAsJsonObject(bkey);

                        for(String key : types.keySet()) {
                           SkyboxType type = typeOf(key);
                           if (type != null) {
                              SkyboxEntry e = deserialize(type, types.getAsJsonObject(key));
                              if (e != null) {
                                 mgr.setBiome(biomeId, type, e);
                              }
                           }
                        }
                     }
                  }
               } catch (Throwable var15) {
                  if (r != null) {
                     try {
                        r.close();
                     } catch (Throwable var13) {
                        var15.addSuppressed(var13);
                     }
                  }

                  throw var15;
               }

               if (r != null) {
                  r.close();
               }

               return;
            }

            if (r != null) {
               r.close();
            }
         } catch (Exception e) {
            Customskyboxes.LOGGER.error("Failed to load config", e);
         }
      }
   }

   private static SkyboxType typeOf(String key) {
      String id = key.contains("#") ? key.substring(0, key.indexOf(35)) : key;
      return (SkyboxType)SkyboxType.fromId(id).orElse(null);
   }

   private static JsonObject serialize(SkyboxEntry e) {
      JsonObject o = new JsonObject();
      if (e.texture() != null) {
         o.addProperty("texture", e.texture().toString());
      }

      if (e.isGif()) {
         o.addProperty("gif", true);
      }

      if (e.isVanilla()) {
         o.addProperty("vanilla", true);
      }

      if (e.color() != -1) {
         o.addProperty("color", e.color());
      }

      JsonObject args = new JsonObject();
      Map<String, String> var10000 = e.params().toMap();
      Objects.requireNonNull(args);
      var10000.forEach(args::addProperty);
      o.add("args", args);
      return o;
   }

   private static SkyboxEntry deserialize(SkyboxType type, JsonObject o) {
      try {
         Map<String, String> args = new LinkedHashMap();
         if (o.has("args")) {
            JsonObject saved = o.getAsJsonObject("args");

            for(String key : saved.keySet()) {
               args.put(key, saved.get(key).getAsString());
            }
         }

         SkyParams params = SkyParams.fromMap(type, args);
         if (o.has("color")) {
            return SkyboxEntry.ofColor(o.get("color").getAsInt(), params);
         } else if (o.has("vanilla") && o.get("vanilla").getAsBoolean()) {
            return SkyboxEntry.ofVanilla(params);
         } else if (!o.has("texture")) {
            return null;
         } else {
            Identifier id = Identifier.of(o.get("texture").getAsString());
            boolean gif = o.has("gif") && o.get("gif").getAsBoolean();
            return SkyboxEntry.ofTexture(id, gif, params);
         }
      } catch (Exception var6) {
         return null;
      }
   }
}
