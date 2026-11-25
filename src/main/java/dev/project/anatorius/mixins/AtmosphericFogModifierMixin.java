package dev.project.anatorius.mixins;

import dev.project.anatorius.modules.impl.visuals.AtmosphereModule;
import net.minecraft.client.render.fog.AtmosphericFogModifier;
import net.minecraft.client.render.fog.FogData;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AtmosphericFogModifier.class)
public class AtmosphericFogModifierMixin {
    @Inject(method = "getFogColor", at = @At("RETURN"), cancellable = true)
    private void getFogColor(ClientWorld world, Camera camera, int viewDistance, float skyDarkness, CallbackInfoReturnable<Integer> info) {
        AtmosphereModule module = AtmosphereModule.fogModule();
        if (module != null) info.setReturnValue(0xFF000000 | (module.fogColor.getColor().getRGB() & 0xFFFFFF));
    }

    @Inject(method = "applyStartEndModifier", at = @At("TAIL"))
    private void applyStartEndModifier(FogData data, Camera camera, ClientWorld world, float viewDistanceBlocks, RenderTickCounter counter, CallbackInfo info) {
        AtmosphereModule module = AtmosphereModule.fogModule();
        if (module == null) return;

        float start = module.fogStart.getValue().floatValue();
        float end = Math.max(module.fogEnd.getValue().floatValue(), start + 1.0f);
        data.environmentalStart = start;
        data.environmentalEnd = end;
        data.skyEnd = Math.min(data.skyEnd, Math.max(end, 32.0f));
        data.cloudEnd = Math.min(data.cloudEnd, Math.max(end, 32.0f));
    }
}
