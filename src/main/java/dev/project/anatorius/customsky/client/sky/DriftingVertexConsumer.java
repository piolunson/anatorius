package dev.project.anatorius.customsky.client.sky;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.VertexConsumer;

@Environment(EnvType.CLIENT)
public final class DriftingVertexConsumer implements VertexConsumer {
   private final VertexConsumer delegate;

   public DriftingVertexConsumer(VertexConsumer delegate) {
      this.delegate = delegate;
   }

   public VertexConsumer vertex(float x, float y, float z) {
      this.delegate.vertex(x + WeatherDrift.driftX((double)y), y, z + WeatherDrift.driftZ((double)y));
      return this;
   }

   public VertexConsumer color(int red, int green, int blue, int alpha) {
      this.delegate.color(red, green, blue, alpha);
      return this;
   }

   public VertexConsumer texture(float u, float v) {
      this.delegate.texture(u, v);
      return this;
   }

   public VertexConsumer overlay(int u, int v) {
      this.delegate.overlay(u, v);
      return this;
   }

   public VertexConsumer light(int u, int v) {
      this.delegate.light(u, v);
      return this;
   }

   public VertexConsumer normal(float x, float y, float z) {
      this.delegate.normal(x, y, z);
      return this;
   }

   public VertexConsumer lineWidth(float width) {
      this.delegate.lineWidth(width);
      return this;
   }

   public VertexConsumer color(int argb) {
      this.delegate.color(argb);
      return this;
   }
}
