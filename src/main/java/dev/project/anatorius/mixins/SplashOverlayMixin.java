package dev.project.anatorius.mixins;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.project.anatorius.utils.graphics.HalloweenEgg;
import dev.project.anatorius.utils.graphics.SplashShader;
import dev.project.anatorius.utils.graphics.winter.WinterTheme;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.resource.ResourceReload;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Optional;
import java.util.function.Consumer;

@Mixin(SplashOverlay.class)
public abstract class SplashOverlayMixin {
    @Final @Shadow private boolean reloading;
    @Shadow private float progress;
    @Shadow private long reloadCompleteTime = -1L;
    @Shadow private long reloadStartTime = -1L;
    @Final @Shadow private ResourceReload reload;
    @Final @Shadow private Consumer<Optional<Throwable>> exceptionHandler;

    @Overwrite
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        long now = Util.getMeasuringTimeMs();

        if (reloading && reloadStartTime == -1L) {
            reloadStartTime = now;
        }

        float fadeIn  = reloadStartTime > -1L    ? (float)(now - reloadStartTime)    / 500.0f  : -1f;
        float fadeOut = reloadCompleteTime > -1L  ? (float)(now - reloadCompleteTime) / 1000.0f : -1f;

        if (fadeOut >= 1.0f) {
            if (mc.currentScreen != null)
                mc.currentScreen.render(context, 0, 0, delta);
        } else if (reloading && mc.currentScreen != null && fadeIn < 1.0f) {
            mc.currentScreen.render(context, mouseX, mouseY, delta);
        }

        this.progress = MathHelper.clamp(
            this.progress * 0.95f + this.reload.getProgress() * 0.05f,
            0.0f, 1.0f
        );

        boolean halloween = HalloweenEgg.isActive();
        if (halloween || WinterTheme.isActive()) {
            float alpha = fadeOut >= 1.0f ? 1.0f - MathHelper.clamp(fadeOut - 1.0f, 0.0f, 1.0f)
                    : reloading ? MathHelper.clamp(fadeIn, 0.15f, 1.0f) : 1.0f;
            context.createNewRootLayer();
            if (halloween) HalloweenEgg.renderSplash(context, sw, sh, alpha);
            else WinterTheme.renderSplash(context, sw, sh, alpha);
            context.createNewRootLayer();
            int cx = sw / 2;
            int cy = sh / 2;
            double logoHeight = Math.min(sw * 0.75, sh) * 0.25;
            int halfHeight = (int) (logoHeight * 0.5);
            int halfWidth = (int) (logoHeight * 2.0);
            int color = halloween ? HalloweenEgg.logoColor(alpha) : WinterTheme.logoColor(alpha);
            context.drawTexture(RenderPipelines.MOJANG_LOGO, SplashOverlay.LOGO, cx - halfWidth, cy - halfHeight, -0.0625f, 0.0f, halfWidth, (int) logoHeight, 120, 60, 120, 120, color);
            context.drawTexture(RenderPipelines.MOJANG_LOGO, SplashOverlay.LOGO, cx, cy - halfHeight, 0.0625f, 60.0f, halfWidth, (int) logoHeight, 120, 60, 120, 120, color);
            if (fadeOut < 1.0f) {
                int bar = (int) (sh * 0.8325);
                float barAlpha = alpha * (1.0f - MathHelper.clamp(fadeOut, 0.0f, 1.0f));
                if (halloween) HalloweenEgg.progressBar(context, cx - halfWidth, bar - 5, cx + halfWidth, bar + 5, this.progress, barAlpha);
                else WinterTheme.progressBar(context, cx - halfWidth, bar - 5, cx + halfWidth, bar + 5, this.progress, barAlpha);
            }
        }

        if (fadeOut >= 2.0f) {
            mc.setOverlay(null);
            SplashShader.getInstance().cleanup();
        }

        if (reloadCompleteTime == -1L && reload.isComplete()
                && (!reloading || fadeIn >= 2.0f)) {
            try {
                reload.throwException();
                exceptionHandler.accept(Optional.empty());
            } catch (Throwable t) {
                exceptionHandler.accept(Optional.of(t));
            }
            reloadCompleteTime = now;
            if (mc.currentScreen != null)
                mc.currentScreen.resize(sw, sh);
        }
    }
}
