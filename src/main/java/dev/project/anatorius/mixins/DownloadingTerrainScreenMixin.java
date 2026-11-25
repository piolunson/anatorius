package dev.project.anatorius.mixins;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.modules.impl.core.MenuModule;
import dev.project.anatorius.utils.IMinecraft;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.LevelLoadingScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelLoadingScreen.class)
public class DownloadingTerrainScreenMixin extends Screen implements IMinecraft {
    @Shadow @Final private LevelLoadingScreen.WorldEntryReason reason;

    protected DownloadingTerrainScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void renderBackground(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (Anatorius.MODULE_MANAGER == null) return;
        MenuModule module = Anatorius.MODULE_MANAGER.getModule(MenuModule.class);
        if (module != null && reason.equals(LevelLoadingScreen.WorldEntryReason.OTHER) && module.isToggled() && module.mainMenu.getValue()) {
            return;
        }
    }
}
