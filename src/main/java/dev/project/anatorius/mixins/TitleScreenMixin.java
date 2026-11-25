package dev.project.anatorius.mixins;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.utils.IMinecraft;
import dev.project.anatorius.utils.graphics.HalloweenEgg;
import dev.project.anatorius.utils.graphics.winter.WinterTheme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.SplashTextRenderer;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.*;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen implements IMinecraft {
    @Shadow private boolean doBackgroundFade;

    @Shadow private long backgroundFadeStart;

    @Shadow private SplashTextRenderer splashText;

    protected TitleScreenMixin(Text title) {
        super(title);
    }
    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void renderShaderBackground(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        try {
            if (false) {
            ci.cancel();
            }
        } catch (Throwable t) {
            System.err.println("[Anatorius-Shader] CRITICAL ERROR:");
            t.printStackTrace();
        }
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void halloweenSplashText(CallbackInfo ci) {
        if (splashText != null) return;
        if (HalloweenEgg.isActive()) splashText = new SplashTextRenderer(Text.literal("Trick or treat!"));
        else if (WinterTheme.isActive()) splashText = new SplashTextRenderer(Text.literal(WinterTheme.splashText()));
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/TitleScreen;renderPanoramaBackground(Lnet/minecraft/client/gui/DrawContext;F)V", shift = At.Shift.AFTER))
    private void renderHalloween(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        boolean halloween = HalloweenEgg.isActive();
        if (!halloween && !WinterTheme.isActive()) return;
        float alpha = 1.0F;
        if (doBackgroundFade) {
            float fade = MathHelper.clamp((float) (Util.getMeasuringTimeMs() - backgroundFadeStart) / 2000.0F, 0.0F, 1.0F);
            alpha = MathHelper.clampedMap(fade, 0.5F, 1.0F, 0.0F, 1.0F);
        }
        context.createNewRootLayer();
        if (halloween) HalloweenEgg.renderMenu(context, width, height, alpha);
        else WinterTheme.renderMenu(context, width, height, alpha);
        context.createNewRootLayer();
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void render$drawTextWithShadow(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo info) {
        if (Anatorius.UPDATE_STATUS.equalsIgnoreCase("none")) return;

        String primaryText = "";
        String secondaryText = "";
        Color color = Color.WHITE;

        if (Anatorius.UPDATE_STATUS.equalsIgnoreCase("update-available")) {
            secondaryText = "An update is available for Anatorius.";
            primaryText = "Please restart the game to apply changes.";
            color = Color.ORANGE;
        }

        if (Anatorius.UPDATE_STATUS.equalsIgnoreCase("failed-connection")) {
            secondaryText = "Failed to connect to Anatorius' servers.";
            primaryText = "Please make sure you have a working internet connection.";
            color = Color.RED;
        }

        if (Anatorius.UPDATE_STATUS.equalsIgnoreCase("failed")) {
            secondaryText = "Failed to update Anatorius.";
            primaryText = "Please make sure the auto-updater is working properly.";
            color = Color.RED;
        }

        if (Anatorius.UPDATE_STATUS.equalsIgnoreCase("up-to-date")) {
            primaryText = "Anatorius is on the latest version.";
        }

        if (primaryText.isEmpty()) return;

        float f = 1.0F;
        if (doBackgroundFade) {
            float g = (float) (Util.getMeasuringTimeMs() - backgroundFadeStart) / 2000.0F;
            if (g > 1.0F) {
                doBackgroundFade = false;
            } else {
                g = MathHelper.clamp(g, 0.0F, 1.0F);
                f = MathHelper.clampedMap(g, 0.5F, 1.0F, 0.0F, 1.0F);
            }
        }

        int i = MathHelper.ceil(f * 255.0F) << 24;

        if ((i & -67108864) != 0) {
            if (!secondaryText.isEmpty()) context.drawTextWithShadow(mc.textRenderer, secondaryText, width / 2 - mc.textRenderer.getWidth(secondaryText) / 2, height - 30, color.getRGB() | i);
            context.drawTextWithShadow(mc.textRenderer, primaryText, width / 2 - mc.textRenderer.getWidth(primaryText) / 2, height - 20, color.getRGB() | i);
        }
    }
}
