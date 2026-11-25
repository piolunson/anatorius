package dev.project.anatorius.modules.impl.movement;

import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.settings.impl.NumberSetting;

@RegisterModule(name = "IceSpeed", description = "Modifies your speed when walking on ice.", category = Module.Category.MOVEMENT)
public class IceSpeedModule extends Module {
    public NumberSetting speed = new NumberSetting("Speed", "The speed that will be applied when walking on ice.", 0.8f, 0.0f, 1.0f);

    @Override
    public String getMetaData() {
        return String.valueOf(speed.getValue().floatValue());
    }
}
