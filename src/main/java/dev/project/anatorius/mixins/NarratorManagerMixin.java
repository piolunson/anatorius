package dev.project.anatorius.mixins;

import dev.project.anatorius.modules.impl.miscellaneous.NoNarratorModule;
import net.minecraft.client.util.NarratorManager;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NarratorManager.class)
public class NarratorManagerMixin {
    @Inject(method = "isActive", at = @At("HEAD"), cancellable = true)
    private void isActive(CallbackInfoReturnable<Boolean> info) {
        if (NoNarratorModule.active()) info.setReturnValue(false);
    }

    @Inject(method = {"narrateChatMessage", "narrate", "narrateSystemMessage", "narrateSystemImmediately(Lnet/minecraft/text/Text;)V"}, at = @At("HEAD"), cancellable = true)
    private void narrateText(Text text, CallbackInfo info) {
        if (NoNarratorModule.active()) info.cancel();
    }

    @Inject(method = "narrateSystemImmediately(Ljava/lang/String;)V", at = @At("HEAD"), cancellable = true)
    private void narrateString(String text, CallbackInfo info) {
        if (NoNarratorModule.active()) info.cancel();
    }
}
