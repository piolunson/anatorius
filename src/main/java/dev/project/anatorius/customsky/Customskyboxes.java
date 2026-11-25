package dev.project.anatorius.customsky;

import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Customskyboxes {
   public static final String MOD_ID = "customskyboxes";
   public static final Logger LOGGER = LoggerFactory.getLogger("customskyboxes");

   public static Identifier id(String path) {
      return Identifier.of("customskyboxes", path);
   }
}
