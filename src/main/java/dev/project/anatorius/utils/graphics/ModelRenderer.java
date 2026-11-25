package dev.project.anatorius.utils.graphics;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.modules.impl.visuals.EntityModifierModule;
import dev.project.anatorius.modules.impl.visuals.NoInterpolationModule;
import dev.project.anatorius.utils.mixins.IEndCrystalEntityRenderer;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.entity.model.EndCrystalEntityModel;
import dev.project.anatorius.utils.IMinecraft;
import dev.project.anatorius.utils.mixins.ILivingEntityRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EndCrystalEntityRenderer;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.EndCrystalEntityRenderState;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.*;

import java.awt.*;
import java.util.List;

public class ModelRenderer implements IMinecraft {
    private static Render render;
    private static Matrix4f matrix4f;
    private static Vec3d offset;
    private static Vec3d camera;
    private static Bloom bloom;
    private static float bloomCenterY;
    private static boolean depthTested;
    private static float lineWidth = 1.0f;

    public static void renderModel(Entity entity, float scale, float tickDelta, Render render) {
        renderModel(entity, false, scale, tickDelta, render);
    }

    public static void renderModel(Entity entity, float scale, float tickDelta, Render render, Bloom bloom, float lineWidth, boolean depthTested) {
        ModelRenderer.lineWidth = lineWidth;
        ModelRenderer.depthTested = depthTested;
        try {
            renderModel(entity, false, scale, tickDelta, render, 0.0, bloom);
        } finally {
            ModelRenderer.lineWidth = 1.0f;
            ModelRenderer.depthTested = false;
        }
    }

    public static void renderModel(Entity entity, boolean staticEntity, float scale, float tickDelta, Render render) {
        renderModel(entity, staticEntity, scale, tickDelta, render, 0.0);
    }

    public static void renderModel(Entity entity, boolean staticEntity, float scale, float tickDelta, Render render, double yOffset) {
        renderModel(entity, staticEntity, scale, tickDelta, render, yOffset, null);
    }

    public static void renderModel(Entity entity, boolean staticEntity, float scale, float tickDelta, Render render, double yOffset, Bloom bloom) {
        ModelRenderer.bloom = bloom;
        ModelRenderer.bloomCenterY = entity.getHeight() * 0.5f * scale;
        try {
            renderModelInternal(entity, scale, tickDelta, render, yOffset);
        } finally {
            ModelRenderer.bloom = null;
        }
    }

    private static void renderModelInternal(Entity entity, float scale, float tickDelta, Render render, double yOffset) {
        ModelRenderer.render = render;
        ModelRenderer.camera = mc.gameRenderer.getCamera().getCameraPos();

        if (!Anatorius.MODULE_MANAGER.getModule(NoInterpolationModule.class).isToggled()) ModelRenderer.offset = new Vec3d(MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX()), MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY()), MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ()));
        else ModelRenderer.offset = new Vec3d(entity.getX(), entity.getY(), entity.getZ());
        ModelRenderer.offset = ModelRenderer.offset.add(0.0, yOffset, 0.0);

        EntityRenderer<?, ?> renderer = mc.getEntityRenderDispatcher().getRenderer(entity);
        EntityRenderState renderState = ((EntityRenderer<Entity, ?>) renderer).getAndUpdateRenderState(entity, tickDelta);

        MatrixStack matrices = new MatrixStack();
        ModelRenderer.matrix4f = matrices.peek().getPositionMatrix();

        matrices.push();
        matrices.scale(scale, scale, scale);

        if (renderer instanceof LivingEntityRenderer<?, ?, ?> && renderState instanceof LivingEntityRenderState state) {
            ((ILivingEntityRenderer) renderer).anatorius$render(state, matrices, CustomVertexConsumerProvider.INSTANCE, 15);
        }

        if (renderer instanceof IEndCrystalEntityRenderer crystalRenderer && renderState instanceof EndCrystalEntityRenderState state) {
            EndCrystalEntityModel model = crystalRenderer.anatorius$getModel();
            EntityModifierModule modifier = Anatorius.MODULE_MANAGER.getModule(EntityModifierModule.class);
            float crystalScale = modifier != null && modifier.isToggled() && modifier.crystals.getValue() ? modifier.crystalScale.getValue().floatValue() : 1.0f;

            matrices.push();
            matrices.scale(2.0f * crystalScale, 2.0f * crystalScale, 2.0f * crystalScale);
            matrices.translate(0.0f, -0.5f, 0.0f);
            model.setAngles(state);
            model.render(matrices, CustomVertexConsumer.INSTANCE, 15, OverlayTexture.DEFAULT_UV, -1);
            matrices.pop();
        }

        matrices.pop();
    }

    private static class CustomVertexConsumerProvider implements VertexConsumerProvider {
        public static final CustomVertexConsumerProvider INSTANCE = new CustomVertexConsumerProvider();

        @Override
        public VertexConsumer getBuffer(RenderLayer layer) {
            return CustomVertexConsumer.INSTANCE;
        }
    }

    private static class CustomVertexConsumer implements VertexConsumer {
        public static final CustomVertexConsumer INSTANCE = new CustomVertexConsumer();
        private final float[] xs = new float[4];
        private final float[] ys = new float[4];
        private final float[] zs = new float[4];

        private int i = 0;

        @Override
        public VertexConsumer vertex(float x, float y, float z) {
            xs[i] = x;
            ys[i] = y;
            zs[i] = z;

            i++;

            if (i == 4) {
                List<Renderer3D.VertexCollection> quads = depthTested
                        ? (render.shine() ? Renderer3D.DEPTH_SHINE_QUADS : Renderer3D.DEPTH_QUADS)
                        : (render.shine() ? Renderer3D.SHINE_QUADS : Renderer3D.QUADS);
                List<Renderer3D.VertexCollection> debugLines = depthTested
                        ? (render.shine() ? Renderer3D.DEPTH_SHINE_DEBUG_LINES : Renderer3D.DEPTH_DEBUG_LINES)
                        : (render.shine() ? Renderer3D.SHINE_DEBUG_LINES : Renderer3D.DEBUG_LINES);

                if (render.fill) {
                    quads.add(new Renderer3D.VertexCollection(new Renderer3D.Vertex(matrix4f, (float) (offset.getX() + xs[0] - camera.getX()), (float) (offset.getY() + ys[0] - camera.getY()), (float) (offset.getZ() + zs[0] - camera.getZ()), render.fillColor().getRGB()),
                            new Renderer3D.Vertex(matrix4f, (float) (offset.getX() + xs[1] - camera.getX()), (float) (offset.getY() + ys[1] - camera.getY()), (float) (offset.getZ() + zs[1] - camera.getZ()), render.fillColor().getRGB()),
                            new Renderer3D.Vertex(matrix4f, (float) (offset.getX() + xs[2] - camera.getX()), (float) (offset.getY() + ys[2] - camera.getY()), (float) (offset.getZ() + zs[2] - camera.getZ()), render.fillColor().getRGB()),
                            new Renderer3D.Vertex(matrix4f, (float) (offset.getX() + xs[3] - camera.getX()), (float) (offset.getY() + ys[3] - camera.getY()), (float) (offset.getZ() + zs[3] - camera.getZ()), render.fillColor().getRGB())));
                }

                if (bloom != null) addBloom();

                if (render.outline) {
                    int rgb = render.outlineColor().getRGB();
                    Renderer3D.Vertex[] edge = new Renderer3D.Vertex[8];
                    for (int k = 0; k < 4; k++) {
                        int a = k, b = (k + 1) % 4;
                        edge[k * 2] = new Renderer3D.Vertex(matrix4f, (float) (offset.x + xs[a] - camera.getX()), (float) (offset.y + ys[a] - camera.getY()), (float) (offset.z + zs[a] - camera.getZ()), rgb, lineWidth);
                        edge[k * 2 + 1] = new Renderer3D.Vertex(matrix4f, (float) (offset.x + xs[b] - camera.getX()), (float) (offset.y + ys[b] - camera.getY()), (float) (offset.z + zs[b] - camera.getZ()), rgb, lineWidth);
                    }
                    debugLines.add(new Renderer3D.VertexCollection(edge));
                }

                i = 0;
            }

            return this;
        }

        private void addBloom() {
            float ax = xs[1] - xs[0], ay = ys[1] - ys[0], az = zs[1] - zs[0];
            float bx = xs[2] - xs[0], by = ys[2] - ys[0], bz = zs[2] - zs[0];
            float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
            float length = (float) java.lang.Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (length < 1.0E-6f) return;
            nx /= length; ny /= length; nz /= length;
            float cx = (xs[0] + xs[1] + xs[2] + xs[3]) * 0.25f, cy = (ys[0] + ys[1] + ys[2] + ys[3]) * 0.25f, cz = (zs[0] + zs[1] + zs[2] + zs[3]) * 0.25f;
            if (nx * cx + ny * (cy - bloomCenterY) + nz * cz < 0) { nx = -nx; ny = -ny; nz = -nz; }
            int layers = java.lang.Math.max(1, bloom.layers());
            float total = 0.0f;
            for (int k = 1; k <= layers; k++) total += (1.0f - (k - 0.5f) / layers) * (1.0f - (k - 0.5f) / layers);
            for (int k = 1; k <= layers; k++) {
                float d = bloom.radius() * k / layers;
                float falloff = 1.0f - (k - 0.5f) / layers;
                int alpha = java.lang.Math.round(bloom.color().getAlpha() * 0.5f * falloff * falloff / total);
                if (alpha <= 0) continue;
                int argb = (alpha << 24) | (bloom.color().getRGB() & 0xFFFFFF);
                Renderer3D.Vertex[] v = new Renderer3D.Vertex[4];
                for (int i = 0; i < 4; i++) {
                    float sx = xs[i] - cx, sy = ys[i] - cy, sz = zs[i] - cz;
                    float spread = (float) java.lang.Math.sqrt(sx * sx + sy * sy + sz * sz);
                    float f = spread < 1.0E-6f ? 0.0f : d / spread;
                    v[i] = new Renderer3D.Vertex(matrix4f, (float) (offset.x + xs[i] + nx * d + sx * f - camera.getX()),
                            (float) (offset.y + ys[i] + ny * d + sy * f - camera.getY()), (float) (offset.z + zs[i] + nz * d + sz * f - camera.getZ()), argb);
                }
                (depthTested ? Renderer3D.DEPTH_SHINE_QUADS : Renderer3D.SHINE_QUADS).add(new Renderer3D.VertexCollection(v));
            }
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer color(int argb) {
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer lineWidth(float width) {
            return this;
        }
    }

    private static class EmptyVertexConsumer implements VertexConsumer {
        private static final EmptyVertexConsumer INSTANCE = new EmptyVertexConsumer();

        @Override
        public VertexConsumer vertex(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer color(int argb) {
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer lineWidth(float width) {
            return this;
        }
    }

    public record Render(boolean fill, Color fillColor, boolean outline, Color outlineColor, boolean shine) { }

    public record Bloom(float radius, Color color, int layers) { }
}
