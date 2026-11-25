package dev.project.anatorius.utils.graphics;

import dev.project.anatorius.Anatorius;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.command.BatchingRenderCommandQueue;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueueImpl;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.util.math.ColorHelper;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class ImmediateRenderQueue extends OrderedRenderCommandQueueImpl {
    private final Batch batch;

    public ImmediateRenderQueue(VertexConsumerProvider provider) {
        this(provider, false, 0);
    }

    public ImmediateRenderQueue(VertexConsumerProvider provider, boolean seeThrough) {
        this(provider, seeThrough, 0);
    }

    public ImmediateRenderQueue(VertexConsumerProvider provider, boolean seeThrough, int fillColor) {
        this.batch = new Batch(this, provider, seeThrough, fillColor);
    }

    @Override
    public BatchingRenderCommandQueue getBatchingQueue(int order) {
        return batch;
    }

    private static final class Batch extends BatchingRenderCommandQueue {
        private final VertexConsumerProvider provider;
        private final boolean seeThrough;
        private final int fillColor;

        Batch(OrderedRenderCommandQueueImpl impl, VertexConsumerProvider provider, boolean seeThrough, int fillColor) {
            super(impl);
            this.provider = provider;
            this.seeThrough = seeThrough;
            this.fillColor = fillColor;
        }

        private RenderLayer layer(RenderLayer original) {
            return seeThrough ? EspLayers.ITEM_NODEPTH : original;
        }

        @Override
        public <S> void submitModel(Model<? super S> model, S state, MatrixStack matrices, RenderLayer renderLayer,
                                    int light, int overlay, int tintedColor, @Nullable Sprite sprite, int outlineColor,
                                    ModelCommandRenderer.@Nullable CrumblingOverlayCommand crumblingOverlay) {
            try {
                model.setAngles(state);
                VertexConsumer consumer = provider.getBuffer(layer(renderLayer));
                if (sprite != null) consumer = sprite.getTextureSpecificVertexConsumer(consumer);
                model.render(matrices, consumer, light, overlay, fillColor != 0 ? fillColor : tintedColor);
            } catch (Exception e) {
                Anatorius.LOGGER.trace("[Anatorius] immediate model render failed", e);
            }
        }

        @Override
        public void submitModelPart(ModelPart part, MatrixStack matrices, RenderLayer renderLayer, int light,
                                    int overlay, @Nullable Sprite sprite, boolean sheeted, boolean hasGlint,
                                    int tintedColor, ModelCommandRenderer.@Nullable CrumblingOverlayCommand crumblingOverlay,
                                    int outlineColor) {
            try {
                VertexConsumer consumer = provider.getBuffer(layer(renderLayer));
                if (sprite != null) consumer = sprite.getTextureSpecificVertexConsumer(consumer);
                part.render(matrices, consumer, light, overlay, fillColor != 0 ? fillColor : tintedColor);
            } catch (Exception e) {
                Anatorius.LOGGER.trace("[Anatorius] immediate model-part render failed", e);
            }
        }

        @Override
        public void submitItem(MatrixStack matrices, ItemDisplayContext displayContext, int light, int overlay,
                               int outlineColors, int[] tintLayers, List<BakedQuad> quads, RenderLayer renderLayer,
                               ItemRenderState.Glint glintType) {
            try {
                VertexConsumer consumer = provider.getBuffer(layer(renderLayer));
                MatrixStack.Entry entry = matrices.peek();
                for (BakedQuad quad : quads) {
                    int tint = fillColor != 0 ? fillColor
                            : (quad.tintIndex() >= 0 && quad.tintIndex() < tintLayers.length ? tintLayers[quad.tintIndex()] : -1);
                    float r = ColorHelper.getRed(tint) / 255.0f;
                    float g = ColorHelper.getGreen(tint) / 255.0f;
                    float b = ColorHelper.getBlue(tint) / 255.0f;
                    float a = fillColor != 0 ? ColorHelper.getAlpha(fillColor) / 255.0f : 1.0f;
                    consumer.quad(entry, quad, r, g, b, a, light, overlay);
                }
            } catch (Exception e) {
                Anatorius.LOGGER.trace("[Anatorius] immediate item render failed", e);
            }
        }
    }
}
