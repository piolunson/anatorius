package dev.project.anatorius.utils.graphics;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.mixins.accessors.WorldRendererAccessor;
import dev.project.anatorius.utils.IMinecraft;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class Renderer3D implements IMinecraft {
    public static boolean RENDERING = false;

    public static List<VertexCollection> QUADS = new ArrayList<>();
    public static List<VertexCollection> DEBUG_LINES = new ArrayList<>();

    public static List<VertexCollection> SHINE_QUADS = new ArrayList<>();
    public static List<VertexCollection> SHINE_DEBUG_LINES = new ArrayList<>();

    public static List<VertexCollection> DEPTH_QUADS = new ArrayList<>();
    public static List<VertexCollection> DEPTH_DEBUG_LINES = new ArrayList<>();
    public static List<VertexCollection> DEPTH_SHINE_QUADS = new ArrayList<>();
    public static List<VertexCollection> DEPTH_SHINE_DEBUG_LINES = new ArrayList<>();

    public static void renderBox(MatrixStack matrices, Box box, Color color) {
        renderGradientBox(matrices, box, color, color);
    }

    public static void renderGradientBox(MatrixStack matrices, Box box, Color startColor, Color endColor) {
        if (!RENDERING) return;
        if (!isFrustumVisible(box)) return;

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        box = cameraTransform(box);

        QUADS.add(new VertexCollection(new Vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.minZ, startColor.getRGB()),
                new Vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.maxZ, startColor.getRGB()),
                new Vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.maxZ, startColor.getRGB()),
                new Vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.minZ, startColor.getRGB())));

        QUADS.add(new VertexCollection(new Vertex(matrix, (float) box.minX, (float) box.minY, (float) box.maxZ, endColor.getRGB()),
                new Vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.maxZ, endColor.getRGB()),
                new Vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.maxZ, startColor.getRGB()),
                new Vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.maxZ, startColor.getRGB())));

        QUADS.add(new VertexCollection(new Vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.maxZ, startColor.getRGB()),
                new Vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.maxZ, endColor.getRGB()),
                new Vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.minZ, endColor.getRGB()),
                new Vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.minZ, startColor.getRGB())));

        QUADS.add(new VertexCollection(new Vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.minZ, startColor.getRGB()),
                new Vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.minZ, endColor.getRGB()),
                new Vertex(matrix, (float) box.minX, (float) box.minY, (float) box.minZ, endColor.getRGB()),
                new Vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.minZ, startColor.getRGB())));

        QUADS.add(new VertexCollection(new Vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.minZ, startColor.getRGB()),
                new Vertex(matrix, (float) box.minX, (float) box.minY, (float) box.minZ, endColor.getRGB()),
                new Vertex(matrix, (float) box.minX, (float) box.minY, (float) box.maxZ, endColor.getRGB()),
                new Vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.maxZ, startColor.getRGB())));

        QUADS.add(new VertexCollection(new Vertex(matrix, (float) box.minX, (float) box.minY, (float) box.minZ, endColor.getRGB()),
                new Vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.minZ, endColor.getRGB()),
                new Vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.maxZ, endColor.getRGB()),
                new Vertex(matrix, (float) box.minX, (float) box.minY, (float) box.maxZ, endColor.getRGB())));
    }

    public static void renderBoxOutline(MatrixStack matrices, Box box, Color color) {
        renderGradientBoxOutline(matrices, box, color, color);
    }

    public static void renderGradientBoxOutline(MatrixStack matrices, Box box, Color startColor, Color endColor) {
        if (!RENDERING) return;
        if (!isFrustumVisible(box)) return;

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        box = cameraTransform(box);

        DEBUG_LINES.add(new VertexCollection(new Vertex(matrix, (float) box.minX, (float) box.minY, (float) box.minZ, endColor.getRGB()),
                new Vertex(matrix, (float) box.minX, (float) box.minY, (float) box.maxZ, endColor.getRGB()),
                new Vertex(matrix, (float) box.minX, (float) box.minY, (float) box.maxZ, endColor.getRGB()),
                new Vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.maxZ, endColor.getRGB())));

        DEBUG_LINES.add(new VertexCollection(new Vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.maxZ, endColor.getRGB()),
                new Vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.minZ, endColor.getRGB()),
                new Vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.minZ, endColor.getRGB()),
                new Vertex(matrix, (float) box.minX, (float) box.minY, (float) box.minZ, endColor.getRGB())));

        DEBUG_LINES.add(new VertexCollection(new Vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.minZ, startColor.getRGB()),
                new Vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.maxZ, startColor.getRGB()),
                new Vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.maxZ, startColor.getRGB()),
                new Vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.maxZ, startColor.getRGB())));

        DEBUG_LINES.add(new VertexCollection(new Vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.maxZ, startColor.getRGB()),
                new Vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.minZ, startColor.getRGB()),
                new Vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.minZ, startColor.getRGB()),
                new Vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.minZ, startColor.getRGB())));

        DEBUG_LINES.add(new VertexCollection(new Vertex(matrix, (float) box.minX, (float) box.minY, (float) box.minZ, endColor.getRGB()),
                new Vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.minZ, startColor.getRGB()),
                new Vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.minZ, endColor.getRGB()),
                new Vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.minZ, startColor.getRGB())));

        DEBUG_LINES.add(new VertexCollection(new Vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.maxZ, endColor.getRGB()),
                new Vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.maxZ, startColor.getRGB()),
                new Vertex(matrix, (float) box.minX, (float) box.minY, (float) box.maxZ, endColor.getRGB()),
                new Vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.maxZ, startColor.getRGB())));
    }

    public static void renderLine(MatrixStack matrices, Vec3d from, Vec3d to, Color color) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        from = cameraTransform(from);
        to = cameraTransform(to);

        DEBUG_LINES.add(new VertexCollection(new Vertex(matrix, (float) from.x, (float) from.y, (float) from.z, color.getRGB()),
                new Vertex(matrix, (float) to.x, (float) to.y, (float) to.z, color.getRGB())));
    }

    public static void renderFilledRing(MatrixStack matrices, Vec3d center, double innerRadius, double outerRadius,
                                        int segments, Color innerColor, Color outerColor) {
        if (!RENDERING) return;

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        Vec3d c = cameraTransform(center);
        int inRgb = innerColor.getRGB();
        int outRgb = outerColor.getRGB();

        for (int i = 0; i < segments; i++) {
            double a0 = (i / (double) segments) * Math.PI * 2.0;
            double a1 = ((i + 1) / (double) segments) * Math.PI * 2.0;
            double cos0 = Math.cos(a0), sin0 = Math.sin(a0);
            double cos1 = Math.cos(a1), sin1 = Math.sin(a1);

            Vertex in0 = new Vertex(matrix, (float) (c.x + cos0 * innerRadius), (float) c.y, (float) (c.z + sin0 * innerRadius), inRgb);
            Vertex out0 = new Vertex(matrix, (float) (c.x + cos0 * outerRadius), (float) c.y, (float) (c.z + sin0 * outerRadius), outRgb);
            Vertex out1 = new Vertex(matrix, (float) (c.x + cos1 * outerRadius), (float) c.y, (float) (c.z + sin1 * outerRadius), outRgb);
            Vertex in1 = new Vertex(matrix, (float) (c.x + cos1 * innerRadius), (float) c.y, (float) (c.z + sin1 * innerRadius), inRgb);

            QUADS.add(new VertexCollection(in0, out0, out1, in1));
        }
    }

    private static final double MAX_TEXT_DISTANCE = 64;

    public static void renderScaledText(MatrixStack matrices, String text, double x, double y, double z, int scale, boolean background, Color color) {
        Vec3d camera = mc.gameRenderer.getCamera().getCameraPos();
        double real = Math.sqrt(camera.squaredDistanceTo(x, y, z));
        if (real > MAX_TEXT_DISTANCE) {
            double k = MAX_TEXT_DISTANCE / real;
            x = camera.x + (x - camera.x) * k;
            y = camera.y + (y - camera.y) * k;
            z = camera.z + (z - camera.z) * k;
        }
        float distance = (float) Math.min(real, MAX_TEXT_DISTANCE);
        float scaling = 0.0018f + (scale / 10000.0f) * distance;
        if (distance <= 8.0) scaling = 0.0245f;

        renderText(matrices, text, x, y, z, scaling, background, color);
    }

    public static void renderText(MatrixStack matrices, String text, double x, double y, double z, float scaling, boolean background, Color color) {
        VertexConsumerProvider.Immediate vertexConsumers = mc.getBufferBuilders().getEntityVertexConsumers();

        Vec3d vec3d = new Vec3d(x - mc.gameRenderer.getCamera().getCameraPos().x, y - mc.gameRenderer.getCamera().getCameraPos().y, z - mc.gameRenderer.getCamera().getCameraPos().z);

        matrices.push();
        matrices.translate(vec3d.x, vec3d.y, vec3d.z);
        matrices.multiply(mc.gameRenderer.getCamera().getRotation());
        matrices.scale(scaling, -scaling, scaling);

        if (background) Renderer2D.renderQuad(matrices, -Anatorius.FONT_MANAGER.getWidth(text) / 2.0f - 2, -Anatorius.FONT_MANAGER.getHeight() - 2, Anatorius.FONT_MANAGER.getWidth(text) / 2.0f + 2, 1, new Color(0, 0, 0, 100));
        Anatorius.FONT_MANAGER.drawTextWithShadow(matrices, text, -Anatorius.FONT_MANAGER.getWidth(text) / 2, -Anatorius.FONT_MANAGER.getHeight(), vertexConsumers, color);

        matrices.pop();
    }

    public static void prepare() {
        QUADS = new ArrayList<>();
        DEBUG_LINES = new ArrayList<>();

        SHINE_QUADS = new ArrayList<>();
        SHINE_DEBUG_LINES = new ArrayList<>();

        DEPTH_QUADS = new ArrayList<>();
        DEPTH_DEBUG_LINES = new ArrayList<>();
        DEPTH_SHINE_QUADS = new ArrayList<>();
        DEPTH_SHINE_DEBUG_LINES = new ArrayList<>();

        RENDERING = true;
    }

    public static void drawDepthTested(List<VertexCollection> quads, List<VertexCollection> debugLines, boolean shine) {
        VertexConsumerProvider.Immediate immediate = mc.getBufferBuilders().getEntityVertexConsumers();
        if (!quads.isEmpty()) {
            RenderLayer layer = shine ? EspLayers.DEPTH_SHINE_QUADS : EspLayers.DEPTH_QUADS;
            VertexConsumer buffer = immediate.getBuffer(layer);
            for (VertexCollection collection : quads) collection.vertexQuad(buffer);
            immediate.draw(layer);
        }
        if (!debugLines.isEmpty()) {
            RenderLayer layer = shine ? EspLayers.DEPTH_SHINE_LINES : EspLayers.DEPTH_LINES;
            VertexConsumer buffer = immediate.getBuffer(layer);
            for (VertexCollection collection : debugLines) collection.vertexLine(buffer);
            immediate.draw(layer);
        }
    }

    public static void draw(List<VertexCollection> quads, List<VertexCollection> debugLines, boolean shine) {
        VertexConsumerProvider.Immediate immediate = mc.getBufferBuilders().getEntityVertexConsumers();

        if (!quads.isEmpty()) {
            RenderLayer layer = shine ? EspLayers.SHINE_QUADS : EspLayers.QUADS;
            VertexConsumer buffer = immediate.getBuffer(layer);
            for (VertexCollection collection : quads) collection.vertexQuad(buffer);
            immediate.draw(layer);
        }

        if (!debugLines.isEmpty()) {
            RenderLayer layer = shine ? EspLayers.SHINE_LINES : EspLayers.LINES;
            VertexConsumer buffer = immediate.getBuffer(layer);
            for (VertexCollection collection : debugLines) collection.vertexLine(buffer);
            immediate.draw(layer);
        }
    }

    public static net.minecraft.client.render.Frustum capturedFrustum;

    public static boolean isFrustumVisible(Box box) {
        net.minecraft.client.render.Frustum frustum = capturedFrustum;
        return frustum == null || frustum.isVisible(box);
    }

    private static Vec3d cameraTransform(Vec3d vec3d) {
        Vec3d camera = mc.gameRenderer.getCamera().getCameraPos();
        return new Vec3d(vec3d.x - camera.getX(), vec3d.y - camera.getY(), vec3d.z - camera.getZ());
    }

    private static Box cameraTransform(Box box) {
        Vec3d camera = mc.gameRenderer.getCamera().getCameraPos();
        return new Box(box.minX - camera.getX(), box.minY - camera.getY(), box.minZ - camera.getZ(), box.maxX - camera.getX(), box.maxY - camera.getY(), box.maxZ - camera.getZ());
    }

    public record VertexCollection(Vertex... vertices) {
        public void vertexQuad(VertexConsumer buffer) {
            for (Vertex vertex : vertices) buffer.vertex(vertex.matrix, vertex.x, vertex.y, vertex.z).color(vertex.color);
        }

        public void vertexLine(VertexConsumer buffer) {
            for (int i = 0; i + 1 < vertices.length; i += 2) {
                Vertex a = vertices[i], b = vertices[i + 1];
                float nx = b.x - a.x, ny = b.y - a.y, nz = b.z - a.z;
                float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (len != 0.0f) { nx /= len; ny /= len; nz /= len; } else { ny = 1.0f; }
                buffer.vertex(a.matrix, a.x, a.y, a.z).color(a.color).normal(nx, ny, nz).lineWidth(a.width);
                buffer.vertex(b.matrix, b.x, b.y, b.z).color(b.color).normal(nx, ny, nz).lineWidth(b.width);
            }
        }
    }

    public record Vertex(Matrix4f matrix, float x, float y, float z, int color, float width) {
        public Vertex(Matrix4f matrix, float x, float y, float z, int color) {
            this(matrix, x, y, z, color, 1.0F);
        }
    }
}
