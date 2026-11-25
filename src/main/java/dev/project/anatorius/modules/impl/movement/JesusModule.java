package dev.project.anatorius.modules.impl.movement;

import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.BlockCollisionEvent;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.settings.impl.ModeSetting;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;

@RegisterModule(name = "Jesus", description = "Allows you to walk on water like Jesus.", category = Module.Category.MOVEMENT)
public class JesusModule extends Module {
    public ModeSetting mode = new ModeSetting("Mode", "The method used for walking on water.", "Solid", new String[]{"Solid", "Solid2"});

    @SubscribeEvent
    public void onBlockCollision(BlockCollisionEvent event) {
        if (event.getState().getBlock() instanceof FluidBlock) {
            event.setState(mode.getValue().equals("Solid") ? Blocks.ENDER_CHEST.getDefaultState() : Blocks.OBSIDIAN.getDefaultState());
        }
    }
}
