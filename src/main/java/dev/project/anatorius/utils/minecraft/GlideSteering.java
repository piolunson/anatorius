package dev.project.anatorius.utils.minecraft;

import dev.project.anatorius.modules.impl.combat.AutoMaceModule;
import dev.project.anatorius.modules.impl.movement.FakeFlyModule;
import net.minecraft.entity.Entity;

public final class GlideSteering {
    private GlideSteering() {}

    public static float[] look(Entity entity) {
        float[] look = AutoMaceModule.glideLook(entity);
        if (look != null) return look;
        look = dev.project.anatorius.modules.impl.movement.ElytraFlyModule.glideLook(entity);
        if (look != null) return look;
        return FakeFlyModule.glideLook(entity);
    }
}
