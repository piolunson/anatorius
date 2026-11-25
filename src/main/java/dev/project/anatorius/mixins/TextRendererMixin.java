package dev.project.anatorius.mixins;

import dev.project.anatorius.Anatorius;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TextRenderer.class)
public class TextRendererMixin {
    @Inject(method = "draw(Ljava/lang/String;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/font/TextRenderer$TextLayerType;II)V", at = @At("HEAD"), cancellable = true)
    private void drawLayer$String(String text, float x, float y, int color, boolean shadow, Matrix4f matrix, VertexConsumerProvider vertexConsumerProvider, TextRenderer.TextLayerType layerType, int backgroundColor, int light, CallbackInfo info) {
        if (!global()) return;
        int width = Anatorius.FONT_MANAGER.getWidth(text);
        drawCustom(matrix, vertexConsumerProvider, layerType, backgroundColor, light, x, y, width, (sx, sy, shadowPass) ->
                Anatorius.FONT_MANAGER.getFontRenderer().drawString(matrix, vertexConsumerProvider, text, sx, sy, color, shadowPass, layerType == TextRenderer.TextLayerType.SEE_THROUGH, light), shadow);
        info.cancel();
    }

    @Inject(method = "draw(Lnet/minecraft/text/OrderedText;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/font/TextRenderer$TextLayerType;II)V", at = @At("HEAD"), cancellable = true)
    private void drawLayer$OrderedText(OrderedText text, float x, float y, int color, boolean shadow, Matrix4f matrix, VertexConsumerProvider vertexConsumerProvider, TextRenderer.TextLayerType layerType, int backgroundColor, int light, CallbackInfo info) {
        if (!global()) return;
        int width = Anatorius.FONT_MANAGER.getWidth(text);
        drawCustom(matrix, vertexConsumerProvider, layerType, backgroundColor, light, x, y, width, (sx, sy, shadowPass) ->
                Anatorius.FONT_MANAGER.getFontRenderer().drawText(matrix, vertexConsumerProvider, text, sx, sy, color, shadowPass, layerType == TextRenderer.TextLayerType.SEE_THROUGH, light), shadow);
        info.cancel();
    }

    private static boolean global() {
        return Anatorius.FONT_MANAGER != null && Anatorius.FONT_MANAGER.globalFont();
    }

    private static void drawCustom(Matrix4f matrix, VertexConsumerProvider provider, TextRenderer.TextLayerType layerType, int backgroundColor, int light,
                                   float x, float y, int width, Pass pass, boolean shadow) {
        if (backgroundColor != 0) {
            VertexConsumer consumer = provider.getBuffer(layerType == TextRenderer.TextLayerType.SEE_THROUGH ? RenderLayers.textBackgroundSeeThrough() : RenderLayers.textBackground());
            float x1 = x - 1, y1 = y - 1, x2 = x + width + 1, y2 = y + Anatorius.FONT_MANAGER.getHeight();
            consumer.vertex(matrix, x1, y1, 0.0f).color(backgroundColor).light(light);
            consumer.vertex(matrix, x1, y2, 0.0f).color(backgroundColor).light(light);
            consumer.vertex(matrix, x2, y2, 0.0f).color(backgroundColor).light(light);
            consumer.vertex(matrix, x2, y1, 0.0f).color(backgroundColor).light(light);
        }
        float offset = Anatorius.FONT_MANAGER.getShadowOffset();
        if (shadow && offset != 0.0f) pass.draw(x + offset, y + offset, true);
        pass.draw(x, y, false);
    }

    @FunctionalInterface
    private interface Pass {
        void draw(float x, float y, boolean shadow);
    }

    @Inject(method = "getWidth(Ljava/lang/String;)I", at = @At("HEAD"), cancellable = true)
    private void getWidth(String text, CallbackInfoReturnable<Integer> info) {
        if (global()) {
            info.setReturnValue(Anatorius.FONT_MANAGER.getWidth(text));
        }
    }

    @Inject(method = "getWidth(Lnet/minecraft/text/StringVisitable;)I", at = @At("HEAD"), cancellable = true)
    private void getWidth(StringVisitable text, CallbackInfoReturnable<Integer> info) {
        if (global()) {
            info.setReturnValue(Anatorius.FONT_MANAGER.getWidth(net.minecraft.util.Language.getInstance().reorder(text)));
        }
    }

    @Inject(method = "getWidth(Lnet/minecraft/text/OrderedText;)I", at = @At("HEAD"), cancellable = true)
    private void getWidth(OrderedText text, CallbackInfoReturnable<Integer> info) {
        if (global()) {
            info.setReturnValue(Anatorius.FONT_MANAGER.getWidth(text));
        }
    }
}
