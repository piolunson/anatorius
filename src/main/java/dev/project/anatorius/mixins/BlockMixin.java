package dev.project.anatorius.mixins;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.modules.impl.movement.IceSpeedModule;
import dev.project.anatorius.modules.impl.movement.NoSlowModule;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Block.class)
public class BlockMixin {
    @Inject(method = "getSlipperiness", at = @At("HEAD"), cancellable = true)
    private void getSlipperiness(CallbackInfoReturnable<Float> info) {
        if ((((Object) this) == Blocks.ICE || ((Object) this) == Blocks.PACKED_ICE || ((Object) this) == Blocks.BLUE_ICE || ((Object) this) == Blocks.FROSTED_ICE) && Anatorius.MODULE_MANAGER.getModule(IceSpeedModule.class).isToggled()) {
            info.setReturnValue(1.0f - (0.8f * Anatorius.MODULE_MANAGER.getModule(IceSpeedModule.class).speed.getValue().floatValue()));
        }

        if ((Object) this == Blocks.SLIME_BLOCK && Anatorius.MODULE_MANAGER.getModule(NoSlowModule.class).isToggled() && Anatorius.MODULE_MANAGER.getModule(NoSlowModule.class).slimeBlocks.getValue()) {
            info.setReturnValue(0.6f);
        }

        NoSlowModule noSlow = Anatorius.MODULE_MANAGER.getModule(NoSlowModule.class);
        if (noSlow != null && noSlow.isToggled()) {
            Object block = this;
            if (noSlow.ice.getValue() && (block == Blocks.ICE || block == Blocks.PACKED_ICE || block == Blocks.BLUE_ICE)) info.setReturnValue(0.6f);
            if (noSlow.honeyBlocks.getValue() && block == Blocks.HONEY_BLOCK) info.setReturnValue(0.6f);
        }
    }
}
