package dev.project.anatorius.modules.impl.miscellaneous;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;

@RegisterModule(name = "NoNarrator", description = "Disables the narrator features (Ctrl+B, chat and menu narration).", category = Module.Category.MISCELLANEOUS)
public class NoNarratorModule extends Module {
    public static boolean active() {
        if (Anatorius.MODULE_MANAGER == null) return false;
        NoNarratorModule module = Anatorius.MODULE_MANAGER.getModule(NoNarratorModule.class);
        return module != null && module.isToggled();
    }

    @Override
    public void onEnable() {
        mc.getNarratorManager().clear();
    }
}
