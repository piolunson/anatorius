package dev.project.anatorius.mixins;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.modules.impl.core.MenuModule;
import dev.project.anatorius.utils.IMinecraft;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public class ScreenMixin implements IMinecraft {
    @Shadow public int width;
    @Shadow public int height;

    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void renderBackground(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        MenuModule module = Anatorius.MODULE_MANAGER.getModule(MenuModule.class);
        if(module != null && module.isToggled() && module.mainMenu.getValue() && mc.world == null) {
            return;
        }
    }
}
