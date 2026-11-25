package dev.project.anatorius.modules.impl.core;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import lombok.Getter;
import net.minecraft.util.Identifier;

@Getter
@RegisterModule(name = "Cape", description = "Shows the Anatorius cape on yourself.", category = Module.Category.CORE, toggled = true, drawn = false)
public class CapeModule extends Module {
    private final Identifier capeTexture = Identifier.of(Anatorius.MOD_ID, "textures/cape.png");
}
