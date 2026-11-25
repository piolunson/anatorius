package dev.project.anatorius.utils.graphics;

import dev.project.anatorius.utils.IMinecraft;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

import java.awt.*;

public class Renderer2D implements IMinecraft {
    public static final Matrix4f LAST_PROJECTION_MATRIX = new Matrix4f();
    public static final Matrix4f LAST_MODEL_MATRIX = new Matrix4f();
    public static final Matrix4f LAST_WORLD_MATRIX = new Matrix4f();

    public static float[] worldToScreen(double worldX, double worldY, double worldZ) {
        Vec3d cam = mc().gameRenderer.getCamera().getCameraPos();
        Matrix4f mvp = new Matrix4f(LAST_PROJECTION_MATRIX).mul(LAST_WORLD_MATRIX);

        Vector4f point = new Vector4f((float) (worldX - cam.x), (float) (worldY - cam.y), (float) (worldZ - cam.z), 1.0f);
        mvp.transform(point);
        if (point.w <= 0.0f) return null;

        float ndcX = point.x / point.w;
        float ndcY = point.y / point.w;
        if (ndcX < -1.15f || ndcX > 1.15f || ndcY < -1.15f || ndcY > 1.15f) return null;

        int guiW = mc().getWindow().getScaledWidth();
        int guiH = mc().getWindow().getScaledHeight();
        return new float[]{(ndcX + 1.0f) / 2.0f * guiW, (1.0f - ndcY) / 2.0f * guiH};
    }

    private static net.minecraft.client.MinecraftClient mc() {
        return net.minecraft.client.MinecraftClient.getInstance();
    }

    public static void renderQuad(DrawContext context, float left, float top, float right, float bottom, Color color) {
        context.fill((int) left, (int) top, (int) right, (int) bottom, color.getRGB());
    }

    public static void renderGradient(DrawContext context, float left, float top, float right, float bottom, Color startColor, Color endColor) {
        context.fillGradient((int) left, (int) top, (int) right, (int) bottom, startColor.getRGB(), endColor.getRGB());
    }

    public static void renderSidewaysGradient(DrawContext context, float left, float top, float right, float bottom, Color startColor, Color endColor) {
        context.fillGradient((int) left, (int) top, (int) right, (int) bottom, startColor.getRGB(), endColor.getRGB());
    }

    public static void renderOutline(DrawContext context, float left, float top, float right, float bottom, Color color) {
        int argb = color.getRGB();
        context.fill((int) left, (int) top, (int) right, (int) top + 1, argb);
        context.fill((int) left, (int) bottom - 1, (int) right, (int) bottom, argb);
        context.fill((int) left, (int) top, (int) left + 1, (int) bottom, argb);
        context.fill((int) right - 1, (int) top, (int) right, (int) bottom, argb);
    }

    public static void renderRoundedQuad(DrawContext context, float left, float top, float right, float bottom, float radius, Color color) {
        if (right <= left || bottom <= top || color.getAlpha() == 0) return;
        int scale = guiScale();
        int l = Math.round(left * scale), t = Math.round(top * scale), r = Math.round(right * scale), b = Math.round(bottom * scale);
        if (r <= l || b <= t) return;
        float rad = Math.max(0.0f, Math.min(radius * scale, Math.min(r - l, b - t) / 2.0f));
        int argb = color.getRGB();

        org.joml.Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.scale(1.0f / scale, 1.0f / scale);

        int band = (int) Math.ceil(rad);
        if (b - t - 2 * band > 0) context.fill(l, t + band, r, b - band, argb);
        for (int i = 0; i < band; i++) {
            int rowTop = t + i, rowBottom = b - 1 - i;
            if (rowTop > rowBottom) break;
            float dy = rad - (i + 0.5f);
            float inset = dy > 0 ? rad - (float) Math.sqrt(Math.max(0.0f, rad * rad - dy * dy)) : 0.0f;
            int full = (int) Math.ceil(inset);
            float cover = full - inset;
            int edge = cover > 0.02f ? (Math.round(color.getAlpha() * cover) << 24) | (argb & 0xFFFFFF) : 0;
            roundedRow(context, l, r, rowTop, full, argb, edge);
            if (rowBottom != rowTop) roundedRow(context, l, r, rowBottom, full, argb, edge);
        }

        matrices.popMatrix();
    }

    private static void roundedRow(DrawContext context, int l, int r, int row, int inset, int argb, int edge) {
        if (r - inset > l + inset) context.fill(l + inset, row, r - inset, row + 1, argb);
        if (edge != 0 && inset > 0) {
            context.fill(l + inset - 1, row, l + inset, row + 1, edge);
            context.fill(r - inset, row, r - inset + 1, row + 1, edge);
        }
    }

    public static void renderRoundedShadow(DrawContext context, float left, float top, float right, float bottom, float radius, int size, int alpha) {
        for (int i = size; i >= 1; i--) {
            float f = 1.0f - (i - 0.5f) / size;
            int a = Math.round(alpha * f * f / size * 2.2f);
            if (a <= 0) continue;
            renderRoundedQuad(context, left - i, top - i + 1.5f, right + i, bottom + i + 1.5f, radius + i, new Color(0, 0, 0, Math.min(255, a)));
        }
    }

    private static int guiScale() {
        return Math.max(1, mc.getWindow() == null ? 1 : mc.getWindow().getScaleFactor());
    }

    public static void renderRoundedOutline(DrawContext context, float left, float top, float right, float bottom, float radius, float thickness, Color color) {
        renderOutline(context, left, top, right, bottom, color);
    }

    public static void renderCircle(DrawContext context, float x, float y, float radius, Color color) {
        context.fill((int) (x - radius), (int) (y - radius), (int) (x + radius), (int) (y + radius), color.getRGB());
    }

    public static void renderArrow(DrawContext context, float x, float y, float width, float height, Color color) {
    }

    public static void renderArrowOutline(DrawContext context, float x, float y, float width, float height, Color color) {
    }

    public static void renderTexture(DrawContext context, float left, float top, float right, float bottom, Identifier identifier, Color color) {
        int w = (int) (right - left), h = (int) (bottom - top);
        context.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, identifier, (int) left, (int) top,
                0.0f, 0.0f, w, h, w, h, w, h, color.getRGB());
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float x1, float y1, float x2, float y2, int c1, int c2, int c3, int c4) {
        vc.vertex(m, x1, y1, 0.0f).color(c1);
        vc.vertex(m, x1, y2, 0.0f).color(c2);
        vc.vertex(m, x2, y2, 0.0f).color(c3);
        vc.vertex(m, x2, y1, 0.0f).color(c4);
    }

    public static void renderQuad(MatrixStack matrices, float left, float top, float right, float bottom, Color color) {
        Matrix4f m = matrices.peek().getPositionMatrix();
        VertexConsumerProvider.Immediate immediate = mc.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer vc = immediate.getBuffer(RenderLayers.debugQuads());
        int c = color.getRGB();
        quad(vc, m, left, top, right, bottom, c, c, c, c);
        immediate.draw(RenderLayers.debugQuads());
    }

    public static void renderGradient(MatrixStack matrices, float left, float top, float right, float bottom, Color startColor, Color endColor) {
        Matrix4f m = matrices.peek().getPositionMatrix();
        VertexConsumerProvider.Immediate immediate = mc.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer vc = immediate.getBuffer(RenderLayers.debugQuads());
        int s = startColor.getRGB(), e = endColor.getRGB();
        quad(vc, m, left, top, right, bottom, s, e, e, s);
        immediate.draw(RenderLayers.debugQuads());
    }

    public static void renderSidewaysGradient(MatrixStack matrices, float left, float top, float right, float bottom, Color startColor, Color endColor) {
        Matrix4f m = matrices.peek().getPositionMatrix();
        VertexConsumerProvider.Immediate immediate = mc.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer vc = immediate.getBuffer(RenderLayers.debugQuads());
        int s = startColor.getRGB(), e = endColor.getRGB();
        quad(vc, m, left, top, right, bottom, s, s, e, e);
        immediate.draw(RenderLayers.debugQuads());
    }

    public static void renderOutline(MatrixStack matrices, float left, float top, float right, float bottom, Color color) {
        renderQuad(matrices, left, top, left + 0.5f, bottom, color);
        renderQuad(matrices, right - 0.5f, top, right, bottom, color);
        renderQuad(matrices, left, top, right, top + 0.5f, color);
        renderQuad(matrices, left, bottom - 0.5f, right, bottom, color);
    }

    public static void renderCircle(MatrixStack matrices, float x, float y, float radius, Color color) {
        Matrix4f m = matrices.peek().getPositionMatrix();
        VertexConsumerProvider.Immediate immediate = mc.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer vc = immediate.getBuffer(RenderLayers.debugTriangleFan());
        int c = color.getRGB();
        vc.vertex(m, x, y, 0.0f).color(c);
        for (int i = 0; i <= 360; i += 8) {
            vc.vertex(m, (float) (x + Math.sin(Math.toRadians(i)) * radius), (float) (y + Math.cos(Math.toRadians(i)) * radius), 0.0f).color(c);
        }
        immediate.draw(RenderLayers.debugTriangleFan());
    }

    public static void renderArrow(MatrixStack matrices, float x, float y, float width, float height, Color color) {
        Matrix4f m = matrices.peek().getPositionMatrix();
        VertexConsumerProvider.Immediate immediate = mc.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer vc = immediate.getBuffer(RenderLayers.debugQuads());
        int c = color.getRGB();
        vc.vertex(m, x, y, 0.0f).color(c);
        vc.vertex(m, x - width, y + height, 0.0f).color(c);
        vc.vertex(m, x + width, y + height, 0.0f).color(c);
        vc.vertex(m, x, y, 0.0f).color(c);
        immediate.draw(RenderLayers.debugQuads());
    }

    public static void renderArrowOutline(MatrixStack matrices, float x, float y, float width, float height, Color color) {
        renderLine(matrices, x, y, x - width, y + height, color);
        renderLine(matrices, x - width, y + height, x + width, y + height, color);
        renderLine(matrices, x + width, y + height, x, y, color);
    }

    public static void renderLine(MatrixStack matrices, float x, float y, float targetX, float targetY, Color color) {
        Matrix4f m = matrices.peek().getPositionMatrix();
        VertexConsumerProvider.Immediate immediate = mc.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer vc = immediate.getBuffer(RenderLayers.lines());
        int c = color.getRGB();
        float nx = targetX - x, ny = targetY - y;
        float len = (float) Math.sqrt(nx * nx + ny * ny);
        if (len != 0.0f) { nx /= len; ny /= len; } else { ny = 1.0f; }
        vc.vertex(m, x, y, 0.0f).color(c).normal(nx, ny, 0.0f).lineWidth(1.0F);
        vc.vertex(m, targetX, targetY, 0.0f).color(c).normal(nx, ny, 0.0f).lineWidth(1.0F);
        immediate.draw(RenderLayers.lines());
    }

    public static void renderTexture(MatrixStack matrices, float left, float top, float right, float bottom, Identifier identifier, Color color) {
    }

    public static Vec3d project(Vec3d vec3d) {
        Vec3d camera = mc.gameRenderer.getCamera().getCameraPos();
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);

        Vector3f target = new Vector3f();
        Vector4f transform = new Vector4f((float) (vec3d.x - camera.x), (float) (vec3d.y - camera.y),
                (float) (vec3d.z - camera.z), 1.0f).mul(LAST_WORLD_MATRIX);

        Matrix4f matrixProj = new Matrix4f(LAST_PROJECTION_MATRIX);
        Matrix4f matrixModel = new Matrix4f(LAST_MODEL_MATRIX);

        matrixProj.mul(matrixModel).project(transform.x(), transform.y(), transform.z(), viewport, target);

        return new Vec3d(target.x / mc.getWindow().getScaleFactor(),
                (mc.getWindow().getHeight() - target.y) / mc.getWindow().getScaleFactor(), target.z);
    }
}
