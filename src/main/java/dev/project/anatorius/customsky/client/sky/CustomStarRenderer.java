package dev.project.anatorius.customsky.client.sky;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.random.Random;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.render.BuiltBuffer;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

@Environment(EnvType.CLIENT)
public final class CustomStarRenderer {
   private static GpuBuffer starBuffer;
   private static int starIndexCount;
   private static int lastAmount = -1;
   private static float lastScale = -1.0F;

   private CustomStarRenderer() {
   }

   public static void reset() {
      if (starBuffer != null) {
         starBuffer.close();
         starBuffer = null;
      }

      lastAmount = -1;
      lastScale = -1.0F;
   }

   public static void render(Matrix4f modelView, float brightness, SkyboxEntry entry) {
      if (entry != null && !(brightness <= 0.0F)) {
         int amount = Math.max(1, Math.min(10, entry.starAmount()));
         float scale = Math.max(0.1F, entry.scale());
         if (starBuffer == null || amount != lastAmount || Float.compare(scale, lastScale) != 0) {
            rebuildBuffer(amount, scale);
            lastAmount = amount;
            lastScale = scale;
         }

         float fadeAlpha = FadeController.alphaForActive(entry);
         float r = brightness;
         float g = brightness;
         float b = brightness;
         if (entry.color() != -1) {
            int c = entry.color();
            r = brightness * (float)(c >> 16 & 255) / 255.0F;
            g = brightness * (float)(c >> 8 & 255) / 255.0F;
            b = brightness * (float)(c & 255) / 255.0F;
         }

         Matrix4f transform = (new Matrix4f(RenderSystem.getModelViewMatrix())).mul(modelView);
         GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().write(transform, new Vector4f(r, g, b, brightness * fadeAlpha), new Vector3f(), new Matrix4f());
         Framebuffer fb = MinecraftClient.getInstance().getFramebuffer();
         RenderSystem.ShapeIndexBuffer idxHelper = RenderSystem.getSequentialBuffer(DrawMode.QUADS);
         GpuBuffer idxBuf = idxHelper.getIndexBuffer(starIndexCount);
         RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "customskyboxes_stars", fb.getColorAttachmentView(), OptionalInt.empty(), fb.getDepthAttachmentView(), OptionalDouble.empty());

         try {
            pass.setPipeline(RenderPipelines.POSITION_STARS);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.setVertexBuffer(0, starBuffer);
            pass.setIndexBuffer(idxBuf, idxHelper.getIndexType());
            pass.drawIndexed(0, 0, starIndexCount, 1);
         } catch (Throwable var18) {
            if (pass != null) {
               try {
                  pass.close();
               } catch (Throwable var17) {
                  var18.addSuppressed(var17);
               }
            }

            throw var18;
         }

         if (pass != null) {
            pass.close();
         }
      }
   }

   private static void rebuildBuffer(int amount, float scale) {
      if (starBuffer != null) {
         starBuffer.close();
         starBuffer = null;
      }

      Random random = Random.create(10842L);
      int count = 1500 * amount;
      float baseSize = 0.15F * scale;
      int vertexSize = VertexFormats.POSITION.getVertexSize();
      BufferAllocator allocator = new BufferAllocator(vertexSize * count * 4);
      BufferBuilder bb = new BufferBuilder(allocator, DrawMode.QUADS, VertexFormats.POSITION);
      int generated = 0;
      int attempts = 0;

      while(generated < count && attempts < count * 10) {
         ++attempts;
         float fx = random.nextFloat() * 2.0F - 1.0F;
         float fy = random.nextFloat() * 2.0F - 1.0F;
         float fz = random.nextFloat() * 2.0F - 1.0F;
         float size = baseSize + random.nextFloat() * baseSize * 0.66F;
         float lenSq = fx * fx + fy * fy + fz * fz;
         if (!(lenSq >= 1.0F) && !(lenSq <= 0.01F)) {
            ++generated;
            float invLen = 1.0F / (float)Math.sqrt((double)lenSq);
            float px = fx * invLen * 100.0F;
            float py = fy * invLen * 100.0F;
            float pz = fz * invLen * 100.0F;
            double d = Math.atan2((double)fx, (double)fz);
            double sinD = Math.sin(d);
            double cosD = Math.cos(d);
            double p = Math.atan2(Math.sqrt((double)(fx * fx + fz * fz)), (double)fy);
            double sinP = Math.sin(p);
            double cosP = Math.cos(p);
            double rot = random.nextDouble() * Math.PI * (double)2.0F;
            double sinR = Math.sin(rot);
            double cosR = Math.cos(rot);

            for(int v = 0; v < 4; ++v) {
               double xOff = (double)((float)((v & 2) - 1) * size);
               double yOff = (double)((float)((v + 1 & 2) - 1) * size);
               double aa = xOff * cosR - yOff * sinR;
               double ab = yOff * cosR + xOff * sinR;
               double ac = aa * sinP;
               double ad = -aa * cosP;
               double ae = ad * sinD - ab * cosD;
               double af = ab * sinD + ad * cosD;
               bb.vertex((float)((double)px + ae), (float)((double)py + ac), (float)((double)pz + af));
            }
         }
      }

      starIndexCount = generated * 6;
      BuiltBuffer built = bb.end();

      try {
         starBuffer = RenderSystem.getDevice().createBuffer(() -> "custom_stars", 32, built.getBuffer());
      } catch (Throwable var55) {
         if (built != null) {
            try {
               built.close();
            } catch (Throwable var54) {
               var55.addSuppressed(var54);
            }
         }

         throw var55;
      }

      if (built != null) {
         built.close();
      }

      allocator.close();
   }
}
