package dev.project.anatorius.customsky.client.texture;

import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import dev.project.anatorius.customsky.Customskyboxes;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

@Environment(EnvType.CLIENT)
public class GifTexture extends NativeImageBackedTexture {
   private final NativeImage[] frames;
   private final int[] delaysMs;
   private double animMs;
   private long lastNanos = -1L;
   private int lastFrame = -1;

   private GifTexture(NativeImage[] frames, int[] delaysMs) {
      super(() -> "customskyboxes_gif", copyOf(frames[0]));
      this.frames = frames;
      this.delaysMs = delaysMs;
   }

   public static GifTexture fromData(GifData data) {
      return new GifTexture(data.frames, data.delaysMs);
   }

   private static NativeImage copyOf(NativeImage src) {
      NativeImage dst = new NativeImage(src.getWidth(), src.getHeight(), false);
      dst.copyFrom(src);
      return dst;
   }

   public void tick(float speed) {
      long now = System.nanoTime();
      if (this.lastNanos >= 0L) {
         this.animMs += (double)(now - this.lastNanos) / (double)1000000.0F * (double)Math.max(0.0F, speed);
      }

      this.lastNanos = now;
      int idx = this.currentFrameIndex();
      if (idx != this.lastFrame) {
         this.lastFrame = idx;
         NativeImage target = this.getImage();
         if (target != null) {
            target.copyFrom(this.frames[idx]);
            MinecraftClient.getInstance().execute(this::upload);
         }
      }
   }

   public int currentFrame() {
      return Math.max(this.lastFrame, 0);
   }

   private int currentFrameIndex() {
      if (this.frames.length <= 1) {
         return 0;
      } else {
         long total = 0L;

         for(int d : this.delaysMs) {
            total += (long)d;
         }

         if (total <= 0L) {
            return 0;
         } else {
            long elapsed = (long)this.animMs % total;
            long acc = 0L;

            for(int i = 0; i < this.delaysMs.length; ++i) {
               acc += (long)this.delaysMs[i];
               if (elapsed < acc) {
                  return i;
               }
            }

            return this.frames.length - 1;
         }
      }
   }

   public void close() {
      for(NativeImage img : this.frames) {
         if (img != null) {
            img.close();
         }
      }

      super.close();
   }

   @Deprecated
   public static GifTexture load(ResourceManager rm, Identifier id) throws IOException {
      return load(rm, id, false);
   }

   @Deprecated
   public static GifTexture load(ResourceManager rm, Identifier id, boolean tileMoonAtlas) throws IOException {
      return fromData(loadData(rm, id, tileMoonAtlas, false));
   }

   public static GifData loadData(ResourceManager rm, Identifier id, boolean tileMoonAtlas, boolean glow) throws IOException {
      Resource res = rm.getResourceOrThrow(id);

      try {
         InputStream in = res.getInputStream();

         GifData var34;
         try {
            ImageInputStream iis = ImageIO.createImageInputStream(in);

            try {
               Iterator<ImageReader> it = ImageIO.getImageReadersByFormatName("gif");
               if (!it.hasNext()) {
                  throw new IOException("No GIF ImageReader available");
               }

               ImageReader reader = (ImageReader)it.next();
               reader.setInput(iis, false);
               int count = reader.getNumImages(true);
               List<NativeImage> framesL = new ArrayList(count);
               List<Integer> delaysL = new ArrayList(count);
               int width = 0;
               int height = 0;
               BufferedImage canvas = null;

               for(int i = 0; i < count; ++i) {
                  BufferedImage frame = reader.read(i);
                  if (canvas == null) {
                     width = frame.getWidth();
                     height = frame.getHeight();
                     canvas = new BufferedImage(width, height, 2);
                  }

                  canvas.createGraphics().drawImage(frame, 0, 0, (ImageObserver)null);
                  IIOMetadata meta = reader.getImageMetadata(i);
                  int delay = extractDelayMs(meta);
                  delaysL.add(delay <= 0 ? 100 : delay);
                  NativeImage ni = new NativeImage(width, height, false);
                  int[] argb = canvas.getRGB(0, 0, width, height, (int[])null, 0, width);

                  for(int y = 0; y < height; ++y) {
                     for(int x = 0; x < width; ++x) {
                        ni.setColorArgb(x, y, argb[y * width + x]);
                     }
                  }

                  if (glow) {
                     CelestialGlow.apply(ni);
                  }

                  framesL.add(ni);
               }

               reader.dispose();
               if (framesL.isEmpty()) {
                  throw new IOException("Empty GIF: " + String.valueOf(id));
               }

               if (tileMoonAtlas) {
                  List<NativeImage> atlased = new ArrayList(framesL.size());

                  for(NativeImage frame : framesL) {
                     atlased.add(MoonAtlasBuilder.tile4x2(frame));
                     frame.close();
                  }

                  framesL = atlased;
                  width *= 4;
                  height *= 2;
               }

               NativeImage[] arr = (NativeImage[])framesL.toArray(new NativeImage[0]);
               int[] dArr = delaysL.stream().mapToInt(Integer::intValue).toArray();
               Customskyboxes.LOGGER.info("Loaded GIF {} ({} frames, {}x{})", new Object[]{id, arr.length, width, height});
               var34 = new GifData(arr, dArr, id);
            } catch (Throwable var25) {
               if (iis != null) {
                  try {
                     iis.close();
                  } catch (Throwable var24) {
                     var25.addSuppressed(var24);
                  }
               }

               throw var25;
            }

            if (iis != null) {
               iis.close();
            }
         } catch (Throwable var26) {
            if (in != null) {
               try {
                  in.close();
               } catch (Throwable var23) {
                  var26.addSuppressed(var23);
               }
            }

            throw var26;
         }

         if (in != null) {
            in.close();
         }

         return var34;
      } catch (IOException e) {
         throw e;
      } catch (Exception e) {
         throw new IOException("Failed to load GIF " + String.valueOf(id), e);
      }
   }

   private static int extractDelayMs(IIOMetadata meta) {
      try {
         String nativeFormat = meta.getNativeMetadataFormatName();
         Node root = meta.getAsTree(nativeFormat);
         NodeList children = root.getChildNodes();

         for(int i = 0; i < children.getLength(); ++i) {
            Node node = children.item(i);
            if ("GraphicControlExtension".equals(node.getNodeName())) {
               Node delay = node.getAttributes().getNamedItem("delayTime");
               if (delay != null) {
                  int centi = Integer.parseInt(delay.getNodeValue());
                  return centi * 10;
               }
            }
         }
      } catch (Exception var8) {
      }

      return 100;
   }

   @Environment(EnvType.CLIENT)
   public static final class GifData {
      public final NativeImage[] frames;
      public final int[] delaysMs;
      public final Identifier id;

      public GifData(NativeImage[] frames, int[] delaysMs, Identifier id) {
         this.frames = frames;
         this.delaysMs = delaysMs;
         this.id = id;
      }
   }
}
