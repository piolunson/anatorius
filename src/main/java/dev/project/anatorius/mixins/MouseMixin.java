package dev.project.anatorius.mixins;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.impl.MouseInputEvent;
import dev.project.anatorius.events.impl.UnfilteredMouseInputEvent;
import dev.project.anatorius.modules.impl.visuals.ZoomModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.input.MouseInput;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class MouseMixin {
    @Shadow @Final private MinecraftClient client;

    @Inject(method = "onMouseButton", at = @At("HEAD"))
    private void onMouseButton(long window, MouseInput input, int action, CallbackInfo info) {
        int button = input.button();
        int mods = input.modifiers();
        Anatorius.EVENT_HANDLER.post(new UnfilteredMouseInputEvent(button, action, mods));
        if (window == client.getWindow().getHandle() && (action == 1 || action == 0) && client.currentScreen == null) {
            Anatorius.EVENT_HANDLER.post(new MouseInputEvent(button, action));
        }
    }

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void onMouseScroll(long window, double horizontal, double vertical, CallbackInfo info) {
        if (window != client.getWindow().getHandle() || client.currentScreen != null || client.player == null || Anatorius.MODULE_MANAGER == null) return;
        ZoomModule zoom = Anatorius.MODULE_MANAGER.getModule(ZoomModule.class);
        if (zoom != null && zoom.isToggled() && zoom.onScroll(vertical)) info.cancel();
    }
}
