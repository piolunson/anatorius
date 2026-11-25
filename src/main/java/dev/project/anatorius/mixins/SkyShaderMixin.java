package dev.project.anatorius.mixins;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.modules.impl.visuals.SkyboxModule;
import net.minecraft.client.render.SkyRendering;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRendering.class)
public abstract class SkyShaderMixin {
    @Inject(method = "renderTopSky", at = @At("RETURN"))
    private void anatorius$skyShader(int skyColor, CallbackInfo ci) {
        SkyboxModule module = module();
        if (module != null) module.renderSky(false);
    }

    @Inject(method = "renderEndSky", at = @At("RETURN"))
    private void anatorius$endSkyShader(CallbackInfo ci) {
        SkyboxModule module = module();
        if (module != null) module.renderSky(true);
    }

    private static SkyboxModule module() {
        return Anatorius.MODULE_MANAGER == null ? null : Anatorius.MODULE_MANAGER.getModule(SkyboxModule.class);
    }
}
