package dev.project.anatorius.customsky.client.resource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourceManager;
import dev.project.anatorius.customsky.Customskyboxes;
import dev.project.anatorius.customsky.client.sky.SkyboxType;

@Environment(EnvType.CLIENT)
public final class ResourcePackScanner {
   private static final ResourcePackScanner INSTANCE = new ResourcePackScanner();
   private final Map<SkyboxType, List<String>> byType = new ConcurrentHashMap();
   private final Map<SkyboxType, Map<String, Identifier>> byName = new ConcurrentHashMap();

   public static ResourcePackScanner get() {
      return INSTANCE;
   }

   private ResourcePackScanner() {
      for(SkyboxType t : SkyboxType.values()) {
         this.byType.put(t, Collections.emptyList());
      }
   }

   public void rescan() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc != null) {
         ResourceManager rm = mc.getResourceManager();

         for(SkyboxType type : SkyboxType.values()) {
            if (!type.isTextureBased()) {
               this.byType.put(type, Collections.emptyList());
            } else {
               String prefix = type.subfolder();
               List<String> names = new ArrayList();
               Map<String, Identifier> found = new LinkedHashMap();
               scan(rm, prefix, (id) -> "customskyboxes".equals(id.getNamespace()), found, false);
               scan(rm, "customskyboxes/" + prefix, (id) -> true, found, true);
               scan(rm, "textures/customskyboxes/" + prefix, (id) -> true, found, true);
               names.addAll(found.keySet());
               this.byName.put(type, found);
               Collections.sort(names);
               this.byType.put(type, names);
            }
         }

         Customskyboxes.LOGGER.info("ResourcePackScanner rescan: {}", this.byType.entrySet().stream().map((e) -> {
            String var10000 = ((SkyboxType)e.getKey()).id();
            return var10000 + "=" + ((List)e.getValue()).size();
         }).toList());
      }
   }

   private static void scan(ResourceManager rm, String folder, Predicate<Identifier> namespace, Map<String, Identifier> found, boolean qualified) {
      try {
         Map<Identifier, ?> hits = rm.findResources(folder, (idx) -> namespace.test(idx) && (idx.getPath().endsWith(".png") || idx.getPath().endsWith(".gif")));

         for(Identifier id : hits.keySet()) {
            String rest = id.getPath().substring(folder.length() + 1);
            String name = qualified && !"customskyboxes".equals(id.getNamespace()) ? id.getNamespace() + ":" + rest : rest;
            found.putIfAbsent(name, id);
         }
      } catch (Exception e) {
         Customskyboxes.LOGGER.warn("Error scanning {}", folder, e);
      }
   }

   public List<String> list(SkyboxType type) {
      return (List)this.byType.getOrDefault(type, Collections.emptyList());
   }

   public static Identifier resolveTexture(SkyboxType type, String input) {
      if (input != null && !input.isBlank()) {
         Map<String, Identifier> known = (Map)INSTANCE.byName.get(type);
         if (known != null && known.containsKey(input)) {
            return (Identifier)known.get(input);
         } else if (input.contains(":")) {
            try {
               return Identifier.of(input);
            } catch (Exception var5) {
               return null;
            }
         } else {
            String path;
            if (input.contains("/")) {
               path = input;
            } else {
               String var10000 = type.subfolder();
               path = var10000 + "/" + input;
            }

            try {
               return Identifier.of("customskyboxes", path);
            } catch (Exception var6) {
               return null;
            }
         }
      } else {
         return null;
      }
   }

   public static boolean isGif(Identifier id) {
      return id != null && id.getPath().endsWith(".gif");
   }
}
