package dev.project.anatorius.customsky.api;

import java.util.List;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import dev.project.anatorius.customsky.client.config.SkyboxConfig;
import dev.project.anatorius.customsky.client.model.SkyModels;
import dev.project.anatorius.customsky.client.resource.ResourcePackScanner;
import dev.project.anatorius.customsky.client.sky.CelestialPreset;
import dev.project.anatorius.customsky.client.sky.RiftAnim;
import dev.project.anatorius.customsky.client.sky.RiftEffects;
import dev.project.anatorius.customsky.client.sky.RiftShape;
import dev.project.anatorius.customsky.client.sky.SkyAnim;
import dev.project.anatorius.customsky.client.sky.SkyEffects;
import dev.project.anatorius.customsky.client.sky.SkyParams;
import dev.project.anatorius.customsky.client.sky.SkyboxEntry;
import dev.project.anatorius.customsky.client.sky.SkyboxManager;
import dev.project.anatorius.customsky.client.sky.SkyboxType;

@Environment(EnvType.CLIENT)
public final class CustomSkyboxesAPI {
   private CustomSkyboxesAPI() {
   }

   public static Optional<SkyboxType> type(String id) {
      return SkyboxType.fromId(id);
   }

   public static boolean set(SkyboxType type, Identifier texture, String args) {
      SkyParams.Result parsed = SkyParams.parse(type, args);
      if (!parsed.ok()) {
         return false;
      } else {
         SkyboxManager.get().setGlobal(type, SkyboxEntry.ofTexture(texture, ResourcePackScanner.isGif(texture), parsed.params()));
         SkyboxConfig.save();
         return true;
      }
   }

   public static boolean setColor(SkyboxType type, int rgb, String args) {
      SkyParams.Result parsed = SkyParams.parse(type, args);
      if (!parsed.ok()) {
         return false;
      } else {
         SkyboxManager.get().setGlobal(type, SkyboxEntry.ofColor(rgb & 16777215, parsed.params()));
         SkyboxConfig.save();
         return true;
      }
   }

   public static boolean setVanilla(SkyboxType type, String args) {
      SkyParams.Result parsed = SkyParams.parse(type, args);
      if (!parsed.ok()) {
         return false;
      } else {
         SkyboxManager.get().setGlobal(type, SkyboxEntry.ofVanilla(parsed.params()));
         SkyboxConfig.save();
         return true;
      }
   }

   public static boolean setForBiome(Identifier biome, SkyboxType type, Identifier texture, String args) {
      SkyParams.Result parsed = SkyParams.parse(type, args);
      if (!parsed.ok()) {
         return false;
      } else {
         SkyboxManager.get().setBiome(biome, type, SkyboxEntry.ofTexture(texture, ResourcePackScanner.isGif(texture), parsed.params()));
         SkyboxConfig.save();
         return true;
      }
   }

   public static void clear(SkyboxType type) {
      SkyboxManager.get().clearGlobal(type);
      SkyboxConfig.save();
   }

   public static void clearLayer(SkyboxType type, int layer) {
      SkyboxManager.get().clearGlobalLayer(type, layer);
      SkyboxConfig.save();
   }

   public static void clearAll() {
      SkyboxManager.get().clearAllGlobal();
      SkyEffects.stopAll();
      SkyboxConfig.save();
   }

   public static void clearForBiome(Identifier biome, SkyboxType type) {
      SkyboxManager.get().clearBiome(biome, type);
      SkyboxConfig.save();
   }

   public static SkyboxEntry active(SkyboxType type) {
      return SkyboxManager.get().resolve(type);
   }

   public static List<SkyboxEntry> layers(SkyboxType type) {
      return SkyboxManager.get().activeLayers(type);
   }

   public static boolean isActive(SkyboxType type) {
      return active(type) != null;
   }

   public static List<String> availableTextures(SkyboxType type) {
      return ResourcePackScanner.get().list(type);
   }

   public static boolean animateDome(SkyboxType type, String effect, int ticks, int color, float strength) {
      return animateDome(type, effect, ticks, color, strength, 1.0F);
   }

   public static boolean animateDome(SkyboxType type, String effect, int ticks, int color, float strength, float size) {
      if (!type.isDome()) {
         return false;
      } else {
         Optional<SkyEffects.Effect> found = SkyEffects.Effect.byId(effect);
         if (found.isEmpty()) {
            return false;
         } else {
            SkyEffects.start(type, (SkyEffects.Effect)found.get(), ticks, color & 16777215, strength, size);
            return true;
         }
      }
   }

   public static boolean animateCelestial(SkyboxType type, String preset, int ticks, float strength) {
      if (!type.isCelestial()) {
         return false;
      } else {
         Optional<CelestialPreset> found = CelestialPreset.byId(preset);
         if (found.isEmpty()) {
            return false;
         } else {
            SkyEffects.start(type, (CelestialPreset)found.get(), ticks, strength);
            return true;
         }
      }
   }

   public static boolean setRift(String args) {
      return setVanilla(SkyboxType.RIFT, args);
   }

   public static boolean animateRift(String effect, int ticks, float strength, boolean loop, int layer, int color) {
      return animateRift(effect, ticks, strength, loop, layer, new RiftEffects.Options(color < 0 ? -1 : color & 16777215, 1.0F, 0, 0.0F, 1.0F, 1.0F, 1.0F));
   }

   public static boolean animateRift(String effect, int ticks, float strength, boolean loop, int layer, RiftEffects.Options options) {
      Optional<RiftEffects.Effect> found = RiftEffects.Effect.byId(effect);
      if (found.isEmpty()) {
         return false;
      } else {
         RiftEffects.start((RiftEffects.Effect)found.get(), ticks, strength, loop, Math.max(0, layer), options);
         return true;
      }
   }

   public static List<String> riftEffects() {
      return RiftEffects.Effect.names();
   }

   public static List<String> riftShapes() {
      return RiftShape.names();
   }

   public static List<String> riftAnimations(boolean in) {
      return RiftAnim.names(in);
   }

   public static boolean setModel(SkyboxType type, String model, String args) {
      return type.isCelestial() && SkyModels.get(model) != null ? setVanilla(type, (args == null ? "" : args) + " model=" + model) : false;
   }

   public static boolean playModelAnimation(SkyboxType type, int layer, String animation) {
      int l = Math.max(1, layer);
      SkyboxEntry entry = null;

      for(SkyboxEntry e : SkyboxManager.get().activeLayers(type)) {
         if (e.layer() == l) {
            entry = e;
         }
      }

      SkyModels.Loaded loaded = SkyModels.forEntry(entry);
      if (loaded == null) {
         return false;
      } else if (!"stop".equalsIgnoreCase(animation) && SkyModels.find(loaded, animation) == null) {
         return false;
      } else {
         SkyModels.play(type, l, animation);
         return true;
      }
   }

   public static List<String> models() {
      return SkyModels.names();
   }

   public static List<String> modelAnimations(String model) {
      return SkyModels.animationNames(SkyModels.get(model));
   }

   public static void stopAnimations() {
      SkyEffects.stopAll();
      RiftEffects.stopAll();
   }

   public static List<String> domeEffects() {
      return SkyEffects.Effect.names();
   }

   public static List<String> celestialPresets() {
      return CelestialPreset.names();
   }

   public static List<String> animations(boolean in) {
      return SkyAnim.names(in);
   }
}
