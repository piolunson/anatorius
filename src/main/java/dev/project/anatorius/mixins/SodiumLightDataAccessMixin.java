package dev.project.anatorius.mixins;

import dev.project.anatorius.modules.impl.visuals.XrayModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.model.light.data.LightDataAccess", remap = false)
public class SodiumLightDataAccessMixin {
    @Inject(method = "compute(III)I", at = @At("RETURN"), cancellable = true, require = 0)
    private void xrayLight(int x, int y, int z, CallbackInfoReturnable<Integer> info) {
        if (XrayModule.isActive()) info.setReturnValue(info.getReturnValue() | 0xFF);
    }
}
