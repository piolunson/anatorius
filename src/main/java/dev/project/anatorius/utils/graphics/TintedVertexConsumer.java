package dev.project.anatorius.utils.graphics;

import net.minecraft.client.render.VertexConsumer;

public final class TintedVertexConsumer implements VertexConsumer {
    private final VertexConsumer delegate;
    private final int red, green, blue, alpha;

    public TintedVertexConsumer(VertexConsumer delegate, int argb) {
        this.delegate = delegate;
        this.red = argb >> 16 & 0xFF;
        this.green = argb >> 8 & 0xFF;
        this.blue = argb & 0xFF;
        this.alpha = argb >>> 24;
    }

    @Override public VertexConsumer vertex(float x, float y, float z) { delegate.vertex(x, y, z); return this; }
    @Override public VertexConsumer color(int r, int g, int b, int a) { delegate.color(red, green, blue, a * alpha / 255); return this; }
    @Override public VertexConsumer color(int argb) { return color(argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF, argb >>> 24); }
    @Override public VertexConsumer texture(float u, float v) { delegate.texture(u, v); return this; }
    @Override public VertexConsumer overlay(int u, int v) { delegate.overlay(u, v); return this; }
    @Override public VertexConsumer light(int u, int v) { delegate.light(u, v); return this; }
    @Override public VertexConsumer normal(float x, float y, float z) { delegate.normal(x, y, z); return this; }
    @Override public VertexConsumer lineWidth(float width) { delegate.lineWidth(width); return this; }
}
