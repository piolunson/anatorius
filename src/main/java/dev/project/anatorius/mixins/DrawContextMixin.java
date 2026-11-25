package dev.project.anatorius.mixins;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.managers.FontManager;
import dev.project.anatorius.utils.IMinecraft;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.Color;

@Mixin(DrawContext.class)
public abstract class DrawContextMixin implements IMinecraft {
    @Inject(method = "drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;IIIZ)V", at = @At("HEAD"), cancellable = true)
    private void drawText$customFont(TextRenderer textRenderer, OrderedText text, int x, int y, int color, boolean shadow, CallbackInfo info) {
        FontManager fonts = Anatorius.FONT_MANAGER;
        if (fonts == null || textRenderer != mc.textRenderer || !fonts.globalFont()) return;
        info.cancel();
        if ((color >>> 24) == 0) return;

        Color c = new Color(color, true);
        if (shadow) fonts.drawTextWithShadow((DrawContext) (Object) this, text, x, y, c);
        else fonts.drawText((DrawContext) (Object) this, text, x, y, c);
    }
}
