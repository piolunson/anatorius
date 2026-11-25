package dev.project.anatorius.mixins;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.modules.impl.movement.FakeFlyModule;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.sound.SoundSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundManager.class)
public class SoundManagerMixin {
    @Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)Lnet/minecraft/client/sound/SoundSystem$PlayResult;", at = @At("HEAD"), cancellable = true)
    private void anatorius$muteFakeFlySwap(SoundInstance sound, CallbackInfoReturnable<SoundSystem.PlayResult> cir) {
        if (Anatorius.MODULE_MANAGER == null || sound == null) return;
        if ((FakeFlyModule.muted() && FakeFlyModule.isSwapSound(sound.getId())) || dev.project.anatorius.modules.impl.movement.ElytraFlyModule.mutes(sound.getId())) {
            cir.setReturnValue(SoundSystem.PlayResult.NOT_STARTED);
        }
    }
}
