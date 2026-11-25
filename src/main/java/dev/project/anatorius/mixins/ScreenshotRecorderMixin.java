package dev.project.anatorius.mixins;

import dev.project.anatorius.modules.impl.miscellaneous.ExtraScreenshotModule;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.function.Consumer;

@Mixin(ScreenshotRecorder.class)
public class ScreenshotRecorderMixin {
    @ModifyVariable(method = "saveScreenshot(Ljava/io/File;Ljava/lang/String;Lnet/minecraft/client/gl/Framebuffer;ILjava/util/function/Consumer;)V", at = @At("HEAD"), argsOnly = true)
    private static Consumer<Text> wrapReceiver(Consumer<Text> receiver) {
        return message -> {
            receiver.accept(message);
            ExtraScreenshotModule.onScreenshotMessage(message);
        };
    }
}
