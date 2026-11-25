package dev.project.anatorius.customsky.client.texture;

import java.io.IOException;
import java.io.InputStream;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import dev.project.anatorius.customsky.Customskyboxes;

@Environment(EnvType.CLIENT)
public final class MoonAtlasBuilder {
   private MoonAtlasBuilder() {
   }

   public static NativeImage fromResource(ResourceManager rm, Identifier source) throws IOException {
      Resource res = rm.getResourceOrThrow(source);
      InputStream in = res.getInputStream();

      NativeImage var5;
      try {
         NativeImage src = NativeImage.read(in);

         try {
            var5 = tile4x2(src);
         } finally {
            src.close();
         }
      } catch (Throwable var12) {
         if (in != null) {
            try {
               in.close();
            } catch (Throwable var10) {
               var12.addSuppressed(var10);
            }
         }

         throw var12;
      }

      if (in != null) {
         in.close();
      }

      return var5;
   }

   public static NativeImage tile4x2(NativeImage src) {
      int w = src.getWidth();
      int h = src.getHeight();
      NativeImage atlas = new NativeImage(w * 4, h * 2, false);

      for(int row = 0; row < 2; ++row) {
         for(int col = 0; col < 4; ++col) {
            for(int y = 0; y < h; ++y) {
               for(int x = 0; x < w; ++x) {
                  atlas.setColorArgb(col * w + x, row * h + y, src.getColorArgb(x, y));
               }
            }
         }
      }

      Customskyboxes.LOGGER.debug("Built moon atlas {}x{} -> {}x{}", new Object[]{w, h, w * 4, h * 2});
      return atlas;
   }
}
