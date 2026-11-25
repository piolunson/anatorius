package dev.project.anatorius.utils.font;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2f;

final class GlyphRun implements SimpleGuiElementRenderState {
    static final int STRIDE = 8;

    private final TextureSetup textureSetup;
    private final Matrix3x2f pose;
    private final float[] quads;
    private final int[] colors;
    private final int count;
    private final ScreenRect scissorArea;
    private final ScreenRect bounds;

    GlyphRun(TextureSetup textureSetup, Matrix3x2f pose, float[] quads, int[] colors, int count, ScreenRect scissorArea, ScreenRect bounds) {
        this.textureSetup = textureSetup;
        this.pose = pose;
        this.quads = quads;
        this.colors = colors;
        this.count = count;
        this.scissorArea = scissorArea;
        this.bounds = bounds;
    }

    @Override
    public void setupVertices(VertexConsumer consumer) {
        for (int i = 0; i < count; i++) {
            int o = i * STRIDE;
            float x1 = quads[o], y1 = quads[o + 1], x2 = quads[o + 2], y2 = quads[o + 3];
            float u1 = quads[o + 4], v1 = quads[o + 5], u2 = quads[o + 6], v2 = quads[o + 7];
            int color = colors[i];
            consumer.vertex(pose, x1, y1).texture(u1, v1).color(color);
            consumer.vertex(pose, x1, y2).texture(u1, v2).color(color);
            consumer.vertex(pose, x2, y2).texture(u2, v2).color(color);
            consumer.vertex(pose, x2, y1).texture(u2, v1).color(color);
        }
    }

    @Override
    public RenderPipeline pipeline() {
        return RenderPipelines.GUI_TEXTURED;
    }

    @Override
    public TextureSetup textureSetup() {
        return textureSetup;
    }

    @Override
    public ScreenRect scissorArea() {
        return scissorArea;
    }

    @Override
    public ScreenRect bounds() {
        return bounds;
    }
}
