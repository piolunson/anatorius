package dev.project.anatorius.mixins;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.managers.FontManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.render.state.TextGuiElementRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderState.class)
public abstract class GuiRenderStateMixin {
    @Inject(method = "addText", at = @At("HEAD"), cancellable = true)
    private void anatorius$customFont(TextGuiElementRenderState text, CallbackInfo info) {
        FontManager fonts = Anatorius.FONT_MANAGER;
        if (fonts == null || text.textRenderer != MinecraftClient.getInstance().textRenderer || !fonts.globalFont()) return;
        try {
            float shadow = text.shadow ? fonts.getShadowOffset() : 0.0f;
            fonts.getFontRenderer().drawState((GuiRenderState) (Object) this, text.matrix, text.clipBounds, text.orderedText,
                    text.x, text.y, text.color, shadow);
            info.cancel();
        } catch (Throwable t) {
        }
    }
}
