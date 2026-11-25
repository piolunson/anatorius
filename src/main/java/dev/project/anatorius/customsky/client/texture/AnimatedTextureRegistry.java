package dev.project.anatorius.customsky.client.texture;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.ResourceTexture;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import dev.project.anatorius.customsky.Customskyboxes;
import dev.project.anatorius.customsky.client.model.SkyModels;
import dev.project.anatorius.customsky.client.sky.SkyboxEntry;
import dev.project.anatorius.customsky.client.sky.SkyboxManager;
import dev.project.anatorius.customsky.client.sky.SkyboxType;

@Environment(EnvType.CLIENT)
public final class AnimatedTextureRegistry {
   private static final AnimatedTextureRegistry INSTANCE = new AnimatedTextureRegistry();
   private final Map<Key, Identifier> resolved = new ConcurrentHashMap();
   private final Map<Identifier, GifTexture> gifTextures = new ConcurrentHashMap();
   private final Map<Identifier, Key> gifKeys = new ConcurrentHashMap();
   private final Set<Key> loading = ConcurrentHashMap.newKeySet();

   public static AnimatedTextureRegistry get() {
      return INSTANCE;
   }

   private AnimatedTextureRegistry() {
   }

   public Identifier resolve(SkyboxType type, Identifier source) {
      if (source == null) {
         return null;
      } else {
         Key key = new Key(type, source);
         Identifier cached = (Identifier)this.resolved.get(key);
         if (cached != null) {
            return cached;
         } else {
            boolean isGif = source.getPath().endsWith(".gif");
            boolean needsMoonAtlas = type == SkyboxType.MOON;
            boolean celestial = type == SkyboxType.SUN || type == SkyboxType.MOON;
            if (!isGif && !needsMoonAtlas) {
               if (!this.loading.contains(key)) {
                  this.loading.add(key);
                  MinecraftClient mc2 = MinecraftClient.getInstance();
                  if (mc2 != null) {
                     CompletableFuture.runAsync(() -> {
                        try {
                           mc2.getResourceManager().getResourceOrThrow(source);
                        } catch (Exception var7) {
                           Customskyboxes.LOGGER.error("Texture not found: {} for {}", source, type.id());
                           this.loading.remove(key);
                           return;
                        }

                        mc2.execute(() -> {
                           try {
                              mc2.getTextureManager().registerTexture(source, new ResourceTexture(source));
                           } catch (Exception var6) {
                           }

                           this.resolved.put(key, celestial ? CelestialGlow.keyed(source) : source);
                           this.loading.remove(key);
                        });
                     });
                  } else {
                     this.loading.remove(key);
                  }
               }

               return null;
            } else if (this.loading.contains(key)) {
               return null;
            } else {
               this.loading.add(key);
               MinecraftClient mc = MinecraftClient.getInstance();
               if (mc == null) {
                  this.loading.remove(key);
                  return null;
               } else {
                  CompletableFuture.supplyAsync(() -> {
                     try {
                        String var10001 = type.id();
                        Identifier dyn = Identifier.of("customskyboxes", "dynamic/" + var10001 + "/" + source.getNamespace() + "_" + source.getPath().replace('/', '_').replace('.', '_'));
                        if (isGif) {
                           GifTexture.GifData gif = GifTexture.loadData(mc.getResourceManager(), source, needsMoonAtlas, celestial);
                           return new Object[]{dyn, gif, null};
                        } else {
                           NativeImage atlas = MoonAtlasBuilder.fromResource(mc.getResourceManager(), source);
                           CelestialGlow.apply(atlas);
                           return new Object[]{dyn, null, atlas};
                        }
                     } catch (Exception e) {
                        Customskyboxes.LOGGER.error("Failed to load {} for {}: {}", new Object[]{source, type.id(), e.getMessage()});
                        return null;
                     }
                  }).thenAcceptAsync((result) -> {
                     if (result != null) {
                        Identifier dyn = (Identifier)result[0];
                        GifTexture.GifData gifData = (GifTexture.GifData)result[1];
                        NativeImage atlasImage = (NativeImage)result[2];

                        try {
                           AbstractTexture tex;
                           if (gifData != null) {
                              GifTexture gif = GifTexture.fromData(gifData);
                              this.gifTextures.put(dyn, gif);
                              this.gifKeys.put(dyn, key);
                              tex = gif;
                           } else {
                              tex = new NativeImageBackedTexture(() -> "customskyboxes_atlas", atlasImage);
                           }

                           mc.getTextureManager().registerTexture(dyn, tex);
                           this.resolved.put(key, dyn);
                        } catch (Exception e) {
                           Customskyboxes.LOGGER.error("Failed to register {} for {}: {}", new Object[]{dyn, type.id(), e.getMessage()});
                        }
                     }

                     this.loading.remove(key);
                  }, mc);
                  return null;
               }
            }
         }
      }
   }

   public void tick() {
      this.gifTextures.forEach((dyn, tex) -> tex.tick(speedFor((Key)this.gifKeys.get(dyn))));
   }

   private static float speedFor(Key key) {
      if (key == null) {
         return 1.0F;
      } else {
         SkyboxEntry active = SkyboxManager.get().resolve(key.type());
         if (active != null && key.source().equals(active.texture())) {
            return active.gifSpeed();
         } else {
            SkyboxManager.FadingOut fading = SkyboxManager.get().getFadingOut(key.type());
            return fading != null && key.source().equals(fading.entry().texture()) ? fading.entry().gifSpeed() : 1.0F;
         }
      }
   }

   public int gifFrame(SkyboxType type, Identifier source) {
      Identifier dyn = (Identifier)this.resolved.get(new Key(type, source));
      GifTexture tex = dyn == null ? null : (GifTexture)this.gifTextures.get(dyn);
      return tex == null ? -1 : tex.currentFrame();
   }

   public void reset() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc != null) {
         for(Identifier id : this.resolved.values()) {
            if ("customskyboxes".equals(id.getNamespace()) && id.getPath().startsWith("dynamic/")) {
               mc.getTextureManager().destroyTexture(id);
            }
         }
      }

      this.resolved.clear();
      CelestialGlow.reset();
      SkyModels.reset();
      this.gifTextures.clear();
      this.gifKeys.clear();
      this.loading.clear();
   }

   @Environment(EnvType.CLIENT)
   private static record Key(SkyboxType type, Identifier source) {
   }
}
