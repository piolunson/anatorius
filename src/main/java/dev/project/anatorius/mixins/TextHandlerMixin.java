package dev.project.anatorius.mixins;

import dev.project.anatorius.Anatorius;
import net.minecraft.client.font.TextHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextHandler.class)
public class TextHandlerMixin {
    @Shadow @Final @Mutable TextHandler.WidthRetriever widthRetriever;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void wrapWidthRetriever(TextHandler.WidthRetriever original, CallbackInfo info) {
        this.widthRetriever = (codePoint, style) -> Anatorius.FONT_MANAGER != null && Anatorius.FONT_MANAGER.globalFont()
                ? Anatorius.FONT_MANAGER.getFontRenderer().getCharWidth(codePoint, style)
                : original.getWidth(codePoint, style);
    }
}
