package dev.project.anatorius.modules.impl.player;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.RenderWorldEvent;
import dev.project.anatorius.events.impl.TickEvent;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.modules.impl.visuals.BlockHighlightModule;
import dev.project.anatorius.utils.graphics.Renderer3D;
import dev.project.anatorius.utils.minecraft.WorldUtils;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;

@RegisterModule(name = "AirPlace", description = "Lets you place blocks in the air in servers that allow it.", category = Module.Category.PLAYER)
public class AirPlaceModule extends Module {
    private HitResult hitResult;

    @SubscribeEvent
    public void onTick(TickEvent event) {
        if(getNull()) return;

        hitResult = mc.getCameraEntity().raycast(mc.player.getBlockInteractionRange(), 0, false);

        if(!(hitResult instanceof BlockHitResult blockHitResult) || !(mc.player.getMainHandStack().getItem() instanceof BlockItem)) return;

        if(mc.options.useKey.isPressed() && mc.world.getBlockState(blockHitResult.getBlockPos()).getBlock().equals(Blocks.AIR)) {
            WorldUtils.placeBlock(blockHitResult.getBlockPos(), WorldUtils.getDirection(blockHitResult.getBlockPos(), false), Hand.MAIN_HAND, false, false);
        }
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldEvent event) {
        if(getNull()) return;

        BlockHighlightModule blockHighlightModule = Anatorius.MODULE_MANAGER.getModule(BlockHighlightModule.class);

        if(!blockHighlightModule.isToggled() || !(hitResult instanceof BlockHitResult blockHitResult) || !(mc.player.getMainHandStack().getItem() instanceof BlockItem)) return;

        if(mc.world.getBlockState(blockHitResult.getBlockPos()).getBlock().equals(Blocks.AIR)) {
            Box box = new Box(blockHitResult.getBlockPos());
            if (blockHighlightModule.mode.getValue().equalsIgnoreCase("Fill") || blockHighlightModule.mode.getValue().equalsIgnoreCase("Both")) Renderer3D.renderBox(event.getMatrices(), box, blockHighlightModule.fillColor.getColor());
            if (blockHighlightModule.mode.getValue().equalsIgnoreCase("Outline") || blockHighlightModule.mode.getValue().equalsIgnoreCase("Both")) Renderer3D.renderBoxOutline(event.getMatrices(), box, blockHighlightModule.outlineColor.getColor());
        }
    }
}
