package dev.project.anatorius.mixins;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.impl.KeyInputEvent;
import dev.project.anatorius.events.impl.UnfilteredKeyInputEvent;
import dev.project.anatorius.modules.impl.core.ClickGuiModule;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyInput;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public class KeyboardMixin {
    @Shadow @Final private MinecraftClient client;

    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void onKey(long window, int action, KeyInput input, CallbackInfo info) {
        int key = input.key();
        int scancode = input.scancode();
        int modifiers = input.modifiers();
        Anatorius.EVENT_HANDLER.post(new UnfilteredKeyInputEvent(key, scancode, action, modifiers));
        if (window == client.getWindow().getHandle() && (action == 1 || action == 0) && client.currentScreen == null) {
            Anatorius.EVENT_HANDLER.post(new KeyInputEvent(key, modifiers, action));
        }

        if (window == client.getWindow().getHandle() && action == 1
                && client.currentScreen instanceof net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen screen
                && Anatorius.MODULE_MANAGER != null) {
            ClickGuiModule clickGui = Anatorius.MODULE_MANAGER.getModule(ClickGuiModule.class);
            if (clickGui != null && key != org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN && key == clickGui.getBind()) {
                clickGui.openFrom(screen);
                info.cancel();
            }
        }
    }
}
