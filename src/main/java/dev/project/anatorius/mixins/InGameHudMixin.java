package dev.project.anatorius.mixins;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.impl.RenderOverlayEvent;
import dev.project.anatorius.modules.impl.core.HUDModule;
import dev.project.anatorius.modules.impl.visuals.CrosshairModule;
import dev.project.anatorius.modules.impl.visuals.NoRenderModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class InGameHudMixin {
    @Shadow @Final private MinecraftClient client;

    private boolean anatorius$parallaxPushed;

    @Inject(method = "render", at = @At("HEAD"))
    private void render(DrawContext context, RenderTickCounter tickCounter, CallbackInfo info) {
        anatorius$parallaxPushed = false;
        if (Anatorius.MODULE_MANAGER != null) {
            HUDModule hud = Anatorius.MODULE_MANAGER.getModule(HUDModule.class);
            float[] offset = hud == null ? null : hud.parallaxOffset();
            if (offset != null && (offset[0] != 0.0f || offset[1] != 0.0f)) {
                context.getMatrices().pushMatrix();
                context.getMatrices().translate(offset[0], offset[1]);
                anatorius$parallaxPushed = true;
            }
        }
        if (client.options.hudHidden) return;
        Anatorius.EVENT_HANDLER.post(new RenderOverlayEvent(context, tickCounter.getTickProgress(true)));
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void render$parallaxPop(DrawContext context, RenderTickCounter tickCounter, CallbackInfo info) {
        if (anatorius$parallaxPushed) {
            context.getMatrices().popMatrix();
            anatorius$parallaxPushed = false;
        }
    }

    @Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true)
    private void renderStatusEffectOverlay(DrawContext context, RenderTickCounter tickCounter, CallbackInfo info) {
        if (Anatorius.MODULE_MANAGER != null && Anatorius.MODULE_MANAGER.getModule(HUDModule.class).isToggled() && Anatorius.MODULE_MANAGER.getModule(HUDModule.class).vanillaPotions.getValue().equalsIgnoreCase("Hide")) {
            info.cancel();
        }
    }

    @Inject(method = "renderPortalOverlay", at = @At("HEAD"), cancellable = true)
    private void renderPortalOverlay(DrawContext context, float nauseaStrength, CallbackInfo info) {
        if (Anatorius.MODULE_MANAGER.getModule(NoRenderModule.class).isToggled() && Anatorius.MODULE_MANAGER.getModule(NoRenderModule.class).portalOverlay.getValue()) {
            info.cancel();
        }
    }

    @Inject(method = "renderVignetteOverlay", at = @At("HEAD"), cancellable = true)
    private void renderVignetteOverlay(DrawContext context, Entity entity, CallbackInfo info) {
        if (Anatorius.MODULE_MANAGER.getModule(NoRenderModule.class).isToggled() && Anatorius.MODULE_MANAGER.getModule(NoRenderModule.class).vignette.getValue()) {
            info.cancel();
        }
    }

    @WrapWithCondition(method = "renderMiscOverlays", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud;renderOverlay(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/util/Identifier;F)V", ordinal = 0))
    private boolean renderPumpkinOverlay(InGameHud instance, DrawContext context, Identifier texture, float opacity) {
        return !(Anatorius.MODULE_MANAGER.getModule(NoRenderModule.class).isToggled() && Anatorius.MODULE_MANAGER.getModule(NoRenderModule.class).pumpkinOverlay.getValue());
    }

    @WrapWithCondition(method = "renderMiscOverlays", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud;renderOverlay(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/util/Identifier;F)V", ordinal = 1))
    private boolean renderSnowOverlay(InGameHud instance, DrawContext context, Identifier texture, float opacity) {
        return !(Anatorius.MODULE_MANAGER.getModule(NoRenderModule.class).isToggled() && Anatorius.MODULE_MANAGER.getModule(NoRenderModule.class).snowOverlay.getValue());
    }

    @Inject(method = "renderCrosshair", at = @At(value = "HEAD"), cancellable = true)
    private void renderCrosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo info) {
        if(Anatorius.MODULE_MANAGER.getModule(CrosshairModule.class).isToggled()) info.cancel();
    }
}
