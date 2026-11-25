package dev.project.anatorius.utils.graphics;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.project.anatorius.Anatorius;
import dev.project.anatorius.utils.IMinecraft;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public final class ChunkOverlayRenderer implements IMinecraft {
    public static final int VERTEX_BYTES = 16;

    public enum GpuTier { LOW, HIGH }

    private static RenderPipeline fillPipeline(String name, boolean xray) {
        RenderPipeline.Builder b = RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                .withLocation("pipeline/anatorius_" + name)
                .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
                .withCull(false)
                .withDepthWrite(false)
                .withBlend(BlendFunction.TRANSLUCENT);
        if (xray) b.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST);
        return RenderPipelines.register(b.build());
    }

    private static RenderPipeline linePipeline(String name, boolean xray) {
        RenderPipeline.Builder b = RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                .withLocation("pipeline/anatorius_" + name)
                .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.DEBUG_LINES)
                .withCull(false)
                .withDepthWrite(false)
                .withBlend(BlendFunction.TRANSLUCENT);
        if (xray) b.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST);
        return RenderPipelines.register(b.build());
    }

    private static final class Pipelines {
        static final RenderPipeline FILL = fillPipeline("chunks_fill", false);
        static final RenderPipeline FILL_XRAY = fillPipeline("chunks_fill_xray", true);
        static final RenderPipeline LINES = linePipeline("chunks_lines", false);
        static final RenderPipeline LINES_XRAY = linePipeline("chunks_lines_xray", true);
    }

    private static GpuTier detectedTier;
    private static String detectedRenderer = "";

    private final String label;
    private GpuBuffer fillBuffer, lineBuffer;
    private int fillVertices, lineVertices;
    private double originX, originZ;
    private boolean warned;

    public ChunkOverlayRenderer(String label) {
        this.label = label;
    }

    public static GpuTier gpuTier() {
        if (detectedTier == null) {
            try {
                detectedRenderer = RenderSystem.getDevice().getRenderer();
            } catch (Throwable t) {
                detectedRenderer = "";
            }
            detectedTier = detectedRenderer.toLowerCase(Locale.ROOT).contains("rtx") ? GpuTier.HIGH : GpuTier.LOW;
            Anatorius.LOGGER.info("[Anatorius] GPU '{}' -> overlay quality {}", detectedRenderer, detectedTier);
        }
        return detectedTier;
    }

    public static String gpuName() {
        gpuTier();
        return detectedRenderer;
    }

    public void upload(ByteBuffer fill, int fillCount, ByteBuffer lines, int lineCount, double originX, double originZ) {
        closeBuffers();
        this.originX = originX;
        this.originZ = originZ;
        if (fillCount > 0) {
            fillBuffer = RenderSystem.getDevice().createBuffer(() -> label + " fill", GpuBuffer.USAGE_VERTEX, fill);
            fillVertices = fillCount;
        }
        if (lineCount > 0) {
            lineBuffer = RenderSystem.getDevice().createBuffer(() -> label + " lines", GpuBuffer.USAGE_VERTEX, lines);
            lineVertices = lineCount;
        }
    }

    public void draw(double y, boolean xray) {
        if (fillVertices == 0 && lineVertices == 0) return;
        try {
            Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
            Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix())
                    .translate((float) (originX - cam.x), (float) (y - cam.y), (float) (originZ - cam.z));
            GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
                    .write(modelView, new Vector4f(1f, 1f, 1f, 1f), new Vector3f(), new Matrix4f());
            Framebuffer fb = mc.getFramebuffer();
            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> label,
                    fb.getColorAttachmentView(), OptionalInt.empty(), fb.getDepthAttachmentView(), OptionalDouble.empty())) {
                if (fillVertices > 0) drawBuffer(pass, xray ? Pipelines.FILL_XRAY : Pipelines.FILL, fillBuffer, fillVertices, transforms);
                if (lineVertices > 0) drawBuffer(pass, xray ? Pipelines.LINES_XRAY : Pipelines.LINES, lineBuffer, lineVertices, transforms);
            }
        } catch (Throwable t) {
            if (!warned) {
                warned = true;
                Anatorius.LOGGER.warn("[Anatorius] {} draw failed: {}", label, t.toString());
            }
        }
    }

    private static void drawBuffer(RenderPass pass, RenderPipeline pipeline, GpuBuffer buffer, int count, GpuBufferSlice transforms) {
        pass.setPipeline(pipeline);
        RenderSystem.bindDefaultUniforms(pass);
        pass.setUniform("DynamicTransforms", transforms);
        pass.setVertexBuffer(0, buffer);
        pass.draw(0, count);
    }

    public static void vertex(ByteBuffer b, float x, float y, float z, int argb) {
        b.putFloat(x).putFloat(y).putFloat(z)
                .put((byte) (argb >> 16)).put((byte) (argb >> 8)).put((byte) argb).put((byte) (argb >>> 24));
    }

    public static void quad(ByteBuffer b, float x0, float y0, float z0, float x1, float y1, float z1,
                            float x2, float y2, float z2, float x3, float y3, float z3, int argbA, int argbB) {
        vertex(b, x0, y0, z0, argbA); vertex(b, x1, y1, z1, argbA); vertex(b, x2, y2, z2, argbB);
        vertex(b, x0, y0, z0, argbA); vertex(b, x2, y2, z2, argbB); vertex(b, x3, y3, z3, argbB);
    }

    public static void wall(ByteBuffer b, float xa, float za, float xb, float zb, float y0, float h, int bottom, int top) {
        quad(b, xa, y0, za, xb, y0, zb, xb, y0 + h, zb, xa, y0 + h, za, bottom, top);
    }

    public static int argb(java.awt.Color c, float scale) {
        int a = Math.max(0, Math.min(255, Math.round(c.getAlpha() * scale)));
        return (a << 24) | (c.getRed() << 16) | (c.getGreen() << 8) | c.getBlue();
    }

    public boolean isEmpty() {
        return fillVertices == 0 && lineVertices == 0;
    }

    private void closeBuffers() {
        if (fillBuffer != null) fillBuffer.close();
        if (lineBuffer != null) lineBuffer.close();
        fillBuffer = lineBuffer = null;
        fillVertices = lineVertices = 0;
    }

    public void close() {
        closeBuffers();
        warned = false;
    }
}
