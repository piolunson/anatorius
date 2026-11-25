package dev.project.anatorius.mixins.customsky;

import dev.project.anatorius.customsky.CustomSky;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.hud.BossBarHud;
import net.minecraft.client.render.GameRenderer;
import dev.project.anatorius.customsky.client.sky.SkyboxTextures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin({GameRenderer.class})
public abstract class GameRendererSkyMixin {
   @WrapOperation(
      method = {"renderWorld"},
      at = {@At(
   value = "INVOKE",
   target = "Lnet/minecraft/client/gui/hud/BossBarHud;shouldThickenFog()Z"
)},
      require = 0
   )
   private boolean cs$disableBossBarFogForCustomSky(BossBarHud bossBars, Operation<Boolean> original) {
      return (Boolean)original.call(new Object[]{bossBars}) && !(CustomSky.active() && SkyboxTextures.hasAltDimensionSkyOverride());
   }
}
