package dev.project.anatorius.customsky.client.sky;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.world.dimension.DimensionType.Skybox;
import dev.project.anatorius.customsky.client.compat.IrisCompat;
import dev.project.anatorius.customsky.client.model.SkyModels;
import dev.project.anatorius.customsky.client.resource.ResourcePackScanner;
import dev.project.anatorius.customsky.client.texture.AnimatedTextureRegistry;
import dev.project.anatorius.customsky.client.texture.CelestialGlow;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

@Environment(EnvType.CLIENT)
public final class SkyDomeRenderer {
   private static final float RADIUS = 100.0F;
   private static final RenderPipeline SKY_PIPELINE;
   private static boolean drawingLate;
   private static float lastTickDelta;
   private static final RenderPipeline CELESTIAL_PIPELINE;
   private static final Vector4f WHITE;
   private static final SkyboxType[] CELESTIAL_ORDER;
   private static final int PASS_ALL = 0;
   private static final int PASS_BELOW = 1;
   private static final int PASS_ABOVE = 2;
   private static boolean cloudsAfterDomes;
   private static final Identifier RIM_TEXTURE;
   private static final int CLOUD_GRID = 20;
   private static final Identifier VANILLA_CLOUDS;

   private SkyDomeRenderer() {
   }

   private static boolean isLate(SkyboxEntry entry) {
      return entry != null && entry.overcome().any();
   }

   public static boolean needsLateDraw() {
      if (hasRift()) {
         return true;
      } else if (hasCustom()) {
         return true;
      } else {
         for(SkyboxType type : SkyboxType.values()) {
            if (type.isDome()) {
               if (isLate(SkyboxTextures.activeEntry(type))) {
                  return true;
               }

               SkyboxManager.FadingOut fading = SkyboxManager.get().getFadingOut(type);
               if (fading != null && isLate(fading.entry())) {
                  return true;
               }
            }
         }

         return false;
      }
   }

   public static boolean overcomes(SkyboxType element) {
      for(SkyboxType type : SkyboxType.values()) {
         if (type.isDome()) {
            SkyboxEntry entry = SkyboxTextures.activeEntry(type);
            if (isLate(entry) && entry.overcome().lastCovered() == element) {
               return true;
            }

            SkyboxManager.FadingOut fading = SkyboxManager.get().getFadingOut(type);
            if (fading != null && isLate(fading.entry()) && fading.entry().overcome().lastCovered() == element) {
               return true;
            }
         }
      }

      return false;
   }

   public static void renderOvercomingDomes(Matrix4f domeMatrix) {
      if (!drawingLate) {
         drawingLate = true;

         try {
            renderIfOverride(domeMatrix, lastTickDelta);
         } finally {
            drawingLate = false;
         }
      }
   }

   public static void renderIfOverride(Matrix4f domeMatrix, float tickDelta) {
      lastTickDelta = tickDelta;
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc != null && mc.world != null) {
         renderFadingOut(domeMatrix, SkyboxType.SKY, 1.0F);
         SkyboxEntry sky = SkyboxTextures.activeEntry(SkyboxType.SKY);
         if (sky != null && isLate(sky) == drawingLate) {
            float a = FadeController.alphaForActive(sky);
            Identifier tex = AnimatedTextureRegistry.get().resolve(SkyboxType.SKY, sky.texture());
            if (tex != null) {
               drawSphere(domeMatrix, tex, 100.0F * sky.scale(), a, sky.rotation(), SkyboxType.SKY, FadeController.animSample(sky));
            }
         }

         float daylight = daylightFactor(mc);
         float nightlight = 1.0F - daylight;
         renderFadingOut(domeMatrix, SkyboxType.DAY_SKY, daylight);
         renderFadingOut(domeMatrix, SkyboxType.NIGHT_SKY, nightlight);
         SkyboxEntry day = SkyboxTextures.activeEntry(SkyboxType.DAY_SKY);
         if (day != null && isLate(day) == drawingLate && daylight > 0.01F) {
            float a = daylight * FadeController.alphaForActive(day);
            Identifier tex = AnimatedTextureRegistry.get().resolve(SkyboxType.DAY_SKY, day.texture());
            if (tex != null) {
               drawSphere(domeMatrix, tex, 100.0F * day.scale(), a, day.rotation(), SkyboxType.DAY_SKY, FadeController.animSample(day));
            }
         }

         SkyboxEntry night = SkyboxTextures.activeEntry(SkyboxType.NIGHT_SKY);
         if (night != null && isLate(night) == drawingLate && nightlight > 0.01F) {
            float a = nightlight * FadeController.alphaForActive(night);
            Identifier tex = AnimatedTextureRegistry.get().resolve(SkyboxType.NIGHT_SKY, night.texture());
            if (tex != null) {
               drawSphere(domeMatrix, tex, 100.0F * night.scale(), a, night.rotation(), SkyboxType.NIGHT_SKY, FadeController.animSample(night));
            }
         }
      }
   }

   private static float daylightFactor(MinecraftClient mc) {
      if (mc.world.getDimension().skybox() != Skybox.OVERWORLD) {
         return SkyboxTextures.activeEntry(SkyboxType.NIGHT_SKY) != null ? 0.0F : 1.0F;
      } else {
         return SkyState.daylight();
      }
   }

   private static void renderFadingOut(Matrix4f m, SkyboxType type, float brightness) {
      SkyboxManager.FadingOut fading = SkyboxManager.get().getFadingOut(type);
      if (fading != null && !(brightness <= 0.01F)) {
         if (isLate(fading.entry()) == drawingLate) {
            float a = brightness * FadeController.alphaForFadingOut(fading);
            if (!(a <= 0.0F) && !FadeController.fadingOutFinished(fading)) {
               Identifier tex = AnimatedTextureRegistry.get().resolve(type, fading.entry().texture());
               if (tex != null) {
                  drawSphere(m, tex, 100.0F * fading.entry().scale(), a, fading.entry().rotation(), type, FadeController.animSample(fading));
               }
            }
         }
      }
   }

   private record CachedDome(GpuBuffer buffer, int indexCount) { }
   private static final Map<Long, CachedDome> DOMES = new HashMap<>();

   private static boolean drawCachedSphere(Matrix4f m, Identifier texture, float r, float alpha, float rotation, SkyboxType type, SkyAnim.Sample anim) {
      if (anim != null && !anim.equals(SkyAnim.Sample.IDLE)) return false;
      if (type != null && (SkyEffects.isRunning(type) || SkyEffects.frame(type) != null)) return false;
      float a = Math.min(1.0F, alpha);
      boolean smooth = type == null || SkyboxTextures.smoothEdges(type);
      long key = ((long) Float.floatToIntBits(r) * 31 + Float.floatToIntBits(rotation)) * 31 + Math.round(a * 255.0F);
      key = key * 2 + (smooth ? 1 : 0);
      CachedDome dome = DOMES.get(key);
      if (dome == null) {
         if (DOMES.size() >= 8) clearCachedDomes();
         BufferAllocator allocator = new BufferAllocator(SkyDomeMesh.quadCount() * 4 * VertexFormats.POSITION_TEXTURE_COLOR.getVertexSize());
         try {
            BufferBuilder vc = new BufferBuilder(allocator, DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            SkyDomeMesh.build(r, rotation, type, anim, a, (x, y, z, u, v, argb) -> vc.vertex(x, y, z).texture(u, v).color(argb));
            try (BuiltBuffer built = vc.endNullable()) {
               if (built == null) return true;
               int count = built.getDrawParameters().indexCount();
               GpuBuffer gpu = RenderSystem.getDevice().createBuffer(() -> "customskyboxes dome", GpuBuffer.USAGE_VERTEX, built.getBuffer());
               dome = new CachedDome(gpu, count);
               DOMES.put(key, dome);
            }
         } finally {
            allocator.close();
         }
      }
      drawBuffer(dome.buffer(), dome.indexCount(), texture, SKY_PIPELINE, WHITE, new Matrix4f(RenderSystem.getModelViewMatrix()).mul(m));
      return true;
   }

   public static void clearCachedDomes() {
      for (CachedDome d : DOMES.values()) d.buffer().close();
      DOMES.clear();
   }

   private static void drawBuffer(GpuBuffer vertexBuffer, int indexCount, Identifier texture, RenderPipeline pipeline, Vector4f colorModulator, Matrix4f modelView) {
      MinecraftClient mc = MinecraftClient.getInstance();
      AbstractTexture gpuTexture = mc.getTextureManager().getTexture(texture);
      if (gpuTexture == null || gpuTexture.getGlTextureView() == null) return;
      RenderSystem.ShapeIndexBuffer shapeIndex = RenderSystem.getSequentialBuffer(DrawMode.QUADS);
      GpuBuffer indexBuffer = shapeIndex.getIndexBuffer(indexCount);
      GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().write(modelView, colorModulator, new Vector3f(), new Matrix4f());
      Framebuffer framebuffer = mc.getFramebuffer();
      try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "customskyboxes_sky", framebuffer.getColorAttachmentView(), OptionalInt.empty(), framebuffer.getDepthAttachmentView(), OptionalDouble.empty())) {
         pass.setPipeline(pipeline);
         RenderSystem.bindDefaultUniforms(pass);
         pass.setUniform("DynamicTransforms", transforms);
         pass.bindTexture("Sampler0", gpuTexture.getGlTextureView(), gpuTexture.getSampler());
         pass.setVertexBuffer(0, vertexBuffer);
         pass.setIndexBuffer(indexBuffer, shapeIndex.getIndexType());
         pass.drawIndexed(0, 0, indexCount, 1);
      }
   }

   private static void drawSphere(Matrix4f m, Identifier texture, float r, float alpha, float rotation, SkyboxType type, SkyAnim.Sample anim) {
      if (!(alpha <= 0.0F)) {
         if (drawCachedSphere(m, texture, r, alpha, rotation, type, anim)) return;
         BufferAllocator allocator = new BufferAllocator(SkyDomeMesh.quadCount() * 4 * VertexFormats.POSITION_TEXTURE_COLOR.getVertexSize());
         BufferBuilder vc = new BufferBuilder(allocator, DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
         SkyDomeMesh.build(r, rotation, type, anim, Math.min(1.0F, alpha), (x, y, z, u, v, argb) -> vc.vertex(m, x, y, z).texture(u, v).color(argb));
         drawQuads(vc, allocator, texture, SKY_PIPELINE, WHITE);
         if (type != null && SkyEffects.isRunning(type)) {
            BufferAllocator glowAllocator = new BufferAllocator(SkyDomeMesh.quadCount() * 4 * VertexFormats.POSITION_TEXTURE_COLOR.getVertexSize());
            BufferBuilder glow = new BufferBuilder(glowAllocator, DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            SkyDomeMesh.buildGlow(r, rotation, type, anim, Math.min(1.0F, alpha), (x, y, z, u, v, argb) -> glow.vertex(m, x, y, z).texture(u, v).color(argb));
            drawQuads(glow, glowAllocator, texture, CELESTIAL_PIPELINE, WHITE);
         }
      }
   }

   public static boolean hasCustom() {
      return !SkyboxManager.get().activeLayers(SkyboxType.CUSTOM).isEmpty() || !SkyboxManager.get().fadingLayers(SkyboxType.CUSTOM).isEmpty();
   }

   public static boolean hasCelestialOverride() {
      return !SkyboxManager.get().activeLayers(SkyboxType.SUN).isEmpty() || !SkyboxManager.get().activeLayers(SkyboxType.MOON).isEmpty() || !SkyboxManager.get().fadingLayers(SkyboxType.SUN).isEmpty() || !SkyboxManager.get().fadingLayers(SkyboxType.MOON).isEmpty();
   }

   public static void renderCelestialLayers(Matrix4f m, float skyAngle, int moonPhase) {
      boolean late = anyLateDome();

      for(SkyboxType type : CELESTIAL_ORDER) {
         drawCelestialType(m, type, skyAngle, moonPhase, late ? 1 : 0);
      }

      if (late) {
         renderOvercomingDomes(m);
         cloudsAfterDomes = true;

         try {
            renderCloudPlaneIfOverride(m);
         } finally {
            cloudsAfterDomes = false;
         }

         for(SkyboxType type : CELESTIAL_ORDER) {
            drawCelestialType(m, type, skyAngle, moonPhase, 2);
         }
      }

      renderRift(m);
   }

   private static boolean anyLateDome() {
      for(SkyboxType type : SkyboxType.values()) {
         if (type.isDome()) {
            if (isLate(SkyboxTextures.activeEntry(type))) {
               return true;
            }

            SkyboxManager.FadingOut fading = SkyboxManager.get().getFadingOut(type);
            if (fading != null && isLate(fading.entry())) {
               return true;
            }
         }
      }

      return false;
   }

   private static long coveredUntil(SkyboxType element) {
      long newest = Long.MIN_VALUE;

      for(SkyboxType type : SkyboxType.values()) {
         if (type.isDome()) {
            SkyboxEntry entry = SkyboxTextures.activeEntry(type);
            if (isLate(entry) && entry.overcome().covers(element)) {
               newest = Math.max(newest, entry.appliedAtTick());
            }

            SkyboxManager.FadingOut fading = SkyboxManager.get().getFadingOut(type);
            if (fading != null && isLate(fading.entry()) && fading.entry().overcome().covers(element)) {
               newest = Math.max(newest, fading.entry().appliedAtTick());
            }
         }
      }

      return newest;
   }

   private static boolean inPass(SkyboxEntry entry, int pass, long coveredUntil) {
      if (pass == 0) {
         return true;
      } else {
         boolean below = entry.appliedAtTick() <= coveredUntil;
         return pass == 1 ? below : !below;
      }
   }

   private static void drawCelestialType(Matrix4f m, SkyboxType type, float skyAngle, int moonPhase) {
      drawCelestialType(m, type, skyAngle, moonPhase, 0);
   }

   private static void drawCelestialType(Matrix4f m, SkyboxType type, float skyAngle, int moonPhase, int pass) {
      long covered = pass == 0 ? Long.MIN_VALUE : coveredUntil(type);

      for(SkyboxEntry entry : SkyboxManager.get().activeLayers(type)) {
         if (inPass(entry, pass, covered)) {
            drawCelestial(m, type, entry, skyAngle, moonPhase, FadeController.alphaForActive(entry), FadeController.animSample(entry));
         }
      }

      for(SkyboxManager.FadingOut fading : SkyboxManager.get().fadingLayers(type)) {
         if (inPass(fading.entry(), pass, covered)) {
            drawCelestial(m, type, fading.entry(), skyAngle, moonPhase, FadeController.alphaForFadingOut(fading), FadeController.animSample(fading));
         }
      }
   }

   private static void drawCelestial(Matrix4f m, SkyboxType type, SkyboxEntry entry, float skyAngle, int moonPhase, float alpha, SkyAnim.Sample anim) {
      if (!(alpha <= 0.0F)) {
         Identifier modelTexture = SkyModels.textureFor(entry);
         Identifier texture = modelTexture != null ? modelTexture : (entry.texture() != null ? AnimatedTextureRegistry.get().resolve(type, entry.texture()) : (type == SkyboxType.MOON ? CelestialGlow.vanillaMoon() : CelestialGlow.vanillaSun()));
         if (texture != null) {
            BufferAllocator allocator = new BufferAllocator(CelestialMesh.vertexCount(entry) * VertexFormats.POSITION_TEXTURE_COLOR.getVertexSize());
            BufferBuilder vc = new BufferBuilder(allocator, DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            boolean[] any = new boolean[]{false};
            CelestialMesh.build(type, entry, skyAngle, alpha, anim, moonPhase, (x, y, z, u, v, argb) -> {
               any[0] = true;
               vc.vertex(m, x, y, z).texture(u, v).color(argb);
            });
            if (!any[0]) {
               allocator.close();
            } else {
               drawQuads(vc, allocator, texture, modelTexture == null && type != SkyboxType.CUSTOM ? CELESTIAL_PIPELINE : SKY_PIPELINE, WHITE);
            }
         }
      }
   }

   private static void drawModelMesh(Matrix4f m, Identifier texture, int vertexCount, Consumer<CelestialMesh.VertexSink> build) {
      BufferAllocator allocator = new BufferAllocator(vertexCount * VertexFormats.POSITION_TEXTURE_COLOR.getVertexSize());
      BufferBuilder vc = new BufferBuilder(allocator, DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
      boolean[] any = new boolean[]{false};
      build.accept((CelestialMesh.VertexSink)(x, y, z, u, v, argb) -> {
         any[0] = true;
         vc.vertex(m, x, y, z).texture(u, v).color(argb);
      });
      if (!any[0]) {
         allocator.close();
      } else {
         drawQuads(vc, allocator, texture, texture != null ? SKY_PIPELINE : CELESTIAL_PIPELINE, WHITE);
      }
   }

   private static void drawQuads(BufferBuilder buffer, BufferAllocator allocator, Identifier texture, RenderPipeline pipeline, Vector4f colorModulator) {
      MinecraftClient mc = MinecraftClient.getInstance();

      try {
         BuiltBuffer built = buffer.end();

         label146: {
            try {
               AbstractTexture gpuTexture = mc.getTextureManager().getTexture(texture);
               if (gpuTexture == null || gpuTexture.getGlTextureView() == null) {
                  break label146;
               }

               BuiltBuffer.DrawParameters params = built.getDrawParameters();
               GpuBuffer vertexBuffer = VertexFormats.POSITION_TEXTURE_COLOR.uploadImmediateVertexBuffer(built.getBuffer());
               RenderSystem.ShapeIndexBuffer shapeIndex = RenderSystem.getSequentialBuffer(DrawMode.QUADS);
               GpuBuffer indexBuffer = shapeIndex.getIndexBuffer(params.indexCount());
               GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().write(RenderSystem.getModelViewMatrix(), colorModulator, new Vector3f(), new Matrix4f());
               Framebuffer framebuffer = mc.getFramebuffer();
               RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "customskyboxes_sky", framebuffer.getColorAttachmentView(), OptionalInt.empty(), framebuffer.getDepthAttachmentView(), OptionalDouble.empty());

               try {
                  pass.setPipeline(pipeline);
                  RenderSystem.bindDefaultUniforms(pass);
                  pass.setUniform("DynamicTransforms", transforms);
                  pass.bindTexture("Sampler0", gpuTexture.getGlTextureView(), gpuTexture.getSampler());
                  pass.setVertexBuffer(0, vertexBuffer);
                  pass.setIndexBuffer(indexBuffer, shapeIndex.getIndexType());
                  pass.drawIndexed(0, 0, params.indexCount(), 1);
               } catch (Throwable var25) {
                  if (pass != null) {
                     try {
                        pass.close();
                     } catch (Throwable var24) {
                        var25.addSuppressed(var24);
                     }
                  }

                  throw var25;
               }

               if (pass != null) {
                  pass.close();
               }
            } catch (Throwable var26) {
               if (built != null) {
                  try {
                     built.close();
                  } catch (Throwable var23) {
                     var26.addSuppressed(var23);
                  }
               }

               throw var26;
            }

            if (built != null) {
               built.close();
            }

            return;
         }

         if (built != null) {
            built.close();
         }
      } finally {
         allocator.close();
      }
   }

   public static float cloudsOvercome() {
      float most = 0.0F;

      for(SkyboxType type : SkyboxType.values()) {
         for(SkyboxEntry entry : SkyboxManager.get().activeLayers(type)) {
            if (entry.overcome().clouds()) {
               most = Math.max(most, FadeController.alphaForActive(entry));
            }
         }

         for(SkyboxManager.FadingOut fading : SkyboxManager.get().fadingLayers(type)) {
            if (fading.entry().overcome().clouds()) {
               most = Math.max(most, FadeController.alphaForFadingOut(fading));
            }
         }
      }

      return Math.min(1.0F, most);
   }

   public static void renderRift(Matrix4f m) {
      for(SkyboxEntry entry : SkyboxManager.get().activeLayers(SkyboxType.RIFT)) {
         drawRift(m, entry, FadeController.alphaForActive(entry), FadeController.riftSample(entry));
      }

      for(SkyboxManager.FadingOut fading : SkyboxManager.get().fadingLayers(SkyboxType.RIFT)) {
         drawRift(m, fading.entry(), FadeController.alphaForFadingOut(fading), FadeController.riftSample(fading));
      }
   }

   public static boolean hasRift() {
      return !SkyboxManager.get().activeLayers(SkyboxType.RIFT).isEmpty() || !SkyboxManager.get().fadingLayers(SkyboxType.RIFT).isEmpty();
   }

   private static Identifier existingTexture(SkyboxType type, String name) {
      Identifier id = ResourcePackScanner.resolveTexture(type, name);
      if (id == null) {
         return null;
      } else {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc == null) {
            return null;
         } else {
            return mc.getResourceManager().getResource(id).isPresent() ? id : null;
         }
      }
   }

   private static void drawRift(Matrix4f m, SkyboxEntry entry, float alpha, RiftAnim.Sample anim) {
      if (!(alpha <= 0.0F)) {
         Identifier inside = null;
         String background = entry.background();
         if (background != null) {
            Identifier id = existingTexture(SkyboxType.RIFT, background);
            if (id == null) {
               id = existingTexture(SkyboxType.SKY, background);
            }

            if (id == null) {
               id = existingTexture(SkyboxType.NIGHT_SKY, background);
            }

            if (id != null) {
               inside = AnimatedTextureRegistry.get().resolve(SkyboxType.RIFT, id);
            }
         }

         if (inside == null && entry.texture() != null) {
            inside = AnimatedTextureRegistry.get().resolve(SkyboxType.RIFT, entry.texture());
         }

         if (inside != null) {
            drawRiftPass(m, entry, alpha, anim, true, inside, false);
         }

         SkyModels.Loaded model = SkyModels.forEntry(entry);
         float[][] frame = model == null ? null : RiftMesh.modelFrame(entry, anim, alpha);
         if (frame != null) {
            List<float[]> faces = new ArrayList();
            SkyModels.build(SkyboxType.RIFT, entry, model, frame[0], frame[1], frame[2][0], frame[2][1], (x, y, z, u, v, argb) -> faces.add(new float[]{x, y, z, u, v, Float.intBitsToFloat(argb)}));
            List<float[]> seen = new ArrayList();
            RiftMesh.clipToHole(entry, anim, alpha, faces, (x, y, z, u, v, argb) -> seen.add(new float[]{x, y, z, u, v, Float.intBitsToFloat(argb)}));
            if (!seen.isEmpty()) {
               drawModelMesh(m, model.texture(), seen.size(), (sink) -> {
                  for(float[] s : seen) {
                     sink.vertex(s[0], s[1], s[2], s[3], s[4], Float.floatToRawIntBits(s[5]));
                  }
               });
            }
         }

         drawRiftPass(m, entry, alpha, anim, false, RIM_TEXTURE, true);
      }
   }

   private static void drawRiftPass(Matrix4f m, SkyboxEntry entry, float alpha, RiftAnim.Sample anim, boolean inside, Identifier texture, boolean additive) {
      BufferAllocator allocator = new BufferAllocator(RiftMesh.quadCount(inside) * 4 * VertexFormats.POSITION_TEXTURE_COLOR.getVertexSize());
      BufferBuilder vc = new BufferBuilder(allocator, DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
      boolean[] any = new boolean[]{false};
      RiftMesh.build(entry, anim, alpha, inside, (x, y, z, u, v, argb) -> {
         any[0] = true;
         vc.vertex(m, x, y, z).texture(u, v).color(argb);
      });
      if (!any[0]) {
         allocator.close();
      } else {
         drawQuads(vc, allocator, texture, additive ? CELESTIAL_PIPELINE : SKY_PIPELINE, WHITE);
      }
   }

   private static float cloudScroll(float speed) {
      return (float)((double)((float)(SkyClock.ticks() % 4000000L) + SkyClock.partial()) * 2.5E-4 * (double)speed);
   }

   public static void renderCloudPlaneIfOverride(Matrix4f domeMatrix) {
      if (cloudsAfterDomes || !anyLateDome()) {
         SkyboxEntry clouds = SkyboxManager.get().resolve(SkyboxType.CLOUDS);
         float cloudFade;
         if (clouds != null) {
            cloudFade = FadeController.alphaForActive(clouds);
         } else {
            SkyboxManager.FadingOut fading = SkyboxManager.get().getFadingOut(SkyboxType.CLOUDS);
            if (fading == null || FadeController.fadingOutFinished(fading)) {
               return;
            }

            clouds = fading.entry();
            cloudFade = FadeController.alphaForFadingOut(fading);
         }

         Identifier tex = clouds.texture() == null ? VANILLA_CLOUDS : AnimatedTextureRegistry.get().resolve(SkyboxType.CLOUDS, clouds.texture());
         if (tex != null) {
            float alpha = cloudFade * clouds.opacity() * (1.0F - cloudsOvercome());
            if (!(alpha <= 0.002F)) {
               if (!(alpha <= 0.0F)) {
                  float tintR = 1.0F;
                  float tintG = 1.0F;
                  float tintB = 1.0F;
                  if (clouds.skyColor()) {
                     float dayFactor = SkyState.daylight();
                     float sunsetFactor = Math.max(0.0F, 1.0F - Math.abs(dayFactor - 0.3F) * 5.0F);
                     tintR = Math.min(1.0F, dayFactor * 0.95F + sunsetFactor * 0.3F + (1.0F - dayFactor) * 0.15F);
                     tintG = Math.min(1.0F, dayFactor * 0.95F + sunsetFactor * 0.1F + (1.0F - dayFactor) * 0.15F);
                     tintB = Math.min(1.0F, dayFactor * 1.0F + (1.0F - dayFactor) * 0.25F);
                  }

                  BufferAllocator allocator = new BufferAllocator(1600 * VertexFormats.POSITION_TEXTURE_COLOR.getVertexSize());
                  BufferBuilder vc = new BufferBuilder(allocator, DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
                  float y = 80.0F;
                  float size = 300.0F;
                  float time = cloudScroll(clouds.speed());
                  int ir = (int)(tintR * 255.0F);
                  int ig = (int)(tintG * 255.0F);
                  int ib = (int)(tintB * 255.0F);

                  for(int i = 0; i < 20; ++i) {
                     for(int j = 0; j < 20; ++j) {
                        float x0 = -size + 2.0F * size * (float)i / 20.0F;
                        float x1 = -size + 2.0F * size * (float)(i + 1) / 20.0F;
                        float z0 = -size + 2.0F * size * (float)j / 20.0F;
                        float z1 = -size + 2.0F * size * (float)(j + 1) / 20.0F;
                        float u0 = (float)i / 20.0F + time;
                        float u1 = (float)(i + 1) / 20.0F + time;
                        float fv0 = (float)j / 20.0F;
                        float fv1 = (float)(j + 1) / 20.0F;
                        int c00 = edgeAlpha(i, j, alpha) << 24 | ir << 16 | ig << 8 | ib;
                        int c10 = edgeAlpha(i + 1, j, alpha) << 24 | ir << 16 | ig << 8 | ib;
                        int c01 = edgeAlpha(i, j + 1, alpha) << 24 | ir << 16 | ig << 8 | ib;
                        int c11 = edgeAlpha(i + 1, j + 1, alpha) << 24 | ir << 16 | ig << 8 | ib;
                        vc.vertex(domeMatrix, x0, y, z0).texture(u0, fv0).color(c00);
                        vc.vertex(domeMatrix, x1, y, z0).texture(u1, fv0).color(c10);
                        vc.vertex(domeMatrix, x1, y, z1).texture(u1, fv1).color(c11);
                        vc.vertex(domeMatrix, x0, y, z1).texture(u0, fv1).color(c01);
                     }
                  }

                  drawQuads(vc, allocator, tex, SKY_PIPELINE, new Vector4f(1.0F, 1.0F, 1.0F, 1.0F));
               }
            }
         }
      }
   }

   private static int edgeAlpha(int gi, int gj, float baseAlpha) {
      float nx = 2.0F * (float)gi / 20.0F - 1.0F;
      float ny = 2.0F * (float)gj / 20.0F - 1.0F;
      float dist = (float)Math.sqrt((double)(nx * nx + ny * ny));
      float fade = 1.0F - smoothstep(0.5F, 1.0F, dist);
      return (int)(Math.min(1.0F, baseAlpha * fade) * 255.0F) & 255;
   }

   private static float smoothstep(float edge0, float edge1, float x) {
      float t = Math.max(0.0F, Math.min(1.0F, (x - edge0) / (edge1 - edge0)));
      return t * t * (3.0F - 2.0F * t);
   }

   static {
      SKY_PIPELINE = RenderPipeline.builder(new RenderPipeline.Snippet[0]).withLocation(Identifier.of("customskyboxes", "pipeline/sky_dome")).withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER).withUniform("Projection", UniformType.UNIFORM_BUFFER).withVertexShader("core/position_tex_color").withFragmentShader("core/position_tex_color").withSampler("Sampler0").withBlend(BlendFunction.TRANSLUCENT).withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false).withCull(false).withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, DrawMode.QUADS).build();
      CELESTIAL_PIPELINE = RenderPipeline.builder(new RenderPipeline.Snippet[0]).withLocation(Identifier.of("customskyboxes", "pipeline/sky_celestial")).withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER).withUniform("Projection", UniformType.UNIFORM_BUFFER).withVertexShader("core/position_tex_color").withFragmentShader("core/position_tex_color").withSampler("Sampler0").withBlend(BlendFunction.OVERLAY).withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false).withCull(false).withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, DrawMode.QUADS).build();
      IrisCompat.assign(SKY_PIPELINE, "SKY_TEXTURED");
      IrisCompat.assign(CELESTIAL_PIPELINE, "SKY_TEXTURED");
      WHITE = new Vector4f(1.0F, 1.0F, 1.0F, 1.0F);
      CELESTIAL_ORDER = new SkyboxType[]{SkyboxType.SUN, SkyboxType.MOON, SkyboxType.CUSTOM};
      RIM_TEXTURE = Identifier.of("customskyboxes", "textures/rift_rim.png");
      VANILLA_CLOUDS = Identifier.ofVanilla("textures/environment/clouds.png");
   }
}
