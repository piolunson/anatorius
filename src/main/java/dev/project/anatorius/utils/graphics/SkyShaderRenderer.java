package dev.project.anatorius.utils.graphics;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import dev.project.anatorius.Anatorius;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.OptionalDouble;
import java.util.OptionalInt;

public final class SkyShaderRenderer {
    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder()
                    .withLocation("pipeline/anatorius_sky_shader")
                    .withVertexShader(Identifier.of("anatorius", "core/sky_shader"))
                    .withFragmentShader(Identifier.of("anatorius", "core/sky_shader"))
                    .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                    .withVertexFormat(VertexFormats.POSITION, DrawMode.QUADS)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build());

    private static final long CLOCK_ORIGIN = System.nanoTime();
    private static GpuBuffer quad;
    private static boolean failed;

    private SkyShaderRenderer() {}

    public static void init() {}

    public static void render(int style, int argb, float speed, float parallax) {
        if (failed) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        try {
            if (quad == null) quad = buildQuad();
            if (quad == null) return;

            Framebuffer fb = mc.getFramebuffer();
            float width = fb.textureWidth, height = Math.max(1, fb.textureHeight);
            float viewX = 0.0f, viewY = 0.0f;
            if (mc.gameRenderer != null) {
                viewX = mc.gameRenderer.getCamera().getYaw() / 180.0f * (width / height) * parallax;
                viewY = -mc.gameRenderer.getCamera().getPitch() / 90.0f * parallax;
            }
            float time = (float) (((System.nanoTime() - CLOCK_ORIGIN) / 1.0E9 * speed) % 4096.0);

            Vector4f color = new Vector4f(((argb >> 16) & 0xFF) / 255.0f, ((argb >> 8) & 0xFF) / 255.0f, (argb & 0xFF) / 255.0f, ((argb >>> 24) & 0xFF) / 255.0f);
            Matrix4f params = new Matrix4f().m00(style).m01(width).m02(height);
            GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().write(new Matrix4f(), color, new Vector3f(viewX, viewY, time), params);

            RenderSystem.ShapeIndexBuffer shapeIndex = RenderSystem.getSequentialBuffer(DrawMode.QUADS);
            GpuBuffer indexBuffer = shapeIndex.getIndexBuffer(6);
            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "anatorius sky shader",
                    fb.getColorAttachmentView(), OptionalInt.empty(), fb.getDepthAttachmentView(), OptionalDouble.empty())) {
                pass.setPipeline(PIPELINE);
                pass.setUniform("DynamicTransforms", transforms);
                pass.setVertexBuffer(0, quad);
                pass.setIndexBuffer(indexBuffer, shapeIndex.getIndexType());
                pass.drawIndexed(0, 0, 6, 1);
            }
        } catch (Throwable t) {
            failed = true;
            Anatorius.LOGGER.warn("[Anatorius] sky shader disabled: {}", t.toString());
        }
    }

    private static GpuBuffer buildQuad() {
        BufferAllocator allocator = new BufferAllocator(4 * VertexFormats.POSITION.getVertexSize());
        try {
            BufferBuilder builder = new BufferBuilder(allocator, DrawMode.QUADS, VertexFormats.POSITION);
            builder.vertex(-1.0f, -1.0f, 0.0f);
            builder.vertex(1.0f, -1.0f, 0.0f);
            builder.vertex(1.0f, 1.0f, 0.0f);
            builder.vertex(-1.0f, 1.0f, 0.0f);
            try (BuiltBuffer built = builder.endNullable()) {
                if (built == null) return null;
                return RenderSystem.getDevice().createBuffer(() -> "anatorius sky quad", GpuBuffer.USAGE_VERTEX, built.getBuffer());
            }
        } finally {
            allocator.close();
        }
    }
}
