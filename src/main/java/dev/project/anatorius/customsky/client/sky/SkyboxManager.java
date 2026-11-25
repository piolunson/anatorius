package dev.project.anatorius.customsky.client.sky;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.biome.Biome;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.RegistryKeys;

@Environment(EnvType.CLIENT)
public final class SkyboxManager {
   private static final SkyboxManager INSTANCE = new SkyboxManager();
   private final Map<SkyKey, SkyboxEntry> global = new ConcurrentHashMap();
   private final Map<Identifier, Map<SkyKey, SkyboxEntry>> perBiome = new ConcurrentHashMap();
   private final Map<SkyKey, FadingOut> globalFadingOut = new ConcurrentHashMap();
   private final Map<SkyKey, FadingOut> biomeFadingOut = new ConcurrentHashMap();
   private Identifier lastBiome = null;

   public static SkyboxManager get() {
      return INSTANCE;
   }

   private SkyboxManager() {
   }

   public void setGlobal(SkyboxType type, SkyboxEntry entry) {
      SkyKey key = SkyboxManager.SkyKey.of(type, entry);
      this.global.put(key, entry.withAppliedTick(currentTick()));
      this.globalFadingOut.remove(key);
   }

   public void clearGlobal(SkyboxType type) {
      for(SkyKey key : List.copyOf(this.global.keySet())) {
         if (key.type() == type) {
            this.clearGlobalLayer(type, key.layer());
         }
      }
   }

   public void clearGlobalLayer(SkyboxType type, int layer) {
      SkyKey key = SkyboxManager.SkyKey.of(type, layer);
      SkyboxEntry removed = (SkyboxEntry)this.global.remove(key);
      if (removed != null && startsFadeOut(removed)) {
         this.globalFadingOut.put(key, new FadingOut(removed, currentTick()));
      }
   }

   public void clearAllGlobal() {
      for(SkyboxType t : SkyboxType.values()) {
         this.clearGlobal(t);
      }
   }

   public SkyboxEntry getGlobal(SkyboxType type) {
      return (SkyboxEntry)this.global.get(SkyboxManager.SkyKey.of(type, 1));
   }

   public Map<SkyKey, SkyboxEntry> snapshotGlobal() {
      return sorted(this.global);
   }

   public void setBiome(Identifier biome, SkyboxType type, SkyboxEntry entry) {
      ((Map)this.perBiome.computeIfAbsent(biome, (k) -> new ConcurrentHashMap())).put(SkyboxManager.SkyKey.of(type, entry), entry.withAppliedTick(currentTick()));
   }

   public void clearBiome(Identifier biome, SkyboxType type) {
      Map<SkyKey, SkyboxEntry> m = (Map)this.perBiome.get(biome);
      if (m != null) {
         boolean here = standingIn(biome);
         long now = currentTick();

         for(SkyKey key : List.copyOf(m.keySet())) {
            if (key.type() == type) {
               SkyboxEntry removed = (SkyboxEntry)m.remove(key);
               if (here && removed != null && startsFadeOut(removed)) {
                  this.biomeFadingOut.put(key, new FadingOut(removed, now));
               }
            }
         }

         if (m.isEmpty()) {
            this.perBiome.remove(biome);
         }
      }
   }

   public void clearAllForBiome(Identifier biome) {
      if (this.perBiome.containsKey(biome)) {
         for(SkyboxType type : SkyboxType.values()) {
            this.clearBiome(biome, type);
         }

         this.perBiome.remove(biome);
      }
   }

   private static boolean standingIn(Identifier biome) {
      Identifier current = currentBiomeId();
      return current != null && current.equals(biome);
   }

   public Map<Identifier, Map<SkyKey, SkyboxEntry>> snapshotBiomes() {
      Map<Identifier, Map<SkyKey, SkyboxEntry>> out = new HashMap();
      this.perBiome.forEach((k, v) -> out.put(k, sorted(v)));
      return out;
   }

   public Set<Identifier> biomesWithOverride() {
      return this.perBiome.keySet();
   }

   public void tickBiomeTransition() {
      SkyClock.tick();
      WeatherDrift.tick(currentTick());
      Identifier current = currentBiomeId();
      if (current != null) {
         if (current.equals(this.lastBiome)) {
            this.biomeFadingOut.entrySet().removeIf((e) -> FadeController.fadingOutFinished((FadingOut)e.getValue()));
         } else {
            long now = currentTick();
            if (this.lastBiome != null) {
               Map<SkyKey, SkyboxEntry> oldEntries = (Map)this.perBiome.get(this.lastBiome);
               if (oldEntries != null) {
                  oldEntries.forEach((key, entry) -> this.biomeFadingOut.put(key, new FadingOut(entry, now)));
               }
            }

            Map<SkyKey, SkyboxEntry> newEntries = (Map)this.perBiome.get(current);
            if (newEntries != null) {
               newEntries.replaceAll((key, entry) -> entry.withAppliedTick(now));
            }

            this.lastBiome = current;
         }
      }
   }

   public SkyboxEntry resolve(SkyboxType type) {
      return this.resolveLayer(type, 1);
   }

   public SkyboxEntry resolveLayer(SkyboxType type, int layer) {
      SkyKey key = SkyboxManager.SkyKey.of(type, layer);
      Identifier biome = currentBiomeId();
      if (biome != null) {
         Map<SkyKey, SkyboxEntry> m = (Map)this.perBiome.get(biome);
         if (m != null) {
            SkyboxEntry e = (SkyboxEntry)m.get(key);
            if (e != null) {
               return e;
            }
         }
      }

      return (SkyboxEntry)this.global.get(key);
   }

   public List<SkyboxEntry> activeLayers(SkyboxType type) {
      List<SkyboxEntry> out = new ArrayList();

      for(int layer = 1; layer <= 8; ++layer) {
         SkyboxEntry e = this.resolveLayer(type, layer);
         if (e != null) {
            out.add(e);
         }

         if (!type.hasLayers()) {
            break;
         }
      }

      return out;
   }

   public FadingOut getFadingOut(SkyboxType type) {
      return this.getFadingOut(type, 1);
   }

   public FadingOut getFadingOut(SkyboxType type, int layer) {
      SkyKey key = SkyboxManager.SkyKey.of(type, layer);
      FadingOut bf = (FadingOut)this.biomeFadingOut.get(key);
      return bf != null && !FadeController.fadingOutFinished(bf) ? bf : (FadingOut)this.globalFadingOut.get(key);
   }

   public List<FadingOut> fadingLayers(SkyboxType type) {
      List<FadingOut> out = new ArrayList();

      for(int layer = 1; layer <= 8; ++layer) {
         FadingOut f = this.getFadingOut(type, layer);
         if (f != null && !FadeController.fadingOutFinished(f)) {
            out.add(f);
         }

         if (!type.hasLayers()) {
            break;
         }
      }

      return out;
   }

   private static boolean startsFadeOut(SkyboxEntry entry) {
      return entry.fadeOutTicks() > 0 || !entry.animOut().isNone() || !entry.riftAnimOut().isNone();
   }

   private static Map<SkyKey, SkyboxEntry> sorted(Map<SkyKey, SkyboxEntry> source) {
      Map<SkyKey, SkyboxEntry> out = new LinkedHashMap();
      source.entrySet().stream().sorted(Entry.comparingByKey(Comparator.naturalOrder())).forEach((e) -> out.put((SkyKey)e.getKey(), (SkyboxEntry)e.getValue()));
      return out;
   }

   private static long currentTick() {
      return SkyClock.ticks();
   }

   private static Identifier currentBiomeId() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc != null && mc.world != null && mc.player != null) {
         SkyAnchor.set(mc.player.getX(), mc.player.getY(), mc.player.getZ());
         RegistryEntry<Biome> biomeEntry = mc.world.getBiome(mc.player.getBlockPos());
         return (Identifier)biomeEntry.getKey().map(RegistryKey::getValue).orElse(null);
      } else {
         return null;
      }
   }

   public static RegistryKey<Registry<Biome>> biomeRegistryKey() {
      return RegistryKeys.BIOME;
   }

   public SkyboxEntry cloudsToDraw() {
      SkyboxEntry active = this.resolve(SkyboxType.CLOUDS);
      if (active != null) {
         return active;
      } else {
         FadingOut fading = this.getFadingOut(SkyboxType.CLOUDS);
         return fading != null && !FadeController.fadingOutFinished(fading) ? fading.entry() : null;
      }
   }

   public static String currentBiomeText() {
      Identifier id = currentBiomeId();
      return id == null ? null : id.toString();
   }

   @Environment(EnvType.CLIENT)
   public static record SkyKey(SkyboxType type, int layer) implements Comparable<SkyKey> {
      public static SkyKey of(SkyboxType type, int layer) {
         return new SkyKey(type, type.hasLayers() ? Math.max(1, Math.min(8, layer)) : 1);
      }

      public static SkyKey of(SkyboxType type, SkyboxEntry entry) {
         return of(type, entry.layer());
      }

      public int compareTo(SkyKey other) {
         int byType = this.type.compareTo(other.type);
         return byType != 0 ? byType : Integer.compare(this.layer, other.layer);
      }

      public String toString() {
         return this.layer > 1 ? this.type.id() + "#" + this.layer : this.type.id();
      }
   }

   @Environment(EnvType.CLIENT)
   public static record FadingOut(SkyboxEntry entry, long startTick) {
   }
}
