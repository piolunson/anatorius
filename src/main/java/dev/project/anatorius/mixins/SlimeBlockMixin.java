package dev.project.anatorius.mixins;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.modules.impl.movement.NoSlowModule;
import dev.project.anatorius.utils.IMinecraft;
import net.minecraft.block.BlockState;
import net.minecraft.block.SlimeBlock;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SlimeBlock.class)
public class SlimeBlockMixin implements IMinecraft {
    @Inject(method = "onSteppedOn", at = @At("HEAD"), cancellable = true)
    private void onSteppedOn(World world, BlockPos pos, BlockState state, Entity entity, CallbackInfo info) {
        if (entity == mc.player && Anatorius.MODULE_MANAGER.getModule(NoSlowModule.class).isToggled() && Anatorius.MODULE_MANAGER.getModule(NoSlowModule.class).slimeBlocks.getValue()) {
            info.cancel();
        }
    }

    @Inject(method = "bounce", at = @At("HEAD"), cancellable = true)
    private void bounce$noSlow(Entity entity, CallbackInfo info) {
        dev.project.anatorius.modules.impl.movement.NoSlowModule module = dev.project.anatorius.Anatorius.MODULE_MANAGER.getModule(dev.project.anatorius.modules.impl.movement.NoSlowModule.class);
        if (entity == mc.player && module != null && module.isToggled() && module.slimeBlocks.getValue() && module.noSlimeBounce.getValue()) {
            info.cancel();
        }
    }
}
