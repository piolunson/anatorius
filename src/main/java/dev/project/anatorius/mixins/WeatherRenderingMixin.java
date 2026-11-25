package dev.project.anatorius.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.project.anatorius.modules.impl.visuals.AtmosphereModule;
import net.minecraft.client.render.WeatherRendering;
import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(WeatherRendering.class)
public class WeatherRenderingMixin {
    @ModifyExpressionValue(method = "getPrecipitationAt", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/biome/Biome;getPrecipitation(Lnet/minecraft/util/math/BlockPos;I)Lnet/minecraft/world/biome/Biome$Precipitation;"))
    private Biome.Precipitation getPrecipitationAt(Biome.Precipitation precipitation) {
        String weather = AtmosphereModule.weatherOverride();
        if (weather == null) return precipitation;
        return switch (weather) {
            case "Snow" -> Biome.Precipitation.SNOW;
            case "Rain" -> Biome.Precipitation.RAIN;
            default -> Biome.Precipitation.NONE;
        };
    }
}
