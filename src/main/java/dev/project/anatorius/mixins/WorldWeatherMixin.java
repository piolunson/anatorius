package dev.project.anatorius.mixins;

import dev.project.anatorius.modules.impl.visuals.AtmosphereModule;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public abstract class WorldWeatherMixin {
    @Shadow protected float rainGradient;
    @Shadow protected float thunderGradient;

    @Shadow public abstract boolean canHaveWeather();

    @Inject(method = "getRainGradient", at = @At("HEAD"), cancellable = true)
    private void getRainGradient(float tickProgress, CallbackInfoReturnable<Float> info) {
        if (!((Object) this instanceof ClientWorld) || !canHaveWeather()) return;
        String weather = AtmosphereModule.weatherOverride();
        if (weather != null) info.setReturnValue(weather.equals("Clear") ? 0.0f : 1.0f);
    }

    @Inject(method = "isRaining", at = @At("HEAD"), cancellable = true)
    private void isRaining(CallbackInfoReturnable<Boolean> info) {
        if ((Object) this instanceof ClientWorld && AtmosphereModule.weatherOverride() != null) {
            info.setReturnValue(canHaveWeather() && rainGradient > 0.2f);
        }
    }

    @Inject(method = "isThundering", at = @At("HEAD"), cancellable = true)
    private void isThundering(CallbackInfoReturnable<Boolean> info) {
        if ((Object) this instanceof ClientWorld && AtmosphereModule.weatherOverride() != null) {
            info.setReturnValue(canHaveWeather() && thunderGradient * rainGradient > 0.9f);
        }
    }
}
