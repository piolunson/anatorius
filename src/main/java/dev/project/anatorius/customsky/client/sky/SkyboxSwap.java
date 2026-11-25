package dev.project.anatorius.customsky.client.sky;

import java.util.HashMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public final class SkyboxSwap {
   private static final Map<String, SkyboxType> VANILLA_TO_TYPE = new HashMap();

   private SkyboxSwap() {
   }

   public static Identifier swap(Identifier id) {
      if (id == null) {
         return null;
      } else if (!"minecraft".equals(id.getNamespace())) {
         return id;
      } else {
         SkyboxType type = (SkyboxType)VANILLA_TO_TYPE.get(id.getPath());
         if (type == null) {
            return id;
         } else {
            SkyboxEntry entry = SkyboxTextures.activeEntry(type);
            if (entry != null && entry.texture() == null) {
               return id;
            } else {
               Identifier override = SkyboxTextures.activeTexture(type);
               return override != null ? override : id;
            }
         }
      }
   }

   static {
      VANILLA_TO_TYPE.put("textures/environment/sun.png", SkyboxType.SUN);
      VANILLA_TO_TYPE.put("textures/environment/moon_phases.png", SkyboxType.MOON);
      VANILLA_TO_TYPE.put("textures/environment/clouds.png", SkyboxType.CLOUDS);
      VANILLA_TO_TYPE.put("textures/environment/rain.png", SkyboxType.RAIN);
      VANILLA_TO_TYPE.put("textures/environment/snow.png", SkyboxType.SNOW);
   }
}
