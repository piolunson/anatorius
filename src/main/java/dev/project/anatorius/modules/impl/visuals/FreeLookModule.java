package dev.project.anatorius.modules.impl.visuals;

import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.settings.impl.ModeSetting;
import lombok.Getter;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.MathHelper;

@Getter
@RegisterModule(name = "FreeLook", description = "Move your camera freely while your body keeps facing (and moving) its original direction.", category = Module.Category.VISUALS)
public class FreeLookModule extends Module {
    public ModeSetting perspective = new ModeSetting("Perspective", "The view to force while FreeLook is active.", "Third Person", new String[]{"Third Person", "First Person", "Keep"});

    private float cameraYaw, cameraPitch;
    private Perspective previousPerspective;

    public void applyMouse(double cursorDeltaX, double cursorDeltaY) {
        cameraPitch = MathHelper.clamp(cameraPitch + (float) cursorDeltaY * 0.15F, -90.0F, 90.0F);
        cameraYaw += (float) cursorDeltaX * 0.15F;
    }

    @Override
    public void onEnable() {
        if (getNull()) {
            setToggled(false);
            return;
        }

        cameraYaw = mc.player.getYaw();
        cameraPitch = mc.player.getPitch();
        previousPerspective = mc.options.getPerspective();

        switch (perspective.getValue()) {
            case "Third Person" -> mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            case "First Person" -> mc.options.setPerspective(Perspective.FIRST_PERSON);
            default -> {  }
        }
    }

    @Override
    public void onDisable() {
        if (mc.options != null && previousPerspective != null) {
            mc.options.setPerspective(previousPerspective);
        }
    }
}
