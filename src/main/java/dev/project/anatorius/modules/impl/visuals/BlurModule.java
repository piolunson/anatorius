package dev.project.anatorius.modules.impl.visuals;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;

@RegisterModule(name = "Blur", description = "Blurs the game behind the client's menus (ClickGui, HUD editor). Strength: Minecraft's Menu Background Blur option.", category = Module.Category.VISUALS, toggled = true, drawn = false)
public class BlurModule extends Module {
    public static boolean active() {
        if (Anatorius.MODULE_MANAGER == null) return false;
        BlurModule blur = Anatorius.MODULE_MANAGER.getModule(BlurModule.class);
        return blur != null && blur.isToggled();
    }
}
