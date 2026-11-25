package dev.project.anatorius.modules.impl.player;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.settings.impl.BooleanSetting;

@RegisterModule(name = "NoGlitchBlocks", description = "Makes sure there are no ghost blocks as you interact with them.", category = Module.Category.PLAYER)
public class NoGlitchBlocksModule extends Module {
    public BooleanSetting breaking = new BooleanSetting("Breaking", "Broken blocks disappear when the server confirms the break.", true);
    public BooleanSetting placing = new BooleanSetting("Placing", "Placed blocks appear when the server confirms the placement.", true);

    public static boolean manualUse;

    private static NoGlitchBlocksModule get() {
        return Anatorius.MODULE_MANAGER == null ? null : Anatorius.MODULE_MANAGER.getModule(NoGlitchBlocksModule.class);
    }

    public static boolean blocksBreakPrediction() {
        NoGlitchBlocksModule module = get();
        return module != null && module.isToggled() && module.breaking.getValue();
    }

    public static boolean blocksPlacePrediction() {
        NoGlitchBlocksModule module = get();
        return manualUse && module != null && module.isToggled() && module.placing.getValue();
    }
}
